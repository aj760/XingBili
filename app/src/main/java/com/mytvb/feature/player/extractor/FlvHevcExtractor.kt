package com.mytvb.feature.player.extractor

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.AacUtil
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.AvcConfig
import androidx.media3.extractor.HevcConfig
import com.mytvb.core.common.log.AppLog

/**
 * FLV extractor that supports both AVC (H.264) and HEVC (H.265) video codecs.
 *
 * Standard ExoPlayer [androidx.media3.extractor.flv.FlvExtractor] only supports AVC (codec ID 7)
 * and throws an unsupported format exception for HEVC (codec ID 12). This extractor handles both
 * by parsing the respective decoder configuration records (AVCDecoderConfigurationRecord and
 * HEVCDecoderConfigurationRecord) and outputting the correct MIME type.
 */
class FlvHevcExtractor : Extractor {

    companion object {
        private const val TAG_TYPE_AUDIO = 8
        private const val TAG_TYPE_VIDEO = 9

        private const val CODEC_AVC = 7
        private const val CODEC_HEVC = 12

        private const val PACKET_SEQ_START = 0
        private const val PACKET_NAL_UNIT = 1

        private const val SOUND_FORMAT_MP3 = 2
        private const val SOUND_FORMAT_A_LAW = 7
        private const val SOUND_FORMAT_MU_LAW = 8
        private const val SOUND_FORMAT_AAC = 10
        private const val AAC_PACKET_SEQ_START = 0
        private const val AAC_PACKET_RAW = 1

        private const val STATE_HEADER = 0
        private const val STATE_TAG_HEADER = 1
        private const val STATE_TAG_DATA = 2
        private const val STATE_SKIP = 3

        private const val FLV_HEADER_SIZE = 9
        private const val TAG_HEADER_SIZE = 11
        private const val PREV_TAG_SIZE = 4

        private val NAL_START_CODE = byteArrayOf(0, 0, 0, 1)

        // FLV 音频头字节 soundRate 位(2 bits)对应的采样率档位,MP3 等 PCM 类格式用它声明 format
        private val FLV_HEADER_SAMPLE_RATES = intArrayOf(5512, 11025, 22050, 44100)

        // endTracks 兜底阈值:B 站 FLV 直播 tag 顺序不保证音频配置先于视频帧,
        // 若音频始终未出现(纯视频流),最多容忍这么多样本后强制封盘,保证 prepare 不被卡死
        private const val END_TRACKS_SOLO_SAMPLE_LIMIT = 60
    }

    private var state = STATE_HEADER
    private var tagType = 0
    private var tagDataSize = 0
    private var tagTimestampUs = 0L
    private var skipBytes = 0

    private lateinit var output: ExtractorOutput
    private var videoTrack: TrackOutput? = null
    private var audioTrack: TrackOutput? = null
    private var tracksEnded = false
    private var nalLengthSize = 4
    private var videoFormatDeclared = false
    private var audioFormatDeclared = false
    private var emittedSampleCount = 0

    private val headerBuf = ParsableByteArray(FLV_HEADER_SIZE)
    private val tagHeaderBuf = ParsableByteArray(TAG_HEADER_SIZE)

