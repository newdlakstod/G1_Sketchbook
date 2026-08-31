package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VectorPageTest {
    @Test fun roundTripsThroughJson() {
        val page = VectorPage(
            listOf(
                VectorStroke(-13421773L, listOf(VectorPoint(1f, 2f, 3f), VectorPoint(4.5f, 5.5f, 6.5f))),
                VectorStroke(-65536L, listOf(VectorPoint(-1f, 0f, 2f))),
            ),
        )
        val decoded = vectorPageFromJson(page.toJson())
        assertEquals(page, decoded)
    }

    @Test fun emptyPageRoundTrips() {
        val page = VectorPage(emptyList())
        assertEquals(page, vectorPageFromJson(page.toJson()))
    }

    @Test fun malformedJsonReturnsNull() {
        assertNull(vectorPageFromJson("not json at all"))
    }

    @Test fun blankStringReturnsNull() {
        assertNull(vectorPageFromJson(""))
    }

    @Test fun capRoundTripsThroughJson() {
        val page = VectorPage(
            listOf(
                VectorStroke(-65536L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), VectorCap.ROUND),
                VectorStroke(-16711936L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), VectorCap.SQUARE),
            ),
        )
        assertEquals(page, vectorPageFromJson(page.toJson()))
    }

    @Test fun jsonWithoutCapFieldDefaultsToButt() {
        // 이 옵션이 생기기 전에 저장된 예전 형식(끝에 "cap" 필드가 아예 없음) — 값이 없으면
        // BUTT로 취급해 예전 그림의 생김새를 그대로 유지해야 한다.
        val legacyJson = "{\"strokes\":[{\"color\":-65536,\"points\":[{\"x\":0.0,\"y\":0.0,\"w\":4.0},{\"x\":10.0,\"y\":0.0,\"w\":4.0}]}]}"
        val decoded = vectorPageFromJson(legacyJson)!!
        assertEquals(VectorCap.BUTT, decoded.strokes[0].cap)
    }

    @Test fun fillAndStrokeRoundTripThroughJson() {
        val page = VectorPage(
            listOf(
                VectorStroke(-65536L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)),
                    fillEnabled = false, strokeColor = -16777216L, strokeWidthPx = 5f),
                VectorStroke(-16711936L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f))),
            ),
        )
        assertEquals(page, vectorPageFromJson(page.toJson()))
    }

    @Test fun jsonWithoutFillStrokeFieldsDefaultsToFillOnlyLikeBefore() {
        // "cap"까지는 있지만 fill/stroke 필드가 생기기 전 형식 — 채움만(테두리 없음)으로 읽혀야
        // 예전 그림의 생김새가 그대로 유지된다.
        val json = "{\"strokes\":[{\"color\":-65536,\"points\":[{\"x\":0.0,\"y\":0.0,\"w\":4.0},{\"x\":10.0,\"y\":0.0,\"w\":4.0}],\"cap\":\"ROUND\"}]}"
        val decoded = vectorPageFromJson(json)!!.strokes[0]
        assertEquals(true, decoded.fillEnabled)
        assertEquals(null, decoded.strokeColor)
        assertEquals(2f, decoded.strokeWidthPx)
    }

    @Test fun brushProfileIdRoundTripsThroughJson() {
        val page = VectorPage(listOf(
            VectorStroke(-65536L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), brushProfileId = "stamp-1"),
        ))
        assertEquals(page, vectorPageFromJson(page.toJson()))
    }

    @Test fun jsonWithoutBrushProfileFieldDefaultsToNull() {
        val json = "{\"strokes\":[{\"color\":-65536,\"points\":[{\"x\":0.0,\"y\":0.0,\"w\":4.0},{\"x\":10.0,\"y\":0.0,\"w\":4.0}],\"cap\":\"ROUND\",\"fillEnabled\":true,\"strokeColor\":-9223372036854775808,\"strokeWidthPx\":2.0}]}"
        val decoded = vectorPageFromJson(json)!!.strokes[0]
        assertEquals(null, decoded.brushProfileId)
    }
}
