package com.g1.sketchbook.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorLibraryTest {
    private val libA = ColorLibrary("a", "A", listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L))
    private val libB = ColorLibrary("b", "B", listOf(11L, 12L, 13L, 14L, 15L, 16L, 17L))
    private val libC = ColorLibrary("c", "C", listOf(21L, 22L, 23L, 24L, 25L, 26L, 27L))

    @Test fun derivePaletteConcatenatesInOrder() {
        assertEquals(libB.colors + libA.colors, deriveLibraryPalette(listOf(libA, libB, libC), listOf("b", "a")))
    }

    @Test fun derivePaletteSkipsMissingIds() {
        assertEquals(libA.colors, deriveLibraryPalette(listOf(libA), listOf("a", "missing")))
    }

    @Test fun derivePaletteOfEmptyActiveIdsIsEmpty() {
        assertEquals(emptyList(), deriveLibraryPalette(listOf(libA, libB), emptyList()))
    }

    @Test fun toggleActivatesWhenNotPresent() {
        assertEquals(listOf("a"), toggleActiveLibrary(emptyList(), "a"))
    }

    @Test fun toggleDeactivatesWhenPresent() {
        assertEquals(listOf("a", "c"), toggleActiveLibrary(listOf("a", "b", "c"), "b"))
    }

    @Test fun toggleEvictsOldestWhenAtMax() {
        assertEquals(listOf("b", "c", "d"), toggleActiveLibrary(listOf("a", "b", "c"), "d"))
    }

    @Test fun toggleRespectsCustomMax() {
        assertEquals(listOf("y"), toggleActiveLibrary(listOf("x"), "y", maxActive = 1))
    }

    @Test fun addLibraryAppendsWithSevenStarterColors() {
        val result = addLibrary(listOf(libA), "새 라이브러리")
        assertEquals(2, result.size)
        assertEquals("새 라이브러리", result[1].name)
        assertEquals(7, result[1].colors.size)
    }

    @Test fun addLibraryDoesNothingAtCap() {
        val full = (1..MaxLibraries).map { ColorLibrary("id$it", "L$it", libA.colors) }
        assertEquals(full, addLibrary(full, "넘침"))
    }

    @Test fun renameLibraryChangesOnlyTheMatchingOne() {
        val result = renameLibrary(listOf(libA, libB), "a", "새 이름")
        assertEquals("새 이름", result[0].name)
        assertEquals("B", result[1].name)
    }

    @Test fun updateLibraryColorChangesOnlyThatSlot() {
        val result = updateLibraryColor(listOf(libA), "a", 2, 999L)
        assertEquals(listOf(1L, 2L, 999L, 4L, 5L, 6L, 7L), result[0].colors)
    }

    @Test fun removeLibraryDropsIt() {
        assertEquals(listOf(libB), removeLibrary(listOf(libA, libB), "a"))
    }

    @Test fun serializeAndParseRoundTrip() {
        val libraries = listOf(libA, libB, libC)
        assertEquals(libraries, parseLibraries(serializeLibraries(libraries)))
    }

    @Test fun parseLibrariesOfEmptyStringIsEmptyList() {
        assertEquals(emptyList(), parseLibraries(""))
    }

    @Test fun parseLibrariesHandlesNameWithCommas() {
        val withComma = listOf(ColorLibrary("x", "이름, 쉼표 포함", libA.colors))
        assertEquals(withComma, parseLibraries(serializeLibraries(withComma)))
    }

    @Test fun parseLibrariesReturnsNullForGarbage() {
        assertEquals(null, parseLibraries("완전히 잘못된 형식모자란필드"))
    }

    @Test fun serializeAndParseRoundTripOfEmptyList() {
        assertEquals(emptyList(), parseLibraries(serializeLibraries(emptyList())))
    }

    @Test fun parseLibrariesReturnsNullForNonNumericColor() {
        assertEquals(null, parseLibraries("x이름not,a,number,4,5,6,7"))
    }
}
