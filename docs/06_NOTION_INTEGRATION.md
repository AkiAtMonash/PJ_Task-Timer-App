# 06. Notion 連携（v2 向けの記録）

**v1 では実装しない。** ここは既存システムの仕様を失わないための記録であり、
v2 で同期を作るときの設計材料。

出典：Notion「時間計測自動化：運用マニュアル」（2026-01-07 更新）

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

**TaskTimer が置き換えるのは [1] と [2]。**
[3] の GAS は当面そのまま残す（v2 で同期を作るときに再検討）。

---

## 2. Notion 側の DB スキーマ

DB ID: `18a0bc4f73378145ae19d00b3921f39b`

| プロパティ | 型 | TaskTimer 側の対応 |
|---|---|---|
| 名前 | title | `Session.name` |
| status | status | `Session.status`（進行中 / 完了 / 引継ぎ待ち / 未着手） |
| time | date（範囲） | `startedAt` 〜 `endedAt` |
| タグ | multi_select | `Session.tag` |
| 評価 | select | `Session.rating`（◯（良い）/ △（普通）/ ✕（悪い）） |
| メモ | text | `Session.ratingNote` |
| ゴール | text | `Session.goal` |
| 予定時間（分） | number | `Session.plannedMinutes` |
| 種別 | status | ログ / 予定 |
| 時間 | formula | 自動計算（下記） |
| 参考ページ | url | v1 では未使用 |
| 日付 | date | 用途未確認 |

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

## 5. v2 で同期を作るときの設計方針

### 5.1 ローカルが正、Notion は写し

TaskTimer の Room DB を**唯一の真実**とし、Notion へは一方向に push する。
双方向同期は競合解決が地獄になるので v2 でもやらない。

### 5.2 同期キューを持つ

オフラインでも計測は止まらないことが最優先。

```
SyncQueue テーブル
  id, sessionId, operation (CREATE/UPDATE), payload, status, retryCount, lastError
```

- セッションの開始・終了・延長のたびにキューに積む
- WorkManager（`NetworkType.CONNECTED` 制約）でバックグラウンド送信
- 失敗したら指数バックオフでリトライ
- `Session` に `notionPageId: String?` を追加して、更新時に使う

### 5.3 日またぎの分割は同期時に行う

v1 の設計どおり、ローカルでは 1 レコードのまま保持する（`docs/01_SPEC.md` 6.1）。
Notion へ送るときだけ、既存スキーマに合わせて日境界で分割して複数ページとして送る。

`DailyAggregator.splitByDay()` をそのまま流用できる。

**この方針を取るなら、GAS の 23:59 分割は不要になるので停止する。**
アプリと GAS が両方分割すると二重分割で壊れる。**v2 実装時に必ず GAS を止めること。**

### 5.4 トークンの保管

Notion の Integration Token は機密情報。

- **ソースコードにハードコードしない**
- `EncryptedSharedPreferences` または DataStore ＋ Android Keystore で暗号化して保存
- 設定画面から入力させる

### 5.5 移行期の運用

v2 リリース時、既存の MacroDroid マクロと TaskTimer が両方 Notion に書くと重複する。
**切り替えの日を決めて、MacroDroid のマクロを無効化してから TaskTimer の同期を有効にする。**

---

## 6. 参照

- Notion「時間計測自動化：運用マニュアル」
  https://app.notion.com/p/2c00bc4f73378160a8e4e3486d4e3dd4
- Notion「pixel 9a標準のタイマー使いづらいから自作する。」（この企画の発端）
  https://app.notion.com/p/3a70bc4f733780adaa05c5af5980a6ca
