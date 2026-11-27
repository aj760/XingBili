package com.mytvb.repository.remote

import com.mytvb.model.BaseResponse
import com.mytvb.model.user.ScanQrModel
import com.mytvb.model.user.SignInResultModel
import com.mytvb.model.user.SsoListModel
import com.mytvb.network.api.ApiService
import com.mytvb.network.session.NetworkSessionGateway

class AuthRepository(
    private val apiService: ApiService,
) {

    suspend fun getQrCode(): Result<BaseResponse<ScanQrModel>> =
        runCatching {
            apiService.getSignInQrCode()
        }

    suspend fun checkSignInResult(qrcodeKey: String, bRet: String): Result<BaseResponse<SignInResultModel>> =
        runCatching {
            apiService.checkSignInResult(qrcodeKey, bRet = bRet)
        }

    suspend fun getSsoList(csrf: String): Result<BaseResponse<SsoListModel>> =
        runCatching {
            apiService.getSsoList(csrf)
        }

    suspend fun setSso(url: String, bRet: String): Result<BaseResponse<Any>> =
        runCatching {
            apiService.setSso(url, bRet = bRet)
        }
}
