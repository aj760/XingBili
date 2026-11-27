package com.mytvb.network.security

import com.mytvb.network.NetworkManager

interface NetworkSecurityGateway {
    suspend fun ensureHealthyForPlay()

    suspend fun prewarmWebSession(forceUaRefresh: Boolean = false): Boolean
}

class NetworkManagerSecurityGateway : NetworkSecurityGateway {
    override suspend fun ensureHealthyForPlay() {
        NetworkManager.ensureHealthyForPlay()
    }

    override suspend fun prewarmWebSession(forceUaRefresh: Boolean): Boolean {
        return NetworkManager.prewarmWebSession(forceUaRefresh)
    }
}
