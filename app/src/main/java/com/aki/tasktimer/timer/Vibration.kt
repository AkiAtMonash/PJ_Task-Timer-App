package com.aki.tasktimer.timer

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * 超過時のバイブ（docs/01_SPEC.md 4.4-6：音は鳴らさない）。
 *
 * 「延長する」か「中断して別のタスクへ」を押すまで**止まらない**（Aki の決定）。
 * 繰り返しのバイブは呼び出したプロセスが生きている間しか続かないので、
 * BroadcastReceiver ではなくフォアグラウンドサービスから鳴らす。
 *
 * USAGE_ALARM にしているのは、サイレント中でもアラーム扱いで通すため。
 * Pixel の「アラームのバイブレーション」設定が OFF だと鳴らない。
 */
class Vibration(context: Context) {

    private val vibrator = context.applicationContext
        .getSystemService(VibratorManager::class.java)
        .defaultVibrator

    private val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)

    /** 短く 3 回 → 1 秒休み、を繰り返す。[stop] まで続く。 */
    fun startRepeating() {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(
            VibrationEffect.createWaveform(PATTERN, /* repeat = */ 0),
            attributes,
        )
    }

    /** 1 回だけ（サービスが起動できなかったときの退避策）。 */
    fun once() {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, /* repeat = */ -1), attributes)
    }

    fun stop() {
        vibrator.cancel()
    }

    private companion object {
        // 待ち 0 → 震 400 → 休 200 → 震 400 → 休 200 → 震 600 → 休 1000
        val PATTERN = longArrayOf(0, 400, 200, 400, 200, 600, 1000)
    }
}
