package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VectorDocumentCodecTest {
    @Test fun v2RoundTripKeepsLegacyAndEditableObjects() {
        val source = VectorDocument(objects = listOf(
            LegacyStrokeObject("old", VectorStroke(
                0xFF000000,
                listOf(VectorPoint(0f, 0f, 2f), VectorPoint(2f, 2f, 1f)),
                cap = VectorCap.SQUARE,
                fillEnabled = false,
                strokeColor = 0xFF112233,
                strokeWidthPx = 3f,
                brushProfileId = "stamp\\\"one",
                fillColor = 0xFF445566,
            )),
            EditablePathObject(
                "new",
                PathGeometry(listOf(PathPoint(1f, 2f, 1f), PathPoint(3f, 4f, .5f)), closed = true),
                PathAppearance(
                    fill = FillStyle(true, 0xFF778899),
                    stroke = StrokeStyle(false, 0xFFAABBCC, 12f, VectorCap.SQUARE, VectorJoin.BEVEL),
                    brush = BrushStyle(VectorBrushKind.ART, "art\\nprofile"),
                ),
                ObjectTransform(1f, 2f, .5f, 2f, 45f),
            ),
        ))

        assertEquals(source, decodeVectorDocument(encodeVectorDocument(source)))
    }

    @Test fun encodingIsByteStable() {
        val document = VectorDocument(objects = listOf(editablePath("stable")))

        assertEquals(encodeVectorDocument(document), encodeVectorDocument(document))
    }

    @Test fun invalidV2FallsBackToV1() {
        val legacy = VectorPage(listOf(VectorStroke(
            0xFF000000,
            listOf(VectorPoint(0f, 0f, 2f), VectorPoint(2f, 2f, 1f)),
        )))

        assertEquals(legacyPageAsDocument(legacy), decodeStoredVectorDocument("{broken", legacy.toJson()))
    }

    @Test fun decoderRejectsInvalidVersionsIdsAndLegacyPointCounts() {
        assertNull(decodeVectorDocument("{\"version\":3,\"objects\":[]}"))
        assertNull(decodeVectorDocument("{\"version\":2,\"objects\":[{\"type\":\"legacy\",\"id\":\"\",\"stroke\":{\"color\":1,\"points\":[{\"x\":0,\"y\":0,\"w\":1},{\"x\":1,\"y\":1,\"w\":1}],\"cap\":\"ROUND\",\"fillEnabled\":true,\"strokeColor\":null,\"strokeWidthPx\":2,\"brushProfileId\":null,\"fillColor\":null}}]}"))
        assertNull(decodeVectorDocument("{\"version\":2,\"objects\":[{\"type\":\"legacy\",\"id\":\"one\",\"stroke\":{\"color\":1,\"points\":[{\"x\":0,\"y\":0,\"w\":1}],\"cap\":\"ROUND\",\"fillEnabled\":true,\"strokeColor\":null,\"strokeWidthPx\":2,\"brushProfileId\":null,\"fillColor\":null}}]}"))
    }

    @Test fun decoderRejectsNonFiniteNumbersAndInvalidEditableWidthFactors() {
        val valid = encodeVectorDocument(VectorDocument(objects = listOf(editablePath("editable"))))

        assertNull(decodeVectorDocument(valid.replace("\"widthFactor\":1.0", "\"widthFactor\":1e999")))
        assertNull(decodeVectorDocument(valid.replace("\"widthFactor\":1.0", "\"widthFactor\":0.01")))
        assertTrue(decodeVectorDocument(valid) != null)
    }
}
