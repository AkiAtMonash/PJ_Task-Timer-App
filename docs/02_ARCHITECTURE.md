# 02. 技術構成

## 1. バージョン方針（2026年7月時点）

Play ストアに出さず自分の Pixel 9a にサイドロードするだけなので、
**最新版に寄せて、古い端末向けの分岐は一切書かない。**

| 項目 | 値 | 補足 |
|---|---|---|
| `compileSdk` | 36 | Android 16 |
| `targetSdk` | 36 | |
| `minSdk` | 34 | Android 14。Pixel 9a は Android 15 出荷なので余裕がある。低くすると権限まわりの分岐が増えるだけで無益 |
| Android Gradle Plugin | 9.0.x | Kotlin Gradle Plugin 2.2.10 を内包するので Kotlin 版を別途宣言する必要がない |
| Kotlin | 2.2.10（AGP 同梱） | |
| Compose BOM | `2026.06.00` | 個別のバージョン指定は禁止。必ず BOM 経由 |
| Java / JVM target | 17 | |
| KSP | 2.2.10-2.0.2 以上 | Room 用 |

> **注意**：Compose 1.12.0 以降は `compileSdk 37` ＋ AGP 10 が必要になる予定。
> 今は追わない。BOM `2026.06.00` で固定して進める。
> ビルドが通らない場合、まず「バージョンを上げる」のではなく
> **「なぜ通らないか」をエラーメッセージから特定する**こと。

**バージョンは `gradle/libs.versions.toml`（Version Catalog）に集約する。**
`build.gradle.kts` に直接バージョン文字列を書かない。

---

## 2. ライブラリ選定

| 用途 | 採用 | 理由 |
|---|---|---|
| UI | Jetpack Compose + Material 3 | |
| DB | Room（KSP） | |
| 設定・軽量な状態 | DataStore (Preferences) | SharedPreferences は使わない |
| 非同期 | Coroutines + Flow | |
| DI | **手動 DI**（`AppContainer` クラス） | Hilt は KSP との組み合わせでビルドが不安定になりがち。単一モジュールの個人アプリに Hilt は過剰。**Hilt を導入しないこと** |
| ナビゲーション | Navigation Compose | |
| 日時 | `java.time`（`kotlinx-datetime` は不要） | minSdk 34 なので desugaring も不要 |

**追加ライブラリを勝手に増やさない。** 必要になったら理由を添えて提案し、承認を得てから入れる。

---

## 3. パッケージ構成

```
com.aki.tasktimer
├── TaskTimerApp.kt              # Application。AppContainer を保持
├── di/
│   └── AppContainer.kt          # 手動 DI のコンテナ
├── data/
│   ├── db/
│   │   ├── TaskTimerDatabase.kt
│   │   ├── entity/              # SessionEntity, ExtensionEntity, TaskPresetEntity,
│   │   │                        # ExtensionPresetEntity
│   │   ├── dao/                 # SessionDao, PresetDao
│   │   └── Converters.kt
│   ├── prefs/
│   │   └── SettingsRepository.kt   # DataStore
│   ├── model/                   # ドメインモデル（Session, Extension, Tag, ...）
│   │                            # Entity とドメインモデルは分ける
│   └── repository/
│       ├── SessionRepository.kt    # 進行中セッションの唯一の入口
│       └── PresetRepository.kt
├── domain/
│   ├── TimeCalculator.kt        # 経過時間・超過率・合計所要時間の計算（純関数）
│   └── DailyAggregator.kt       # 日境界での按分集計（純関数）
├── timer/
│   ├── TimerService.kt          # フォアグラウンドサービス。通知常駐
│   ├── TimerNotification.kt     # 通知の組み立て
│   ├── AlarmScheduler.kt        # AlarmManager のラッパ
│   ├── OverdueReceiver.kt       # 期限到達の BroadcastReceiver
│   ├── BootReceiver.kt          # 再起動時のアラーム再登録
│   └── Vibrator.kt
├── block/
│   ├── BlockerOverlayService.kt # TYPE_APPLICATION_OVERLAY のブロック画面
│   └── OverlayView.kt
├── permission/
│   └── PermissionChecker.kt     # 5 種の権限の状態確認と設定画面への導線
└── ui/
    ├── theme/                   # ダークモードのみ
    ├── home/                    # HomeScreen, HomeViewModel
    ├── switch/                  # タスク切り替えフロー（Step 1〜4）
    ├── overdue/                 # OverdueActivity + Composable
    ├── history/
    ├── settings/
    └── component/               # TagChip, ElapsedTimeDisplay, PresetGrid など
```

