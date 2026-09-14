package com.omniticker.ui

import com.omniticker.util.FormatUtils
import java.awt.Color
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

/**
 * Renders quote columns: numeric columns right-aligned, A-share colors
 * (red up / green down) on the change columns. The row sorter sees raw
 * Double values (see [QuoteTableModel]); all display formatting lives here.
 *
 * Selection: the selected row keeps the default selection background (whole
 * row highlight), while per-column font colors stay red-up/green-down — only
 * a +0.00% (flat) value falls back to the default foreground.
 */
class QuoteCellRenderer : DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
        val model = table.model
        val defaultFg = table.foreground
        // 整行红涨绿跌（选中/未选中一致）：所有列均按该行的涨跌着色，
        // 上涨红、下跌绿、平值(0.00%)清为默认前景色；选中时父类设置的白色前景
        // 在此被覆写，选中只体现为背景 highlight。
        if (model is QuoteTableModel) {
            val q = model.quoteAt(table.convertRowIndexToModel(row))
            c.foreground = if (q != null) trendColor(q) ?: defaultFg else defaultFg
            c.horizontalAlignment = if (column <= QuoteTableModel.COL_NAME) JLabel.LEFT else JLabel.RIGHT
        } else {
            c.foreground = defaultFg
        }
        // 选中背景由父类保留（整行 highlight）
        return c
    }

    /** 上涨红、下跌绿；+0.00%（平）→ null（由调用方回落到默认前景色）。 */
    private fun trendColor(q: com.omniticker.model.Quote): Color? = when {
        q.change > 0 -> QuoteColors.up
        q.change < 0 -> QuoteColors.down
        else -> null
    }
}