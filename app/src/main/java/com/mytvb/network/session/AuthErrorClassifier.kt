package com.mytvb.network.session

/**
 * B 站接口错误码的统一分类（-101 登录失效 / -111 csrf 失配 / 风控 / 频率限制）。
 * 纯函数、无状态，UI 与 Repository 共用；从 NetworkSessionGateway 抽出以便独立维护与测试。
 */
sealed interface ActionError {
    data class SessionExpired(val message: String) : ActionError
    data class CsrfMismatch(val message: String) : ActionError
    data class CsrfMissing(val message: String) : ActionError
    data class RiskControl(val message: String) : ActionError
    data class FrequencyLimit(val message: String) : ActionError
    data class Other(val message: String) : ActionError
}

object AuthErrorClassifier {

    // -403: WBI 签名失败专用码（签错返回 -403 而非 -352，漏了会导致签名过期后一路失败不重试）
    // 22015: 动态流风控码
    private val riskControlCodes = setOf(-352, -351, -403, 22015)
    // -509 / 34004: 请求过快类限流码（对标 BiliPai 的可重试集合）
    private val frequencyLimitCodes = setOf(-412, -509, 34004)
    private val riskControlKeywords = listOf("风控", "拦截", "风险", "神秘力量", "risk", "blocked")
    private val frequencyLimitKeywords = listOf("频繁", "过快", "稍后", "too many", "rate limit")

    fun isCsrfError(code: Int, message: String?): Boolean {
        if (code == -101 || code == -111) return true
        return message.orEmpty().contains("csrf", ignoreCase = true)
    }

    fun isRiskControl(code: Int, message: String? = null): Boolean {
        if (code in riskControlCodes) return true
        val msg = message.orEmpty()
        return riskControlKeywords.any { msg.contains(it, ignoreCase = true) }
    }

    fun isRetryableError(code: Int, message: String? = null): Boolean {
        if (code in riskControlCodes) return true
        if (code in frequencyLimitCodes) return true
        val msg = message.orEmpty()
        if (riskControlKeywords.any { msg.contains(it, ignoreCase = true) }) return true
        if (frequencyLimitKeywords.any { msg.contains(it, ignoreCase = true) }) return true
        return false
    }

    fun classifyActionError(code: Int, message: String?): ActionError {
        val msg = message ?: ""
        if (code == -111 || (msg.contains("csrf", ignoreCase = true) && code != -101)) {
            return ActionError.CsrfMismatch(msg.ifEmpty { "csrf 校验失败" })
        }
        if (code == -101) {
            return ActionError.SessionExpired(msg.ifEmpty { "登录已过期" })
        }
        val isFreqByKeyword = frequencyLimitKeywords.any { msg.contains(it, ignoreCase = true) }
        if (code in frequencyLimitCodes || isFreqByKeyword) {
            return ActionError.FrequencyLimit(msg.ifEmpty { "操作过于频繁，请稍后再试" })
        }
        if (isRiskControl(code, message)) {
            return ActionError.RiskControl(msg.ifEmpty { "账号被风控" })
        }
        return ActionError.Other(msg.ifEmpty { "操作失败" })
    }
}
