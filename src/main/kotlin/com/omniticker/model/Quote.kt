package com.omniticker.model

/** One level of the order book (买一/卖一...). */
data class QuoteLevel(
    val price: Double,
    val volume: Long,   // 手 (100 shares)
)

/** A single quote for one instrument (stock or index). All monetary values in 元. */
data class Quote(
    val code: String,            // 6-digit
    val name: String,
    val market: Market,
    val price: Double,
    val change: Double,          // 涨跌额
    val changePercent: Double,   // 涨跌幅 %
    val open: Double,
    val high: Double,
    val low: Double,
    val prevClose: Double,
    val volume: Long,            // 手
    val amount: Double,          // 元
    val turnoverRate: Double? = null,
    val amplitude: Double? = null,      // 振幅 %
    val bidAsk: List<QuoteLevel>? = null, // 买一→卖五 (or sell for indices: null)
    val pe: Double? = null,      // PE(TTM)
    val pb: Double? = null,
    val totalMarketCap: Double? = null,  // 元
    val limitUp: Double? = null,
    val limitDown: Double? = null,
    val timestamp: Long,
    val source: String,          // provider id that produced this quote
    val assetClass: AssetClass = AssetClass.A_SHARE,  // 品类（默认 A 股，P2 起美股/虚拟币填充）
) {
    val isUp: Boolean get() = change > 0.0
    val isDown: Boolean get() = change < 0.0
    val isFlat: Boolean get() = change == 0.0
}

/** Search result entry from the suggest endpoint. */
data class SearchHit(
    val code: String,
    val name: String,
    val market: Market,
    val pinyin: String = "",
    val type: String = "",   // 1=股票 2=指数 ...
    val assetClass: AssetClass = AssetClass.A_SHARE,  // 品类（默认 A 股）
)