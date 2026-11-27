package com.mytvb.model.proto

import com.google.protobuf.CodedInputStream

object DmProtoParser {

    data class SegmentMeta(
        val state: Int = 0,
        val aiFlag: DanmakuAIFlagProto = DanmakuAIFlagProto(),
        val colorfulSrc: List<DmColorfulProto> = emptyList()
    )

    data class SegmentElemScanResult(
        val meta: SegmentMeta,
        val elemCount: Int
    )

    fun parseSegment(bytes: ByteArray): DmSegMobileReplyProto {
        val input = CodedInputStream.newInstance(bytes)
        val elems = mutableListOf<DanmakuElemProto>()
        var state = 0
        var aiFlag = DanmakuAIFlagProto()
        val colorfulSrc = mutableListOf<DmColorfulProto>()
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        1 -> elems += parseElem(input.readByteArray())
                        2 -> state = input.readInt32()
                        3 -> aiFlag = parseAiFlag(input.readByteArray())
                        5 -> colorfulSrc += parseColorful(input.readByteArray())
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return DmSegMobileReplyProto(
            elems = elems,
            state = state,
            aiFlag = aiFlag,
            colorfulSrc = colorfulSrc
        )
    }

    fun parseSegmentMeta(bytes: ByteArray): SegmentMeta {
        val input = CodedInputStream.newInstance(bytes)
        var state = 0
        var aiFlag = DanmakuAIFlagProto()
        val colorfulSrc = mutableListOf<DmColorfulProto>()
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        2 -> state = input.readInt32()
                        3 -> aiFlag = parseAiFlag(input.readByteArray())
                        5 -> colorfulSrc += parseColorful(input.readByteArray())
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return SegmentMeta(
            state = state,
            aiFlag = aiFlag,
            colorfulSrc = colorfulSrc
        )
    }

    fun forEachSegmentElem(bytes: ByteArray, onElem: (DanmakuElemProto) -> Unit): Int {
        val input = CodedInputStream.newInstance(bytes)
        var count = 0
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        1 -> {
                            onElem(parseElem(input.readRawVarint32(), input))
                            count++
                        }
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return count
    }

    fun forEachSegmentElemInProgressRange(
        bytes: ByteArray,
        startMs: Long?,
        endMs: Long?,
        onElem: (DanmakuElemProto) -> Unit
    ): Int {
        if (startMs == null && endMs == null) {
            return forEachSegmentElem(bytes, onElem)
        }
        val input = CodedInputStream.newInstance(bytes)
        var count = 0
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        1 -> {
                            val length = input.readRawVarint32()
                            val offset = input.totalBytesRead
                            val oldLimit = input.pushLimit(length)
                            val progress = parseElemProgress(input)
                            skipRemainingMessageBytes(input, offset, length)
                            input.popLimit(oldLimit)
                            if ((startMs == null || progress >= startMs) &&
                                (endMs == null || progress < endMs)
                            ) {
                                onElem(parseElem(bytes, offset, length))
                            }
                            count++
                        }
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return count
    }

    fun forEachSegmentElemWithMetaInProgressRange(
        bytes: ByteArray,
        startMs: Long?,
        endMs: Long?,
        onElem: (DanmakuElemProto) -> Unit
    ): SegmentElemScanResult {
        val input = CodedInputStream.newInstance(bytes)
        var count = 0
        var state = 0
        var aiFlag = DanmakuAIFlagProto()
        val colorfulSrc = mutableListOf<DmColorfulProto>()
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        1 -> {
                            if (startMs == null && endMs == null) {
                                onElem(parseElem(input.readRawVarint32(), input))
                            } else {
                                val length = input.readRawVarint32()
                                val offset = input.totalBytesRead
                                val oldLimit = input.pushLimit(length)
                                val progress = parseElemProgress(input)
                                skipRemainingMessageBytes(input, offset, length)
                                input.popLimit(oldLimit)
                                if ((startMs == null || progress >= startMs) &&
                                    (endMs == null || progress < endMs)
                                ) {
                                    onElem(parseElem(bytes, offset, length))
                                }
                            }
                            count++
                        }
                        2 -> state = input.readInt32()
                        3 -> aiFlag = parseAiFlag(input.readByteArray())
                        5 -> colorfulSrc += parseColorful(input.readByteArray())
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return SegmentElemScanResult(
            meta = SegmentMeta(
                state = state,
                aiFlag = aiFlag,
                colorfulSrc = colorfulSrc
            ),
            elemCount = count
        )
    }

