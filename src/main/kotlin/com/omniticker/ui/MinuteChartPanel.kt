package com.omniticker.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.omniticker.model.MinutePoint
import com.omniticker.model.MinuteSeries
import com.omniticker.util.FormatUtils
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Line2D
import javax.swing.JComponent

/**
 * 分时走势图（自绘，交易所风格，手写绘图保证细节可控）：
 *  - Y 轴以昨收为 0 基准、上下对称，0 轴恒居中；0 轴与绘图区四周为实线，
 *    其余水平网格/垂直分界为虚线（0 轴上/下各一条等分虚线）。
 *  - 刻度在图外：左价格、右涨跌幅%、底部时间（9:30…15:00，午休折叠）。
 *  - 价格线 + 成交量加权均价线；量柱红涨/绿跌/平灰（相对上一分钟，柱高=分钟增量）。
 *  - 悬停十字线 + 读数。
 */
class MinuteChartPanel : JComponent() {

    companion object {
        private val LINE_COLOR = JBColor(0x2D6CDF, 0x4B8AFF)   // 价格线：蓝
        private val AVG_COLOR = JBColor(0xD4A017, 0xF5C242)    // 均价线：黄
        private val ZERO_COLOR = JBColor(0xAAAAAA, 0x777777)   // 0 轴（昨收）
        private val GRID_COLOR = JBColor(0xDDDDDD, 0x444444)   // 虚线网格 / 实线边框
        private val AXIS_COLOR = JBColor(0x5A5A5A, 0xC8C8C8)   // 刻度文字（加深，保证可读）
        private val LABEL_FONT = JBUI.Fonts.label(10f)
        private val TICK_FONT = JBUI.Fonts.label(11f)

        // 版面留白：左右刻度区、底部时间区、顶部读数字带
        const val M_LEFT = 44.0
        const val M_RIGHT = 44.0
        const val M_BOTTOM = 14.0
        const val M_TOP = 16.0

        /** 交易分钟轴分段：上午 570–690 恒右 0..0.5，下午 780–900 恒右 0.5..1（无缝拼接、无休时空档）。 */
        fun xFraction(t: Double): Double = if (t <= 690.0) (t - 570.0) / 120.0 * 0.5 else 0.5 + (t - 780.0) / 120.0 * 0.5
    }

    private var series: MinuteSeries? = null
    private var prevClose: Double? = null
    private var hoverIdx: Int = -1   // 悬停最近点索引；-1 = 无

