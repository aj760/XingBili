package com.mytvb.network.session

import com.mytvb.core.common.log.AppLog
import com.mytvb.model.BaseResponse
import com.mytvb.model.user.UserDetailInfoModel
import com.mytvb.network.NetworkManager
import com.mytvb.network.response.Base2Response
import com.mytvb.network.response.BaseBaseResponse
import com.mytvb.network.security.RiskControlCooldownManager
import kotlinx.coroutines.flow.StateFlow

interface NetworkSessionGateway {
    fun getCsrfToken(): String

    fun isLoggedIn(): Boolean

    /** 会话单一状态源：登录态变化自动推送，UI 不再轮询 */
    val sessionState: StateFlow<SessionState>

    fun currentSessionState(): SessionState

    fun getUserInfo(): UserDetailInfoModel?

    fun getWbiKeys(): Pair<String, String>

    fun areWbiKeysStale(): Boolean

    suspend fun ensureWbiKeys()

    suspend fun forceCookieRefresh()

    suspend fun tryRecoverExpiredSession(): Boolean

    fun clearUserSession(reason: String)

    fun handleAuthFailureCode(code: Int, source: String)

    suspend fun prewarmWebSession(forceUaRefresh: Boolean = false): Boolean

    fun syncUserSession(
        response: BaseResponse<UserDetailInfoModel>,
        source: String
    ): UserDetailInfoModel?

    fun <T> syncAuthState(
        response: BaseResponse<T>,
        source: String
    ): BaseResponse<T>

    fun syncAuthState(
        response: BaseBaseResponse,
        source: String
    ): BaseBaseResponse

    fun <T> syncAuthState(
        response: Base2Response<T>,
        source: String
    ): Base2Response<T>

    fun isCsrfError(code: Int, message: String?): Boolean

    // ========== 新增：Context-aware 方法 ==========

    fun syncUserSession(
        response: BaseResponse<UserDetailInfoModel>,
        source: String,
        context: AuthContext
    ): UserDetailInfoModel?

    fun <T> syncAuthState(
        response: BaseResponse<T>,
        source: String,
        context: AuthContext
    ): BaseResponse<T>

    fun syncAuthState(
        response: BaseBaseResponse,
        source: String,
        context: AuthContext
    ): BaseBaseResponse

    fun <T> syncAuthState(
        response: Base2Response<T>,
        source: String,
        context: AuthContext
    ): Base2Response<T>

    /** 返回 csrf token，为空则返回 null（表示 session 不完整） */
    fun requireCsrfToken(): String?

    /** 统一风控检测 */
    fun isRiskControl(code: Int, message: String? = null): Boolean

    /** 统一可重试错误判断（风控 + 频率限制，含关键词匹配） */
    fun isRetryableError(code: Int, message: String? = null): Boolean

    /** 统一错误分类，给 UI 层使用 */
    fun classifyActionError(code: Int, message: String?): ActionError

    /** 风控冷却管理器 */
    fun getCooldownManager(): RiskControlCooldownManager

    /** 统一风控重试，自动处理冷却、预热、重试 */
    suspend fun <T> executeWithRiskControlRetry(
        key: String,
        source: String,
        maxRetries: Int = 1,
        block: suspend (attempt: Int) -> BaseResponse<T>
    ): BaseResponse<T>

    /**
     * 通用风控重试辅助，适用于 BaseBaseResponse / Base2Response 等非 BaseResponse 类型。
     * 调用方提供 code/message 提取器，方法内部处理冷却、预热和重试。
     */
    suspend fun <T : Any> retryOnRiskControl(
        key: String,
        source: String,
        getCode: (T) -> Int,
        getMessage: (T) -> String?,
        getIsSuccess: (T) -> Boolean,
        maxRetries: Int = 1,
        block: suspend () -> T
    ): T
}

class NetworkManagerSessionGateway : NetworkSessionGateway, SessionStateRepository {

    private val riskControlRetryExecutor = RiskControlRetryExecutor(
        prewarmWebSession = { forceUaRefresh -> NetworkManager.prewarmWebSession(forceUaRefresh) },
        forceWbiRefresh = { NetworkManager.ensureWbiKeys() }
    )

    override fun getCsrfToken(): String = NetworkManager.getCsrfToken()

    override fun isLoggedIn(): Boolean = NetworkManager.isLoggedIn()

    override val sessionState: StateFlow<SessionState>
        get() = NetworkManager.sessionState

    override fun currentSessionState(): SessionState = NetworkManager.currentSessionState()

