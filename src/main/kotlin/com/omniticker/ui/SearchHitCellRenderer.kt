package com.omniticker.ui

import com.intellij.ui.components.JBLabel
import com.omniticker.model.SearchHit
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class SearchHitCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JBLabel
        if (value is SearchHit) {
            c.text = "${value.code}  ${value.name}${if (value.pinyin.isNotBlank()) " ($value.pinyin)" else ""}"
        }
        return c
    }
}