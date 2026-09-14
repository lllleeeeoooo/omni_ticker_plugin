package com.omniticker.service

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import com.omniticker.util.RequestClient
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * CN statutory holidays for the A-share calendar, auto-refreshed from the
 * free nager.at public-holiday API (no key):
 *
 *     https://date.nager.at/api/v3/PublicHolidays/{year}/CN
 *
 * Resolved asynchronously (prefetch on plugin start); until a year's data
 * arrives (or on network failure) [MarketCalendar] falls back to its built-in
 * table. Access from any thread is safe — reads are never blocked on I/O.
 */
object TradingCalendar {

    private val log = Logger.getInstance(TradingCalendar::class.java)
    private val cache = ConcurrentHashMap<Int, Set<LocalDate>>()   // year -> holidays
    private val lastFetched = ConcurrentHashMap<Int, LocalDate>()  // year -> fetch date

    /** Synchronous read for the scheduler/hide logic — never blocks. */
    fun holidaysSync(year: Int): Set<LocalDate> =
        cache[year] ?: MarketCalendar.BUILT_IN_HOLIDAYS(year)

    /** Prefetch this year + next year in the background. */
    suspend fun prefetch() {
        val thisYear = LocalDate.now(MarketCalendar.SHANGHAI).year
        fetchIfStale(thisYear)
        fetchIfStale(thisYear + 1)
    }

    private suspend fun fetchIfStale(year: Int) {
        val today = LocalDate.now(MarketCalendar.SHANGHAI)
        if (lastFetched[year] == today) return   // already refreshed today
        val fetched = fetch(year)
        cache[year] = fetched
        lastFetched[year] = today
        if (fetched === MarketCalendar.BUILT_IN_HOLIDAYS(year)) {
            log.info("TradingCalendar: nager.at unavailable, using built-in table for $year")
        } else {
            log.info("TradingCalendar: refreshed ${fetched.size} CN holidays for $year")
        }
    }

    private suspend fun fetch(year: Int): Set<LocalDate> {
        val url = "https://date.nager.at/api/v3/PublicHolidays/$year/CN"
        val body = RequestClient.getText(url, timeoutMs = 6000) ?: return MarketCalendar.BUILT_IN_HOLIDAYS(year)
        return runCatching {
            val arr = JsonParser.parseString(body)
            if (!arr.isJsonArray) return MarketCalendar.BUILT_IN_HOLIDAYS(year)
            (arr as JsonArray).mapNotNull { el ->
                val dateStr = el.asJsonObject.get("date")?.asString ?: return@mapNotNull null
                LocalDate.parse(dateStr)
            }.toSet().takeIf { it.isNotEmpty() } ?: MarketCalendar.BUILT_IN_HOLIDAYS(year)
        }.getOrElse { MarketCalendar.BUILT_IN_HOLIDAYS(year) }
    }
}