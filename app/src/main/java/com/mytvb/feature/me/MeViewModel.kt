package com.mytvb.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytvb.model.user.UserDetailInfoModel
import com.mytvb.model.user.UserStatModel
import com.mytvb.network.session.SessionStateRepository
import com.mytvb.network.session.SessionState
import com.mytvb.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeViewModel(
    private val userRepository: UserRepository,
    private val sessionGateway: SessionStateRepository
) : ViewModel() {
    private var lastLoadedAt = 0L

    private val _userInfo = MutableStateFlow(sessionGateway.getUserInfo())
    val userInfo: StateFlow<UserDetailInfoModel?> = _userInfo.asStateFlow()

    private val _userStat = MutableStateFlow<UserStatModel?>(null)
    val userStat: StateFlow<UserStatModel?> = _userStat.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(sessionGateway.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        // 订阅会话单一状态源：登录/登出/-101 软清除自动反映到 UI，
        // 不再在 loadUserInfo 里轮询对齐，也不再由 ViewModel 清理会话（越权副作用已移除）
        viewModelScope.launch {
            sessionGateway.sessionState.collect { state ->
                _isLoggedIn.value = state.isLoggedIn
                _userInfo.value = (state as? SessionState.LoggedIn)?.info
                if (state is SessionState.LoggedOut) {
                    _userStat.value = null
                }
            }
        }
    }

    fun loadUserInfo() {
        if (!sessionGateway.isLoggedIn()) {
            return
        }
        viewModelScope.launch {
            val refreshedUserInfo = userRepository.refreshCurrentUserInfo().getOrNull() ?: return@launch
            _userInfo.value = refreshedUserInfo
            lastLoadedAt = System.currentTimeMillis()

            runCatching { userRepository.getUserStat() }
                .onSuccess { response ->
                    if (response.isSuccess) {
                        _userStat.value = response.data
                    }
                }
        }
    }

    fun resolveCurrentUserMid(onResult: (Long?) -> Unit) {
        viewModelScope.launch {
            val mid = userRepository.resolveCurrentUserMid().getOrNull()
            onResult(mid)
        }
    }

    fun syncWithGateway() {
        // 状态由 init 中的 sessionState 订阅驱动，这里只做兜底同步
        _isLoggedIn.value = sessionGateway.isLoggedIn()
    }

    fun shouldRefresh(ttlMs: Long): Boolean {
        if (!sessionGateway.isLoggedIn()) {
            return false
        }
        if (_userInfo.value == null) {
            return true
        }
        return System.currentTimeMillis() - lastLoadedAt >= ttlMs
    }
}
