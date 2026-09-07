# CLAUDE.md — Claude Code への指示書

## このプロジェクトについて

Pixel 9a 用のネイティブ Android アプリ「TaskTimer」を、**ゼロから**実装する。
タスクの時間計測ツール。予定時間を超えたら全画面で強制的に「延長か終了か」を選ばせる。

## 最初にやること

以下を**この順で**読む。読まずに実装を始めない。

1. `README.md` — 全体像
2. `docs/01_SPEC.md` — **機能仕様の正典**
3. `docs/02_ARCHITECTURE.md` — 技術構成
4. `docs/05_ANDROID_CONSTRAINTS.md` — **権限と OS 制約。ここを読まずに書くと必ず壊れる**
5. `docs/03_IMPLEMENTATION_PLAN.md` — Phase 0 から着手

## ユーザーについて

- 名前：Aki
- **Android アプリ開発は未経験。** Web アプリの開発経験はある
- Android Studio は未インストールの可能性が高い（`docs/04_SETUP.md` を案内する）
- Gradle、AGP、KSP、フォアグラウンドサービスといった Android 固有の概念は初見だと思って説明する
- **日本語で応答する**

### 計画・提案の書き方（2026-09-07 に Aki から明示的に依頼）

計画（プランモードのファイル、Phase の提案、仕様のすり合わせ）は
**エンジニアではない人がそのまま読める文章**で書く。

- 書く：ユーザーが画面で何をするか、そのとき何が起きるか、「操作 → 起きること」の表、使用感、手順、限界
- 書かない：関数名、ファイル名、クラス名、ライブラリの説明、「○○を使います」という技術選定の羅列
- 理由：Aki は「この関数を使います」と言われても正しいか判断できない。
  「こう動く」と書かれていれば「そこは違う／こうしてほしい」と指示が出せる
- 技術的な設計メモが必要なら会話の中（自分用）に留め、プランファイルには入れない

---

## 進め方の原則

### 1. Phase 単位で進み、都度止まる

`docs/03_IMPLEMENTATION_PLAN.md` の Phase 0〜7 を順に進める（Phase 2.5 = Notion 同期は 2026-09-07 に前倒しで実装済み）。
**1 つの Phase が完了条件を満たしたら、必ず Aki に報告して実機確認を促し、返事を待つ。**
勝手に次の Phase に進まない。

### 2. 実機確認を挟む

Android は「コードは正しいのに実機で動かない」が日常的に起きる。
特に権限・通知・アラーム・オーバーレイは**必ず実機で確認**する。
エミュレータでは full-screen intent とオーバーレイの挙動が実機と異なる。

Aki が実機確認をやりやすいように、**確認手順を具体的に書いて渡す**こと。
「動作確認してください」ではなく、
「① タイマーを 1 分で開始 ② 画面を消す ③ 1 分待つ ④ 画面が点いて超過画面が出るか」と書く。

### 3. ビルドエラーの直し方

Gradle まわりは初見だと迷宮なので、以下を守る。

- ❌ **バージョンを闇雲に上げ下げしない**
- ✅ エラーメッセージが指している依存関係・タスク名を特定してから直す
- ✅ 同じエラーで 2 回失敗したら、**推測で直し続けずに Aki に状況を説明する**
- ✅ バージョンは `gradle/libs.versions.toml` に集約。`build.gradle.kts` に直書きしない

### 4. 質問すべきとき

以下は勝手に決めず、必ず聞く。

- `docs/01_SPEC.md` に書かれていない仕様の判断
- 新しいライブラリの追加（`docs/02_ARCHITECTURE.md` の選定を変える）
- Phase の順序を変えたいとき
- スコープ外（`docs/01_SPEC.md` 7 章）のことをやりたくなったとき

### 5. コミット

各 Phase の完了時、および意味のある単位でコミットする。
コミットメッセージは日本語で、何を実現したかを書く（例：`Phase 3: 常駐通知の実装`）。

---

## コーディング規約

### 全般

- Kotlin。Java は書かない
- UI は Jetpack Compose のみ。XML レイアウトは書かない
  （ただしオーバーレイの `WindowManager` 用ビューは Compose を `ComposeView` で載せる）