---

## 4. Activity 構成

**2 つだけ。**

| Activity | 役割 |
|---|---|
| `MainActivity` | ホーム・切り替えフロー・履歴・設定。Navigation Compose で切り替え |
| `OverdueActivity` | 超過画面**専用**。full-screen intent から起動される |

`OverdueActivity` を分ける理由：`showWhenLocked` / `turnScreenOn` / `excludeFromRecents` /
特殊な `launchMode` を設定する必要があり、`MainActivity` と混ぜると事故る。

```xml
<activity
    android:name=".ui.overdue.OverdueActivity"
    android:showWhenLocked="true"
    android:turnScreenOn="true"
    android:excludeFromRecents="true"
    android:launchMode="singleInstance"
    android:exported="false" />
```

---

## 5. 状態管理

### 5.1 進行中セッションの真実は DB にある

進行中セッションの状態は **Room の `status = RUNNING` のレコードが唯一の正**。
サービス、ViewModel、オーバーレイのそれぞれが独自に状態を持たない。
全員が `SessionRepository.observeRunningSession(): Flow<Session?>` を見る。

### 5.2 経過時間の計算

経過時間はカウンタで積算しない。**毎回 `now - startedAt` で計算する。**
プロセスが死んでも再起動しても、正しい値が出る。

```kotlin
// ui 層で 1 秒ごとに now を更新するだけ
val elapsed = System.currentTimeMillis() - session.startedAt
```

### 5.3 期限判定は AlarmManager だけが行う

`TimerService` のティックは通知の表示更新にしか使わない。
「期限が来たか」の判定を `TimerService` にさせない（Doze で止まると鳴らなくなる）。

---

## 6. 純関数として切り出すもの（テストを書く対象）

`domain/` に置く以下は**副作用ゼロの純関数**にする。ユニットテストを必ず書く。

```kotlin
// TimeCalculator.kt
fun elapsedMinutes(startedAt: Long, now: Long): Int
fun overrunRate(elapsedMinutes: Int, plannedMinutes: Int): Int  // % 、分母は当初予定
fun totalRequiredMinutes(elapsedMinutes: Int, selectedExtension: Int): Int
fun deadlineMillis(startedAt: Long, totalPlannedMinutes: Int): Long

// DailyAggregator.kt
fun splitByDay(startedAt: Long, endedAt: Long, zone: ZoneId): Map<LocalDate, Duration>
fun dailyTotals(sessions: List<Session>, zone: ZoneId): Map<LocalDate, Map<Tag, Duration>>
```

`splitByDay` は日をまたぐセッションの按分。**ここのテストは特に厚く書く**
（0 時ちょうど開始、23:59:59 終了、3 日以上またぐ、同日内で完結、など）。

---

## 7. テスト方針

Android 実機ビルドは遅いので、**ロジックは JVM ユニットテストで回す。**

| 種類 | 対象 | 実行 |
|---|---|---|
| ユニットテスト | `domain/`、Repository のロジック | `./gradlew test`（実機不要・数秒） |
| Room テスト | DAO のクエリ | `./gradlew connectedAndroidTest`（実機必要） |
| 手動確認 | 権限、通知、オーバーレイ、超過画面 | 実機で目視。チェックリストは Phase ごとに用意 |

UI テスト（Compose Test）は v1 では書かない。手動確認で足りる。

---

## 8. ビルド設定の注意

```kotlin
// app/build.gradle.kts の要点
android {
    compileSdk = 36
    defaultConfig {
        applicationId = "com.aki.tasktimer"
        minSdk = 34
        targetSdk = 36
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }

    buildTypes {
        release {
            isMinifyEnabled = false   // 個人利用。難読化は不要でトラブルの元
        }
    }
}
```

- **`isMinifyEnabled = false`** にしておく。R8 でリフレクション絡みが壊れるのを避ける
- 署名は debug 鍵のままで問題ない（自分の端末にしか入れない）
- `versionCode` / `versionName` は手で上げる
