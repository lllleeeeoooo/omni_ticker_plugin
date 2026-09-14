package com.omniticker.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.omniticker.model.SearchHit
import com.omniticker.service.QuoteManager
import com.omniticker.service.WatchlistState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.RowSorter
import javax.swing.SortOrder
import java.awt.event.KeyEvent
import javax.swing.AbstractAction

/**
 * The "Omni Ticker" tool window: an add-stock input row (exact code or fuzzy
 * name/pinyin search w/ picker), the quote table with numeric row sorting
 * plus 上移/下移 ordering, and the detail pane below.
 */
class QuoteToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val manager = project.getService(QuoteManager::class.java)
        val model = QuoteTableModel()
        val table = JBTable(model)
        // 关闭单元格选择 + 禁止表格获取键盘焦点：消除选中/聚焦 cell 的蓝色描边
        // （整行选择与选中背景保留）。聚焦描边来自 LAF 的 Table.focusCellHighlightBorder，
        // 表格无需键盘导航故直接禁用焦点。
        table.setCellSelectionEnabled(false)
        table.isFocusable = false
        table.rowSelectionAllowed = true
        table.setShowVerticalLines(false)
        table.setShowHorizontalLines(false)
        table.intercellSpacing = JBUI.size(0, 1)
        table.rowHeight = JBUI.scale(22)
        table.autoCreateRowSorter = true
        table.rowSorter?.setSortKeys(
            listOf(RowSorter.SortKey(QuoteTableModel.COL_PERCENT, SortOrder.DESCENDING))
        )

        val renderer = QuoteCellRenderer()
        for (col in 0 until model.columnCount) {
            table.setDefaultRenderer(model.getColumnClass(col), renderer)
        }

        val detail = QuoteDetailPanel()

        // Keep the selected detail pane in sync when data refreshes.
        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                var viewRow = table.selectedRow
                // 列表未选中任何个股时默认展示第一行
                if (viewRow < 0 && model.rowCount > 0) viewRow = 0
                val q = if (viewRow >= 0) model.quoteAt(table.convertRowIndexToModel(viewRow)) else null
                detail.showQuote(q)
            }
        }

        // 打开面板立即用当前快照填充并默认选中第一只自选股——
        // subscribe 注册后要等下一次刷新回调才触发，不能依赖它。
        model.setData(manager.watchlistQuotes())
        if (model.rowCount > 0) {
            table.selectionModel.setSelectionInterval(0, 0)
        }

        // ---- add-stock row: exact code, or fuzzy name/pinyin via search -----
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val searchField = JBTextField().apply {
            preferredSize = com.intellij.util.ui.JBUI.size(JBUI.scale(220), 26)
            toolTipText = "输入 6 位代码回车直接添加；输入名称/拼音回车搜索选择添加"
        }
        val feedback = JBLabel("")
        val addStockButton = JButton("添加").apply {
            addActionListener { doAddStock(project, toolWindow, manager, scope, searchField, feedback) }
        }
        searchField.addActionListener {
            doAddStock(project, toolWindow, manager, scope, searchField, feedback)
        }

        // ---- toolbar: refresh / remove / reorder ----------------------------
        val refreshButton = JButton("立即刷新").apply {
            addActionListener { manager.refreshNow() }
        }
        val removeButton = JButton("删除自选").apply {
            addActionListener {
                val viewRow = table.selectedRow
                if (viewRow >= 0) {
                    val quote = model.quoteAt(table.convertRowIndexToModel(viewRow))
                    val code = quote?.code ?: return@addActionListener
                    WatchlistState.getInstance().remove(code)
                    manager.refreshNow()
                    // 删除后由选择监听重新兜底到第一行（或空列表 → null）
                }
            }
        }
        val moveUpButton = JButton("上移").apply {
            addActionListener { moveSelectedRow(table, model, -1, manager) }
        }
        val moveDownButton = JButton("下移").apply {
            addActionListener { moveSelectedRow(table, model, 1, manager) }
        }
        val detailToggle = JButton("收起详情").apply {
            addActionListener {
                detail.isVisible = !detail.isVisible
                text = if (detail.isVisible) "收起详情" else "展开详情"
                detail.parent?.revalidate()
                detail.parent?.repaint()
            }
        }

        val inputRow = JPanel(BorderLayout()).apply {
            add(searchField, BorderLayout.CENTER)
            add(addStockButton, BorderLayout.EAST)
        }
        val toolbar = JPanel(BorderLayout()).apply {
            add(JPanel().apply {
                add(refreshButton)
                add(removeButton)
                add(moveUpButton)
                add(moveDownButton)
                add(detailToggle)
            }, BorderLayout.WEST)
            add(feedback, BorderLayout.EAST)
        }
        val head = JPanel(VerticalLayout(2)).apply {
            add(inputRow)
            add(toolbar)
        }

        manager.subscribe {
            val watchlist = manager.watchlistQuotes()
            // Keep the user's selection across auto-refresh: remember the code,
            // re-select the row after the model is rebuilt.
            val selectedCode = if (table.selectedRow >= 0)
                model.quoteAt(table.convertRowIndexToModel(table.selectedRow))?.code
            else null
            model.setData(watchlist)
            if (selectedCode != null) {
                val modelRow = (0 until model.rowCount).find { i -> model.quoteAt(i)?.code == selectedCode }
                if (modelRow != null) {
                    val viewRow = table.convertRowIndexToView(modelRow)
                    table.selectionModel.setSelectionInterval(viewRow, viewRow)
                }
            }
            // 仍未选中任何行（首次打开/删除后）时默认选中第一行
            if (table.selectedRow < 0 && model.rowCount > 0) {
                table.selectionModel.setSelectionInterval(0, 0)
            }
            val viewRow = table.selectedRow
            if (viewRow >= 0) {
                detail.showQuote(model.quoteAt(table.convertRowIndexToModel(viewRow)))
            }
        }

        val root = JPanel(BorderLayout()).apply {
            add(head, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(detail, BorderLayout.SOUTH)
        }
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(root, "行情", false)
        )
        Disposer.register(toolWindow.disposable, Disposable { scope.cancel() })

        // First paint immediately with the cached snapshot; if the cache is
        // empty (e.g. off-hours restart), pull one quote now so the detail
        // pane is populated too — no polling off hours, just this one fetch.
        if (manager.quoteSnapshot().isEmpty()) manager.refreshNow()
        model.setData(manager.watchlistQuotes())
    }

    override fun shouldBeAvailable(project: Project) = true
}

