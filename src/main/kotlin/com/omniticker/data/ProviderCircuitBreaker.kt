package com.omniticker.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-provider circuit breaker: after [failureThreshold] consecutive failures
 * the provider is opened (skipped) for [cooldownMs]; one success closes it.
 * This protects against the free quote APIs throttling/banning the client IP.
 */
class ProviderCircuitBreaker(
    private val failureThreshold: Int = 3,
    private val cooldownMs: Long = 60_000,
) {
    private data class State(var streak: Int = 0, var cooldownUntil: Long = 0)

    private val states = ConcurrentHashMap<String, State>()

    fun isOpen(providerId: String): Boolean {
        val s = states[providerId] ?: return false
        return if (System.currentTimeMillis() < s.cooldownUntil) {
            true
        } else {
            states.remove(providerId)
            false
        }
    }

    fun onFailure(providerId: String) {
        val s = states.computeIfAbsent(providerId) { State() }
        val n = s.streak + 1
        s.streak = if (n >= failureThreshold) 0 else n
        if (n >= failureThreshold) s.cooldownUntil = System.currentTimeMillis() + cooldownMs
    }

    fun onSuccess(providerId: String) {
        states.remove(providerId)
    }
}