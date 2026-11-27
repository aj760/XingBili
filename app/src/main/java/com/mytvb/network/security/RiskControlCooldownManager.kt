package com.mytvb.network.security

import com.mytvb.core.common.log.AppLog
import java.util.concurrent.ConcurrentHashMap

class RiskControlCooldownManager {

    companion object {
        private const val TAG = "RiskCooldown"
        private const val SINGLE_KEY_COOLDOWN_MS = 60_000L
        private const val GLOBAL_FAILURE_THRESHOLD = 8
        private const val GLOBAL_COOLDOWN_MS = 120_000L
        private const val FREQUENCY_LIMIT_COOLDOWN_MS = 5_000L
        private const val RISK_CONTROL_CODE_352 = -352
        private const val RISK_CONTROL_CODE_351 = -351
        private const val FREQUENCY_LIMIT_CODE_412 = -412
    }

    private val keyCooldownUntil = ConcurrentHashMap<String, Long>()
    private val keyFailureCount = ConcurrentHashMap<String, Int>()
    @Volatile
    private var globalCooldownUntilMs: Long = 0L
    @Volatile
    private var consecutiveGlobalFailures: Int = 0

    fun checkCooldown(key: String): Long {
        val now = System.currentTimeMillis()
        val globalLeft = (globalCooldownUntilMs - now).coerceAtLeast(0L)
        val keyLeft = (keyCooldownUntil[key]?.let { it - now } ?: 0L).coerceAtLeast(0L)
        return maxOf(globalLeft, keyLeft)
    }

    fun recordFailure(key: String, code: Int) {
        val now = System.currentTimeMillis()
        val cooldownMs = when (code) {
            FREQUENCY_LIMIT_CODE_412 -> FREQUENCY_LIMIT_COOLDOWN_MS
            RISK_CONTROL_CODE_352, RISK_CONTROL_CODE_351 -> SINGLE_KEY_COOLDOWN_MS
            else -> SINGLE_KEY_COOLDOWN_MS
        }
        keyCooldownUntil[key] = now + cooldownMs
        val failures = keyFailureCount.getOrPut(key) { 0 }.let { old ->
            val newValue = old + 1
            keyFailureCount[key] = newValue
            newValue
        }
        val globalFailures = ++consecutiveGlobalFailures
        if (globalFailures >= GLOBAL_FAILURE_THRESHOLD) {
            globalCooldownUntilMs = now + GLOBAL_COOLDOWN_MS
            AppLog.w(TAG, "global cooldown triggered after $globalFailures consecutive failures, cooldown ${GLOBAL_COOLDOWN_MS}ms")
        }
        AppLog.d(TAG, "recordFailure: key=$key, code=$code, cooldown=${cooldownMs}ms, keyFailures=$failures, globalFailures=$globalFailures")
    }

    fun recordSuccess(key: String) {
        keyFailureCount.remove(key)
        consecutiveGlobalFailures = 0
        AppLog.d(TAG, "recordSuccess: key=$key, globalFailures reset to 0")
    }

    fun isGlobalCooldownActive(): Boolean {
        return System.currentTimeMillis() < globalCooldownUntilMs
    }

    fun clearAll() {
        keyCooldownUntil.clear()
        keyFailureCount.clear()
        globalCooldownUntilMs = 0L
        consecutiveGlobalFailures = 0
    }
}
