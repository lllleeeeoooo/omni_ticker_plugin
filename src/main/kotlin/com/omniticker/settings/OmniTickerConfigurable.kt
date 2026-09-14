package com.omniticker.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.FormBuilder
import com.omniticker.service.PluginSettings
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

/**
 * Settings pane — platform-standard FormBuilder layout (left-aligned
 * label/control rows) grouped by TitledSeparator headings. Applying notifies
 * [PluginSettings.notifyChanged] so the status bar / detail pane repaint
 * immediately.
 */
class OmniTickerConfigurable : Configurable {

    private val settings = PluginSettings.getInstance()

    private var refreshSpinner: JBIntSpinner? = null
    private var showSH: JCheckBox? = null
    private var showSZ: JCheckBox? = null
    private var showCY: JCheckBox? = null
    private var showWatchlist: JCheckBox? = null
    private var showPrice: JCheckBox? = null
    private var showPercent: JCheckBox? = null
    private var hideOffHours: JCheckBox? = null
    private var nameFull: JRadioButton? = null
    private var namePinyin: JRadioButton? = null
    private var colorChange: JRadioButton? = null
    private var colorPlain: JRadioButton? = null
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Omni Ticker"

    override fun createComponent(): JComponent {
        refreshSpinner = JBIntSpinner(settings.refreshSeconds, 1, 120, 1).apply {
            preferredSize = Dimension(100, 26)
        }
        showSH = JCheckBox("上证指数", settings.showIndexSH)
        showSZ = JCheckBox("深证指数", settings.showIndexSZ)
        showCY = JCheckBox("创业板指数", settings.showIndexCY)
        showWatchlist = JCheckBox("自选个股", settings.showWatchlist)
        showPrice = JCheckBox("展示价格", settings.showPrice)
        showPercent = JCheckBox("展示涨跌幅", settings.showPercent)
        hideOffHours = JCheckBox("非交易日/非开盘时段自动隐藏", settings.hideOffHours)
        nameFull = JRadioButton("股票全称", settings.nameMode == PluginSettings.NAME_FULL)
        namePinyin = JRadioButton("拼音首字母缩写（茅台→MT）", settings.nameMode == PluginSettings.NAME_PINYIN)
        val nameGroup = ButtonGroup().apply {
            add(nameFull)
            add(namePinyin)
        }
        colorChange = JRadioButton("红涨绿跌", settings.colorMode == PluginSettings.COLOR_CHANGE)
        colorPlain = JRadioButton("保持默认颜色（白色）", settings.colorMode == PluginSettings.COLOR_PLAIN)
        val colorGroup = ButtonGroup().apply {
            add(colorChange)
            add(colorPlain)
        }
        val root = JPanel(VerticalLayout(8)).apply {
            border = BorderFactory.createEmptyBorder(10, 12, 12, 12)
        }

        root.add(TitledSeparator("行情刷新"))
        root.add(form {
            addLabeledComponent("刷新间隔（秒）", refreshSpinner!!)
        })

        root.add(TitledSeparator("状态栏显示"))
        root.add(inlineRow(listOf(showSH!!, showSZ!!, showCY!!, showWatchlist!!)))

        root.add(TitledSeparator("状态栏内容"))
        root.add(inlineRow(listOf(showPrice!!, showPercent!!, hideOffHours!!)))

        root.add(TitledSeparator("个股展示"))
        root.add(inlineRow(listOf(nameFull!!, namePinyin!!)))

        root.add(TitledSeparator("颜色"))
        root.add(inlineRow(listOf(colorChange!!, colorPlain!!)))

        panel = root
        return root
    }

    /**
     * 单行排布：组件间在【尾部】留间距，第一个组件紧贴左缘（不产生缩进感）。
     */
    private fun inlineRow(components: List<JComponent>, gap: Int = 12): JPanel {
        val row = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0))
        for (i in components.indices) {
            row.add(components[i])
            if (i < components.size - 1) {
                row.add(javax.swing.Box.createHorizontalStrut(gap))
            }
        }
        return row
    }

    override fun isModified(): Boolean =
        refreshSpinner?.number != settings.refreshSeconds ||
            showSH?.isSelected != settings.showIndexSH ||
            showSZ?.isSelected != settings.showIndexSZ ||
            showCY?.isSelected != settings.showIndexCY ||
            showWatchlist?.isSelected != settings.showWatchlist ||
            showPrice?.isSelected != settings.showPrice ||
            showPercent?.isSelected != settings.showPercent ||
            hideOffHours?.isSelected != settings.hideOffHours ||
            nameFull?.isSelected != (settings.nameMode == PluginSettings.NAME_FULL) ||
            colorChange?.isSelected != (settings.colorMode == PluginSettings.COLOR_CHANGE)

    @Throws(ConfigurationException::class)
    override fun apply() {
        refreshSpinner?.number?.let { settings.refreshSeconds = it }
        settings.showIndexSH = showSH?.isSelected ?: true
        settings.showIndexSZ = showSZ?.isSelected ?: true
        settings.showIndexCY = showCY?.isSelected ?: true
        settings.showWatchlist = showWatchlist?.isSelected ?: true
        settings.showPrice = showPrice?.isSelected ?: true
        settings.showPercent = showPercent?.isSelected ?: true
        settings.hideOffHours = hideOffHours?.isSelected ?: true
        settings.nameMode = if (namePinyin?.isSelected == true) PluginSettings.NAME_PINYIN else PluginSettings.NAME_FULL
        settings.colorMode = if (colorPlain?.isSelected == true) PluginSettings.COLOR_PLAIN else PluginSettings.COLOR_CHANGE
        // Live-update: status bar / detail pane repaint with the new values.
        settings.notifyChanged()
    }

    override fun reset() {
        refreshSpinner?.setNumber(settings.refreshSeconds)
        showSH?.isSelected = settings.showIndexSH
        showSZ?.isSelected = settings.showIndexSZ
        showCY?.isSelected = settings.showIndexCY
        showWatchlist?.isSelected = settings.showWatchlist
        showPrice?.isSelected = settings.showPrice
        showPercent?.isSelected = settings.showPercent
        hideOffHours?.isSelected = settings.hideOffHours
        nameFull?.isSelected = settings.nameMode == PluginSettings.NAME_FULL
        namePinyin?.isSelected = settings.nameMode == PluginSettings.NAME_PINYIN
        colorChange?.isSelected = settings.colorMode == PluginSettings.COLOR_CHANGE
        colorPlain?.isSelected = settings.colorMode == PluginSettings.COLOR_PLAIN
    }

    private fun form(build: FormBuilder.() -> Unit): JPanel {
        val fb = FormBuilder.createFormBuilder()
        build(fb)
        return fb.getPanel()
    }
}