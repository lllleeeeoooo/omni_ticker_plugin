package com.omniticker.data

import com.omniticker.model.Market
import com.omniticker.model.Quote
import com.omniticker.model.SearchHit

/** A batch request: one instrument. Indices need an explicit [market] override. */
data class QuoteRequest(
    val code: String,
    val market: Market? = null,   // overrides code-derived market (e.g. 000001 = 上证指数 is SH)
) {
    val resolvedMarket: Market get() = market ?: Market.derive(code)

    /** "sh600519" / "sz000858" — the shared market-code form used by Tencent & Sina. */
    fun marketCode(): String = resolvedMarket.prefix + code
}

/**
 * Unified data-source interface. Implementations are pure I/O + parsing;
 * they never touch the EDT and never throw up to callers — a failed fetch
 * returns null so the aggregator can fall back to the next source.
 */
interface QuoteProvider {
    val id: String

    /** Bulk quotes for [requests]; null on failure (timeout, HTTP error, parse failure). */
    suspend fun fetchQuotes(requests: List<QuoteRequest>): List<Quote>?

    /**
     * Keyword search (code / Chinese name / pinyin prefix).
     * @return null when this source does not provide search (aggregator skips it);
     * an empty list when it does but found nothing.
     */
    suspend fun search(keyword: String, limit: Int = 10): List<SearchHit>?
}