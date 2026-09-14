package com.omniticker.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.omniticker.data.AggregatingQuoteSource
import com.omniticker.data.QuoteRequest
import com.omniticker.data.SinaProvider
import com.omniticker.data.TencentProvider
import com.omniticker.model.Market
import com.omniticker.model.Quote
import com.omniticker.model.SearchHit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-level coordinator: owns the polling loop, the quote cache and the
 * listener registry. The tool window and status bar both subscribe here and
 * are pushed updates on the EDT. Network work never happens on the EDT.
 */
@Service(Service.Level.PROJECT)
class QuoteManager(private val project: Project) : Disposable {

    private val log = Logger.getInstance(QuoteManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Tencent first: verified live to return the full dataset (5-level book,
    // PE/PB, caps, limit prices) in one fast GBK request. Sina (hq.sinajs.cn)
    // is the fallback — core fields only, no search, HTTPS + Referer required.
    private val source = AggregatingQuoteSource(listOf(TencentProvider(), SinaProvider()))
    private val quotes = ConcurrentHashMap<String, Quote>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile var lastError: String? = null
        private set
    @Volatile var lastUpdate: Long = 0
        private set

    private var refreshJob: kotlinx.coroutines.Job? = null
    private val settingsListener: () -> Unit = { refreshNow() }

    private val idleCheckMs = 30_000L        // 交易日非交易时段的唤醒检查间隔
    private val idleWeekendMs = 5 * 60_000L  // 非交易日的唤醒检查间隔

    init {
        // Fetch once immediately, regardless of the time of day, so the status
        // bar / detail pane always have a snapshot to show (e.g. after a
        // restart during a weekend). Then poll only during trading time
        // (09:10-11:30 / 13:00-15:05); off hours hit the APIs just this once.
        refreshJob = scope.launch {
            refreshInternal()
            while (true) {
                val now = MarketCalendar.now()
                if (MarketCalendar.isTradingTime(now)) {
                    refreshInternal()
                    kotlinx.coroutines.delay(computeRefreshIntervalMs())
                } else {
                    kotlinx.coroutines.delay(
                        if (MarketCalendar.isTradingDay(now)) idleCheckMs else idleWeekendMs
                    )
                }
            }
        }
        // Settings changes feed through a forced refresh — another path for the
        // status bar / tool window to repaint with the new values immediately.
        // Prefetch CN holidays (background) so the trading-day calendar is
        // accurate even across new-year boundaries.
        scope.launch { TradingCalendar.prefetch() }
        PluginSettings.getInstance().addChangeListener(settingsListener)
        log.info("Omni Ticker QuoteManager initialized")
    }

    fun refreshNow() {
        scope.launch { refreshInternal() }
    }

    /** Keyword search (code / Chinese name / pinyin), first usable source. */
    suspend fun search(keyword: String, limit: Int = 10): List<SearchHit> =
        source.search(keyword.trim(), limit)

    fun quoteSnapshot(): List<Quote> = quotes.values.toList()

    fun indexQuotes(): List<Quote> = INDEX_CODES.mapNotNull { quotes[it.code] }

    /** 自选股行情，按自选列表顺序返回（稳定可预期的展示顺序）。 */
    fun watchlistQuotes(): List<Quote> =
        watchlistCodes().mapNotNull { quotes[it] }

    fun subscribe(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun unsubscribe(listener: () -> Unit) {
        listeners.remove(listener)
    }

    override fun dispose() {
        PluginSettings.getInstance().removeChangeListener(settingsListener)
        refreshJob?.cancel()
        scope.cancel()
    }

    // ---- internals --------------------------------------------------------

    private fun watchlistCodes(): List<String> = WatchlistState.getInstance().codes()

    private suspend fun refreshInternal() {
        try {
            val requests = buildList {
                watchlistCodes().forEach { add(QuoteRequest(it)) }
                INDEX_CODES.forEach { idx -> add(QuoteRequest(idx.code, idx.market)) }
            }
            val result = source.fetchQuotes(requests)
            if (result.isEmpty()) {
                lastError = "行情源暂时不可用（东方财富/腾讯均未响应）"
            } else {
                lastError = null
            }
            result.forEach { quotes[it.code] = it }
            lastUpdate = System.currentTimeMillis()
            notifyListeners()
        } catch (t: Throwable) {
            log.warn("refresh failed", t)
        }
    }

    private fun notifyListeners() {
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { l ->
                runCatching { l() }
            }
        }
    }

    /** Single configured cadence — auto refresh is always on. */
    private fun computeRefreshIntervalMs(): Long =
        PluginSettings.getInstance().refreshSeconds * 1000L

    companion object {
        data class IndexRef(val code: String, val market: Market)

        /** 上证指数 / 深证成指 / 创业板指. */
        val INDEX_CODES = listOf(
            IndexRef("000001", Market.SH),
            IndexRef("399001", Market.SZ),
            IndexRef("399006", Market.SZ),
        )
    }
}