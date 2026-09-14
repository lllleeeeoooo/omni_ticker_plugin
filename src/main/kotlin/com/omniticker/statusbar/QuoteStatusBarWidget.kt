package com.omniticker.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.components.JBLabel
import com.omniticker.model.Quote
import com.omniticker.service.MarketCalendar
import com.omniticker.service.PluginSettings
import com.omniticker.service.QuoteManager
import com.omniticker.ui.QuoteColors
import com.omniticker.util.FormatUtils
import com.omniticker.util.NameAbbreviation
import java.awt.Color
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Status-bar summary as a [CustomStatusBarWidget] — component mode, the same
 * approach as CodeGlance Pro's settings pipeline: event-driven only, no timer.
 *
 * The widget is a horizontal container of [TextPanel] segments, one per entry,
 * so the red-up/green-down color applies PER STOCK/INDEX instead of a single
 * whole-bar tint (this is what makes the 红涨绿跌 setting actually visible).
 *
 * Entry format: 名称:价格 涨跌幅 —— e.g. "WLY:69.75 -1.04%".
 */
class QuoteStatusBarWidget(private val project: Project) : CustomStatusBarWidget {

    private val manager = project.getService(QuoteManager::class.java)

    private var listener: () -> Unit = { refresh() }
    private val settingsListener: () -> Unit = { refresh() }