    override fun sniff(input: ExtractorInput): Boolean {
        val sig = ByteArray(3)
        input.peekFully(sig, 0, 3)
        return sig[0] == 'F'.code.toByte() &&
            sig[1] == 'L'.code.toByte() &&
            sig[2] == 'V'.code.toByte()
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        state = STATE_HEADER
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        while (true) {
            when (state) {
                STATE_HEADER -> {
                    input.readFully(headerBuf.data, 0, FLV_HEADER_SIZE)
                    headerBuf.setPosition(5)
                    val dataOffset = headerBuf.readUnsignedIntToInt()
                    skipBytes = dataOffset - FLV_HEADER_SIZE + PREV_TAG_SIZE
                    state = STATE_SKIP
                }

                STATE_SKIP -> {
                    input.skipFully(skipBytes)
                    skipBytes = 0
                    state = STATE_TAG_HEADER
                }

                STATE_TAG_HEADER -> {
                    input.readFully(tagHeaderBuf.data, 0, TAG_HEADER_SIZE)
                    tagHeaderBuf.setPosition(0)
                    tagType = tagHeaderBuf.readUnsignedByte()
                    tagDataSize = tagHeaderBuf.readUnsignedInt24()
                    val tsLower = tagHeaderBuf.readUnsignedInt24()
                    val tsUpper = tagHeaderBuf.readUnsignedByte()
                    tagHeaderBuf.skipBytes(3)
                    tagTimestampUs = ((tsUpper.toLong() shl 24) or tsLower.toLong()) * 1000L
                    if (tagDataSize > 0) {
                        state = STATE_TAG_DATA
                    } else {
                        skipBytes = PREV_TAG_SIZE
                        state = STATE_SKIP
                    }
                }

                STATE_TAG_DATA -> {
                    when (tagType) {
                        TAG_TYPE_VIDEO -> readVideoTag(input)
                        TAG_TYPE_AUDIO -> readAudioTag(input)
                        else -> input.skipFully(tagDataSize)
                    }
                    skipBytes = PREV_TAG_SIZE
                    state = STATE_SKIP
                }
            }
        }
    }

    override fun seek(position: Long, timeUs: Long) {
        state = STATE_HEADER
    }

    override fun release() = Unit

    // ---- Video ----

    private fun readVideoTag(input: ExtractorInput) {
        val headerSize = minOf(5, tagDataSize)
        val hdr = ParsableByteArray(headerSize)
        input.readFully(hdr.data, 0, headerSize)
        hdr.setPosition(0)

        val firstByte = hdr.readUnsignedByte()
        val codecId = firstByte and 0x0F
        val frameType = (firstByte shr 4) and 0x0F
        val packetType = if (headerSize > 1) hdr.readUnsignedByte() else 0
        val payloadSize = tagDataSize - headerSize

        // CTS offset (signed 24-bit big-endian): bytes 2-4 of video tag data.
        // FLV tag timestamp is DTS; PTS = DTS + CTS. Ignoring this when B-frames
        // are present causes the player to render frames in decode order, producing
        // visible forward/backward stuttering.
        val ctsOffset = if (headerSize >= 5 && packetType == PACKET_NAL_UNIT) {
            var cts = ((hdr.data[2].toInt() and 0xFF) shl 16) or
                ((hdr.data[3].toInt() and 0xFF) shl 8) or
                (hdr.data[4].toInt() and 0xFF)
            if (cts and 0x800000 != 0) cts = cts or 0xFF000000.toInt()
            cts
        } else 0

        when (codecId) {
            CODEC_AVC, CODEC_HEVC -> handleVideoPacket(input, codecId, packetType, frameType, payloadSize, ctsOffset)
            else -> input.skipFully(payloadSize)
        }
    }

    private fun handleVideoPacket(
        input: ExtractorInput,
        codecId: Int,
        packetType: Int,
        frameType: Int,
        payloadSize: Int,
        ctsOffset: Int
    ) {
        if (payloadSize <= 0) return

        when (packetType) {
            PACKET_SEQ_START -> {
                val data = ParsableByteArray(payloadSize)
                input.readFully(data.data, 0, payloadSize)
                when (codecId) {
                    CODEC_AVC -> parseAvcConfig(data)
                    CODEC_HEVC -> parseHevcConfig(data)
                }
            }

            PACKET_NAL_UNIT -> {
                val track = videoTrack()
                val rawBuf = ByteArray(payloadSize)
                input.readFully(rawBuf, 0, payloadSize)
                val annexB = convertToAnnexB(rawBuf, payloadSize)
                if (annexB.isNotEmpty()) {
                    track.sampleData(ParsableByteArray(annexB), annexB.size)
                    val flags = if (frameType == 1) C.BUFFER_FLAG_KEY_FRAME else 0
                    val ptsUs = tagTimestampUs + ctsOffset * 1000L
                    track.sampleMetadata(ptsUs, flags, annexB.size, 0, null)
                    emittedSampleCount++
                    maybeEndTracks()
                }
            }

            else -> input.skipFully(payloadSize)
        }
    }

