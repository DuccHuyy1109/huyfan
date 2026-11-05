package com.hankukko.huyfan.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Dates {
    private val dayFmt = DateTimeFormatter.ofPattern("dd/MM")
    private val fullFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun todayKey(): String = LocalDate.now().toString()

    fun dayKeyOf(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    fun time(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)

    private fun weekday(d: LocalDate): String = when (d.dayOfWeek.value) {
        1 -> "Thứ Hai"; 2 -> "Thứ Ba"; 3 -> "Thứ Tư"; 4 -> "Thứ Năm"
        5 -> "Thứ Sáu"; 6 -> "Thứ Bảy"; else -> "Chủ Nhật"
    }

    /** "Hôm nay · Thứ Ba 03/11", "Hôm qua · ...", "Thứ Hai 01/11/2026" */
    fun dayLabel(dayKey: String): String {
        val d = runCatching { LocalDate.parse(dayKey) }.getOrNull() ?: return dayKey
        val today = LocalDate.now()
        return when (d) {
            today -> "Hôm nay · ${weekday(d)} ${d.format(dayFmt)}"
            today.minusDays(1) -> "Hôm qua · ${weekday(d)} ${d.format(dayFmt)}"
            else -> if (d.year == today.year) "${weekday(d)} ${d.format(dayFmt)}" else "${weekday(d)} ${d.format(fullFmt)}"
        }
    }

    fun spokenToday(): String {
        val d = LocalDate.now()
        return "${weekday(d)}, ngày ${d.dayOfMonth} tháng ${d.monthValue} năm ${d.year}"
    }

    fun daysAgoKey(n: Long): String = LocalDate.now().minusDays(n).toString()

    /** Chuỗi ngày học liên tiếp (tính tới hôm nay, hoặc hôm qua nếu hôm nay chưa học). */
    fun streak(days: Collection<String>): Int {
        if (days.isEmpty()) return 0
        val set = days.toHashSet()
        var d = LocalDate.now()
        if (d.toString() !in set) d = d.minusDays(1)
        var n = 0
        while (d.toString() in set) {
            n++
            d = d.minusDays(1)
        }
        return n
    }
}
