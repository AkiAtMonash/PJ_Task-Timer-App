package com.aki.tasktimer.block

/**
 * 覆い（ブロック画面）に出す文言。Aki のアプリなので少し怖く、少し気の利いた一言にする。
 * 増やしたくなったらここに足すだけでよい。開くたびにランダムに 1 つ選ばれる。
 */
object OverlayMessages {

    const val HEADLINE = "逃げ場はない。"

    val LINES: List<String> = listOf(
        "逃げても時間は逃げない。",
        "ここは出口じゃない。",
        "予定時間、とっくに過ぎてる。",
        "今やめたら、記録が嘘になる。",
        "延長か、次か。選ぶまで通さない。",
        "見なかったことにはできない。",
    )

    fun random(): String = LINES.random()
}
