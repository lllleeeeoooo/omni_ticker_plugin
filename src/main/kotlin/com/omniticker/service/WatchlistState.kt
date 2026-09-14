package com.omniticker.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * User's watchlist, persisted app-wide (no project dependency, no login).
 * Stores bare 6-digit codes; the exchange is derived via Market.derive(code).
 */
@Service
@State(name = "OmniTickerWatchlist", storages = [Storage("omniticker.xml")])
class WatchlistState : PersistentStateComponent<WatchlistState.State> {

    data class State(
        var stocks: MutableList<String> = mutableListOf("600519", "000858", "300750"),
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun add(code: String) {
        if (code.length == 6 && state.stocks.none { it == code }) {
            state.stocks.add(code)
        }
    }

    fun remove(code: String) {
        state.stocks.remove(code)
    }

    fun toggle(code: String): Boolean {
        return if (code in state.stocks) {
            remove(code); false
        } else {
            add(code); true
        }
    }

    fun codes(): List<String> = state.stocks.toList()

    /** 按给定顺序整体重排（用于状态栏/工具窗显示顺序配置）。 */
    fun reorder(ordered: List<String>) {
        state.stocks = ordered.filter { it.length == 6 }.toMutableList()
    }

    /** 移动单个代码 ±n 位（上/下），越界忽略。 */
    fun move(code: String, delta: Int) {
        val idx = state.stocks.indexOf(code)
        if (idx < 0) return
        val to = idx + delta
        if (to < 0 || to >= state.stocks.size) return
        val item = state.stocks.removeAt(idx)
        state.stocks.add(to, item)
    }

    companion object {
        @JvmStatic
        fun getInstance(): WatchlistState =
            ApplicationManager.getApplication().getService(WatchlistState::class.java)
    }
}