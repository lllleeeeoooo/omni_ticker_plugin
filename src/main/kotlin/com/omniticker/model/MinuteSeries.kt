package com.omniticker.model

/** 单日分时数据点：分钟（HHMM 整数，如 0930 → 930）、价格（元）、成交量（手）、成交额（元）。 */
data class MinutePoint(
    val minute: Int,
    val price: Double,
    val volume: Long,
    val amount: Double = 0.0,   // 当日累计成交额（元），用于计算成交量加权均价（VWAP）
)

/** 腾讯分钟接口返回的当日分时曲线（价格序列 + 成交量）。 */
data class MinuteSeries(
    val code: String,
    val date: String,               // yyyyMMdd
    val points: List<MinutePoint>,
    val prevClose: Double? = null,  // 分时接口不含昨收，由外层 quote 补入作基准线
)