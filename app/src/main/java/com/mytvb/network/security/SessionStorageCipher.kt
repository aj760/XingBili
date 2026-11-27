package com.mytvb.network.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 登录凭据本地持久化加密（AES-GCM / AndroidKeyStore）。
 *
 * 密文格式 `v1:<iv>:<data>`（Base64 URL_SAFE）：不带前缀的值视为历史明文原样返回，
 * 因此升级后旧数据仍可读，并在下一次写入时自然升级为密文。
 * Keystore 暂不可用（个别低端设备会抖动）时 encrypt 返回 null，调用方保留旧值
 * 不落盘，内存会话不受影响；decrypt 失败返回空串，由上层按"未登录"处理。
 */
object SessionStorageCipher {
    private const val STORE = "AndroidKeyStore"
    private const val ALIAS = "myblbl.session.storage"
    private const val PREFIX = "v1:"

    @Volatile
    private var cachedKey: SecretKey? = null

    fun encrypt(value: String): String? {
        if (value.isEmpty()) return value
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                // Android Keystore 要求 GCM 加密使用系统新生成的 IV，不可复用
                init(Cipher.ENCRYPT_MODE, key())
            }
            PREFIX + b64(cipher.iv) + ":" + b64(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)))
        } catch (failure: Exception) {
            android.util.Log.e("SessionStorageCipher", "Keystore encryption failed; skip persistence", failure)
            null
        }
    }

    fun decrypt(value: String): String {
        if (!value.startsWith(PREFIX)) return value
        return runCatching {
            val parts = value.removePrefix(PREFIX).split(":", limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, unb64(parts[0])))
            }
            String(cipher.doFinal(unb64(parts[1])), StandardCharsets.UTF_8)
        }.getOrElse { failure ->
            // Keystore 密钥被系统重置（恢复出厂/清应用数据）时密文全部解不开，身份
            // cookie 会被整体丢弃成未登录态；留痕（不含密文内容）避免"莫名被登出"
            // 排查无线索。用 android.util.Log 而非 AppLog，避免 core 模块反向依赖
            android.util.Log.w(
                "SessionStorageCipher",
                "Keystore decryption failed (len=${value.length}); value dropped",
                failure
            )
            ""
        }
    }

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        return synchronized(this) {
            cachedKey?.let { return@synchronized it }

            var store = KeyStore.getInstance(STORE).apply { load(null) }
            if (!store.containsAlias(ALIAS)) {
                KeyGenerator.getInstance("AES", STORE).apply {
                    init(
                        KeyGenParameterSpec.Builder(
                            ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build(),
                    )
                }.generateKey()
                // 生成后重载，确保本次查询能看到新建的 alias
                store = KeyStore.getInstance(STORE).apply { load(null) }
            }
            (store.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
                .also { cachedKey = it }
        }
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    private fun unb64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)
}
