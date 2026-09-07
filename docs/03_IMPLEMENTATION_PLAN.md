# 03. 実装プラン

**各 Phase の完了条件を満たすまで次に進まない。**
「あとでまとめて動かす」は禁止。Phase ごとに実機で動かして確認する。

---

## Phase 0 — プロジェクト初期化とビルド疎通

**目的：空のアプリが Pixel 9a で起動するところまで。ここで詰まる人が一番多い。**

- [ ] Android Studio で Empty Activity (Compose) プロジェクトを作成
      - Package: `com.aki.tasktimer` / minSdk 34
- [ ] `docs/02_ARCHITECTURE.md` のバージョンに合わせて `libs.versions.toml` を整備
- [ ] ダークテーマのみの Material 3 テーマを設定
- [ ] 実機で「Hello」が表示される

**完了条件**：`./gradlew assembleDebug` が通り、Pixel 9a 実機でアプリが起動する。

> ⚠️ ここで Gradle のバージョン不整合が出やすい。エラーが出たら
> **バージョンを闇雲に上げ下げせず、エラーメッセージが指している依存関係を特定する**こと。

---

## Phase 1 — データ層

**目的：DB に書ける・読める。UI はまだない。**

- [ ] Room の Entity 4 種（Session / Extension / TaskPreset / ExtensionPreset）
- [ ] DAO（`SessionDao`, `PresetDao`）
- [ ] `TaskTimerDatabase` ＋ 初期シード（Tag 6 種、ExtensionPreset 5/10/15/30）
- [ ] `SessionRepository` / `PresetRepository`
      - `observeRunningSession(): Flow<Session?>`
      - `startSession(...)`, `finishSession(...)`, `addExtension(...)`
      - **「RUNNING は最大 1 件」を Repository 層で保証する**
- [ ] `domain/TimeCalculator.kt`（純関数）
- [ ] `domain/DailyAggregator.kt`（純関数）
- [ ] **ユニットテストを書く**：`TimeCalculator` 全関数、`splitByDay` の境界ケース

**完了条件**：`./gradlew test` が全部グリーン。特に日またぎ按分のテストが通っている。

---

## Phase 2 — 基本 UI（タイマーとして最低限成立する）

**目的：手動で開始・停止できる。まだ通知もアラームもない。**

- [ ] `MainActivity` ＋ Navigation Compose の骨格
- [ ] ホーム画面（進行中 / 未計測の 2 状態）
- [ ] 経過時間表示（1 秒更新。`now - startedAt` 方式）
- [ ] タスク切り替えフロー Step 1〜4
      - Step 2 のプリセットグリッドは**スクロールなしで 6〜8 個見える**こと
- [ ] Preset の自動学習（開始のたびに upsert ＋ `useCount++`）

**完了条件**：アプリを開いてタスクを開始し、切り替え、評価を入力して DB にレコードが残る。
アプリを閉じて開き直しても経過時間が正しい。

---

## Phase 2.5 — Notion への自動記録（2026-09-07 に前倒しで追加）

**目的：MacroDroid を止めたので、アプリが直接 Notion のタイムログに書く。記録の空白を作らない。**

当初は v2（Phase 7 の後）だったが、Aki の判断で Phase 3 の前に入れた。仕様は `docs/01_SPEC.md` 6.3、
Notion 側の詳細は `docs/06_NOTION_INTEGRATION.md`。

- [x] 「中断（記録して停止）」ボタンを廃止し、「切り替える」だけにする（仕様修正）
- [x] Room を version 2 に（`notionPageId` 列、送信待ち行列 `sync_queue`）。本物のマイグレーション
- [x] 開始時に「進行中」ページを作成、終了時に「完了」へ更新。日またぎは 0:00 で分割
- [x] オフライン時は溜めて、ネット復帰後に WorkManager が順番どおり自動送信
- [x] トークンは Android Keystore で暗号化して DataStore に保存
- [x] 設定画面（連携 ON/OFF・トークン・DB ID・送信状況・再試行・今すぐ送信・日またぎテスト）
- [x] ユニットテスト：日またぎの区間分割、Notion に送る JSON の組み立て
- [ ] **実機確認**（下のチェックリスト）

**完了条件**：タスクを開始→切り替えで Notion に「進行中」→「完了」が反映される。
機内モードで開始した分がネット復帰後に送られる。日またぎのテスト記録が 2 行になる。

> ⚠️ MacroDroid の 2 マクロと GAS の 23:59 分割は停止済み。再び動かすと行が二重になる。

---

## Phase 3 — 常駐通知（フォアグラウンドサービス）

**目的：アプリを閉じても通知バーで見える。**

