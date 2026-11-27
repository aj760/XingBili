package com.mytvb.network.session

import com.mytvb.core.common.json.GsonHolder
import android.content.SharedPreferences
import com.google.gson.Gson
import com.mytvb.model.BaseResponse
import com.mytvb.model.user.UserDetailInfoModel
import com.mytvb.network.WbiGenerator
import com.mytvb.network.response.Base2Response
import com.mytvb.network.response.BaseBaseResponse
import com.mytvb.core.common.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkSessionStore(
    private val authInvalidCode: Int,
    // Cookie 真值在 CookieManager 里；store 只拿探针判断"是否有 SESSDATA"，
    // 用于在 CookieOnly / LoggedOut 之间区分
    private val hasSessionCookie: () -> Boolean = { false }
) {

    companion object {
        private const val WBI_KEYS_STALE_MS = 24 * 60 * 60 * 1000L
        private const val PREFS_KEY_WBI_IMG = "wbi_img_key"
        private const val PREFS_KEY_WBI_SUB = "wbi_sub_key"
        private const val PREFS_KEY_USER_INFO = "user_info_json"
        private const val TAG = "NetworkSessionStore"
    }

    private val gson = GsonHolder.DEFAULT
    private var wbiImageKey: String = ""
    private var wbiSubKey: String = ""
    private var wbiKeysUpdatedAt: Long = 0L
    private var userInfo: UserDetailInfoModel? = null
    private var sharedPrefs: SharedPreferences? = null

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.LoggedOut)

    /** 会话单一状态源：userInfo/cookie 变更点内部维护，UI 只订阅 */
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    fun initPersistence(prefs: SharedPreferences) {
        sharedPrefs = prefs
        restorePersistedUserInfo()
        restorePersistedWbiKeys()
    }

    private fun restorePersistedUserInfo() {
        val raw = sharedPrefs?.getString(PREFS_KEY_USER_INFO, null)
            ?.takeIf { it.isNotBlank() }
            ?: return
        runCatching {
            gson.fromJson(raw, UserDetailInfoModel::class.java)
        }.onSuccess { restored ->
            userInfo = restored?.let { it.copy(face = normalizeAvatarUrl(it.face)) }
            AppLog.i(TAG, "restorePersistedUserInfo hit mid=${userInfo?.mid ?: 0} hasFace=${!userInfo?.face.isNullOrBlank()}")
            refreshSessionState()
        }.onFailure {
            AppLog.w(TAG, "restorePersistedUserInfo failed; clearing", it)
            sharedPrefs?.edit()?.remove(PREFS_KEY_USER_INFO)?.apply()
        }
    }

    private fun restorePersistedWbiKeys() {
        val img = sharedPrefs?.getString(PREFS_KEY_WBI_IMG, "").orEmpty()
        val sub = sharedPrefs?.getString(PREFS_KEY_WBI_SUB, "").orEmpty()
        if (img.isNotBlank() && sub.isNotBlank()) {
            wbiImageKey = img
            wbiSubKey = sub
            wbiKeysUpdatedAt = System.currentTimeMillis()
        }
    }

    fun setWbiInfo(imgKey: String, subKey: String) {
        wbiImageKey = imgKey
        wbiSubKey = subKey
        if (imgKey.isNotBlank() && subKey.isNotBlank()) {
            wbiKeysUpdatedAt = System.currentTimeMillis()
        }
        sharedPrefs?.edit()?.apply {
            putString(PREFS_KEY_WBI_IMG, imgKey)
            putString(PREFS_KEY_WBI_SUB, subKey)
            apply()
        }
    }

    fun getWbiKeys(): Pair<String, String> {
        return Pair(wbiImageKey, wbiSubKey)
    }

    fun areWbiKeysStale(): Boolean {
        if (wbiImageKey.isBlank() || wbiSubKey.isBlank()) return true
        return System.currentTimeMillis() - wbiKeysUpdatedAt > WBI_KEYS_STALE_MS
    }

    fun getUserInfo(): UserDetailInfoModel? {
        return userInfo
    }

    fun clearUserSession() {
        userInfo = null
        wbiKeysUpdatedAt = 0L
        sharedPrefs?.edit()?.remove(PREFS_KEY_USER_INFO)?.apply()
        setWbiInfo("", "")
        refreshSessionState()
    }

    fun softClearUserSession() {
        userInfo = null
        sharedPrefs?.edit()?.remove(PREFS_KEY_USER_INFO)?.apply()
        refreshSessionState()
    }

    fun isSessionActive(): Boolean {
        return userInfo != null
    }

    fun updateUserSession(info: UserDetailInfoModel?) {
        userInfo = info?.let {
            it.copy(face = normalizeAvatarUrl(it.face))
        }
        if (userInfo == null) {
            sharedPrefs?.edit()?.remove(PREFS_KEY_USER_INFO)?.apply()
            setWbiInfo("", "")
            refreshSessionState()
            return
        }
        val normalizedUser = requireNotNull(userInfo)
        sharedPrefs?.edit()
            ?.putString(PREFS_KEY_USER_INFO, gson.toJson(normalizedUser))
            ?.apply()
        val imgKey = normalizedUser.wbiImg?.imgUrl?.let(WbiGenerator::extractKeyFromUrl).orEmpty()
        val subKey = normalizedUser.wbiImg?.subUrl?.let(WbiGenerator::extractKeyFromUrl).orEmpty()
        setWbiInfo(imgKey, subKey)
        refreshSessionState()
    }

    /**
     * cookie 变更的收口点（登录写入 / 清除 / 冷启动恢复后）由 NetworkManager 调用，
     * 让 CookieOnly 与 LoggedOut 的区分跟上 cookie 真值。
     */
    fun refreshSessionState() {
        val newState = when {
            userInfo != null -> SessionState.LoggedIn(userInfo!!)
            hasSessionCookie() -> SessionState.CookieOnly
            else -> SessionState.LoggedOut
        }
        if (_sessionState.value != newState) {
            _sessionState.value = newState
        }
    }

    fun syncUserSession(
        response: BaseResponse<UserDetailInfoModel>,
        context: AuthContext = AuthContext.FOREGROUND,
        onAuthInvalid: (() -> Unit)? = null
    ): UserDetailInfoModel? {
        val info = response.data?.takeIf { response.isSuccess && it.isLogin }
        if (info != null) {
            updateUserSession(info)
            return userInfo
        }
        if (response.isSuccess && response.data != null) {
            extractWbiKeysFromData(response.data)
            return null
        }
        if (isAuthInvalid(response.code)) {
            extractWbiKeysFromData(response.data)
            if (context.shouldClearSession) {
                val wasLoggedIn = userInfo != null
                softClearUserSession()
                if (wasLoggedIn) {
                    onAuthInvalid?.invoke()
                }
            }
        }
        return null
    }

    fun handleAuthFailureCode(
        code: Int,
        context: AuthContext = AuthContext.FOREGROUND,
        onAuthInvalid: (() -> Unit)? = null
    ) {
        if (isAuthInvalid(code)) {
            if (!context.shouldClearSession) return
            val wasLoggedIn = userInfo != null
            softClearUserSession()
            if (wasLoggedIn) {
                onAuthInvalid?.invoke()
            }
        }
    }

    fun <T> syncAuthState(
        response: BaseResponse<T>,
        context: AuthContext = AuthContext.FOREGROUND,
        onAuthInvalid: (() -> Unit)? = null
    ): BaseResponse<T> {
        handleAuthFailureCode(response.code, context, onAuthInvalid)
        return response
    }

    fun syncAuthState(
        response: BaseBaseResponse,
        context: AuthContext = AuthContext.FOREGROUND,
        onAuthInvalid: (() -> Unit)? = null
    ): BaseBaseResponse {
        handleAuthFailureCode(response.code, context, onAuthInvalid)
        return response
    }

    fun <T> syncAuthState(
        response: Base2Response<T>,
        context: AuthContext = AuthContext.FOREGROUND,
        onAuthInvalid: (() -> Unit)? = null
    ): Base2Response<T> {
        handleAuthFailureCode(response.code, context, onAuthInvalid)
        return response
    }

    private fun normalizeAvatarUrl(url: String): String = when {
        url.startsWith("http://") -> "https://${url.substring(7)}"
        url.startsWith("//") -> "https:$url"
        else -> url
    }

    private fun isAuthInvalid(code: Int): Boolean {
        return code == authInvalidCode
    }

    private fun extractWbiKeysFromData(data: UserDetailInfoModel?) {
        data?.wbiImg?.let { wbiImg ->
            val imgKey = WbiGenerator.extractKeyFromUrl(wbiImg.imgUrl)
            val subKey = WbiGenerator.extractKeyFromUrl(wbiImg.subUrl)
            if (imgKey.isNotBlank() && subKey.isNotBlank()) {
                setWbiInfo(imgKey, subKey)
            }
        }
    }
}
