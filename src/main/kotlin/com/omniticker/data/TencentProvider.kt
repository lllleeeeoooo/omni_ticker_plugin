package com.omniticker.data

import com.omniticker.model.Quote
import com.omniticker.model.SearchHit
import com.omniticker.util.RequestClient

/**
 * Tencent quote feed — the PRIMARY source (verified live: single request
 * returns price, full 5-level order book, PE/PB, caps, limit prices, indexes).
 * GBK-encoded, '~'-separated. Sina (hq.sinajs.cn) serves as the fallback.
 */
class TencentProvider : QuoteProvider {

    override val id: String = ID

    override suspend fun fetchQuotes(requests: List<QuoteRequest>): List<Quote>? {
        if (requests.isEmpty()) return emptyList()
        val codes = requests.joinToString(",") { it.marketCode() }
        // web.sqt.gtimg.cn is the more stable host; qt.gtimg.cn still works as a backup.
        val url = "https://qt.gtimg.cn/q=$codes"
        val body = RequestClient.getTextGbk(url, REFERER) ?: return null
        val parsed = TencentParser.parse(body)
        if (parsed.isNullOrEmpty()) return null
        val byCode = parsed.associateBy { it.code }
        return requests.mapNotNull { byCode[it.code] }
    }

    override suspend fun search(keyword: String, limit: Int, assetClass: com.omniticker.model.AssetClass): List<SearchHit> {
        if (keyword.isBlank()) return emptyList()
        val q = java.net.URLEncoder.encode(keyword, Charsets.UTF_8)
        val url = "https://smartbox.gtimg.cn/s3/?v=2&t=all&c=1&q=$q"
        val body = RequestClient.getText(url, "https://gu.qq.com/") ?: return emptyList()
        return TencentParser.parseSearch(body, limit) ?: emptyList()
    }

    companion object {
        const val ID = "tencent"
        const val REFERER = "https://gu.qq.com/"
    }
}