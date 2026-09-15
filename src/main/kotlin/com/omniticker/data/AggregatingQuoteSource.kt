package com.omniticker.data

import com.omniticker.model.AssetClass
import com.omniticker.model.Quote
import com.omniticker.model.SearchHit

/**
 * Facade the rest of the plugin talks to. Tries providers in priority order,
 * skipping any that are currently open in the circuit breaker; a failed fetch
 * advances the breaker and the next provider takes over. Returns an empty list
 * only when every source is down.
 *
 * 请求按品类（assetClass）分组并只向支持该品类的 Provider 发起调用
 * （P2 起接入美股 / 虚拟币 Provider 时无需改此处）。
 */
class AggregatingQuoteSource(
    private val providers: List<QuoteProvider>,
    private val breaker: ProviderCircuitBreaker = ProviderCircuitBreaker(),
) {
    suspend fun fetchQuotes(requests: List<QuoteRequest>): List<Quote> {
        if (requests.isEmpty()) return emptyList()
        return requests.groupBy { it.assetClass }
            .flatMap { (assetClass, reqs) -> fetchForAsset(assetClass, reqs) }
    }

    private suspend fun fetchForAsset(assetClass: AssetClass, requests: List<QuoteRequest>): List<Quote> {
        if (requests.isEmpty()) return emptyList()
        for (provider in providers) {
            if (assetClass !in provider.supportedAssets) continue     // 品类不匹配，跳过
            if (breaker.isOpen(provider.id)) continue
            val result = try {
                provider.fetchQuotes(requests)
            } catch (_: Throwable) {
                null
            }
            if (!result.isNullOrEmpty()) {
                breaker.onSuccess(provider.id)
                return result
            }
            breaker.onFailure(provider.id)
        }
        return emptyList()
    }

    suspend fun search(
        keyword: String,
        limit: Int = 10,
        assetClass: AssetClass = AssetClass.A_SHARE,
    ): List<SearchHit> {
        if (keyword.isBlank()) return emptyList()
        for (provider in providers) {
            if (assetClass !in provider.supportedAssets) continue     // 品类不匹配，跳过
            if (breaker.isOpen(provider.id)) continue
            val result = try {
                provider.search(keyword, limit, assetClass)
            } catch (_: Throwable) {
                null
            }
            if (result == null) continue          // source without search support; not a failure
            if (result.isNotEmpty()) {
                breaker.onSuccess(provider.id)
                return result
            }
            breaker.onFailure(provider.id)
        }
        return emptyList()
    }

    /** True when at least one provider is usable right now. */
    fun anyUsable(): Boolean = providers.any { !breaker.isOpen(it.id) }
}