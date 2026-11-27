package com.mytvb.model.user

import com.google.gson.annotations.SerializedName

data class TvQrCodeData(
    @SerializedName("url")
    val url: String = "",
    @SerializedName("auth_code")
    val authCode: String = ""
)

data class TvPollData(
    @SerializedName("mid")
    val mid: Long = 0,
    @SerializedName("cookie_info")
    val cookieInfo: TvCookieInfo? = null,
    @SerializedName("token_info")
    val tokenInfo: TvTokenInfo? = null
)

/** app 端 OAuth 凭据，配合 android_hd appkey 签名访问 app 侧接口（如 x/v2/feed/index 推荐流）。 */
data class TvTokenInfo(
    @SerializedName("access_token")
    val accessToken: String = "",
    @SerializedName("refresh_token")
    val refreshToken: String = "",
    @SerializedName("expires_in")
    val expiresIn: Long = 0
)

data class TvCookieInfo(
    @SerializedName("cookies")
    val cookies: List<TvCookie>? = null,
    @SerializedName("domains")
    val domains: List<String>? = null
)

data class TvCookie(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("value")
    val value: String = "",
    @SerializedName("expires")
    val expires: Long = 0,
    @SerializedName("http_only")
    val httpOnly: Int = 0,
    @SerializedName("secure")
    val secure: Int = 0
)
