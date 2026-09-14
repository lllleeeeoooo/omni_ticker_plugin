package com.omniticker.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import org.jetbrains.annotations.Nls

/** Registers the "A股行情" widget in the status bar (all IDEs share this API). */
class QuoteStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = ID

    override fun getDisplayName(): @Nls String = "A股行情"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = QuoteStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        (widget as? QuoteStatusBarWidget)?.dispose()
    }

    companion object {
        const val ID = "com.omniticker.statusBar"
    }
}