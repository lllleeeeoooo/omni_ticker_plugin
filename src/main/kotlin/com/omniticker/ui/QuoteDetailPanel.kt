package com.omniticker.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.omniticker.model.Quote
import com.omniticker.model.QuoteLevel
import com.omniticker.util.FormatUtils
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Detail pane under the table: header (name + price) over a label/value grid,
 * with the five-level order book on the side. Fields left-aligned, values
 * right-aligned — matches the classic quote-panel look.
 */
class QuoteDetailPanel : JPanel(BorderLayout()) {

    private val title = JBLabel("").apply { font = JBUI.Fonts.label(14f) }
    private val subtitle = JBLabel("")

    private val fieldNames = listOf(
        "最高", "最低", "今开", "昨收", "成交量", "成交额",
        "换手率", "振幅", "PE(TTM)", "PB", "总市值", "涨停", "跌停",
    )
    private val fieldValues = fieldNames.associateWith { JBLabel("-", SwingConstants.RIGHT) }
    private val levelLabels: MutableList<Pair<JBLabel, JBLabel>> = mutableListOf() // (price, volume)

    private val headerPanel = JPanel(BorderLayout()).apply {
        add(title, BorderLayout.WEST)
        add(subtitle, BorderLayout.EAST)
        border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    }
    private val gridPanel = JPanel(GridBagLayout())
    private val bookPanel = JPanel(GridBagLayout())

    private val content = JPanel(BorderLayout()).apply {
        add(headerPanel, BorderLayout.NORTH)
        add(gridPanel, BorderLayout.CENTER)
        add(bookPanel, BorderLayout.SOUTH)
    }

    init {
        QuoteDetailPanel.active = this  // 供状态栏点击切换可见性
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, com.intellij.ui.JBColor.border()),
            BorderFactory.createEmptyBorder(4, 6, 6, 6),
        )
        buildGrid()
        buildBook()
        add(content, BorderLayout.CENTER)
        showQuote(null)
    }

    private fun buildGrid() {
        val g = GridBagConstraints()
        g.insets = Insets(2, 6, 2, 6)
        g.anchor = GridBagConstraints.WEST
        var col = 0
        var row = 0
        for (name in fieldNames) {
            val caption = JBLabel(name).apply { foreground = com.intellij.ui.JBColor(Color(0x808080), Color(0x9E9E9E)) }
            val value = fieldValues.getValue(name)
            g.gridx = col * 2; g.gridy = row
            gridPanel.add(caption, g)
            g.gridx = col * 2 + 1
            g.anchor = GridBagConstraints.EAST
            gridPanel.add(value, g)
            g.anchor = GridBagConstraints.WEST
            col++
            if (col >= 4) { col = 0; row++ }
        }
    }

    private fun buildBook() {
        val g = GridBagConstraints()
        g.insets = Insets(1, 6, 1, 6)
        g.anchor = GridBagConstraints.EAST
        for (i in 0 until 5) {
            val bidPrice = JBLabel("-", SwingConstants.RIGHT)
            val bidVol = JBLabel("-", SwingConstants.RIGHT)
            val askPrice = JBLabel("-", SwingConstants.RIGHT)
            val askVol = JBLabel("-", SwingConstants.RIGHT)
            bidPrice.foreground = QuoteColors.down   // 买 - 绿
            askPrice.foreground = QuoteColors.up     // 卖 - 红（A股买卖方向色规则）
            // 顺序：0-4 买一~买五，5-9 卖一~卖五（showQuote 按此索引）
            levelLabels += bidPrice to bidVol
            levelLabels += askPrice to askVol
            g.gridx = 0; g.gridy = i
            bookPanel.add(JBLabel("买${5 - i}"), g.apply { anchor = GridBagConstraints.WEST })
            g.gridx = 1
            bookPanel.add(bidPrice, g.apply { anchor = GridBagConstraints.EAST })
            g.gridx = 2
            bookPanel.add(bidVol, g.apply { anchor = GridBagConstraints.WEST })
            g.gridx = 3
            bookPanel.add(JBLabel("卖${i + 1}"), g.apply { anchor = GridBagConstraints.WEST })
            g.gridx = 4
            bookPanel.add(askPrice, g.apply { anchor = GridBagConstraints.EAST })
            g.gridx = 5
            bookPanel.add(askVol, g.apply { anchor = GridBagConstraints.WEST })
        }
        bookPanel.border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
    }

    fun showQuote(q: Quote?) {
        if (q == null) {
            title.text = "未选择股票"
            title.foreground = com.intellij.ui.JBColor(Color(0x808080), Color(0x9E9E9E))
            subtitle.text = ""
            fieldValues.values.forEach { it.text = "-" }
            levelLabels.forEach { (p, v) -> p.text = "-"; v.text = "-" }
            return
        }
        title.text = "${q.name}  ${q.code}"
        title.foreground = QuoteColors.forChange(q.change)
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
                val bidPair = levelLabels[i]
                val askPair = levelLabels[5 + i]
                setLevel(bidPair.first, bidPair.second, bid)
                setLevel(askPair.first, askPair.second, ask)
            }
        }
    }

    private fun setLevel(priceLabel: JBLabel, volLabel: JBLabel, level: QuoteLevel?) {
        if (level == null) {
            priceLabel.text = "-"; volLabel.text = "-"
            return
        }
        priceLabel.text = FormatUtils.price(level.price)
        volLabel.text = "${level.volume}手"
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