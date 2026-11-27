package com.mytvb.feature.cast

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * 哔哩必连（NVA / Nirvana）协议编解码。
 *
 * 所有字段均为大端序。帧结构：
 *   指令 0xE0 | 参数个数(2/3) | 序号(4B) | 0x01 | 0x07 "Command" | 命令名长度(1B) 命令名 | [JSON 长度(4B) JSON]
 *   回复 0xC0 | 参数个数(0/1) | 序号(4B) | [JSON 长度(4B) JSON]
 *   心跳 0xE4 | 0x00       | 序号(4B)
 *
 * 参考实现：xfangfang/Macast-plugins nirvana。
 */
internal object NvaProtocol {

    /** NVA 长连接固定端口，由手机端硬编码，不能改。 */
    const val PORT = 9958

    const val SSDP_HOST = "239.255.255.250"
    const val SSDP_PORT = 1900

    const val TYPE_COMMAND = 0xE0
    const val TYPE_REPLY = 0xC0
    const val TYPE_PING = 0xE4

    /** 服务端心跳间隔：手机端依赖它判断设备在线。 */
    const val PING_INTERVAL_MS = 1_000L

    private const val ARG_COUNT_WITHOUT_PAYLOAD = 2
    private const val ARG_COUNT_WITH_PAYLOAD = 3

    private val COMMAND_TAG = "Command".toByteArray(Charsets.US_ASCII)

    sealed interface Frame {
        data class Command(val counter: Int, val method: String, val payload: String?) : Frame
        data class Reply(val counter: Int, val payload: String?) : Frame
        data class Ping(val counter: Int) : Frame
    }

    /**
     * 从连接读取一帧。流结束或对端关闭返回 null。
     * 帧头非法时抛 [IOException]，由调用方断开连接。
     */
    @Throws(IOException::class)
    fun readFrame(input: InputStream): Frame? {
        val type = readByteOrNull(input) ?: return null
        val argCount = readByteOrNull(input) ?: return null

        if (type == TYPE_PING) {
            val counter = readIntOrNull(input) ?: return null
            return Frame.Ping(counter)
        }

        val counter = readIntOrNull(input) ?: return null

        return when (type) {
            TYPE_COMMAND -> {
                if (argCount != ARG_COUNT_WITHOUT_PAYLOAD && argCount != ARG_COUNT_WITH_PAYLOAD) {
                    throw IOException("NVA 指令帧参数个数非法: $argCount")
                }
                val blockCount = readByteOrNull(input) ?: return null
                val tagLength = readByteOrNull(input) ?: return null
                val tag = ByteArray(tagLength)
                readFully(input, tag)
                if (blockCount != 1 || tagLength != COMMAND_TAG.size || !tag.contentEquals(COMMAND_TAG)) {
                    throw IOException("NVA 指令帧固定标记非法: block=$blockCount tag=${String(tag)}")
                }
                val methodLength = readByteOrNull(input) ?: return null
                val methodBytes = ByteArray(methodLength)
                readFully(input, methodBytes)
                val method = String(methodBytes, Charsets.UTF_8)

                var payload: String? = null
                if (argCount == ARG_COUNT_WITH_PAYLOAD) {
                    val payloadLength = readIntOrNull(input) ?: return null
                    if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES) {
                        throw IOException("NVA 指令载荷长度非法: $payloadLength")
                    }
                    val payloadBytes = ByteArray(payloadLength)
                    readFully(input, payloadBytes)
                    payload = String(payloadBytes, Charsets.UTF_8)
                }
                Frame.Command(counter, method, payload)
            }

            TYPE_REPLY -> {
                if (argCount != 0 && argCount != 1) {
                    throw IOException("NVA 回复帧参数个数非法: $argCount")
                }
                if (argCount == 0) {
                    Frame.Reply(counter, null)
                } else {
                    val payloadLength = readIntOrNull(input) ?: return null
                    if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES) {
                        throw IOException("NVA 回复载荷长度非法: $payloadLength")
                    }
                    val payloadBytes = ByteArray(payloadLength)
                    readFully(input, payloadBytes)
                    Frame.Reply(counter, String(payloadBytes, Charsets.UTF_8))
                }
            }

            else -> throw IOException("未知 NVA 帧类型: 0x%02X".format(type))
        }
    }

    /** 组装服务端下行指令（含 JSON 参数）。 */
    fun encodeCommand(counter: Int, method: String, payload: String?): ByteArray {
        val methodBytes = method.toByteArray(Charsets.UTF_8)
        val payloadBytes = payload?.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream(64 + (payloadBytes?.size ?: 0))
        DataOutputStream(out).use { d ->
            d.writeByte(TYPE_COMMAND)
            d.writeByte(if (payloadBytes == null) ARG_COUNT_WITHOUT_PAYLOAD else ARG_COUNT_WITH_PAYLOAD)
            d.writeInt(counter)
            d.writeByte(1)
            d.writeByte(COMMAND_TAG.size)
            d.write(COMMAND_TAG)
            d.writeByte(methodBytes.size)
            d.write(methodBytes)
            if (payloadBytes != null) {
                d.writeInt(payloadBytes.size)
                d.write(payloadBytes)
            }
            d.flush()
        }
        return out.toByteArray()
    }

    /** 组装命令回执。payload 为 null 时表示空回复。 */
    fun encodeReply(counter: Int, payload: String?): ByteArray {
        val payloadBytes = payload?.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream(16 + (payloadBytes?.size ?: 0))
        DataOutputStream(out).use { d ->
            d.writeByte(TYPE_REPLY)
            d.writeByte(if (payloadBytes == null) 0 else 1)
            d.writeInt(counter)
            if (payloadBytes != null) {
                d.writeInt(payloadBytes.size)
                d.write(payloadBytes)
            }
            d.flush()
        }
        return out.toByteArray()
    }

    fun encodePing(counter: Int): ByteArray {
        val out = ByteArrayOutputStream(6)
        DataOutputStream(out).use { d ->
            d.writeByte(TYPE_PING)
            d.writeByte(0)
            d.writeInt(counter)
            d.flush()
        }
        return out.toByteArray()
    }

    private fun readByteOrNull(input: InputStream): Int? =
        try {
            input.read().takeIf { it >= 0 }
        } catch (_: EOFException) {
            null
        }

    private fun readIntOrNull(input: InputStream): Int? {
        val b0 = readByteOrNull(input) ?: return null
        val b1 = readByteOrNull(input) ?: return null
        val b2 = readByteOrNull(input) ?: return null
        val b3 = readByteOrNull(input) ?: return null
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    @Throws(IOException::class)
    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) throw EOFException("NVA 帧数据不完整")
            offset += read
        }
    }

    /** 单帧 JSON 载荷上限，防止异常长度导致 OOM。 */
    private const val MAX_PAYLOAD_BYTES = 4 * 1024 * 1024
}
