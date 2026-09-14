package com.omniticker.data

import com.omniticker.model.Market
import com.omniticker.model.MinutePoint
import com.omniticker.model.MinuteSeries
import com.omniticker.model.tencentCode
import com.omniticker.util.RequestClient
import java.util.regex.Pattern

/**
 * 腾讯当日分时接口：web.ifzq.gtimg.cn/appstock/app/minute/query?code=sh600519
 * 返回 UTF-8 JSON：data.<symbol>.data.data = ["HHMM 价格 成交量(手) 成交额(元)", ...]。
 * 该响应结构固定，这里做定向提取，不引入第三方 JSON 依赖。
 */
object MinuteProvider {

    suspend fun fetch(code: String, market: Market): MinuteSeries? {
        val symbol = market.tencentCode(code)
        val url = "https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=$symbol"
        val body = RequestClient.getText(url, "https://gu.qq.com/", 10_000) ?: return null
        return parse(body, code)
    }

    private fun parse(body: String, code: String): MinuteSeries? {
        // "date":"20260914"
        val date = Pattern.compile("\\\"date\\\":\\\"(\\d{8})\\\"").matcher(body).let {
            if (it.find()) it.group(1) else ""
        }
        // "data":["0930 1277.27 133 16987691.06", ...]
        val arrM = Pattern.compile("\\\"data\\\":\\[((?:\\\"[^\\\"]*\\\"\\s*,?\\s*)*)\\]").matcher(body)
        if (!arrM.find()) return null
        val arr = arrM.group(1)
        val pts = mutableListOf<MinutePoint>()
        val item = Pattern.compile("\\\"([^\\\"]*)\\\"")
        val mm = item.matcher(arr)
        while (mm.find()) {
            val f = mm.group(1).split(' ')
            if (f.size < 3) continue
            val minute = f[0].toIntOrNull() ?: continue
            val price = f[1].toDoubleOrNull() ?: continue
            pts += MinutePoint(
                minute, price,
                f[2].toLongOrNull() ?: 0L,
                f.getOrNull(3)?.toDoubleOrNull() ?: 0.0,
            )
        }
        return if (pts.isEmpty()) null else MinuteSeries(code, date, pts, null)
    }
}