/** 添加台股：6 位代码直接添加；否则搜索后单选添加、多选弹出选择。 */
private fun doAddStock(
    project: Project,
    toolWindow: ToolWindow,
    manager: QuoteManager,
    scope: CoroutineScope,
    searchField: JBTextField,
    feedback: JBLabel,
) {
    val keyword = searchField.text.trim()
    if (keyword.isEmpty()) return
    if (keyword.length == 6 && keyword.all { it >= '0' && it <= '9' }) {
        WatchlistState.getInstance().add(keyword)
        manager.refreshNow()
        searchField.text = ""
        flash(feedback, "已添加 $keyword", scope)
        return
    }
    scope.launch {
        try {
            val hits = manager.search(keyword, 12)
            if (hits.isEmpty()) {
                flash(feedback, "未找到「$keyword」", scope)
            } else if (hits.size == 1) {
                addHit(hits[0], searchField, manager, feedback, scope)
            } else {
                showHitChooser(project, toolWindow, hits) { hit ->
                    addHit(hit, searchField, manager, feedback, scope)
                }
            }
        } catch (t: Throwable) {
            flash(feedback, "搜索失败", scope)
        }
    }
}

private fun addHit(
    hit: SearchHit,
    searchField: JBTextField,
    manager: QuoteManager,
    feedback: JBLabel,
    scope: CoroutineScope,
) {
    WatchlistState.getInstance().add(hit.code)
    manager.refreshNow()
    searchField.text = ""
    flash(feedback, "已添加 ${hit.name}(${hit.code})", scope)
}

private fun flash(label: JBLabel, text: String, scope: CoroutineScope) {
    label.text = text
    scope.launch {
        delay(3000)
        if (isActive) label.text = ""
    }
}

/** 搜索结果多于一个 → 弹出候选列表，双击/回车选择。 */
private fun showHitChooser(
    project: Project,
    toolWindow: ToolWindow,
    hits: List<SearchHit>,
    onPick: (SearchHit) -> Unit,
) {
    val model = DefaultListModel<SearchHit>()
    hits.forEach { model.addElement(it) }
    val list = JBList(model)
    list.cellRenderer = SearchHitCellRenderer()
    val scroll = JBScrollPane(list)
    scroll.preferredSize = com.intellij.util.ui.JBUI.size(360, 220)

    val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(scroll, scroll)
        .setTitle("选择股票")
        .setFocusable(true)
        .setRequestFocus(true)
        .createPopup()

    val pick: () -> Unit = {
        val hit = list.selectedValue ?: if (model.size > 0) model.getElementAt(0) else null
        if (hit != null) {
            onPick(hit)
            popup.dispose()
        }
    }
    list.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount >= 2) pick()
        }
    })
    list.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submit")
    list.actionMap.put("submit", object : AbstractAction() {
        override fun actionPerformed(e: java.awt.event.ActionEvent?) {
            if (model.size > 0) {
                val hit = list.selectedValue ?: model.getElementAt(0)
                onPick(hit)
                popup.dispose()
            }
        }
    })
    popup.showCenteredInCurrentWindow(project)
}

/** 调整选中自选股在列表中的上/下位置（-1 上移 / +1 下移）。 */
private fun moveSelectedRow(
    table: JTable,
    model: QuoteTableModel,
    delta: Int,
    manager: QuoteManager,
) {
    val viewRow = table.selectedRow
    if (viewRow < 0) return
    val code = model.quoteAt(table.convertRowIndexToModel(viewRow))?.code ?: return
    WatchlistState.getInstance().move(code, delta)
    manager.refreshNow()
}