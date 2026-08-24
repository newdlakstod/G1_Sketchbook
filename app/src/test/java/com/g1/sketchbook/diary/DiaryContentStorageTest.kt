package com.g1.sketchbook.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiaryContentStorageTest {
    @Test
    fun transparentContentUsesACompanionPngName() {
        assertEquals("2026-08-25_content.png", diaryContentFileName("2026-08-25"))
    }

    @Test
    fun backupDateListingIgnoresTransparentCompanionFiles() {
        assertEquals("2026-08-25", diaryDateFromCompositeFile("2026-08-25.png"))
        assertNull(diaryDateFromCompositeFile("2026-08-25_content.png"))
    }
}
