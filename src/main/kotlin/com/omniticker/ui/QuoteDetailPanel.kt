package com.omniticker.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.omniticker.data.MinuteProvider
import com.omniticker.model.Quote
import com.omniticker.model.QuoteLevel
import com.omniticker.util.FormatUtils
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 详情面板 —— Element Plus 设计理念：
 * 整块是一张卡片：顶部 header（股票名/代码 + 大号主价格），下方依次为
 * 指标表格（4 列等宽、带完整边框、「名称：值」左对齐）、分时走势图、买卖盘
 * （对称两栏、逐档一行）。字号阶梯统一：辅助/名称 11f、指标值 11f、价格 16f；
 * 分时数据在选择股票后异步拉取（腾讯分钟接口），不阻塞 UI。
 */
class QuoteDetailPanel : JPanel(BorderLayout()) {

    private val title = JBLabel("").apply { font = JBUI.Fonts.label(14f) }
    private val subtitle = JBLabel("").apply { font = JBUI.Fonts.label(16f) }

    private val fieldNames = listOf(
        "最高", "最低", "今开", "昨收", "成交量", "成交额",
        "换手率", "振幅", "PE(TTM)", "PB", "总市值", "涨停", "跌停",
    )
    private val fieldValues = fieldNames.associateWith { JBLabel("—", SwingConstants.LEFT) }
    private val levelLabels: MutableList<Pair<JBLabel, JBLabel>> = mutableListOf() // (price, volume)

    // —— 统一样式令牌：辅助色 / 分隔线色 ——
    private val dimColor = com.intellij.ui.JBColor(Color(0x8C8C8C), Color(0xA6A6A6))
    private val ruleColor = com.intellij.ui.JBColor.border()

    private val headerPanel = JPanel(BorderLayout()).apply {
        add(title, BorderLayout.WEST)
        add(subtitle, BorderLayout.EAST)
    }
    /** 真正的表格：四周完整外框线由 gridPanel 提供，单元格只画内部右/下分隔线。 */
    private val gridPanel = JPanel(GridBagLayout()).apply {
        border = BorderFactory.createMatteBorder(1, 1, 1, 1, ruleColor)
    }
    private val bookPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val chart = MinuteChartPanel()

