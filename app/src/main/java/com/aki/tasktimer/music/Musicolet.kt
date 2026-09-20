package com.aki.tasktimer.music

import android.content.ComponentName
import android.content.Context
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log

private const val TAG = "Musicolet"

/**
 * Musicolet の「正面玄関」。カーナビなどが音楽アプリを操作するのに使う受け口で、
 * 実機の Musicolet がこれを持っていることを確認した（2026-09-20）。
 *
 * ponytail: クラス名の直書き。Musicolet 側の改名で黙って効かなくなる。
 * 効かなくなったら dumpsys package in.krosbits.musicolet で
 * MediaBrowserService の受け口を調べ直す。
 */
private val MUSICOLET_BROWSER = ComponentName(
    "in.krosbits.musicolet",
    "in.krosbits.musicolet.MediaBrowserServiceImpl",
)

/**
 * 繋いでから再生が始まるまでの猶予。これを過ぎても鳴っていなければ失敗とみなす。
 * 冷えた Musicolet は起動と曲の読み込みに時間がかかるので、短すぎると誤判定する。
 */
private const val PLAY_TIMEOUT_MS = 2_500L

/**
 * Musicolet に「再生」を頼む（ADR 0001）。
 *
 * イヤホンの再生ボタンと同じ合図を投げる方式だったが、Musicolet が冷えている
 * （再生待ちの曲が 1 つも無い）と合図は届いても何も起きなかった。正面玄関に繋ぐと
 * Musicolet 自体が起きるので、前回の続きから鳴らせる。
 * Musicolet の画面を開く方法を採らないのは、始めた直後にタイマーの画面が奪われるため。
 *
 * すでに鳴っているときに呼んでも止まらない（play であって toggle ではない）。
 *
 * @param onResult 鳴り出したら true、断られた・曲が無い・落ちたときは false。主スレッドで呼ばれる。
 */
fun Context.playMusicolet(onResult: (Boolean) -> Unit) {
    val appContext = applicationContext
    val handler = Handler(Looper.getMainLooper())

    // 接続失敗とタイムアウトの両方から呼ばれうるので、返すのは一度だけにする。
    var answered = false
    fun answer(playing: Boolean) {
        if (answered) return
        answered = true
        onResult(playing)
    }

    val callback = object : MediaBrowser.ConnectionCallback() {
        // MediaBrowser の生成にこのコールバック自身が要るので、後から入れる。
        lateinit var browser: MediaBrowser

        override fun onConnected() {
            val controller = runCatching { MediaController(appContext, browser.sessionToken) }
                .getOrElse {
                    Log.w(TAG, "再生の窓口を掴めませんでした", it)
                    answer(false)
                    browser.disconnect()
                    return
                }
            controller.transportControls.play()
            // すぐ切ると鳴り出す前に手を離すことになるので、猶予を置いてから結果を見て切る。
            handler.postDelayed({
                answer(controller.playbackState.isStarting)
                browser.disconnect()
            }, PLAY_TIMEOUT_MS)
        }

        // Musicolet が外部からの接続を断った。
        override fun onConnectionFailed() {
            Log.w(TAG, "Musicolet が接続を断りました")
            answer(false)
        }

        override fun onConnectionSuspended() {
            Log.w(TAG, "Musicolet との接続が切れました")
            answer(false)
        }
    }

    // 繋ぎに行く時点で落ちても、タスクの開始は止めない。音が鳴らないことより開始の失敗の方が困る。
    runCatching {
        callback.browser = MediaBrowser(appContext, MUSICOLET_BROWSER, callback, null)
        callback.browser.connect()
    }.onFailure {
        Log.w(TAG, "Musicolet に繋げませんでした", it)
        answer(false)
    }
}

/** 鳴り出したか。読み込み中（BUFFERING）も「この先鳴る」とみなす。 */
private val PlaybackState?.isStarting: Boolean
    get() = this?.state == PlaybackState.STATE_PLAYING || this?.state == PlaybackState.STATE_BUFFERING