- [ ] `TimerService`（`foregroundServiceType="specialUse"`）
- [ ] 通知チャンネル 2 種
      - `CHANNEL_ONGOING`（`IMPORTANCE_LOW`）… 常駐通知
      - `CHANNEL_OVERDUE`（`IMPORTANCE_HIGH`）… 超過通知
- [ ] 常駐通知：タスク名・タグ・ゴール・経過/残り時間・「切り替え」アクション
- [ ] セッション開始でサービス起動、終了でサービス停止
- [ ] `POST_NOTIFICATIONS` の実行時要求

**完了条件**：アプリをタスクキルしても通知が残り、時間が更新され続ける。
通知の「切り替え」から切り替えフローが開く。

---

## Phase 4 — アラームと超過画面（★このアプリの核）

**目的：予定時間が来たら全画面で出る。**

- [ ] `AlarmScheduler`（`setExactAndAllowWhileIdle`）
- [ ] `OverdueReceiver`
- [ ] `OverdueActivity`（`showWhenLocked` / `turnScreenOn` / `excludeFromRecents`）
- [ ] full-screen intent 通知（`PRIORITY_HIGH` ＋ `CATEGORY_ALARM`）
- [ ] バイブレーション（音は鳴らさない）
- [ ] 超過画面 UI
      - ゴール再掲（タスク名は出さない）
      - 経過 / 予定 / **超過率**（分母は当初予定。150% 超で警告色）
      - 延長プリセット（**選択式**。押しても即実行しない）
      - **「合計所要時間」が 1 か所だけ更新される**
      - 「延長する」/「タスクを終える」
- [ ] 延長時：`Extension` 追加 → `totalPlannedMinutes` 更新 → アラーム再登録
- [ ] 「終える」時：評価ステップ（Step 1）に遷移
- [ ] `BootReceiver`（再起動後のアラーム再登録）
- [ ] `USE_FULL_SCREEN_INTENT` 未付与時のデグレード動作

**完了条件**：`docs/05_ANDROID_CONSTRAINTS.md` 6 章のチェックリストのうち、
オーバーレイ関連以外がすべて ✅。**画面を消して放置しても正確に鳴る**こと。

---

## Phase 5 — オーバーレイブロック（強制力）

**目的：ホームボタンで逃げられなくする。**

- [ ] `SYSTEM_ALERT_WINDOW` の許可導線
- [ ] `BlockerOverlayService`（`TYPE_APPLICATION_OVERLAY`、全画面）
- [ ] 超過時：full-screen intent ＋ オーバーレイを同時起動
- [ ] 「延長」「終える」の確定でオーバーレイ解除
- [ ] **非常口：画面右上を 3 秒長押し → 「強制的に閉じる」確認ダイアログ**
- [ ] 設定画面で強制力を完全 OFF にできるトグル
- [ ] 権限なし／例外発生時はオーバーレイなしで続行（クラッシュさせない）

**完了条件**：超過画面でホームボタンを押しても引き戻される。
かつ、非常口から確実に脱出できる。強制力 OFF にすると Phase 4 の挙動に戻る。

> ⚠️ **この Phase は端末が操作不能になるリスクがある。**
> 実機でテストする前に必ず非常口を先に実装し、非常口のテストを最初に行うこと。
> ADB (`adb shell am force-stop com.aki.tasktimer`) で強制終了できることも確認しておく。

---

## Phase 6 — 履歴と集計

- [ ] 履歴画面（日付グルーピング、新しい順）
- [ ] 日別合計とタグ別内訳バー
- [ ] セッション詳細（ゴール、評価理由、延長履歴）
- [ ] タスク別の統計（平均所要時間、平均超過率、平均延長回数）
- [ ] 日をまたぐセッションの按分表示（`DailyAggregator` を使う）

**完了条件**：数日分のデータを入れて、日別合計が手計算と一致する。

---

## Phase 7 — 仕上げ

- [ ] 設定画面（延長プリセット編集、タスクプリセット削除、権限状態、強制力トグル）
- [ ] 初回起動オンボーディング（権限を順に取得）
- [ ] データエクスポート（JSON / CSV）
- [ ] アプリアイコン
- [ ] リリースビルドを作って実機にインストール、日常運用に入る

---

## 進め方のルール

1. **Phase をまたいで先走らない。** Phase 4 の途中で履歴画面を作り始めない
2. **各 Phase の終わりに実機で動かす。** エミュレータだけで済ませない
   （オーバーレイと full-screen intent はエミュレータで挙動が違う）
3. **ビルドが通らないときにバージョンを闇雲に変えない。** エラーの原因を特定する
4. Phase 4 と 5 は特に壊れやすい。**小さく作って都度実機確認**する
5. 仕様に迷ったら `docs/01_SPEC.md` に戻る。書いてなければ Aki に聞く
