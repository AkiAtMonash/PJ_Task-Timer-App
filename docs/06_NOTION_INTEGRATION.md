# 06. Notion 連携

**2026-09-07 に v1（Phase 2.5）で実装済み。** MacroDroid を止めたため前倒しした。
アプリ側の挙動は `docs/01_SPEC.md` 6.3、実装は `sync/` パッケージと `data/prefs/`。
ここは Notion 側の仕様と運用の記録。

出典：Notion「時間計測自動化：運用マニュアル」（2026-01-07 更新）＋ 2026-09-07 に MCP で確認した現物のスキーマ

---

## 1. 既存システムの構成

現在は MacroDroid + GAS + Notion で運用している。

```
[1] MacroDroid マクロ「時間計測」
     ホーム画面ウィジェット押下でタスク切り替え
     → 経過時間表示・評価・理由メモ・ゴール・予定時間を入力
     → Notion API で前タスクを「完了」に、新タスクを「進行中」で作成
     → Stopwatch "task_timer" を Reset & Start

[2] MacroDroid マクロ「超過検知」
     Stopwatch が estimated_seconds_global に達したらトリガー
     → 現状はプレースホルダー通知のみ

[3] GAS 日次オートメーション（毎日 23:59）
     日をまたぐタスクを自動分割
     「進行中」→「引継ぎ待ち」に変え、翌日 00:00 開始の新レコードを作成

[4] Cloudflare Workers（未実装）
     超過を Webhook で受けて LINE 通知する構想
```

**TaskTimer が置き換えたのは [1] [2] [3]。** 2026-09-07 時点で MacroDroid の 2 マクロと GAS は停止済み。
再び動かすと行が二重になる（アプリも日またぎを分割するため）。

---

## 2. Notion 側の DB スキーマ（2026-09-07 に現物を確認）

DB ID: `18a0bc4f73378145ae19d00b3921f39b`
データソース: `collection://18a0bc4f-7337-81e3-81b2-000bbed29a72`

| プロパティ | 型 | TaskTimer から送る値 |
|---|---|---|
| 名前 | title | `Session.name` |
| status | status | 開始時 `進行中`、終了時 `完了`（選択肢は 予定 / 引継ぎ待ち / 進行中 / 完了） |
| time | date（範囲） | 開始時 start のみ、終了時 start と end（区間の両端） |
| タグ | multi_select | `Session.tag.label` |
| 評価 | select | 終了時のみ。◯（良い）/ △（普通）/ ✕（悪い） |
| メモ | text | 終了時のみ。`Session.ratingNote`。空なら「特に無し」 |
| ゴール | text | `Session.goal`。空ならプロパティごと省く |
| 予定時間（分） | number | `Session.plannedMinutes`（当初の見積もり。延長分は足さない） |
| 日付 | formula | 送らない（Notion 側で計算） |
| 時間 | formula | 送らない（下記） |
| 重複検知用 | checkbox | 送らない（別のエージェントが使う） |

※ 旧マニュアルにあった「種別」「参考ページ」は現物には存在しない。

### 「時間」プロパティの数式

```
dateBetween(dateEnd(prop("time")), dateStart(prop("time")), "minutes") / 60
```

→ 経過時間を**時間単位（小数）**で出力。

### タグの選択肢（TaskTimer のシードと一致させること）

| タグ | 色 | 用途 |
|---|---|---|
| Sleep | 紫 | 睡眠 |
| Uni Study | 黄 | 大学の勉強 |
| Job Hunting | 赤 | 就活 |
| Hobby | 青 | 趣味 |
| Chore | オレンジ | 雑務・家事 |
| For Myself | 緑 | 自分のための時間 |

### 評価の選択肢

| 値 | 色 | TaskTimer の enum |
|---|---|---|
| ◯（良い） | green | `GOOD` |
| △（普通） | yellow | `NORMAL` |
| ✕（悪い） | red | `BAD` |

`NORMAL` のときの `ratingNote` は既存システムでは
`"特に無し（空欄だとPOST時にエラーになりそうなので）。"` を入れている。
**空文字を POST するとエラーになる可能性があるため、必ず何か入れる。**

---

## 3. status の状態遷移

