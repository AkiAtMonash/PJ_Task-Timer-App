package com.aki.tasktimer.domain

import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.model.Tag
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日別集計。**副作用ゼロの純関数だけを置く。**
 *
 * 既存の Notion システムでは GAS が 23:59 にレコードを物理分割していたが、
 * アプリ側では分割しない（docs/01_SPEC.md 6.1）。
 * セッションは 1 レコードのまま持ち、表示のたびにここで按分する。
 */

/**
 * セッションを日境界で按分する。
 *
 * 境界は **その日の 0 時ちょうど**。23:00〜翌 07:00 なら当日 60 分・翌日 420 分になり、
 * 合計は必ず元のセッション長と一致する。
 *
 * ※ docs/01_SPEC.md 6.1 の例は「当日 59 分」と書かれているが、これは 23:59 で
 *   物理分割していた旧 GAS の挙動の名残と思われる。59 分にすると日境界ごとに
 *   1 分が消え、日別合計がセッション長と合わなくなるため 60 分としている。
 *
 * タイムゾーンは引数で受ける。ZonedDateTime 経由で計算しているので、
 * 夏時間のある地域でも「実時間」で正しく按分される（日本には無いが、
 * 24 時間で割り算する実装にしてしまうと将来ここが壊れる）。
 *
 * 終了が開始以前なら空を返す。
 */
fun splitByDay(startedAt: Long, endedAt: Long, zone: ZoneId): Map<LocalDate, Duration> {
    if (endedAt <= startedAt) return emptyMap()

    val end = Instant.ofEpochMilli(endedAt).atZone(zone)
    var cursor = Instant.ofEpochMilli(startedAt).atZone(zone)
    val result = LinkedHashMap<LocalDate, Duration>()

    while (cursor.isBefore(end)) {
        val date = cursor.toLocalDate()
        val nextDayStart = date.plusDays(1).atStartOfDay(zone)
        val segmentEnd = if (nextDayStart.isBefore(end)) nextDayStart else end
        result[date] = Duration.between(cursor, segmentEnd)
        cursor = segmentEnd
    }
    return result
}

/**
 * 日別・タグ別の合計。
 *
 * 進行中のセッション（endedAt が null）は**含めない**。
 * 純関数に保つため現在時刻を持たないので、終わっていないものは計算できない。
 * 進行中の分を混ぜたい画面があれば、呼び出し側で now を使って endedAt を埋めてから渡すこと。
 *
 * 戻り値は日付の昇順。
 */
fun dailyTotals(sessions: List<Session>, zone: ZoneId): Map<LocalDate, Map<Tag, Duration>> {
    val byDate = sortedMapOf<LocalDate, MutableMap<Tag, Duration>>()

    for (session in sessions) {
        val endedAt = session.endedAt ?: continue
        for ((date, duration) in splitByDay(session.startedAt, endedAt, zone)) {
            val byTag = byDate.getOrPut(date) { linkedMapOf() }
            byTag[session.tag] = (byTag[session.tag] ?: Duration.ZERO).plus(duration)
        }
    }
    return byDate
}