    /**
     * 状态栏容器（直接挂到状态栏右栏的自定义组件），三处防御缺一不可：
     *  - 非不透明：平台 hover 会把 HOVER 背景色写进组件 background（同时置为不透明），
     *    移出时不还原；这里保证组件永远不自绘背景，残留无从谈起。
     *  - 宽度贴合内容：右栏 GridBagLayout 用 weightx 把组件横向拉满（getMaximumSize
     *    无效），平台 hover 的命中与绘制都按组件实际 bounds，拉宽后光标移出文字
     *    仍被视为"在组件上"、背景不消失；钳制宽度后移出文字即脱离。
     *  - 关闭双缓冲：与平台 NonOpaquePanel 一致，避免缓冲住旧背景。
     */
    private val container = object : JPanel() {
        override fun isOpaque(): Boolean = false

        override fun setBounds(x: Int, y: Int, w: Int, h: Int) {
            val prefW = getPreferredSize()?.width ?: 0
            super.setBounds(x, y, Math.min(w, prefW), h)
        }
    }.apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isDoubleBuffered = false
    }

    /** 前缀标签池：只增长于尾部；任何索引的标签一经创建不再替换，更新只改内容。 */
    private val labels: MutableList<JBLabel> = mutableListOf()

    private var currentTooltip: String = ""

    /** 鼠标移出标签时重绘状态栏，并把平台写入的 hover 背景色复位（双保险）。 */
    private val hoverExitHandler = object : java.awt.event.MouseAdapter() {
        override fun mouseExited(e: java.awt.event.MouseEvent) {
            repaintStatusBar()
        }
    }

    private fun repaintStatusBar() {
        // 平台 hover 会把 HOVER 背景色写进容器 background 且不还原；随时复位，
        // 配合 isOpaque=false 保证容器永不自绘背景。
        container.background = null
        try {
            val sb = com.intellij.openapi.wm.WindowManager.getInstance()
                .getStatusBar(project)
            sb.component?.repaint()
        } catch (_: Throwable) {
            // 状态栏不可用时忽略
        }
        container.repaint()
    }

    /** 点击状态栏 → 切换整个行情工具窗口（通过平台 click consumer，不自行挂监听，
     *  避免 LAF 对自定义监听组件绘制且不清理的 hover 背景）。 */
    private fun toggleOmniTickerWindow() {
        val twm = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
        val toolWindow = twm.getToolWindow("OmniTicker") ?: return
        if (toolWindow.isVisible()) {
            toolWindow.hide(null)
        } else {
            toolWindow.show(null)
        }
    }

    /** 平台读取 tooltip（点击由 ClickListener 处理）。 */
    override fun getPresentation(type: StatusBarWidget.PlatformType): StatusBarWidget.WidgetPresentation =
        object : StatusBarWidget.WidgetPresentation {
            override fun getTooltipText(): String = currentTooltip
        }

    /**
     * 点击状态栏 → 切换整个行情工具窗口。
     * 采用平台 ClickListener（参考 Translation 插件的 TranslationWidget 做法）：
     * 平台负责点击检测与 hover 语义，不会引发 LAF hover 背景残留。
     */
    private val clickListener = object : com.intellij.ui.ClickListener() {
        override fun onClick(e: java.awt.event.MouseEvent, onClickCount: Int): Boolean {
            if (onClickCount == 1) {
                toggleOmniTickerWindow()
                return true
            }
            return false
        }
    }

    init {
        manager.subscribe(listener)
        PluginSettings.getInstance().addChangeListener(settingsListener)
        container.addMouseListener(hoverExitHandler)
        // 状态栏展示时若尚无数据，触发一次查询（任意时段），避免一直"加载中"。
        if (manager.quoteSnapshot().isEmpty()) manager.refreshNow()
        refresh()
    }

    override fun ID(): String = QuoteStatusBarWidgetFactory.ID

    override fun getComponent(): JComponent = container

    override fun install(statusBar: StatusBar) {
        // no-op: component-mode widgets render directly
    }

    override fun dispose() {
        manager.unsubscribe(listener)
        PluginSettings.getInstance().removeChangeListener(settingsListener)
    }

    // ---- repaint (event-driven only) ---------------------------------------

    private fun refresh() {
        val s = PluginSettings.getInstance()
        // 非交易日/非开盘时段隐藏状态栏（设置开启时）；展示时段为交易日 09:00-15:30。
        val hidden = s.hideOffHours && !MarketCalendar.isStatusBarTime()
        if (container.isVisible != !hidden) {
            container.isVisible = !hidden
            container.parent?.revalidate()
            container.parent?.repaint()
        }
        if (hidden) return
        render(compose())
    }

    /**
     * 差量更新：段数变化时仅在【尾部】补充/移除标签，前缀组件永不重建；
     * 因此鼠标 hover 期间的任何更新都不会替换被悬停的组件，hover 背景可正常清除。
     */
    private fun render(segments: List<Segment>) {
        // 复位平台写入的 hover 背景色（每次刷新兜底）
        container.background = null
        val tooltip = buildString {
            if (manager.lastError != null) append("注意：").append(manager.lastError).append('\n')
            append("最近更新：").append(FormatUtils.lastUpdateTime(manager.lastUpdate))
        }
        currentTooltip = tooltip
        // 1) 尾部移除多余标签
        while (labels.size > segments.size) {
            container.remove(container.componentCount - 1)   // trailing strut
            container.remove(container.componentCount - 1)   // trailing label
            labels.removeAt(labels.size - 1)
        }
        // 2) 尾部补充不足标签
        while (labels.size < segments.size) {
            val i = labels.size
            val label = createLabel(segments[i], tooltip)
            container.add(label)
            container.add(Box.createHorizontalStrut(6))
            labels.add(label)
        }
        // 3) 只更新内容（文本/颜色/tooltip），组件全部复用
        for (i in segments.indices) {
            val label = labels[i]
            label.text = segments[i].text
            label.foreground = segments[i].color
            label.toolTipText = tooltip
        }
        container.revalidate()
        container.repaint()
    }

    private fun createLabel(seg: Segment, tooltip: String): JBLabel {
        // 用标准 JBLabel（TextPanel 自绘时忽略 setForeground，改用平台主题色，
        // 导致红涨绿跌无效）：JLabel 前景色必然生效。
        // 点击：在每个标签创建时单独安装 ClickListener（池化后递归 installOn
        // 无法覆盖后续新创建的标签，这正是点击文字一直无效的原因）。
        val label = JBLabel(seg.text)
        label.toolTipText = tooltip
        label.foreground = seg.color
        label.font = STATUS_FONT
        clickListener.installOn(label)
        label.addMouseListener(hoverExitHandler)
        return label
    }

    // ---- rendering ----------------------------------------------------------

    private data class Segment(val text: String, val color: Color?)

    private fun compose(): List<Segment> {
        val s = PluginSettings.getInstance()
        val indices = manager.indexQuotes().filter { showIndex(it.code, s) }
        val watchlist = manager.watchlistQuotes()
        val colored = s.colorMode == PluginSettings.COLOR_CHANGE
        val segments = mutableListOf<Segment>()

        indices.forEach { seg -> segments += Segment(indexSummary(seg, s), entryColor(seg, colored)) }
        if (s.showWatchlist && watchlist.isNotEmpty()) {
            segments += Segment("|", null)
            watchlist.take(MAX_STOCKS).forEach { q ->
                segments += Segment(stockSummary(q, s), entryColor(q, colored))
            }
            val hidden = watchlist.size - MAX_STOCKS
            if (hidden > 0) segments += Segment("+$hidden", null)
        }
        if (segments.isEmpty()) segments += Segment("A股行情加载中…", null)
        return segments
    }

    private fun entryColor(q: Quote, colored: Boolean): Color? =
        if (!colored) null
        else when {
            q.isUp -> QuoteColors.up
            q.isDown -> QuoteColors.down
            else -> null
        }

    private fun showIndex(code: String, s: PluginSettings): Boolean = when (code) {
        "000001" -> s.showIndexSH
        "399001" -> s.showIndexSZ
        "399006" -> s.showIndexCY
        else -> true
    }

    private fun indexSummary(q: Quote, s: PluginSettings): String {
        val tag = when (q.code) {
            "000001" -> "沪"
            "399001" -> "深"
            "399006" -> "创"
            else -> q.name
        }
        return quoteEntry(tag, q, s)
    }

    private fun stockSummary(q: Quote, s: PluginSettings): String {
        val name = if (s.nameMode == PluginSettings.NAME_PINYIN)
            NameAbbreviation.abbrev(q.name)
        else
            q.name
        return quoteEntry(name, q, s)
    }

    /** 名称:价格 涨跌幅 —— 冒号跟随名称（只要展示任何数值就带），价格为两位小数。 */
    private fun quoteEntry(name: String, q: Quote, s: PluginSettings): String = buildString {
        append(name)
        val hasValue = s.showPrice || s.showPercent
        if (hasValue) append(':')
        if (s.showPrice) append(FormatUtils.price(q.price))
        if (s.showPercent) append(' ').append(FormatUtils.percent(q.changePercent))
    }

    private companion object {
        const val MAX_STOCKS = 6

        /** 状态栏字体：11px，随 UI 缩放。 */
        val STATUS_FONT: java.awt.Font =
            java.awt.Font(com.intellij.util.ui.UIUtil.getLabelFont().name, java.awt.Font.PLAIN,
                com.intellij.util.ui.JBUI.scaleFontSize(11f))
    }
}