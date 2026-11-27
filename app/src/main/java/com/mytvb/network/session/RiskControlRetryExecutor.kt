package com.mytvb.network.session

import com.mytvb.core.common.coroutine.catchingRequest
import com.mytvb.core.common.log.AppLog
import com.mytvb.model.BaseResponse
import com.mytvb.network.security.RiskControlCooldownManager
import kotlinx.coroutines.delay

/**
 * 风控/频率限制的统一重试执行器：命中可重试错误 → 记录冷却 → 等待冷却 → 预热会话 → 强制刷新 WBI 密钥 → 重试一次。
 * 冷却状态归属本实例（Koin single），从 NetworkManagerSessionGateway 抽出。
 * 刷密钥是必须的独立步骤：prewarmWebSession 有 5min 去重会跳过 nav，而 -403/-352 的
 * 常见根因正是本端 WBI 密钥过期/错乱，冷却后不换 key 重试大概率原地复撞。
 */
class RiskControlRetryExecutor(
    private val prewarmWebSession: suspend (Boolean) -> Boolean,
    private val forceWbiRefresh: suspend () -> Unit
) {

    private val cooldownManager = RiskControlCooldownManager()

    fun cooldownManager(): RiskControlCooldownManager = cooldownManager

    suspend fun <T> executeWithRiskControlRetry(
        key: String,
        source: String,
        maxRetries: Int = 1,
        block: suspend (attempt: Int) -> BaseResponse<T>
    ): BaseResponse<T> {
        val response = block(0)
        if (!AuthErrorClassifier.isRetryableError(response.code, response.message) || maxRetries <= 0) {
            if (response.isSuccess) cooldownManager.recordSuccess(key)
            return response
        }
        cooldownManager.recordFailure(key, response.code)
        val cooldownMs = cooldownManager.checkCooldown(key)
        AppLog.w("RiskRetry", "$source hit retryable error: code=${response.code}, key=$key, cooldown=${cooldownMs}ms")
        if (cooldownMs > 0) delay(cooldownMs)
        prewarmWebSession(true)
        catchingRequest { forceWbiRefresh() }
            .onFailure { AppLog.w("RiskRetry", "$source forceWbiRefresh failed: ${it.message}") }
        val retryResponse = block(1)
        if (retryResponse.isSuccess) cooldownManager.recordSuccess(key)
        else cooldownManager.recordFailure(key, retryResponse.code)
        return retryResponse
    }

    suspend fun <T : Any> retryOnRiskControl(
        key: String,
        source: String,
        getCode: (T) -> Int,
        getMessage: (T) -> String?,
        getIsSuccess: (T) -> Boolean,
        maxRetries: Int = 1,
        block: suspend () -> T
    ): T {
        val response = block()
        val code = getCode(response)
        if (!AuthErrorClassifier.isRetryableError(code, getMessage(response)) || maxRetries <= 0) {
            if (getIsSuccess(response)) cooldownManager.recordSuccess(key)
            return response
        }
        cooldownManager.recordFailure(key, code)
        val cooldownMs = cooldownManager.checkCooldown(key)
        AppLog.w("RiskRetry", "$source hit retryable error: code=$code, key=$key, cooldown=${cooldownMs}ms")
        if (cooldownMs > 0) delay(cooldownMs)
        prewarmWebSession(true)
        catchingRequest { forceWbiRefresh() }
            .onFailure { AppLog.w("RiskRetry", "$source forceWbiRefresh failed: ${it.message}") }
        val retryResponse = block()
        if (getIsSuccess(retryResponse)) cooldownManager.recordSuccess(key)
        else cooldownManager.recordFailure(key, getCode(retryResponse))
        return retryResponse
    }
}
