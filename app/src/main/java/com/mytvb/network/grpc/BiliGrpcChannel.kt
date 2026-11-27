package com.mytvb.network.grpc

import com.mytvb.core.common.log.AppLog
import com.mytvb.network.NetworkManager
import bilibili.metadata.Metadata as BiliMetadataProto
import bilibili.metadata.device.Device
import bilibili.metadata.locale.Locale
import bilibili.metadata.network.Network
import bilibili.metadata.network.NetworkType
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import java.util.concurrent.TimeUnit

/**
 * B 站 app 端 gRPC 通道（grpc.biliapi.net:443，TLS）。
 * 官方 App 热门等接口的承载协议；认证走 metadata：
 * authorization: identify_v1 {access_key} + 四个 x-bili-*-bin protobuf 头。
 * 身份与 REST 侧一致（android_hd），access_token 由 TV 扫码登录下发。
 */
object BiliGrpcChannel {

    private const val TAG = "BiliGrpcChannel"
    private const val HOST = "grpc.biliapi.net"
    private const val PORT = 443

    // 与 newBV/android_hd REST 参数集对齐
    private const val APP_ID = 5
    private const val BUILD_CODE = 2001100
    private const val MOBI_APP = "android_hd"
    private const val PLATFORM = "android"
    private const val CHANNEL_ID = "master"
    private const val DEVICE = "pad"

    @Volatile
    private var channel: ManagedChannel? = null

    fun obtain(): ManagedChannel =
        channel ?: synchronized(this) {
            channel ?: ManagedChannelBuilder.forAddress(HOST, PORT)
                .useTransportSecurity()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(false)
                .intercept(BiliMetadataInterceptor())
                .build()
                .also { channel = it }
        }

    /** 退出登录等场景释放通道（下次调用会带新 token 重建）。 */
    fun reset() {
        synchronized(this) {
            runCatching { channel?.shutdownNow() }
            channel = null
            AppLog.i(TAG, "channel reset")
        }
    }

    private class BiliMetadataInterceptor : ClientInterceptor {
        override fun <ReqT : Any?, RespT : Any?> interceptCall(
            method: MethodDescriptor<ReqT, RespT>,
            callOptions: CallOptions,
            next: Channel
        ): ClientCall<ReqT, RespT> {
            val options =
                if (callOptions.deadline == null) {
                    callOptions.withDeadlineAfter(20, TimeUnit.SECONDS)
                } else {
                    callOptions
                }
            return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, options)
            ) {
                override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                    val accessKey = NetworkManager.getAppAccessToken()
                    val buvid = ""
                    headers.put(
                        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                        "identify_v1 $accessKey"
                    )
                    headers.put(
                        Metadata.Key.of("x-bili-metadata-bin", Metadata.BINARY_BYTE_MARSHALLER),
                        BiliMetadataProto.newBuilder()
                            .setAccessKey(accessKey)
                            .setMobiApp(MOBI_APP)
                            .setDevice(DEVICE)
                            .setBuild(BUILD_CODE)
                            .setChannel(CHANNEL_ID)
                            .setBuvid(buvid)
                            .setPlatform(PLATFORM)
                            .build()
                            .toByteArray()
                    )
                    headers.put(
                        Metadata.Key.of("x-bili-device-bin", Metadata.BINARY_BYTE_MARSHALLER),
                        Device.newBuilder()
                            .setAppId(APP_ID)
                            .setMobiApp(MOBI_APP)
                            .setDevice(DEVICE)
                            .setBuild(BUILD_CODE)
                            .setChannel(CHANNEL_ID)
                            .setBuvid(buvid)
                            .setPlatform(PLATFORM)
                            .build()
                            .toByteArray()
                    )
                    headers.put(
                        Metadata.Key.of("x-bili-local-bin", Metadata.BINARY_BYTE_MARSHALLER),
                        Locale.newBuilder()
                            .setTimezone("Asia/Shanghai")
                            .build()
                            .toByteArray()
                    )
                    headers.put(
                        Metadata.Key.of("x-bili-network-bin", Metadata.BINARY_BYTE_MARSHALLER),
                        Network.newBuilder()
                            .setType(NetworkType.WIFI)
                            .build()
                            .toByteArray()
                    )
                    super.start(responseListener, headers)
                }
            }
        }
    }
}
