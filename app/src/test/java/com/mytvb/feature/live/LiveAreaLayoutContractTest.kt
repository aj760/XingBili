package com.mytvb.feature.live

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveAreaLayoutContractTest {

    @Test
    fun liveAreaUsesDedicatedLayoutWithoutUserMetadata() {
        val adapterSource = projectFile(
            "app/src/main/java/com/mytvb/feature/live/LiveAreaAdapter.kt"
        ).readText()
        val layoutSource = projectFile("app/src/main/res/layout/cell_live_area.xml").readText()

        assertTrue(adapterSource.contains("CellLiveAreaBinding"))
        assertFalse(adapterSource.contains("CellUserBinding"))
        assertTrue(layoutSource.contains("@+id/image_view"))
        assertFalse(layoutSource.contains("TextView"))
        assertFalse(layoutSource.contains("text_level"))
        assertFalse(layoutSource.contains("text_meta"))
        assertFalse(layoutSource.contains("text_sub"))
        assertFalse(layoutSource.contains("粉丝"))
        assertFalse(layoutSource.contains("LV6"))
    }

    private fun projectFile(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(4) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Project file not found: $relativePath")
    }
}
