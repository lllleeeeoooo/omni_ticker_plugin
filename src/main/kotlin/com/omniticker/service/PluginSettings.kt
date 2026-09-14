package com.omniticker.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Plugin preferences, app-wide. Auto-refresh is always on; a single
 * [refreshSeconds] cadence applies everywhere. Widgets that render settings
 * (status bar, detail pane) subscribe via [addChangeListener] so changes
 * apply immediately.
 */
@Service
@State(name = "OmniTickerSettings", storages = [Storage("omniticker.xml")])
class PluginSettings : PersistentStateComponent<PluginSettings.State> {

    data class State(
        var refreshSeconds: Int = 3,
        var showStatusBar: Boolean = true,
        // Status-bar composition
        var showIndexSH: Boolean = true,
        var showIndexSZ: Boolean = true,
        var showIndexCY: Boolean = true,
        var showWatchlist: Boolean = true,
        var showPrice: Boolean = true,        // 状态栏个股是否展示价格
        var showPercent: Boolean = true,      // 状态栏个股是否展示涨跌幅
        var hideOffHours: Boolean = true,     // 非交易日/非开盘时段自动隐藏状态栏行情
        var nameMode: String = NAME_FULL,     // NAME_FULL | NAME_PINYIN
        var colorMode: String = COLOR_CHANGE, // COLOR_CHANGE | COLOR_PLAIN
    )

    private var state = State()
    private val changeListeners = CopyOnWriteArrayList<() -> Unit>()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    var refreshSeconds: Int
        get() = state.refreshSeconds
        set(v) { state.refreshSeconds = v.coerceIn(1, 120) }
    var showStatusBar: Boolean
        get() = state.showStatusBar
        set(v) { state.showStatusBar = v }

    // ---- status bar composition ------------------------------------------

    var showIndexSH: Boolean
        get() = state.showIndexSH
        set(v) { state.showIndexSH = v }
    var showIndexSZ: Boolean
        get() = state.showIndexSZ
        set(v) { state.showIndexSZ = v }
    var showIndexCY: Boolean
        get() = state.showIndexCY
        set(v) { state.showIndexCY = v }
    var showWatchlist: Boolean
        get() = state.showWatchlist
        set(v) { state.showWatchlist = v }
    var showPrice: Boolean
        get() = state.showPrice
        set(v) { state.showPrice = v }
    var showPercent: Boolean
        get() = state.showPercent
        set(v) { state.showPercent = v }
    var hideOffHours: Boolean
        get() = state.hideOffHours
        set(v) { state.hideOffHours = v }
    var nameMode: String
        get() = state.nameMode
        set(v) { state.nameMode = if (v in NAME_MODES) v else NAME_FULL }
    var colorMode: String
        get() = state.colorMode
        set(v) { state.colorMode = if (v in COLOR_MODES) v else COLOR_CHANGE }

    // ---- change notification (settings apply immediately) ----------------

    fun addChangeListener(listener: () -> Unit) {
        changeListeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        changeListeners.remove(listener)
    }

    /** Called after a settings apply; widgets repaint with the new values. */
    fun notifyChanged() {
        changeListeners.forEach { l -> runCatching { l() } }
    }

    companion object {
        const val NAME_FULL = "full"
        const val NAME_PINYIN = "pinyin"
        private val NAME_MODES = setOf(NAME_FULL, NAME_PINYIN)
        const val COLOR_CHANGE = "change"
        const val COLOR_PLAIN = "plain"
        private val COLOR_MODES = setOf(COLOR_CHANGE, COLOR_PLAIN)

        @JvmStatic
        fun getInstance(): PluginSettings =
            ApplicationManager.getApplication().getService(PluginSettings::class.java)
    }
}