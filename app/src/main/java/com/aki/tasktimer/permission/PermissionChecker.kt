package com.aki.tasktimer.permission

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** このアプリが動くのに要る許可（docs/05 4 章）。 */
enum class PermissionKind(val label: String, val purpose: String) {
    NOTIFICATIONS("通知", "超過画面を出す土台。無いと超過画面が出ない"),
    FULL_SCREEN_INTENT("全画面で表示", "ロック画面や他アプリの上に超過画面を出す"),
    OVERLAY("他のアプリの上に表示", "ホームを押しても覆いを出す（ブロック）"),
    BATTERY_OPTIMIZATION("電池の最適化を除外", "省電力で止められないための保険。必須ではない"),
}

data class PermissionState(
    val kind: PermissionKind,
    val granted: Boolean,
    /** false なら「あると良い」レベル */
    val required: Boolean,
)

/**
 * 許可の状態確認と、Android の設定画面への導線。設定画面と初回起動の両方から使う。
 * 許可が無くてもアプリは落とさない。確認して、できることだけやる。
 */
class PermissionChecker(context: Context) {

    private val appContext = context.applicationContext

    fun checkAll(): List<PermissionState> = listOf(
        PermissionState(PermissionKind.NOTIFICATIONS, hasNotifications(), required = true),
        PermissionState(PermissionKind.FULL_SCREEN_INTENT, canUseFullScreenIntent(), required = true),
        PermissionState(PermissionKind.OVERLAY, canDrawOverlays(), required = false),
        PermissionState(PermissionKind.BATTERY_OPTIMIZATION, ignoresBatteryOptimizations(), required = false),
    )

    fun hasNotifications(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun canUseFullScreenIntent(): Boolean =
        appContext.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(appContext)

    fun ignoresBatteryOptimizations(): Boolean =
        appContext.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(appContext.packageName)

    /** その許可を出せる Android の設定画面。 */
    fun intentFor(kind: PermissionKind): Intent {
        val pkg = "package:${appContext.packageName}"
        return when (kind) {
            PermissionKind.NOTIFICATIONS ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            PermissionKind.FULL_SCREEN_INTENT ->
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(Uri.parse(pkg))
            PermissionKind.OVERLAY ->
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(Uri.parse(pkg))
            PermissionKind.BATTERY_OPTIMIZATION ->
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
