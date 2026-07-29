# 05. Android 固有の制約と権限 — 実装前に必読

このアプリは Android の「アプリを制限する側の機能」を正面から使う。
ここを雑にやると**「動いてるように見えて肝心なときに鳴らない」**という最悪の壊れ方をする。

---

## 1. 必要な権限の全リスト

`AndroidManifest.xml`：

```xml
<!-- 通知 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- フォアグラウンドサービス -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- 正確なアラーム -->
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />

<!-- 全画面表示 -->
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />

<!-- 他アプリの上に表示（ブロック用） -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- バイブ -->
<uses-permission android:name="android.permission.VIBRATE" />

<!-- 再起動後のアラーム復元 -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

---

## 2. 権限ごとの注意点

### 2.1 `USE_EXACT_ALARM` vs `SCHEDULE_EXACT_ALARM`

**`USE_EXACT_ALARM` を使う。`SCHEDULE_EXACT_ALARM` は使わない。**

| | `SCHEDULE_EXACT_ALARM` | `USE_EXACT_ALARM` |
|---|---|---|
| 追加 | API 31 | API 33 |
| ユーザー許可 | **必要**（Android 14 以降デフォルト拒否） | **不要**（インストール時に自動付与） |
| 取り消し | ユーザー・システムが取り消せる | 取り消されない |
| 対象 | 汎用 | アラーム／カレンダーが**中核機能**のアプリ |

このアプリはタイマー＝アラームが中核機能なので `USE_EXACT_ALARM` が正当に使える。
Play ストアではポリシー審査があるが、**サイドロードなので審査は発生しない。**

> **両方を同時に宣言しないこと。** 片方だけにする。

呼び出し側：

```kotlin
alarmManager.setExactAndAllowWhileIdle(
    AlarmManager.RTC_WAKEUP,
    deadlineMillis,
    pendingIntent
)
```

`setExact()` ではなく **`setExactAndAllowWhileIdle()`**。
Doze モード中でも発火させるにはこちらが必要。

### 2.2 `USE_FULL_SCREEN_INTENT` — ここが一番の落とし穴

Android 14 以降、この権限は**「通話アプリ」と「アラームアプリ」にしか自動付与されない。**
それ以外のアプリは、Play ストア経由でインストールすると権限を剥奪される。

**サイドロードの場合は自動付与されるが、頼りにしてはいけない。** 必ず実行時に確認する：

```kotlin
val nm = context.getSystemService(NotificationManager::class.java)
if (!nm.canUseFullScreenIntent()) {
    // 設定画面に飛ばす
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            .setData(Uri.parse("package:${context.packageName}"))
    )
}
```

**必ず実装すること：**
- 初回起動時のオンボーディングでチェック → 未付与なら設定画面へ誘導
- 設定画面に「全画面表示：✅ / ⚠️未許可」の状態を常時表示
- **未付与でもクラッシュせず、通知＋バイブだけで動作を続ける**（デグレード動作）

full-screen intent 通知の組み立て：

```kotlin
NotificationCompat.Builder(context, CHANNEL_OVERDUE)
    .setSmallIcon(R.drawable.ic_timer)
    .setPriority(NotificationCompat.PRIORITY_HIGH)     // 必須
    .setCategory(NotificationCompat.CATEGORY_ALARM)    // 必須
    .setFullScreenIntent(pendingIntent, true)
    .setOngoing(true)
    .build()
```

**通知チャンネルの重要度は `IMPORTANCE_HIGH` にすること。** 低いと full-screen intent が無視される。

### 2.3 `FOREGROUND_SERVICE_SPECIAL_USE`

Android 14 以降、すべてのフォアグラウンドサービスは `foregroundServiceType` の宣言が必須。
宣言がないと `MissingForegroundServiceTypeException` でクラッシュする。

タイマー用の専用タイプは存在しないので `specialUse` を使う。
`shortService` は **3 分で強制終了される**ので絶対に使わない。

```xml
<service
    android:name=".timer.TimerService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="task_time_tracking_timer" />
