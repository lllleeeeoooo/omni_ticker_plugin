package com.omniticker.data

import com.omniticker.model.AssetClass
import com.omniticker.model.Market
import com.omniticker.model.Quote
import com.omniticker.model.SearchHit

/** A batch request: one instrument. Indices need an explicit [market] override. */
data class QuoteRequest(
    val code: String,
    val market: Market? = null,   // overrides code-derived market (e.g. 000001 = 上证指数 is SH)
    val assetClass: AssetClass = AssetClass.A_SHARE,  // 品类（聚合器按此路由到对应 Provider）
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

    /**
     * 本数据源覆盖的品类（默认仅 A 股）。聚合器按请求品类只向对应 Provider 发起调用。
     * 未来美股 / 虚拟币 Provider 覆写此属性即可接入，无需改动聚合器。
     */
    val supportedAssets: Set<AssetClass> get() = setOf(AssetClass.A_SHARE)

    /** Bulk quotes for [requests]; null on failure (timeout, HTTP error, parse failure). */
    suspend fun fetchQuotes(requests: List<QuoteRequest>): List<Quote>?

    /**
     * Keyword search (code / Chinese name / pinyin prefix).
     * @return null when this source does not provide search (aggregator skips it);
     * an empty list when it does but found nothing.
     */
    suspend fun search(keyword: String, limit: Int = 10, assetClass: AssetClass = AssetClass.A_SHARE): List<SearchHit>?
}