    fun parseView(bytes: ByteArray): DmWebViewReplyProto {
        val input = CodedInputStream.newInstance(bytes)
        var segmentDurationMs = 0
        var totalSegments = 0
        var totalCount = 0L
        val specialDanmakuUrls = mutableListOf<String>()
        var smartFilterConfig = DmSmartFilterConfigProto()
        var playerConfig = DanmuWebPlayerConfigProto()
        val reportFilters = mutableListOf<String>()
        val commandDms = mutableListOf<CommandDmProto>()
        val restrictPeriods = mutableListOf<DmRestrictPeriodProto>()

        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        4 -> {
                            val dmSge = parseDmSge(input.readByteArray())
                            segmentDurationMs = dmSge.first
                            totalSegments = dmSge.second
                        }
                        5 -> smartFilterConfig = parseDanmakuFlagConfig(input.readByteArray())
                        6 -> specialDanmakuUrls += input.readString()
                        9 -> commandDms += parseCommandDm(input.readByteArray())
                        10 -> {
                            val parsed = parseDanmuWebPlayerConfig(input.readByteArray())
                            playerConfig = parsed
                            smartFilterConfig = smartFilterConfig.copy(
                                playerLevel = parsed.aiLevel,
                                playerEnabled = parsed.aiSwitch
                            )
                        }
                        11 -> reportFilters += input.readString()
                        8 -> totalCount = input.readInt64()
                        19 -> restrictPeriods += parseDmRestrictPeriod(input.readByteArray())
                        else -> input.skipField(tag)
                    }
                }
            }
        }

        return DmWebViewReplyProto(
            segmentDurationMs = segmentDurationMs,
            totalSegments = totalSegments,
            totalCount = totalCount,
            specialDanmakuUrls = specialDanmakuUrls,
            smartFilterConfig = smartFilterConfig,
            playerConfig = playerConfig,
            reportFilters = reportFilters,
            commandDms = commandDms,
            restrictPeriods = restrictPeriods
        )
    }

    private fun parseDanmakuFlagConfig(bytes: ByteArray): DmSmartFilterConfigProto {
        val input = CodedInputStream.newInstance(bytes)
        var cloudLevel = 0
        var cloudText = ""
        var cloudSwitch = 0
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        1 -> cloudLevel = input.readInt32()
                        2 -> cloudText = input.readString()
                        3 -> cloudSwitch = input.readInt32()
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return DmSmartFilterConfigProto(
            cloudLevel = cloudLevel,
            cloudText = cloudText,
            cloudSwitch = cloudSwitch
        )
    }

    private fun parseDanmuWebPlayerConfig(bytes: ByteArray): DanmuWebPlayerConfigProto {
        val input = CodedInputStream.newInstance(bytes)
        var dmSwitch = true
        var aiSwitch = false
        var aiLevel = 0
        var typeTop = true
        var typeScroll = true
        var typeBottom = true
        var typeColor = true
        var typeSpecial = true
        var preventShade = false
        var dmask = false
        var opacity = 1f
        var speedPlus = 1f
        var fontSize = 1f
        var fontFamily = ""
        var bold = false
        var fontBorder = 0
        var seniorModeSwitch = true
        var typeTopBottom = true
        var dmArea = 0
        var dmDensity = 0
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        1 -> dmSwitch = input.readBool()
                        2 -> aiSwitch = input.readBool()
                        3 -> aiLevel = input.readInt32()
                        4 -> typeTop = input.readBool()
                        5 -> typeScroll = input.readBool()
                        6 -> typeBottom = input.readBool()
                        7 -> typeColor = input.readBool()
                        8 -> typeSpecial = input.readBool()
                        9 -> preventShade = input.readBool()
                        10 -> dmask = input.readBool()
                        11 -> opacity = input.readFloat()
                        13 -> speedPlus = input.readFloat()
                        14 -> fontSize = input.readFloat()
                        17 -> fontFamily = input.readString()
                        18 -> bold = input.readBool()
                        19 -> fontBorder = input.readInt32()
                        21 -> seniorModeSwitch = input.readBool()
                        24 -> typeTopBottom = input.readBool()
                        25 -> dmArea = input.readInt32()
                        26 -> dmDensity = input.readInt32()
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return DanmuWebPlayerConfigProto(
            dmSwitch = dmSwitch,
            aiSwitch = aiSwitch,
            aiLevel = aiLevel,
            typeTop = typeTop,
            typeScroll = typeScroll,
            typeBottom = typeBottom,
            typeColor = typeColor,
            typeSpecial = typeSpecial,
            preventShade = preventShade,
            dmask = dmask,
            opacity = opacity,
            speedPlus = speedPlus,
            fontSize = fontSize,
            fontFamily = fontFamily,
            bold = bold,
            fontBorder = fontBorder,
            seniorModeSwitch = seniorModeSwitch,
            typeTopBottom = typeTopBottom,
            dmArea = dmArea,
            dmDensity = dmDensity
        )
    }

    private fun parseCommandDm(bytes: ByteArray): CommandDmProto {
        val input = CodedInputStream.newInstance(bytes)
        var command = ""
        var text = ""
        var stimeMs = 0L
        var dmid = 0L
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        4 -> command = input.readString()
                        5 -> text = input.readString()
                        6 -> stimeMs = input.readTimeMsByWireType(tag)
                        10 -> dmid = input.readInt64()
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return CommandDmProto(
            command = command,
            text = text,
            stimeMs = stimeMs,
            dmid = dmid
        )
    }

    private fun parseDmRestrictPeriod(bytes: ByteArray): DmRestrictPeriodProto {
        val input = CodedInputStream.newInstance(bytes)
        var startMs = 0L
        var endMs = 0L
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        1 -> startMs = input.readInt64()
                        2 -> endMs = input.readInt64()
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return DmRestrictPeriodProto(
            startMs = startMs,
            endMs = endMs
        )
    }

    private fun CodedInputStream.readTimeMsByWireType(tag: Int): Long {
        return when (tag and 0x7) {
            0 -> readInt64()
            1 -> (readDouble() * 1000L).toLong()
            5 -> (readFloat() * 1000L).toLong()
            else -> {
                skipField(tag)
                0L
            }
        }
    }

    private fun parseElem(bytes: ByteArray): DanmakuElemProto {
        return parseElem(CodedInputStream.newInstance(bytes))
    }

    private fun parseElem(length: Int, input: CodedInputStream): DanmakuElemProto {
        val oldLimit = input.pushLimit(length)
        val elem = parseElem(input)
        input.popLimit(oldLimit)
        return elem
    }

    private fun parseElem(bytes: ByteArray, offset: Int, length: Int): DanmakuElemProto {
        return parseElem(CodedInputStream.newInstance(bytes, offset, length))
    }

    private fun parseElem(input: CodedInputStream): DanmakuElemProto {
        var id = 0L
        var progress = 0
        var mode = 1
        var fontSize = 25
        var color = 0xFFFFFFFF.toInt()
        var colorful = 0
        var midHash = ""
        var content = ""
        var ctime = 0L
        var weight = 0
        var pool = 0
        var action = ""
        var attr = 0
        var idStr = ""
        var animation = ""

        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        1 -> id = input.readInt64()
                        2 -> progress = input.readInt32()
                        3 -> mode = input.readInt32()
                        4 -> fontSize = input.readInt32()
                        5 -> color = input.readUInt32().toInt()
                        24 -> colorful = input.readInt32()
                        6 -> midHash = input.readString()
                        7 -> content = input.readString()
                        8 -> ctime = input.readInt64()
                        9 -> weight = input.readInt32()
                        10 -> action = input.readString()
                        11 -> pool = input.readInt32()
                        13 -> attr = input.readInt32()
                        12 -> idStr = input.readString()
                        14 -> animation = input.readString()
                        else -> input.skipField(tag)
                    }
                }
            }
        }

        return DanmakuElemProto(
            id = id,
            progress = progress,
            mode = mode,
            fontSize = fontSize,
            color = color,
            colorful = colorful,
            midHash = midHash,
            content = content,
            ctime = ctime,
            weight = weight,
            pool = pool,
            action = action,
            attr = attr,
            idStr = idStr,
            animation = animation
        )
    }

    private fun parseElemProgress(bytes: ByteArray): Int {
        return parseElemProgress(CodedInputStream.newInstance(bytes))
    }

    private fun parseElemProgress(input: CodedInputStream): Int {
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        2 -> return input.readInt32()
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return 0
    }

    private fun skipRemainingMessageBytes(input: CodedInputStream, offset: Int, length: Int) {
        val remainingBytes = offset + length - input.totalBytesRead
        if (remainingBytes > 0) {
            input.skipRawBytes(remainingBytes)
        }
    }

    private fun parseDmSge(bytes: ByteArray): Pair<Int, Int> {
        val input = CodedInputStream.newInstance(bytes)
        var pageSize = 0
        var totalSegments = 0
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        1 -> pageSize = input.readInt32()
                        2 -> totalSegments = input.readInt32()
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return pageSize to totalSegments
    }

    private fun parseAiFlag(bytes: ByteArray): DanmakuAIFlagProto {
        val input = CodedInputStream.newInstance(bytes)
        val dmFlags = mutableListOf<DanmakuFlagProto>()
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        1 -> dmFlags += parseAiFlagItem(input.readByteArray())
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return DanmakuAIFlagProto(dmFlags = dmFlags)
    }

    private fun parseAiFlagItem(bytes: ByteArray): DanmakuFlagProto {
        val input = CodedInputStream.newInstance(bytes)
        var dmid = 0L
        var flag = 0
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        1 -> dmid = input.readInt64()
                        2 -> flag = input.readUInt32().toInt()
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return DanmakuFlagProto(dmid = dmid, flag = flag)
    }

    private fun parseColorful(bytes: ByteArray): DmColorfulProto {
        val input = CodedInputStream.newInstance(bytes)
        var type = 0
        var src = ""
        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                0 -> break
                else -> {
                    when (tag ushr 3) {
                        1 -> type = input.readInt32()
                        2 -> src = input.readString()
                        else -> input.skipField(tag)
                    }
                }
            }
        }
        return DmColorfulProto(type = type, src = src)
    }
}