    init {
        addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: java.awt.event.MouseEvent) { updateHover(e.x, e.y) }
            override fun mouseDragged(e: java.awt.event.MouseEvent) { updateHover(e.x, e.y) }
        })
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseExited(e: java.awt.event.MouseEvent) {
                if (hoverIdx >= 0) { hoverIdx = -1; repaint() }
            }
        })
    }

    fun setSeries(s: MinuteSeries?, prevClose: Double?) {
        series = s
        this.prevClose = prevClose
        hoverIdx = -1
        repaint()
    }

    override fun getPreferredSize(): Dimension = Dimension(10, 170)
    override fun getMinimumSize(): Dimension = Dimension(10, 130)

    private fun updateHover(mx: Int, my: Int) {
        val pts = series?.points ?: return
        val plotW = (width.toDouble() - M_LEFT - M_RIGHT).coerceAtLeast(10.0)
        val fx = (mx - M_LEFT) / plotW
        if (fx < -0.02 || fx > 1.02) {
            if (hoverIdx >= 0) { hoverIdx = -1; repaint() }
            return
        }
        val t = if (fx <= 0.5) 570.0 + fx / 0.5 * 120.0 else 780.0 + (fx - 0.5) / 0.5 * 120.0
        var best = 0
        var bestD = Double.MAX_VALUE
        for ((i, p) in pts.withIndex()) {
            val d = Math.abs(mOf(p.minute) - t)
            if (d < bestD) { bestD = d; best = i }
        }
        if (best != hoverIdx) { hoverIdx = best; repaint() }
    }

    override fun paintComponent(g: Graphics) {
        val pts = series?.points
        if (pts.isNullOrEmpty()) {
            paintPlaceholder(g)
            return
        }
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width.toDouble()
            val plotX0 = M_LEFT
            val plotX1 = w - M_RIGHT
            val plotW = (plotX1 - plotX0).coerceAtLeast(10.0)
            val plotY0 = M_TOP
            val plotY1 = height.toDouble() - M_BOTTOM
            val plotH = (plotY1 - plotY0).coerceAtLeast(30.0)
            val volH = plotH / 5.0
            val priceH = plotH - volH - 6.0
            val pc = prevClose

            // 以昨收为 0 轴的上下对称价格范围（0 轴恒居中）
            val pMinV = pts.minOf { it.price }
            val pMaxV = pts.maxOf { it.price }
            var radial = (pMaxV - pMinV) / 2.0
            if (pc != null) radial = Math.max(radial, Math.max(pMaxV - pc, pc - pMinV))
            radial = Math.max(radial * 1.04, 0.1)
            val pLow = if (pc != null) pc - radial else pMinV - radial
            val pHigh = if (pc != null) pc + radial else pMaxV + radial
            val yOf = fun(p: Double): Double = plotY0 + (pHigh - p) / (pHigh - pLow) * priceH
            // 越界时间点（集合竞价/收盘余数）钳制在绘图区内
            val xOf = fun(m: Int): Double = plotX0 + xFraction(mOf(m)).coerceIn(0.0, 1.0) * plotW

            // 水平网格线：以昨收 0% 对称，步长按波动自动取 5/2/1/0.5/0.25%（每侧 ≤4）
            val gridPrices = mutableListOf<Double>()
            if (pc != null && pc > 0.0) {
                val stepPct = listOf(5.0, 2.0, 1.0, 0.5).find { radial / pc * 100.0 / it <= 4.0 } ?: 0.25
                for (sign in listOf(1.0, -1.0)) {
                    var k = 1
                    while (true) {
                        val pv = pc * (1.0 + sign * k * stepPct / 100.0)
                        if ((sign > 0 && pv > pHigh) || (sign < 0 && pv < pLow)) break
                        gridPrices += pv
                        k++
                    }
                }
            }
            gridPrices.sort()
            // 过密过滤 ≥14px，保证刻度可读；网格可能为空（价格无波动）——照常绘制其他元素
            val filtered = if (gridPrices.isEmpty()) mutableListOf<Double>() else mutableListOf(gridPrices.first())
            if (!gridPrices.isEmpty()) {
                for (pv in gridPrices) {
                    if (Math.abs(yOf(pv) - yOf(filtered.last())) >= 14.0) filtered += pv
                }
            }
            val mesh = filtered.toList()

            // 绘图区钳制：曲线/柱/网格不越边框
            val plotRect = java.awt.Rectangle(plotX0.toInt(), plotY0.toInt(), plotW.toInt(), plotH.toInt())
            val oldClip = g2.clip
            g2.clip = plotRect

            // 水平网格虚线（0 轴除外）
            g2.color = GRID_COLOR
            for (pv in mesh) drawDashedH(g2, yOf(pv), plotX0, plotX1)
            // 垂直参考线：11:30/13:00 分界实线 + 10:30/14:00 虚线
            g2.draw(Line2D.Double(xOf(1130), plotY0, xOf(1130), plotY0 + priceH))
            g2.draw(Line2D.Double(xOf(1300), plotY0, xOf(1300), plotY0 + priceH))
            drawDashedV(g2, xOf(1030), plotY0, plotY0 + priceH)
            drawDashedV(g2, xOf(1400), plotY0, plotY0 + priceH)

            // 0 轴（昨收）实线恒居中 + 上/下各一条等分虚线
            val yZero = if (pc != null) yOf(pc) else plotY0 + priceH / 2.0
            if (pc != null) {
                g2.color = ZERO_COLOR
                g2.draw(Line2D.Double(plotX0, yZero, plotX1, yZero))
            }
            g2.color = GRID_COLOR
            drawDashedH(g2, (plotY0 + yZero) / 2.0, plotX0, plotX1)
            drawDashedH(g2, (yZero + plotY0 + priceH) / 2.0, plotX0, plotX1)

            // 分时折线与成交量区分界实线
            g2.color = GRID_COLOR
            g2.draw(Line2D.Double(plotX0, plotY0 + priceH, plotX1, plotY0 + priceH))

            // 成交量柱：柱高 = 分钟增量；红涨/绿跌/平灰，相对上一分钟（首柱相对昨收）
            val rawVols = pts.map { it.volume }
            val isCumulative = (1 until rawVols.size).count { rawVols[it] >= rawVols[it - 1] } * 2 >= rawVols.size
            val minuteVols = if (isCumulative) {
                rawVols.mapIndexed { i, v -> v - (if (i > 0) rawVols[i - 1] else 0L) }.map { Math.max(it, 0L) }
            } else {
                rawVols
            }
            val maxVol = minuteVols.maxOrNull()?.coerceAtLeast(1L) ?: 1L
            if (maxVol > 0) {
                val barW = (plotW / pts.size.toDouble() * 0.7).coerceAtLeast(1.0)
                val volTopBase = plotY0 + priceH + 6.0
                var prevPrice = pc
                for ((i, p) in pts.withIndex()) {
                    g2.color = if (prevPrice == null) QuoteColors.flat else QuoteColors.forChange(p.price - prevPrice)
                    val bh = minuteVols[i].toDouble() / maxVol * volH
                    // 柱起点内缩并 clamp 进图内：首/末柱（9:30/15:00）中心贴边界时不会被裁掉一半
                    val cx = xOf(p.minute)
                    val bx = Math.max(plotX0, Math.min(cx - barW / 2.0, plotX1 - barW)).toInt()
                    g2.fillRect(bx, (volTopBase + volH - bh).toInt(), barW.toInt(), bh.toInt())
                    prevPrice = p.price
                }
            }

            // 价格折线（蓝）
            g2.color = LINE_COLOR
            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val b = pts[i + 1]
                g2.draw(Line2D.Double(xOf(a.minute), yOf(a.price), xOf(b.minute), yOf(b.price)))
            }

            // 均价线（黄，VWAP）画在价格线之上
            var accVol = 0.0
            var accAmt = 0.0
            val avgY = mutableListOf<Double?>()
            for (p in pts) {
                accVol += p.volume
                accAmt += p.amount
                avgY += if (accVol > 0.0) yOf(accAmt / accVol) else null
            }
            if (accVol > 0.0 && accAmt > 0.0) {
                g2.color = AVG_COLOR
                for (i in 0 until pts.size - 1) {
                    val ay = avgY[i]
                    val by = avgY[i + 1]
                    if (ay == null || by == null) continue
                    g2.draw(Line2D.Double(xOf(pts[i].minute), ay, xOf(pts[i + 1].minute), by))
                }
            }

            // 悬停十字线（图内）
            if (hoverIdx >= 0 && hoverIdx < pts.size) {
                val hp = pts[hoverIdx]
                val hx = xOf(hp.minute)
                val hy = yOf(hp.price)
                g2.color = GRID_COLOR
                g2.draw(Line2D.Double(hx, plotY0, hx, plotY0 + priceH))
                g2.draw(Line2D.Double(plotX0, hy, plotX1, hy))
            }

            // 恢复裁剪：边框与刻度不被绘图区限制
            g2.clip = oldClip
            // 绘图区四周边框（实线）
            g2.color = GRID_COLOR
            g2.fillRect(plotX0.toInt(), plotY0.toInt(), plotW.toInt(), 1)
            g2.fillRect(plotX0.toInt(), (plotY1 - 1).toInt(), plotW.toInt(), 1)
            g2.fillRect(plotX0.toInt(), plotY0.toInt(), 1, plotH.toInt())
            g2.fillRect((plotX1 - 1).toInt(), plotY0.toInt(), 1, plotH.toInt())

            // 悬停读数（图外顶部带内右对齐，保证不被绘图区裁剪、不被上方详情挡住）
            if (hoverIdx >= 0 && hoverIdx < pts.size) {
                val hp = pts[hoverIdx]
                g2.color = AXIS_COLOR
                g2.font = TICK_FONT
                val read = "%02d:%02d  %s".format(hp.minute / 100, hp.minute % 100, FormatUtils.price(hp.price))
                val readX = Math.max((plotX0 + 2).toInt(), (plotX1 - g2.fontMetrics.stringWidth(read)).toInt())
                g2.drawString(read, readX, (M_TOP - 4).toInt())
            }

            // —— 图外刻度 —— 网格线对应的左右数值；网格为空时用固定 3 条兜底（刻度必然可见）
            val tickPrices = if (mesh.isNotEmpty()) mesh else listOf(
                pLow + (pHigh - pLow) * 0.25,
                pLow + (pHigh - pLow) * 0.5,
                pLow + (pHigh - pLow) * 0.75,
            )
            g2.color = AXIS_COLOR
            g2.font = TICK_FONT
            val tickBaseline = fun(ly: Double): Int = (ly + g2.fontMetrics.ascent / 2.0).toInt()  // 文字中心与网格线对齐
            for (pv in tickPrices) {
                val ly = yOf(pv)
                val priceTxt = FormatUtils.price(pv)
                g2.drawString(priceTxt, (plotX0 - 4 - g2.fontMetrics.stringWidth(priceTxt)).toInt(), tickBaseline(ly))
                if (pc != null && pc > 0.0) {
                    val pctTxt = "%+.2f%%".format((pv / pc - 1.0) * 100.0)
                    g2.drawString(pctTxt, (plotX1 + 4).toInt(), tickBaseline(ly))
                }
            }
            // 底部：时间刻度
            val labelY = (height - 3).toInt()
            g2.drawString("9:30", plotX0.toInt(), labelY)
            val noonLabel = "11:30/13:00"
            g2.drawString(noonLabel, Math.max(plotX0.toInt(), (xOf(1130) - g2.fontMetrics.stringWidth(noonLabel) / 2).toInt()), labelY)
            g2.drawString("15:00", (plotX1 - g2.fontMetrics.stringWidth("15:00")).toInt(), labelY)
        } finally {
            g2.dispose()
        }
    }

    private fun paintPlaceholder(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.color = AXIS_COLOR
            g2.font = JBUI.Fonts.label(12f)
            val s = "暂无分时数据"
            g2.drawString(s, (width - g2.fontMetrics.stringWidth(s)) / 2, (height + g2.fontMetrics.height) / 2)
        } finally {
            g2.dispose()
        }
    }

    /** 水平虚线（3px 实 + 3px 空）。 */
    private fun drawDashedH(g2: Graphics2D, y: Double, x0: Double, x1: Double) {
        var x = x0
        while (x < x1) {
            g2.draw(Line2D.Double(x, y, Math.min(x + 3.0, x1), y))
            x += 6.0
        }
    }

    /** 垂直虚线。 */
    private fun drawDashedV(g2: Graphics2D, x: Double, y0: Double, y1: Double) {
        var y = y0
        while (y < y1) {
            g2.draw(Line2D.Double(x, y, x, Math.min(y + 3.0, y1)))
            y += 6.0
        }
    }

    /** 4 位 HHMM 转当日分钟数：0930 → 570。 */
    private fun mOf(hhmm: Int): Double = (hhmm / 100) * 60.0 + hhmm % 100
}