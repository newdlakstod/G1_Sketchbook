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

    @Test fun decoderRejectsInvalidVersionsBlankIdsAndLegacyPointCounts() {
        assertNull(decodeVectorDocument("{\"version\":3,\"objects\":[]}"))
        assertNull(decodeVectorDocument("{\"version\":2,\"objects\":[{\"type\":\"legacy\",\"id\":\"\",\"stroke\":{\"color\":1,\"points\":[{\"x\":0,\"y\":0,\"w\":1},{\"x\":1,\"y\":1,\"w\":1}],\"cap\":\"ROUND\",\"fillEnabled\":true,\"strokeColor\":null,\"strokeWidthPx\":2,\"brushProfileId\":null,\"fillColor\":null}}]}"))
        assertNull(decodeVectorDocument("{\"version\":2,\"objects\":[{\"type\":\"legacy\",\"id\":\"one\",\"stroke\":{\"color\":1,\"points\":[{\"x\":0,\"y\":0,\"w\":1}],\"cap\":\"ROUND\",\"fillEnabled\":true,\"strokeColor\":null,\"strokeWidthPx\":2,\"brushProfileId\":null,\"fillColor\":null}}]}"))
    }

    @Test fun decoderRejectsDuplicateObjectIds() {
        assertNull(decodeVectorDocument("""{"version":2,"objects":[{"type":"legacy","id":"same","stroke":{"color":1,"points":[{"x":0,"y":0,"w":1},{"x":1,"y":1,"w":1}],"cap":"ROUND","fillEnabled":true,"strokeColor":null,"strokeWidthPx":2,"brushProfileId":null,"fillColor":null}},{"type":"legacy","id":"same","stroke":{"color":2,"points":[{"x":2,"y":2,"w":1},{"x":3,"y":3,"w":1}],"cap":"ROUND","fillEnabled":true,"strokeColor":null,"strokeWidthPx":2,"brushProfileId":null,"fillColor":null}}]}"""))
    }

    @Test fun decoderRejectsMissingRequiredFieldsUnknownObjectTypesAndEnums() {
        assertNull(decodeVectorDocument("""{"version":2,"objects":[{"type":"editable","id":"path","geometry":{"points":[{"x":0,"y":0,"widthFactor":1},{"x":1,"y":1,"widthFactor":0.5}],"closed":false},"appearance":{"fill":{"enabled":false,"color":1},"stroke":{"enabled":true,"color":1,"width":8,"cap":"ROUND","join":"ROUND"},"brush":{"kind":"BASIC","profileId":null}}}]}"""))
        assertNull(decodeVectorDocument("""{"version":2,"objects":[{"type":"future","id":"path"}]}"""))
        assertNull(decodeVectorDocument("""{"version":2,"objects":[{"type":"legacy","id":"old","stroke":{"color":1,"points":[{"x":0,"y":0,"w":1},{"x":1,"y":1,"w":1}],"cap":"TRIANGLE","fillEnabled":true,"strokeColor":null,"strokeWidthPx":2,"brushProfileId":null,"fillColor":null}}]}"""))
    }

    @Test fun decoderRejectsNonFiniteNumbersOutsideWidthFactorAndInvalidEditableWidthFactors() {
        assertNull(decodeVectorDocument("""{"version":2,"objects":[{"type":"editable","id":"path","geometry":{"points":[{"x":0,"y":0,"widthFactor":1},{"x":1,"y":1,"widthFactor":0.01}],"closed":false},"appearance":{"fill":{"enabled":false,"color":1},"stroke":{"enabled":true,"color":1,"width":8,"cap":"ROUND","join":"ROUND"},"brush":{"kind":"BASIC","profileId":null}},"transform":{"translateX":0,"translateY":0,"scaleX":1,"scaleY":1,"rotationDegrees":0}}]}"""))
        assertNull(decodeVectorDocument("""{"version":2,"objects":[{"type":"editable","id":"path","geometry":{"points":[{"x":0,"y":0,"widthFactor":1},{"x":1,"y":1,"widthFactor":0.5}],"closed":false},"appearance":{"fill":{"enabled":false,"color":1},"stroke":{"enabled":true,"color":1,"width":1e999,"cap":"ROUND","join":"ROUND"},"brush":{"kind":"BASIC","profileId":null}},"transform":{"translateX":0,"translateY":0,"scaleX":1,"scaleY":1,"rotationDegrees":0}}]}"""))
        assertNull(decodeVectorDocument("""{"version":2,"objects":[{"type":"legacy","id":"old","stroke":{"color":1,"points":[{"x":1e999,"y":0,"w":1},{"x":1,"y":1,"w":1}],"cap":"ROUND","fillEnabled":true,"strokeColor":null,"strokeWidthPx":2,"brushProfileId":null,"fillColor":null}}]}"""))
    }

    @Test fun storedDecoderRejectsNonCanonicalOrInvalidLegacyBeforeFallback() {
        assertNull(decodeStoredVectorDocument(null, "not-json-but-contains-\"strokes\""))
        assertNull(decodeStoredVectorDocument(null, """{"strokes":[{"color":1,"points":[{"x":0,"y":0,"w":1}],"cap":"BUTT","fillEnabled":true,"strokeColor":-9223372036854775808,"strokeWidthPx":2.0}]}"""))
        assertEquals(VectorDocument(objects = emptyList()), decodeStoredVectorDocument(null, "{\"strokes\":[]}"))
    }
}
