package com.omniticker.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.omniticker.model.SearchHit
import com.omniticker.service.QuoteManager
import com.omniticker.service.WatchlistState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.KeyStroke
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Action

/**
 * Debounced stock search popup (code / Chinese name / pinyin prefix).
 * Enter or double-click adds the selected stock to the watchlist.
 */
object StockSearchPopup {

    fun show(project: Project, manager: QuoteManager) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var searchJob: Job? = null

        val listModel = DefaultListModel<SearchHit>()
        val list = JBList(listModel)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = SearchHitCellRenderer()
        val scroll = JBScrollPane(list)
        scroll.preferredSize = Dimension(320, 240)
        scroll.border = BorderFactory.createEmptyBorder()

        val field = JBTextField()
        field.preferredSize = Dimension(320, 28)

        val content = com.intellij.ui.components.panels.HorizontalBox().apply {
            add(field)
            add(scroll)
        }

        val popup: JBPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, field)
            .setTitle("搜索 A 股（代码 / 名称 / 拼音）")
            .setFocusable(true)
            .setRequestFocus(true)
            .setMovable(false)
            .setCancelOnClickOutside(true)
            .createPopup()

        val addSelected: () -> Unit = {
            val hit = list.selectedValue
                ?: if (listModel.size > 0) listModel.getElementAt(0) else null
            if (hit != null) {
                WatchlistState.getInstance().add(hit.code)
                manager.refreshNow()
                popup.dispose()
            }
        }

        field.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val keyword = field.text.trim()
                searchJob?.cancel()
                if (keyword.isEmpty()) { listModel.clear(); return }
                searchJob = scope.launch {
                    delay(250) // debounce
                    val hits = manager.search(keyword, 10)
                    if (hits.isNotEmpty()) {
                        listModel.clear()
                        hits.forEach { listModel.addElement(it) }
                        if (listModel.size > 0) list.selectedIndex = 0
                    }
                }
            }
        })

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount >= 2) addSelected()
            }
        })

        field.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submit")
        field.actionMap.put("submit", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (listModel.size > 0) addSelected()
            }
        })
        field.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down")
        field.actionMap.put("down", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (listModel.size > 0) {
                    val next = (list.selectedIndex + 1).coerceAtMost(listModel.size - 1)
                    list.selectedIndex = next
                }
            }
        })

        val disposable = Disposable { scope.cancel() }
        Disposer.register(popup, disposable)
        popup.showCenteredInCurrentWindow(project)
        field.requestFocus()
    }
}