- **DI は手動**（`AppContainer`）。**Hilt を導入しない**
- コメントは日本語。ただし「何をしているか」ではなく「**なぜそうしたか**」を書く

### 命名

- Composable は名詞（`HomeScreen`, `TagChip`）
- ViewModel の公開状態は `uiState: StateFlow<XxxUiState>` の 1 本にまとめる
- Entity は `XxxEntity`、ドメインモデルは `Xxx`。**両者を混ぜない**

### 状態管理（重要）

- **進行中セッションの唯一の正は Room の `status = RUNNING` レコード**
- サービス・ViewModel・オーバーレイがそれぞれ状態を持たない。全員が
  `SessionRepository.observeRunningSession()` を購読する
- **経過時間はカウンタで積算しない。** 必ず `now - session.startedAt` で計算する
- **期限判定は `AlarmManager` だけが行う。** サービスのティックは表示更新専用

### 純関数とテスト

`domain/` 配下は副作用ゼロの純関数にし、**ユニットテストを必ず書く**。
特に `DailyAggregator.splitByDay()`（日またぎの按分）は境界ケースを厚くテストする。

実機ビルドは遅いので、ロジックの検証は `./gradlew test` で回す。

この PC（knak3）では `java` が PATH に無い。Gradle を CLI で回すときは
`JAVA_HOME=C:/Users/knak3/Desktop/App_Android-Studio/jbr` を指定する（Android Studio 同梱の JDK）。
adb は `C:/Users/knak3/AppData/Local/Android/Sdk/platform-tools/adb.exe`。

---

## 絶対にやってはいけないこと

| ❌ | 理由 |
|---|---|
| `TimerService` のカウンタで期限を判定する | Doze で止まると鳴らない |
| `AlarmManager.setExact()` を使う | Doze 中に発火しない。`setExactAndAllowWhileIdle()` を使う |
| `foregroundServiceType="shortService"` | 3 分で強制終了される。`specialUse` を使う |
| `SCHEDULE_EXACT_ALARM` を宣言する | `USE_EXACT_ALARM` を使う。両方宣言もダメ |
| オーバーレイに非常口を実装しない | **バグると端末が操作不能になる。事故る** |
| 権限がある前提でクラッシュさせる | 権限は剥奪されうる。確認してデグレード動作する |
| Hilt を入れる | KSP との組み合わせでビルドが不安定。手動 DI で足りる |
| スコープ外の機能を作る | `docs/01_SPEC.md` 7 章を参照 |
| `sync/` 以外から Notion API を叩く | Notion への送信は `sync/` の送信待ち行列経由だけ。画面や Repository から直接叩かない |
| ホームに「中断（記録して停止）」を戻す | 記録が止まる瞬間を作らない。終えるときは必ず次を始める |
| ライトテーマを作る | ダークモードのみ |

---

## Phase 5（オーバーレイ）の安全手順

この Phase は端末が操作不能になるリスクがある。**必ずこの順で作る。**

1. まず**非常口**（画面右上 3 秒長押し → 強制終了）を実装する
2. 非常口が動くことを、ブロック機能を有効にする前にテストする
3. `adb shell am force-stop com.aki.tasktimer` で強制終了できることを Aki に確認してもらう
4. そのうえでブロック機能を有効にする

オーバーレイの起動は必ず `try/catch` で囲み、失敗しても通常動作を続ける。

---

## 用語の対訳（Aki への説明用）

Android 未経験者に説明するとき、以下の対応を意識すると通じやすい。

| Android | Web で言うと |
|---|---|
| Activity | ページ（画面全体） |
| Composable | React のコンポーネント |
| ViewModel | 状態管理層（Redux store / Zustand に近い） |
| Room | ORM（Prisma、TypeORM に近い） |
| DataStore | localStorage の型安全版 |
| Foreground Service | Service Worker の常駐版（ただし OS に殺されにくい） |
| AlarmManager | cron。OS が時刻になったら起こしてくれる |
| Gradle | npm + webpack を足したもの |
| `libs.versions.toml` | package.json の dependencies |
| AGP | ビルドツールチェーン本体 |
