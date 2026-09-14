package com.omniticker.ui

import com.omniticker.model.Quote
import com.omniticker.util.FormatUtils
import javax.swing.table.AbstractTableModel

/**
 * Table data for the tool window. Numeric columns return Double/Long so the
 * RowSorter's stock comparator sorts numerically rather than lexically.
 * Renderers own the display formatting (see [QuoteCellRenderer]).
 */
class QuoteTableModel : AbstractTableModel() {
    private val rows = mutableListOf<Quote>()

    companion object {
        const val COL_CODE = 0
        const val COL_NAME = 1
        const val COL_PRICE = 2
        const val COL_PERCENT = 3
        const val COL_CHANGE = 4
        const val COL_HIGH = 5
        const val COL_LOW = 6
        const val COL_OPEN = 7
        const val COL_PREV_CLOSE = 8
        const val COL_AMOUNT = 9
    }

    private val columnNames = listOf("代码", "名称", "现价", "涨跌幅", "涨跌额", "最高", "最低", "今开", "昨收", "成交额")

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        COL_CODE, COL_NAME -> String::class.java
        else -> Double::class.java
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val q = rows.getOrNull(rowIndex) ?: return null
        return when (columnIndex) {
            COL_CODE -> q.code
            COL_NAME -> q.name
            COL_PRICE -> q.price
            COL_PERCENT -> q.changePercent
            COL_CHANGE -> q.change
            COL_HIGH -> q.high
            COL_LOW -> q.low
            COL_OPEN -> q.open
            COL_PREV_CLOSE -> q.prevClose
            COL_AMOUNT -> q.amount   // numeric for sorting; renderer formats
            else -> null
        }
    }

    fun setData(quotes: List<Quote>) {
        rows.clear()
        rows.addAll(quotes)
        fireTableDataChanged()
    }

    /** Model row by view row (row sorter may reorder). */
    fun quoteAt(modelRow: Int): Quote? = rows.getOrNull(modelRow)
}