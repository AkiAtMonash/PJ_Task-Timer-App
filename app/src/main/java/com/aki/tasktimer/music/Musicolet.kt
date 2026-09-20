package com.aki.tasktimer.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent

/**
 * Musicolet の「再生ボタン」の受け口。実機の AndroidManifest を読んで確認した（2026-09-16）。
 *
 * パッケージ名だけを指定した合図は Android 8 以降ブロックされて届かない（実機で確認済み）。
 * 受け口のクラス名まで名指しする必要がある。
 *
 * ponytail: クラス名の直書き。Musicolet 側の改名で黙って効かなくなる。
 * 効かなくなったら dumpsys package in.krosbits.musicolet で MEDIA_BUTTON の受け口を調べ直す。
 */
private val MUSICOLET_MEDIA_BUTTON = ComponentName(
    "in.krosbits.musicolet",
    "in.krosbits.musicolet.RemoteMediaControlReceiver",
)

/**
 * Musicolet に「再生」を送る。
 *
 * イヤホンの再生ボタンと同じ仕組み（MEDIA_BUTTON）を Musicolet だけに宛てて投げている。
 * アプリを起動する方法（launch intent）にしなかったのは、画面が Musicolet に奪われると
 * タイマーの画面が隠れてしまうため。これなら画面はそのままで音だけ鳴り出す。
 * Musicolet が落ちていても、この受信側（RemoteMediaControlReceiver）が起こしてくれる。
 *
 * PLAY_PAUSE ではなく PLAY なのは、すでに鳴っているときに止めてしまわないため。
 * 失敗しても握りつぶす。音が鳴らないことより、タイマーの開始が失敗する方が困る。
 */
fun Context.playMusicolet() {
    runCatching {
        // ボタンは「押して離す」で 1 回。DOWN だけでは反応しないプレイヤーがあるので両方送る。
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            sendBroadcast(
                Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    component = MUSICOLET_MEDIA_BUTTON
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY))
                },
            )
        }
    }.onFailure { Log.w("Musicolet", "再生を指示できませんでした", it) }
}
