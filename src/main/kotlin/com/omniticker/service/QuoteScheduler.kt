package com.omniticker.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serial polling loop. A tick runs [onTick] (a single batched network call),
 * then sleeps [intervalMs]; both run on the caller's scope but never overlap.
 * Zero/short intervals are clamped to [minIntervalMs] to protect the free APIs.
 */
class QuoteScheduler(
    private val scope: CoroutineScope,
    private val minIntervalMs: Long = 3000,
    private val intervalMs: () -> Long,
    private val onTick: suspend () -> Unit,
) {
    @Volatile
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    val running: Boolean get() = job?.isActive == true

    private suspend fun loop() {
        while (true) {
            onTick()
            delay(intervalMs().coerceAtLeast(minIntervalMs))
        }
    }
}