    private fun parseAvcConfig(data: ParsableByteArray) {
        val config = AvcConfig.parse(data)
        nalLengthSize = config.nalUnitLengthFieldLength
        videoTrack().format(
            Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H264)
                .setInitializationData(config.initializationData)
                .setWidth(config.width)
                .setHeight(config.height)
                .build()
        )
        videoFormatDeclared = true
        maybeEndTracks()
    }

    private fun parseHevcConfig(data: ParsableByteArray) {
        val config = HevcConfig.parse(data)
        nalLengthSize = config.nalUnitLengthFieldLength
        videoTrack().format(
            Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .setInitializationData(config.initializationData)
                .setWidth(config.width)
                .setHeight(config.height)
                .build()
        )
        videoFormatDeclared = true
        maybeEndTracks()
    }

    private fun convertToAnnexB(raw: ByteArray, rawLength: Int): ByteArray {
        var pos = 0
        var outLen = 0
        while (pos + nalLengthSize <= rawLength) {
            var nalLength = 0
            for (i in 0 until nalLengthSize) {
                nalLength = (nalLength shl 8) or (raw[pos + i].toInt() and 0xFF)
            }
            pos += nalLengthSize
            if (nalLength <= 0 || pos + nalLength > rawLength) break
            outLen += 4 + nalLength
            pos += nalLength
        }
        if (outLen == 0) return ByteArray(0)
        val out = ByteArray(outLen)
        pos = 0
        var outPos = 0
        while (pos + nalLengthSize <= rawLength) {
            var nalLength = 0
            for (i in 0 until nalLengthSize) {
                nalLength = (nalLength shl 8) or (raw[pos + i].toInt() and 0xFF)
            }
            pos += nalLengthSize
            if (nalLength <= 0 || pos + nalLength > rawLength) break
            System.arraycopy(NAL_START_CODE, 0, out, outPos, 4)
            outPos += 4
            System.arraycopy(raw, pos, out, outPos, nalLength)
            outPos += nalLength
            pos += nalLength
        }
        return out
    }

    // ---- Audio ----

    // 音频格式分布探针:每种 soundFormat 只记一次,用于排查"某类直播间无声"
    private val loggedSoundFormats = mutableSetOf<Int>()

    private fun readAudioTag(input: ExtractorInput) {
        if (tagDataSize < 2) {
            input.skipFully(tagDataSize)
            return
        }
        val hdr = ParsableByteArray(2)
        input.readFully(hdr.data, 0, 2)
        hdr.setPosition(0)

        val firstByte = hdr.readUnsignedByte()
        val soundFormat = (firstByte shr 4) and 0x0F
        if (loggedSoundFormats.add(soundFormat)) {
            AppLog.i(
                "FlvHevcExtractor",
                "audio tag soundFormat=$soundFormat " +
                    "(1=ADPCM,2=MP3,7=A-Law,8=mu-Law,10=AAC) tagDataSize=$tagDataSize"
            )
        }

        when (soundFormat) {
            SOUND_FORMAT_AAC -> readAacTag(input, hdr)

            SOUND_FORMAT_MP3 -> {
                if (!audioFormatDeclared) {
                    val sampleRateIndex = (firstByte shr 2) and 0x03
                    val channelCount = if (firstByte and 0x01 == 1) 2 else 1
                    audioTrack().format(
                        Format.Builder()
                            .setSampleMimeType(MimeTypes.AUDIO_MPEG)
                            .setSampleRate(FLV_HEADER_SAMPLE_RATES.getOrElse(sampleRateIndex) { 44100 })
                            .setChannelCount(channelCount)
                            .build()
                    )
                    audioFormatDeclared = true
                    maybeEndTracks()
                }
                outputRawAudioPayload(input, tagDataSize - 1)
            }

            SOUND_FORMAT_A_LAW, SOUND_FORMAT_MU_LAW -> {
                if (!audioFormatDeclared) {
                    audioTrack().format(
                        Format.Builder()
                            .setSampleMimeType(
                                if (soundFormat == SOUND_FORMAT_A_LAW) MimeTypes.AUDIO_ALAW else MimeTypes.AUDIO_MLAW
                            )
                            .setSampleRate(8000)
                            .setChannelCount(1)
                            .build()
                    )
                    audioFormatDeclared = true
                    maybeEndTracks()
                }
                outputRawAudioPayload(input, tagDataSize - 1)
            }

            else -> {
                // 未知音频格式只能丢弃,但要保持流对齐:已读 2 字节,跳过剩余 tagDataSize-2
                input.skipFully(tagDataSize - 2)
            }
        }
    }

    private fun readAacTag(input: ExtractorInput, hdr: ParsableByteArray) {
        val aacPacketType = hdr.readUnsignedByte()
        val aacPayload = tagDataSize - 2
        when {
            aacPacketType == AAC_PACKET_SEQ_START && aacPayload > 0 -> {
                val data = ParsableByteArray(aacPayload)
                input.readFully(data.data, 0, aacPayload)
                parseAacConfig(data)
            }

            aacPacketType == AAC_PACKET_RAW && aacPayload > 0 -> {
                val track = audioTrack()
                val audioBuf = ParsableByteArray(aacPayload)
                input.readFully(audioBuf.data, 0, aacPayload)
                track.sampleData(audioBuf, aacPayload)
                track.sampleMetadata(tagTimestampUs, C.BUFFER_FLAG_KEY_FRAME, aacPayload, 0, null)
                emittedSampleCount++
                maybeEndTracks()
            }

            else -> input.skipFully(aacPayload)
        }
    }

    private fun outputRawAudioPayload(input: ExtractorInput, payloadSize: Int) {
        if (payloadSize <= 0) return
        val track = audioTrack()
        val audioBuf = ParsableByteArray(payloadSize)
        input.readFully(audioBuf.data, 0, payloadSize)
        track.sampleData(audioBuf, payloadSize)
        track.sampleMetadata(tagTimestampUs, C.BUFFER_FLAG_KEY_FRAME, payloadSize, 0, null)
        emittedSampleCount++
        maybeEndTracks()
    }

    private fun parseAacConfig(data: ParsableByteArray) {
        // csd 直接用原始 AudioSpecificConfig 字节;AacUtil 负责解析(含 HE-AAC SBR/PS 的
        // 采样率/声道修正),避免手写位解析在 HE-AAC 流上声明错误的 format
        val asc = ByteArray(data.capacity())
        System.arraycopy(data.data, 0, asc, 0, asc.size)
        val config = AacUtil.parseAudioSpecificConfig(asc)
        audioTrack().format(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setCodecs(config.codecs)
                .setSampleRate(config.sampleRateHz)
                .setChannelCount(config.channelCount)
                .setInitializationData(listOf(asc))
                .build()
        )
        audioFormatDeclared = true
        maybeEndTracks()
    }

    // ---- Track helpers ----

    private fun videoTrack(): TrackOutput =
        videoTrack ?: output.track(0, C.TRACK_TYPE_VIDEO).also { videoTrack = it }

    private fun audioTrack(): TrackOutput =
        audioTrack ?: output.track(1, C.TRACK_TYPE_AUDIO).also { audioTrack = it }

    private fun maybeEndTracks() {
        if (tracksEnded) return
        // 封盘时机:音视频 format 都已声明(流里 tag 顺序不定,先到的视频帧不能提前封盘,
        // 否则后到的音频轨会被 ProgressiveMediaPeriod 判为 "added after finishing" 永不启用 → 无声);
        // 某类轨始终不出现时靠样本数兜底,避免纯视频流 prepare 卡死
        val bothDeclared = videoFormatDeclared && audioFormatDeclared
        val soloTimeout = emittedSampleCount >= END_TRACKS_SOLO_SAMPLE_LIMIT
        if (!bothDeclared && !soloTimeout) return
        AppLog.i(
            "FlvHevcExtractor",
            "endTracks videoFormat=$videoFormatDeclared audioFormat=$audioFormatDeclared " +
                "samples=$emittedSampleCount soloTimeout=$soloTimeout"
        )
        output.endTracks()
        output.seekMap(object : SeekMap {
            override fun isSeekable() = false
            override fun getDurationUs() = C.TIME_UNSET
            override fun getSeekPoints(timeUs: Long) = SeekMap.SeekPoints(SeekPoint(0, 0))
        })
        tracksEnded = true
    }
}
