package com.omniticker.service

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A-share trading calendar, Asia/Shanghai.
 *
 * Trading day: Mon-Fri minus CN statutory holidays (auto-refreshed from the
 * nager.at API via [TradingCalendar]; built-in table as fallback).
 *
 * Refresh window (single, no boundary compensations): 09:10-11:30 /
 * 13:00-15:05 on trading days. Starting at 09:10 (5 min before the 09:15 call
 * auction) and ending at 15:05 (5 min after the 15:00 close) absorbs timer
 * jitter on both sides, so no separate "catch-up refresh" is needed.
 *
 * Status-bar visibility: 09:00-15:30 on trading days.
 */
object MarketCalendar {
    val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")

    fun now(): ZonedDateTime = ZonedDateTime.now(SHANGHAI)

    /** Refresh window: 09:10-11:30 / 13:00-15:05 on trading days. */
    fun isTradingTime(now: ZonedDateTime = now()): Boolean {
        if (!isTradingDay(now)) return false
        val minute = now.hour * 60 + now.minute
        return (minute in AM_START..AM_END) || (minute in PM_START..PM_END)
    }

    /** Mon-Fri minus CN statutory holidays (auto-refreshed, built-in fallback). */
    fun isTradingDay(now: ZonedDateTime = now()): Boolean {
        val dow = now.dayOfWeek
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false
        val date = now.toLocalDate()
        if (date in TradingCalendar.holidaysSync(date.year)) return false
        return true
    }

    /** Status-bar visibility: 09:00-15:30 on trading days. */
    fun isStatusBarTime(now: ZonedDateTime = now()): Boolean {
        if (!isTradingDay(now)) return false
        val minute = now.hour * 60 + now.minute
        return minute in DISPLAY_START..DISPLAY_END
    }

    /**
     * Built-in CN statutory holidays (stocks exchange closed) — fallback when
     * the nager.at refresh is offline. Approximate for 2026 from official
     * notices; make-up workdays are omitted (they fall on weekdays and count
     * as trading already).
     */
    fun BUILT_IN_HOLIDAYS(year: Int): Set<LocalDate> = when (year) {
        2026 -> HOLIDAYS_2026
        else -> emptySet()
    }

    private const val AM_START = 9 * 60 + 10
    private const val AM_END = 11 * 60 + 30
    private const val PM_START = 13 * 60
    private const val PM_END = 15 * 60 + 5
    private const val DISPLAY_START = 9 * 60
    private const val DISPLAY_END = 15 * 60 + 30

    private val HOLIDAYS_2026 = setOf(
        LocalDate.of(2026, 1, 1),                                // 元旦
        LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17),    // 春节 (除夕~初六)
        LocalDate.of(2026, 2, 18), LocalDate.of(2026, 2, 19),
        LocalDate.of(2026, 2, 20), LocalDate.of(2026, 2, 21),
        LocalDate.of(2026, 2, 22),
        LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 6),   // 清明
        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 3),   // 劳动节
        LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5),
        LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 20), LocalDate.of(2026, 6, 21), // 端午
        LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27), // 中秋
        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3), // 国庆
        LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6),
        LocalDate.of(2026, 10, 7),
    )
}