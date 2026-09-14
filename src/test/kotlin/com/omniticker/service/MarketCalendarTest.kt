package com.omniticker.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MarketCalendarTest {

    private val sh = ZoneId.of("Asia/Shanghai")

    /** 2026-{month}-{day} at {time} (Asia/Shanghai). */
    private fun at(month: Int, day: Int, time: String): ZonedDateTime =
        ZonedDateTime.of(LocalDate.of(2026, month, day), LocalTime.parse(time), sh)

    @Test
    fun refreshWindowWithMargins() {
        // Mon 2026-09-14: refresh window 09:10-11:30 / 13:00-15:05
        assertFalse(MarketCalendar.isTradingTime(at(9, 14, "09:09")))
        assertTrue(MarketCalendar.isTradingTime(at(9, 14, "09:10")))    // 5min before auction
        assertTrue(MarketCalendar.isTradingTime(at(9, 14, "11:30")))
        assertFalse(MarketCalendar.isTradingTime(at(9, 14, "11:31")))
        assertTrue(MarketCalendar.isTradingTime(at(9, 14, "13:00")))
        assertTrue(MarketCalendar.isTradingTime(at(9, 14, "15:05")))     // 5min after close
        assertFalse(MarketCalendar.isTradingTime(at(9, 14, "15:06")))
    }

    @Test
    fun weekendIsNotTrading() {
        // Sat 2026-09-12 / Sun 2026-09-13
        assertFalse(MarketCalendar.isTradingDay(at(9, 12, "10:00")))
        assertFalse(MarketCalendar.isTradingDay(at(9, 13, "10:00")))
        assertTrue(MarketCalendar.isTradingDay(at(9, 14, "10:00")))
        assertFalse(MarketCalendar.isTradingTime(at(9, 12, "10:00")))
    }

    @Test
    fun statutoryHolidaysAreNotTradingDays() {
        // 2026-01-01 元旦 (Thu) and 2026-10-01 国庆 (Thu): weekdays but closed
        assertFalse(MarketCalendar.isTradingDay(at(1, 1, "10:00")))
        assertFalse(MarketCalendar.isTradingDay(at(10, 1, "10:00")))
        assertFalse(MarketCalendar.isTradingTime(at(10, 1, "10:00")))
        // 2026-12-21 (Mon, regular weekday): trading day
        assertTrue(MarketCalendar.isTradingDay(at(12, 21, "10:00")))
    }

    @Test
    fun statusBarVisibleWindow() {
        // 状态栏按需展示：交易日 09:00-15:30
        assertFalse(MarketCalendar.isStatusBarTime(at(9, 14, "08:59")))
        assertTrue(MarketCalendar.isStatusBarTime(at(9, 14, "09:00")))
        assertTrue(MarketCalendar.isStatusBarTime(at(9, 14, "15:30")))
        assertFalse(MarketCalendar.isStatusBarTime(at(9, 14, "15:31")))
        // weekend hidden
        assertFalse(MarketCalendar.isStatusBarTime(at(9, 12, "10:00")))
    }
}