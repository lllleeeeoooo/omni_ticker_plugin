package com.omniticker.util

import java.text.DecimalFormat

/** Display helpers. Business conversion (万元->元 etc.) happens in the parsers, never here. */
object FormatUtils {

    private val priceFmt = DecimalFormat("0.00")
    private val pctFmt = DecimalFormat("0.00%")
    private val bigFmt = DecimalFormat("0.00")

    fun price(v: Double): String = priceFmt.format(v)

    fun priceOrDash(v: Double?): String = v?.let { price(it) } ?: "-"

    /** 元 -> "1.23亿" / "456.7万" / "0". */
    fun amount(yuan: Double): String = when {
        yuan >= 1e8 -> bigFmt.format(yuan / 1e8) + "亿"
        yuan >= 1e4 -> bigFmt.format(yuan / 1e4) + "万"
        else -> "%.0f".format(yuan)
    }

    fun percent(changePercent: Double): String = buildString {
        append(if (changePercent > 0) "+" else "")
        append(pctFmt.format(changePercent / 100.0).replace("%", ""))
        append("%")
    }

    fun signed(change: Double): String = buildString {
        append(if (change > 0) "+" else if (change < 0) "-" else "")
        append(priceFmt.format(kotlin.math.abs(change)))
    }

    /** "▲"/"▼"/"—" markers for a change (mode: arrow | caret | none). */
    fun trendMark(change: Double, mode: String = "arrow"): String = when {
        change > 0 -> if (mode == "caret") "↑" else if (mode == "none") "" else "▲"
        change < 0 -> if (mode == "caret") "↓" else if (mode == "none") "" else "▼"
        else -> if (mode == "none") "" else "—"
    }

    private val timeFmt = java.text.SimpleDateFormat("HH:mm:ss").apply { timeZone = java.util.TimeZone.getDefault() }

    /** "2026/09/12 15:00" style timestamp for tooltips. */
    fun lastUpdateTime(epochMs: Long): String =
        java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(java.util.Date(epochMs))
}