```
┌──────────┐   切り替え    ┌──────────┐
│  進行中   │ ──────────→ │   完了   │
└──────────┘              └──────────┘
     ↑                          │
     │ 新規作成                  │
     │    ┌──────────────┐      │
     └────│  引継ぎ待ち   │←─────┘
          └──────────────┘  翌日の切り替え時に完了へ
               ↑
               │ GAS 日次処理（23:59）
          ┌──────────┐
          │  進行中   │ ← 日をまたぐタスク
          └──────────┘
```

「引継ぎ待ち」が必要な理由：
23:00 開始・翌 10:00 終了のタスクを 1 レコードで持つと、どちらの日に集計すべきか決まらない。
そこで 23:59 に分割し、当日分（23:00〜23:59）と翌日分（00:00〜）に分ける。

---

## 4. API エンドポイント

| 用途 | URL |
|---|---|
| DB クエリ | `POST https://api.notion.com/v1/databases/{database_id}/query` |
| ページ更新 | `PATCH https://api.notion.com/v1/pages/{page_id}` |
| ページ作成 | `POST https://api.notion.com/v1/pages` |

ヘッダ：
```
Authorization: Bearer {token}
Notion-Version: 2022-06-28
Content-Type: application/json
```

### 新規タスク作成のペイロード例

```json
{
  "parent": { "database_id": "18a0bc4f73378145ae19d00b3921f39b" },
  "properties": {
    "名前":   { "title": [{ "text": { "content": "ES執筆" } }] },
    "status": { "status": { "name": "進行中" } },
    "time":   { "date": { "start": "2026-07-29T14:00:00+09:00" } },
    "ゴール":  { "rich_text": [{ "text": { "content": "A社のガクチカを書き切る" } }] },
    "予定時間（分）": { "number": 45 }
  }
}
```

日時は **ISO 8601 ＋ タイムゾーンオフセット**（`+09:00`）で送る。

---

## 5. 実装した設計（Phase 2.5）

### 5.1 ローカルが正、Notion は写し

TaskTimer の Room DB を**唯一の真実**とし、Notion へは一方向に push する。
双方向同期は競合解決が地獄になるのでやらない。

### 5.2 送信待ち行列

オフラインでも計測は止まらないことが最優先。

- `sync_queue` テーブル（id, sessionId, operation = CREATE/FINISH, status = PENDING/DONE/FAILED, retryCount, lastError, createdAt）
- セッションの開始・終了と**同じトランザクション**で積む。延長では積まない
- 「連携 ON 後に開始したセッションだけ送る」は、CREATE が積まれていないセッションの FINISH を積まないことで実現
- WorkManager（ネット接続時のみ・指数バックオフ）が id 順に消化する。1 件の失敗で全体を止めないが、順序は守る
- `Session.notionPageId` に作成したページ id を入れ、終了時の更新に使う
- 直さないと通らない失敗（401 など）は FAILED に落として設定画面に出す。「再試行」で PENDING に戻す
- 通信エラー・429・5xx は 15 回まで再送してから FAILED

### 5.3 日またぎの分割は送信時に行う

ローカルでは 1 レコードのまま保持する（`docs/01_SPEC.md` 6.1）。
送るときだけ 0:00 で区間に切り、先頭区間は既存ページを「完了」に書き換え、2 日目以降は「完了」で新規作成する。
区間の切り方は `splitSegmentsByDay()`、割り付けは `planFinish()`（どちらも純関数でテスト済み）。

**GAS の 23:59 分割は停止済み。** アプリと GAS が両方分割すると二重分割で壊れる。

### 5.4 トークンの保管

- ソースコードにハードコードしない
- Android Keystore の AES/GCM 鍵で暗号化し、暗号文だけを DataStore に保存
  （`EncryptedSharedPreferences` は非推奨になったので使わない）
- 復号に失敗したら（鍵の無効化・バックアップ復元）例外にせず「再入力」を促す
- 設定画面から入力する。DB ID も設定画面で変えられる（既定値は上の ID）

### 5.5 運用上の注意

- MacroDroid のマクロと GAS は停止済み。再び動かさない
- 送信は成功したのに応答だけ届かなかった場合、再送で同じ行が 2 つできることがある（v1 では許容）
- 日付の区切りは端末のタイムゾーンに従う

---

## 6. 参照

- Notion「時間計測自動化：運用マニュアル」
  https://app.notion.com/p/2c00bc4f73378160a8e4e3486d4e3dd4
- Notion「pixel 9a標準のタイマー使いづらいから自作する。」（この企画の発端）
  https://app.notion.com/p/3a70bc4f733780adaa05c5af5980a6ca
