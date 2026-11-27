package com.mytvb.model.proto

import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DmProtoParserTest {

    @Test
    fun parseSegment_readsExtendedDanmakuFields() {
        val elemBytes = protoMessage(
            numberField(1, 123L),
            numberField(2, 456),
            numberField(3, 7),
            numberField(4, 32),
            uint32Field(5, 0x00FF99FF),
            stringField(6, "mid_hash"),
            stringField(7, "[\"0\",\"0\",\"1-1\",\"4.5\",\"高级弹幕\"]"),
            numberField(8, 1_717_171_717L),
            numberField(9, 11),
            stringField(10, "action"),
            numberField(11, 1),
            stringField(12, "id_str"),
            numberField(13, 2),
            stringField(14, "animation")
        )
        val aiFlagBytes = protoMessage(
            bytesField(
                1,
                protoMessage(
                    numberField(1, 123L),
                    uint32Field(2, 9)
                )
            )
        )
        val segmentBytes = protoMessage(
            bytesField(1, elemBytes),
            numberField(2, 3),
            bytesField(3, aiFlagBytes)
        )

        val result = DmProtoParser.parseSegment(segmentBytes)

        assertEquals(1, result.elems.size)
        val elem = result.elems.first()
        assertEquals(123L, elem.id)
        assertEquals(456, elem.progress)
        assertEquals(7, elem.mode)
        assertEquals(32, elem.fontSize)
        assertEquals(0x00FF99FF, elem.color)
        assertEquals("mid_hash", elem.midHash)
        assertEquals(1_717_171_717L, elem.ctime)
        assertEquals("action", elem.action)
        assertEquals(1, elem.pool)
        assertEquals(2, elem.attr)
        assertEquals("id_str", elem.idStr)
        assertEquals("animation", elem.animation)
        assertEquals(3, result.state)
        assertEquals(1, result.aiFlag.dmFlags.size)
        assertEquals(9, result.aiFlag.dmFlags.first().flag)
    }

    @Test
    fun parseView_readsSegmentAndSpecialInfo() {
        val viewBytes = protoMessage(
            bytesField(
                4,
                protoMessage(
                    numberField(1, 360000),
                    numberField(2, 6)
                )
            ),
            stringField(6, "http://example.com/special-1.bin"),
            stringField(6, "http://example.com/special-2.bin"),
            numberField(8, 6000L)
        )

        val result = DmProtoParser.parseView(viewBytes)

        assertEquals(360000, result.segmentDurationMs)
        assertEquals(6, result.totalSegments)
        assertEquals(6000L, result.totalCount)
        assertEquals(2, result.specialDanmakuUrls.size)
        assertTrue(result.specialDanmakuUrls.first().contains("special-1"))
    }

    @Test
    fun parseView_readsPlayerConfigFiltersCommandsAndRestrictPeriods() {
        val viewBytes = protoMessage(
            bytesField(
                5,
                protoMessage(
                    numberField(1, 7),
                    stringField(2, "ai"),
                    numberField(3, 1)
                )
            ),
            bytesField(
                9,
                protoMessage(
                    stringField(4, "vote"),
                    stringField(5, "去投票"),
                    numberField(6, 12_000L),
                    numberField(10, 99L)
                )
            ),
            bytesField(
                10,
                protoMessage(
                    boolField(1, true),
                    boolField(2, true),
                    numberField(3, 5),
                    boolField(4, false),
                    boolField(5, true),
                    boolField(6, false),
                    boolField(7, false),
                    boolField(8, true),
                    boolField(9, true),
                    boolField(10, true),
                    floatField(11, 0.6f),
                    floatField(13, 1.5f),
                    floatField(14, 1.2f),
                    stringField(17, "sans"),
                    boolField(18, true),
                    numberField(19, 2),
                    boolField(21, false),
                    boolField(24, false),
                    numberField(25, 3),
                    numberField(26, 4)
                )
            ),
            stringField(11, "剧透"),
            bytesField(
                19,
                protoMessage(
                    numberField(1, 1_000L),
                    numberField(2, 2_000L)
                )
            )
        )

        val result = DmProtoParser.parseView(viewBytes)

        assertEquals(7, result.smartFilterConfig.cloudLevel)
        assertEquals(true, result.smartFilterConfig.cloudSwitch != 0)
        assertEquals(5, result.smartFilterConfig.playerLevel)
        assertEquals(true, result.smartFilterConfig.playerEnabled)
        assertEquals(true, result.playerConfig.aiSwitch)
        assertEquals(5, result.playerConfig.aiLevel)
        assertEquals(false, result.playerConfig.typeTop)
        assertEquals(false, result.playerConfig.typeBottom)
        assertEquals(false, result.playerConfig.typeColor)
        assertEquals(0.6f, result.playerConfig.opacity, 0.001f)
        assertEquals("sans", result.playerConfig.fontFamily)
        assertEquals(1, result.reportFilters.size)
        assertEquals("剧透", result.reportFilters.first())
        assertEquals(1, result.commandDms.size)
        assertEquals("vote", result.commandDms.first().command)
        assertEquals(99L, result.commandDms.first().dmid)
        assertEquals(1_000L, result.restrictPeriods.first().startMs)
        assertEquals(2_000L, result.restrictPeriods.first().endMs)
    }

    @Test
    fun parseView_acceptsFloatCommandTimeAsSeconds() {
        val viewBytes = protoMessage(
            bytesField(
                9,
                protoMessage(
                    stringField(4, "vote"),
                    floatField(6, 12.5f),
                    numberField(10, 99L)
                )
            )
        )

        val result = DmProtoParser.parseView(viewBytes)

        assertEquals(12_500L, result.commandDms.first().stimeMs)
    }

    @Test
    fun forEachSegmentElemInProgressRange_onlyParsesMatchingItems() {
        val segmentBytes = protoMessage(
            bytesField(
                1,
                protoMessage(
                    numberField(1, 1L),
                    numberField(2, 1_000),
                    stringField(7, "before")
                )
            ),
            bytesField(
                1,
                protoMessage(
                    numberField(1, 2L),
                    numberField(2, 30_000),
                    stringField(7, "inside-start")
                )
            ),
            bytesField(
                1,
                protoMessage(
                    numberField(1, 3L),
                    numberField(2, 119_999),
                    stringField(7, "inside-end")
                )
            ),
            bytesField(
                1,
                protoMessage(
                    numberField(1, 4L),
                    numberField(2, 120_000),
                    stringField(7, "after")
                )
            )
        )
        val parsed = mutableListOf<DanmakuElemProto>()

        val scanned = DmProtoParser.forEachSegmentElemInProgressRange(
            bytes = segmentBytes,
            startMs = 30_000,
            endMs = 120_000,
            onElem = parsed::add
        )

        assertEquals(4, scanned)
        assertEquals(listOf(2L, 3L), parsed.map { it.id })
        assertEquals(listOf("inside-start", "inside-end"), parsed.map { it.content })
    }

    @Test
    fun forEachSegmentElemWithMetaInProgressRange_readsMetaAndMatchingItems() {
        val aiFlagBytes = protoMessage(
            bytesField(
                1,
                protoMessage(
                    numberField(1, 2L),
                    uint32Field(2, 8)
                )
            )
        )
        val segmentBytes = protoMessage(
            bytesField(
                1,
                protoMessage(
                    numberField(1, 1L),
                    numberField(2, 5_000),
                    stringField(7, "before")
                )
            ),
            numberField(2, 3),
            bytesField(3, aiFlagBytes),
            bytesField(
                5,
                protoMessage(
                    numberField(1, 4),
                    stringField(2, "https://example.com/colorful.json")
                )
            ),
            bytesField(
                1,
                protoMessage(
                    numberField(1, 2L),
                    numberField(2, 35_000),
                    numberField(24, 4),
                    stringField(7, "inside")
                )
            )
        )
        val parsed = mutableListOf<DanmakuElemProto>()

        val result = DmProtoParser.forEachSegmentElemWithMetaInProgressRange(
            bytes = segmentBytes,
            startMs = 30_000,
            endMs = 120_000,
            onElem = parsed::add
        )

        assertEquals(2, result.elemCount)
        assertEquals(3, result.meta.state)
        assertEquals(8, result.meta.aiFlag.dmFlags.first().flag)
        assertEquals("https://example.com/colorful.json", result.meta.colorfulSrc.first().src)
        assertEquals(listOf(2L), parsed.map { it.id })
        assertEquals(listOf("inside"), parsed.map { it.content })
    }

    private fun protoMessage(vararg fields: ByteArray): ByteArray {
        return ByteArrayOutputStream().use { output ->
            fields.forEach(output::write)
            output.toByteArray()
        }
    }

    private fun numberField(fieldNumber: Int, value: Long): ByteArray {
        return ByteArrayOutputStream().use { output ->
            val codedOutput = CodedOutputStream.newInstance(output)
            codedOutput.writeUInt32NoTag(fieldNumber shl 3)
            codedOutput.writeInt64NoTag(value)
            codedOutput.flush()
            output.toByteArray()
        }
    }

    private fun uint32Field(fieldNumber: Int, value: Int): ByteArray {
        return ByteArrayOutputStream().use { output ->
            val codedOutput = CodedOutputStream.newInstance(output)
            codedOutput.writeUInt32NoTag(fieldNumber shl 3)
            codedOutput.writeUInt32NoTag(value)
            codedOutput.flush()
            output.toByteArray()
        }
    }

    private fun boolField(fieldNumber: Int, value: Boolean): ByteArray {
        return ByteArrayOutputStream().use { output ->
            val codedOutput = CodedOutputStream.newInstance(output)
            codedOutput.writeUInt32NoTag(fieldNumber shl 3)
            codedOutput.writeBoolNoTag(value)
            codedOutput.flush()
            output.toByteArray()
        }
    }

    private fun floatField(fieldNumber: Int, value: Float): ByteArray {
        return ByteArrayOutputStream().use { output ->
            val codedOutput = CodedOutputStream.newInstance(output)
            codedOutput.writeUInt32NoTag((fieldNumber shl 3) or 5)
            codedOutput.writeFloatNoTag(value)
            codedOutput.flush()
            output.toByteArray()
        }
    }

    private fun stringField(fieldNumber: Int, value: String): ByteArray {
        return bytesField(fieldNumber, value.toByteArray(Charsets.UTF_8))
    }

    private fun bytesField(fieldNumber: Int, value: ByteArray): ByteArray {
        return ByteArrayOutputStream().use { output ->
            val codedOutput = CodedOutputStream.newInstance(output)
            codedOutput.writeUInt32NoTag((fieldNumber shl 3) or 2)
            codedOutput.writeByteArrayNoTag(value)
            codedOutput.flush()
            output.toByteArray()
        }
    }
}
