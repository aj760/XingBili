package com.mytvb.network.session

import com.mytvb.model.user.UserDetailInfoModel
import kotlinx.coroutines.flow.StateFlow

/**
 * UI 层专用的会话状态只读仓库 + 登出/恢复入口。
 *
 * 与 [NetworkSessionGateway] 的分工：Repository 层与播放内核继续用 Gateway
 * （syncAuthState / WBI / cookie 刷新 / 风控重试等网络侧能力）；
 * Fragment / Dialog / ViewModel 只允许依赖本接口，避免 UI 拿到胖接口后越权做网络同步。
 */
interface SessionStateRepository {

    /** 会话单一状态源：登录态变化自动推送 */
    val sessionState: StateFlow<SessionState>

    fun currentSessionState(): SessionState

    fun isLoggedIn(): Boolean

    fun getUserInfo(): UserDetailInfoModel?

    fun getCsrfToken(): String

    /** csrf 为空返回 null（表示 session 不完整） */
    fun requireCsrfToken(): String?

    /** 登出/清理会话；广播由内部统一处理 */
    fun clearUserSession(reason: String)

    suspend fun tryRecoverExpiredSession(): Boolean

    fun classifyActionError(code: Int, message: String?): ActionError

    fun isCsrfError(code: Int, message: String?): Boolean

    fun isRiskControl(code: Int, message: String?): Boolean
}
