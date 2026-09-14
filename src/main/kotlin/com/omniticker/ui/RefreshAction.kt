package com.omniticker.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.omniticker.service.QuoteManager

/** Tools-menu action: trigger an immediate poll (same code path as auto refresh). */
class RefreshAction : AnAction("立即刷新", "立即刷新 A 股行情", null) {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { it.getService(QuoteManager::class.java).refreshNow() }
    }
}