    /** 垂直堆叠：header / 指标表 / 分时图 / 买卖盘；各区块按自身首选高度，宽度铺满。 */
    private val content = JPanel(GridBagLayout()).apply {
        val g = GridBagConstraints()
        g.fill = GridBagConstraints.HORIZONTAL
        g.weightx = 1.0
        g.gridx = 0
        g.gridy = 0; add(headerPanel, g)
        g.gridy = 1; add(gridPanel, g)
        g.gridy = 2; add(chart, g)
        g.gridy = 3; add(bookPanel, g)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var minuteJob: kotlinx.coroutines.Job? = null

    init {
        QuoteDetailPanel.active = this  // 供状态栏点击切换可见性
        // 卡片外框 + 统一留白（宽松：更多呼吸空间）
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 1, 1, 1, ruleColor),
            BorderFactory.createEmptyBorder(14, 16, 14, 16),
        )
        headerPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ruleColor),
            BorderFactory.createEmptyBorder(0, 0, 8, 0),
        )
        bookPanel.border = BorderFactory.createEmptyBorder(14, 0, 0, 0)
        chart.border = BorderFactory.createEmptyBorder(10, 0, 10, 0)
        buildGrid()
        buildBook()
        add(content, BorderLayout.CENTER)
        showQuote(null)
    }

    /**
     * 指标区：5 列 × 3 行表格。单元格 = 灰名称（左）+ 数值（右，表格惯例），
     * 列间/行间画细网格线（末行末列留白作边界），五列等宽平均分布。
     */
    private fun buildGrid() {
        val g = GridBagConstraints()
        g.fill = GridBagConstraints.BOTH
        g.weightx = 1.0   // 四列等宽
        g.weighty = 1.0   // 四行均分剩余高度
        g.insets = Insets(0, 0, 0, 0)   // 单元格紧贴，格线连续不断
        var col = 0
        var row = 0
        val rows = (fieldNames.size + 3) / 4   // 4 列 × 4 行（13 → 4+4+4+1）
        for (name in fieldNames) {
            // 单元格 = 名称：值 左对齐连续排；内边距放单元格自身 border（不影响格线），
            // 单元格只画内部右/下分隔线，外框由 gridPanel 提供，末行末列留白收边
            val cell = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(JBLabel("$name：", SwingConstants.LEFT).apply {
                    foreground = dimColor
                    font = JBUI.Fonts.label(11f)
                })
                add(fieldValues.getValue(name).apply { font = JBUI.Fonts.label(11f) })
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(
                        0, 0, if (row == rows - 1) 0 else 1, if (col == 3) 0 else 1, ruleColor,
                    ),
                    BorderFactory.createEmptyBorder(5, 8, 5, 8),
                )
            }
            g.gridx = col; g.gridy = row
            gridPanel.add(cell, g)
            col++
            if (col >= 4) { col = 0; row++ }
        }
    }

    /**
     * 买卖盘：5 档一行，买侧（绿）居左、卖侧（红）居右、逐行底部细分隔线，
     * 行高一致，与上方的指标表共用同一套视觉语言。
     */
    private fun buildBook() {
        val bidPairs = mutableListOf<Pair<JBLabel, JBLabel>>()
        val askPairs = mutableListOf<Pair<JBLabel, JBLabel>>()
        for (i in 0 until 5) {
            val bidPrice = JBLabel("-", SwingConstants.LEFT).apply { foreground = QuoteColors.down } // 买 - 绿
            val bidVol = JBLabel("-") // 数量随价格色，注释见下
            val askPrice = JBLabel("-", SwingConstants.LEFT).apply { foreground = QuoteColors.up }   // 卖 - 红
            val askVol = JBLabel("-")
            bidPrice.font = JBUI.Fonts.label(13f)
            askPrice.font = JBUI.Fonts.label(13f)
            bidVol.font = JBUI.Fonts.label(11f)
            askVol.font = JBUI.Fonts.label(11f)
            bidVol.foreground = dimColor
            askVol.foreground = dimColor
            val buyBlock = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(JBLabel("买${5 - i}").apply { foreground = dimColor })
                add(Box.createHorizontalStrut(10))
                add(bidPrice)
                add(Box.createHorizontalStrut(6))
                add(bidVol)
            }
            val sellBlock = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(JBLabel("卖${i + 1}").apply { foreground = dimColor })
                add(Box.createHorizontalStrut(10))
                add(askPrice)
                add(Box.createHorizontalStrut(6))
                add(askVol)
            }
            val row = JPanel(BorderLayout()).apply {
                add(buyBlock, BorderLayout.WEST)
                add(sellBlock, BorderLayout.EAST)
                border = if (i == 4) {
                    BorderFactory.createEmptyBorder(3, 0, 3, 0)
                } else {
                    BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, ruleColor),
                        BorderFactory.createEmptyBorder(3, 0, 3, 0),
                    )
                }
            }
            bookPanel.add(row)
            // 顺序约定：0-4 为买一~买五、5-9 为卖一~卖五（showQuote 按此索引）
            bidPairs += bidPrice to bidVol
            askPairs += askPrice to askVol
        }
        levelLabels += bidPairs
        levelLabels += askPairs
    }

    fun showQuote(q: Quote?) {
        if (q == null) {
            title.text = "未选择股票"
            title.foreground = dimColor
            subtitle.text = ""
            fieldValues.values.forEach { it.text = "-" }
            levelLabels.forEach { (p, v) -> p.text = "-"; v.text = "-" }
            chart.setSeries(null, null)
            return
        }
        title.text = "${q.name}  ${q.code}"
        title.foreground = com.intellij.ui.JBColor(Color(0x1A1A1A), Color(0xBBBBBB))
        subtitle.text = buildString {
            append(FormatUtils.price(q.price)).append("  ")
            append(FormatUtils.percent(q.changePercent)).append(" (")
            append(FormatUtils.signed(q.change)).append(")")
        }
        subtitle.foreground = QuoteColors.forChange(q.change)

        fieldValues.getValue("最高").text = FormatUtils.price(q.high)
        fieldValues.getValue("最低").text = FormatUtils.price(q.low)
        fieldValues.getValue("今开").text = FormatUtils.price(q.open)
        fieldValues.getValue("昨收").text = FormatUtils.price(q.prevClose)
        fieldValues.getValue("成交量").text = "%.2f万手".format(q.volume / 10_000.0)
        fieldValues.getValue("成交额").text = FormatUtils.amount(q.amount)
        fieldValues.getValue("换手率").text = q.turnoverRate?.let { FormatUtils.percent(it) } ?: "-"
        fieldValues.getValue("振幅").text = q.amplitude?.let { FormatUtils.percent(it) } ?: "-"
        fieldValues.getValue("PE(TTM)").text = q.pe?.let { "%.2f".format(it) } ?: "-"
        fieldValues.getValue("PB").text = q.pb?.let { "%.2f".format(it) } ?: "-"
        fieldValues.getValue("总市值").text = q.totalMarketCap?.let { FormatUtils.amount(it) } ?: "-"
        fieldValues.getValue("涨停").text = q.limitUp?.let { FormatUtils.price(it) } ?: "-"
        fieldValues.getValue("跌停").text = q.limitDown?.let { FormatUtils.price(it) } ?: "-"

        q.bidAsk?.let { levels ->
            // levels: bid1..bid5 (0..4), ask1..ask5 (5..9)
            require(levels.size <= 10)
            for (i in 0 until 5) {
                val bid = levels.getOrNull(i)
                val ask = levels.getOrNull(5 + i)
                setLevel(levelLabels[i].first, levelLabels[i].second, bid)
                setLevel(levelLabels[5 + i].first, levelLabels[5 + i].second, ask)
            }
        }

        // 分时走势：切换股票即后台拉取当日分钟数据，完成后回主线程刷新图表。
        minuteJob?.cancel()
        val code = q.code
        val market = q.market
        val pc = q.prevClose
        minuteJob = scope.launch {
            val s = MinuteProvider.fetch(code, market)
            ApplicationManager.getApplication().invokeLater { chart.setSeries(s, pc) }
        }
    }

    private fun setLevel(priceLabel: JBLabel, volLabel: JBLabel, level: QuoteLevel?) {
        if (level == null) {
            priceLabel.text = "-"; volLabel.text = "-"
            return
        }
        priceLabel.text = FormatUtils.price(level.price)
        volLabel.text = "×${level.volume}手"
    }

    companion object {
        @Volatile
        private var active: QuoteDetailPanel? = null

        /** 状态栏点击时切换详情面板的显示/隐藏。 */
        fun toggleVisibility() {
            active?.let { panel ->
                panel.isVisible = !panel.isVisible
                panel.parent?.revalidate()
                panel.parent?.repaint()
            }
        }
    }
}