    override fun getUserInfo(): UserDetailInfoModel? = NetworkManager.getUserInfo()

    override fun getWbiKeys(): Pair<String, String> = NetworkManager.getWbiKeys()

    override fun areWbiKeysStale(): Boolean = NetworkManager.areWbiKeysStale()

    override suspend fun ensureWbiKeys() = NetworkManager.ensureWbiKeys()

    override suspend fun forceCookieRefresh() = NetworkManager.forceCookieRefresh()

    override suspend fun tryRecoverExpiredSession(): Boolean {
        return NetworkManager.tryRecoverExpiredSession()
    }

    override fun clearUserSession(reason: String) {
        NetworkManager.clearUserSession(reason = reason)
    }

    override fun handleAuthFailureCode(code: Int, source: String) {
        NetworkManager.handleAuthFailureCode(code, source)
    }

    override suspend fun prewarmWebSession(forceUaRefresh: Boolean): Boolean {
        return NetworkManager.prewarmWebSession(forceUaRefresh)
    }

    override fun syncUserSession(
        response: BaseResponse<UserDetailInfoModel>,
        source: String
    ): UserDetailInfoModel? {
        return NetworkManager.syncUserSession(response, source)
    }

    override fun <T> syncAuthState(
        response: BaseResponse<T>,
        source: String
    ): BaseResponse<T> {
        return NetworkManager.syncAuthState(response, source)
    }

    override fun syncAuthState(
        response: BaseBaseResponse,
        source: String
    ): BaseBaseResponse {
        return NetworkManager.syncAuthState(response, source)
    }

    override fun <T> syncAuthState(
        response: Base2Response<T>,
        source: String
    ): Base2Response<T> {
        return NetworkManager.syncAuthState(response, source)
    }

    override fun isCsrfError(code: Int, message: String?): Boolean {
        if (code == -101 || code == -111) return true
        return message.orEmpty().contains("csrf", ignoreCase = true)
    }

    // ========== Context-aware 实现 ==========

    override fun syncUserSession(
        response: BaseResponse<UserDetailInfoModel>,
        source: String,
        context: AuthContext
    ): UserDetailInfoModel? {
        return NetworkManager.syncUserSession(response, source, context)
    }

    override fun <T> syncAuthState(
        response: BaseResponse<T>,
        source: String,
        context: AuthContext
    ): BaseResponse<T> {
        return NetworkManager.syncAuthState(response, source, context)
    }

    override fun syncAuthState(
        response: BaseBaseResponse,
        source: String,
        context: AuthContext
    ): BaseBaseResponse {
        return NetworkManager.syncAuthState(response, source, context)
    }

    override fun <T> syncAuthState(
        response: Base2Response<T>,
        source: String,
        context: AuthContext
    ): Base2Response<T> {
        return NetworkManager.syncAuthState(response, source, context)
    }

    override fun requireCsrfToken(): String? {
        val csrf = NetworkManager.getCsrfToken()
        if (csrf.isBlank()) {
            AppLog.w("SessionGateway", "requireCsrfToken: csrf token is blank")
        }
        return csrf.takeIf { it.isNotBlank() }
    }

    override fun isRiskControl(code: Int, message: String?): Boolean {
        return AuthErrorClassifier.isRiskControl(code, message)
    }

    override fun isRetryableError(code: Int, message: String?): Boolean {
        return AuthErrorClassifier.isRetryableError(code, message)
    }

    override fun getCooldownManager(): RiskControlCooldownManager {
        return riskControlRetryExecutor.cooldownManager()
    }

    override suspend fun <T> executeWithRiskControlRetry(
        key: String,
        source: String,
        maxRetries: Int,
        block: suspend (attempt: Int) -> BaseResponse<T>
    ): BaseResponse<T> {
        return riskControlRetryExecutor.executeWithRiskControlRetry(key, source, maxRetries, block)
    }

    override suspend fun <T : Any> retryOnRiskControl(
        key: String,
        source: String,
        getCode: (T) -> Int,
        getMessage: (T) -> String?,
        getIsSuccess: (T) -> Boolean,
        maxRetries: Int,
        block: suspend () -> T
    ): T {
        return riskControlRetryExecutor.retryOnRiskControl(
            key, source, getCode, getMessage, getIsSuccess, maxRetries, block
        )
    }

    override fun classifyActionError(code: Int, message: String?): ActionError {
        return AuthErrorClassifier.classifyActionError(code, message)
    }

}
