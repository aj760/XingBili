package com.mytvb.network.session

import com.mytvb.model.user.UserDetailInfoModel

/**
 * 会话单一状态源。由 [NetworkSessionStore] 维护，UI 只订阅 [kotlinx.coroutines.flow.StateFlow]，
 * 不再各自轮询 isLoggedIn()/getUserInfo() 的组合。
 *
 * - [LoggedOut]：无 SESSDATA 也无用户信息，明确的未登录
 * - [CookieOnly]：有 SESSDATA 但无用户信息（-101 软清除后 / 登录后尚未拉到信息），
 *   按登录态对待，与旧 isLoggedIn() 行为一致
 * - [LoggedIn]：已登录且持有用户信息
 */
sealed interface SessionState {
    data object LoggedOut : SessionState

    data object CookieOnly : SessionState

    data class LoggedIn(val info: UserDetailInfoModel) : SessionState

    val isLoggedIn: Boolean
        get() = this !is LoggedOut
}
