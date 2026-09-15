package com.omniticker.data

import com.omniticker.model.Quote
import com.omniticker.model.SearchHit
import com.omniticker.util.RequestClient

/**
 * Sina hq.sinajs.cn — the FALLBACK quote source (Tencent is primary).
 *
 * Live-verified quirks:
 * - requires HTTPS + a finance.sina.com.cn Referer, else 403 / "Kinsoku jikou desu"
 * - response is UTF-8 (2026-09); [RequestClient.getTextSmart] tolerates the legacy GBK case
 * - limited field set (no turnover/PE/PB/caps) — acceptable as a fallback
 * - no search endpoint implemented; search is served by Tencent's smartbox
 */
class SinaProvider : QuoteProvider {

    override val id: String = ID

    override suspend fun fetchQuotes(requests: List<QuoteRequest>): List<Quote>? {
        if (requests.isEmpty()) return emptyList()
        val codes = requests.joinToString(",") { it.marketCode() } // same sh/sz/bj prefix as Tencent
        val url = "https://hq.sinajs.cn/list=$codes"
        val body = RequestClient.getTextSmart(url, REFERER) ?: return null
        val parsed = SinaParser.parse(body)
        if (parsed.isNullOrEmpty()) return null
        val byCode = parsed.associateBy { it.code }
        return requests.mapNotNull { byCode[it.code] }
    }

    override suspend fun search(
        keyword: String,
        limit: Int,
        assetClass: com.omniticker.model.AssetClass,
    ): List<SearchHit>? = null

    companion object {
        const val ID = "sina"
        const val REFERER = "https://finance.sina.com.cn"
    }
}