</service>
```

`startForeground()` を呼ぶときもタイプを渡す：

```kotlin
ServiceCompat.startForeground(
    this, NOTIFICATION_ID, notification,
    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
)
```

**サービス起動から 5 秒以内に `startForeground()` を呼ぶこと。** 遅れると ANR で殺される。

### 2.4 `SYSTEM_ALERT_WINDOW`（オーバーレイ）

マニフェストの宣言だけでは足りない。ユーザーが設定画面で明示的に許可する必要がある。

```kotlin
if (!Settings.canDrawOverlays(context)) {
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .setData(Uri.parse("package:${context.packageName}"))
    )
}
```

ウィンドウタイプは `TYPE_APPLICATION_OVERLAY` のみ（`TYPE_SYSTEM_ALERT` などは API 26 で廃止）。

```kotlin
WindowManager.LayoutParams(
    MATCH_PARENT, MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
    PixelFormat.TRANSLUCENT
)
```

**Android 15 以降の落とし穴**：
バックグラウンドからフォアグラウンドサービスを起動する場合、`SYSTEM_ALERT_WINDOW` を持っているだけでは不十分で、
**先に可視のオーバーレイウィンドウを表示している必要がある**。
そうでないと `ForegroundServiceStartNotAllowedException` が飛ぶ。
このアプリでは「アラーム受信 → オーバーレイ表示 → サービス操作」の順序を守れば問題ないが、
順序を変えるときは必ずこれを思い出すこと。

### 2.5 `POST_NOTIFICATIONS`

Android 13 以降、実行時パーミッション。初回起動時に `rememberLauncherForActivityResult` で要求する。
**拒否されるとこのアプリは根本的に成立しない**ので、拒否された場合は
「このアプリは通知なしでは機能しません」と明示して設定画面への導線を出す。

---

## 3. Doze モードとバッテリー最適化

`setExactAndAllowWhileIdle()` を使っていても、メーカー独自の省電力機能で殺されることがある。
Pixel は比較的おとなしいが、念のため：

- 設定画面から「バッテリーの最適化から除外」を要求する導線を用意する
  （`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）
- ただし**必須にはしない**。デフォルトで動くのが理想

---

## 4. 権限チェックの集約

`permission/PermissionChecker.kt` に全部まとめる。設定画面と初回オンボーディングの両方から使う。

```kotlin
enum class PermissionKind {
    NOTIFICATIONS,        // POST_NOTIFICATIONS
    FULL_SCREEN_INTENT,   // canUseFullScreenIntent()
    OVERLAY,              // canDrawOverlays()
    BATTERY_OPTIMIZATION, // isIgnoringBatteryOptimizations()
}

data class PermissionState(
    val kind: PermissionKind,
    val granted: Boolean,
    val required: Boolean,   // false なら「あると良い」レベル
)

class PermissionChecker(private val context: Context) {
    fun checkAll(): List<PermissionState>
    fun intentFor(kind: PermissionKind): Intent
}
```

`required = true` … NOTIFICATIONS, FULL_SCREEN_INTENT
`required = false` … OVERLAY（強制力機能を使わないなら不要）, BATTERY_OPTIMIZATION

---

## 5. 絶対にやってはいけないこと

| ❌ | なぜダメか |
|---|---|
| `TimerService` 内のカウンタで期限を判定する | Doze でサービスが止まると永遠に鳴らない |
| `setExact()` を使う | Doze 中に発火しない |
| `shortService` を使う | 3 分で強制終了される |
| 経過時間をカウンタで積算する | プロセス再生成でリセットされる。必ず `now - startedAt` |
| オーバーレイに非常口を付けない | **バグったら端末が使えなくなる。事故る** |
| 権限がない前提でクラッシュさせる | 権限は剥奪されうる。常に確認してデグレード動作する |
| 通知チャンネルの重要度を下げる | full-screen intent が無視される |
| `SCHEDULE_EXACT_ALARM` と `USE_EXACT_ALARM` を両方宣言する | どちらか片方のみ |

---

## 6. 実機での確認チェックリスト（各 Phase 完了時に回す）

- [ ] 画面を消した状態で 5 分放置 → 期限どおりに鳴るか
- [ ] 機内モード → 関係なく鳴るか
- [ ] 他のアプリ（YouTube など）を全画面で使っている最中に超過 → 上に出るか
- [ ] ロック画面の状態で超過 → 画面が点いて出るか
- [ ] 超過画面でホームボタン → オーバーレイに引き戻されるか
- [ ] 非常口（右上長押し 3 秒）で確実に脱出できるか
- [ ] 端末を再起動 → 進行中セッションが復元され、アラームも再登録されるか
- [ ] 音楽を再生しながら超過 → 音楽が止まらず、バイブだけで通知されるか
- [ ] アプリをタスクキル → 通知常駐が残っているか、経過時間が正しいか

---

## 参考リンク

- [Full-screen intent limits — Android Open Source Project](https://source.android.com/docs/core/permissions/fsi-limits)
- [Behavior changes: Apps targeting Android 14 or higher](https://developer.android.com/about/versions/14/behavior-changes-14)
- [Foreground service types are required](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Foreground service types | Background work](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Changes to foreground service types for Android 15](https://developer.android.com/about/versions/15/changes/foreground-service-types)
- [Schedule exact alarms are denied by default](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [Schedule alarms | Background work](https://developer.android.com/develop/background-work/services/alarms)
- [Behavior changes: Apps targeting Android 15 or higher](https://developer.android.com/about/versions/15/behavior-changes-15)
- [Behavior changes: Apps targeting Android 16 or higher](https://developer.android.com/about/versions/16/behavior-changes-16)
