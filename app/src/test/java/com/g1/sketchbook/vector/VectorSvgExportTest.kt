package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class VectorSvgExportTest {
    @Test fun emptyPageIsAnEmptySvgCanvas() {
        val svg = vectorPageToSvg(VectorPage(emptyList()), Bounds(0f, 0f, 1024f, 1024f))
        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.contains("width=\"1024.0\""))
        assertTrue(svg.contains("viewBox=\"0 0 1024.0 1024.0\""))
        assertTrue(svg.contains("</svg>"))
        assertTrue(svg.contains("<path").not())
    }

    @Test fun oneStrokeBecomesOneFilledPath() {
        val page = VectorPage(listOf(VectorStroke(-65536L /* opaque red */, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)))))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 100f, 100f))
        assertEquals(1, Regex("<path").findAll(svg).count())
        assertTrue(svg.contains("fill=\"#ff0000\""))
        assertTrue(svg.contains("d=\"M0.0,2.0 L10.0,2.0 L10.0,-2.0 L0.0,-2.0 Z\""))
    }

    @Test fun strokeWithFewerThanTwoPointsIsSkipped() {
        val page = VectorPage(listOf(VectorStroke(-65536L, listOf(VectorPoint(5f, 5f, 4f)))))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 100f, 100f))
        assertTrue(svg.contains("<path").not())
    }

    @Test fun regionOffsetTranslatesCoordinatesToStartAtZero() {
        val page = VectorPage(listOf(VectorStroke(-65536L, listOf(VectorPoint(10f, 10f, 4f), VectorPoint(20f, 10f, 4f)))))
        val svg = vectorPageToSvg(page, Bounds(10f, 8f, 30f, 20f))
        assertTrue(svg.contains("viewBox=\"0 0 20.0 12.0\""))
        assertTrue(svg.contains("d=\"M0.0,4.0 L10.0,4.0 L10.0,0.0 L0.0,0.0 Z\""))
    }

    @Test fun strokeFullyOutsideRegionIsExcluded() {
        val page = VectorPage(listOf(VectorStroke(-65536L, listOf(VectorPoint(500f, 500f, 4f), VectorPoint(510f, 500f, 4f)))))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 100f, 100f))
        assertTrue(svg.contains("<path").not())
    }

    @Test fun ribbonAlwaysFillsRegardlessOfFillEnabled() {
        // fillEnabled는 더 이상 "리본을 채울지"가 아니라 "자기교차 폐곡선을 채울지"를 뜻한다 —
        // 리본 자체는 fillEnabled=false여도 항상 획 색으로 채워져야 한다.
        val page = VectorPage(listOf(VectorStroke(-65536L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), fillEnabled = false)))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 100f, 100f))
        assertTrue(svg.contains("fill=\"#ff0000\""))
        assertTrue(svg.contains("fill=\"none\"").not())
    }

    @Test fun strokeColorAddsStrokeAndWidthAttributes() {
        val page = VectorPage(listOf(
            VectorStroke(-65536L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), strokeColor = -16777216L /* opaque black */, strokeWidthPx = 3f),
        ))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 100f, 100f))
        assertTrue(svg.contains("fill=\"#ff0000\""))
        assertTrue(svg.contains("stroke=\"#000000\""))
        assertTrue(svg.contains("stroke-width=\"3.0\""))
    }

    @Test fun noStrokeColorMeansNoStrokeAttribute() {
        val page = VectorPage(listOf(VectorStroke(-65536L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)))))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 100f, 100f))
        assertTrue(svg.contains("stroke=").not())
    }

    @Test fun stampBrushStrokeWithResolvedProfileExpandsToMultiplePaths() {
        val square = listOf(Point(-0.5f, -0.5f), Point(0.5f, -0.5f), Point(0.5f, 0.5f), Point(-0.5f, 0.5f))
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 25f, sizePx = 10f)
        val page = VectorPage(listOf(VectorStroke(-65536L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(100f, 0f, 4f)), brushProfileId = "id1")))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 100f, 100f), mapOf("id1" to profile))
        assertEquals(5, Regex("<path").findAll(svg).count())
        assertTrue(svg.contains("fill=\"#ff0000\""))
    }

    @Test fun stampBrushStrokeWithUnresolvedProfileFallsBackToRibbon() {
        val page = VectorPage(listOf(VectorStroke(-65536L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), brushProfileId = "missing")))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 100f, 100f), emptyMap())
        assertEquals(1, Regex("<path").findAll(svg).count())
    }

    @Test fun selfIntersectingStrokeAddsClosedRegionFillPath() {
        val page = VectorPage(listOf(
            VectorStroke(
                -65536L,
                listOf(VectorPoint(0f, 0f, 1f), VectorPoint(10f, 10f, 1f), VectorPoint(0f, 10f, 1f), VectorPoint(10f, 0f, 1f)),
            ),
        ))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 20f, 20f))
        assertEquals(2, Regex("<path").findAll(svg).count()) // 리본 하나 + 닫힌 구역 하나
    }

    @Test fun fillColorOverridesRibbonColorForClosedRegion() {
        val page = VectorPage(listOf(
            VectorStroke(
                -65536L,
                listOf(VectorPoint(0f, 0f, 1f), VectorPoint(10f, 10f, 1f), VectorPoint(0f, 10f, 1f), VectorPoint(10f, 0f, 1f)),
                fillColor = -16711936L,
            ),
        ))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 20f, 20f))
        assertTrue(svg.contains("fill=\"#00ff00\""))
    }

    @Test fun fillEnabledFalseSkipsClosedRegionFill() {
        val page = VectorPage(listOf(
            VectorStroke(
                -65536L,
                listOf(VectorPoint(0f, 0f, 1f), VectorPoint(10f, 10f, 1f), VectorPoint(0f, 10f, 1f), VectorPoint(10f, 0f, 1f)),
                fillEnabled = false,
            ),
        ))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 20f, 20f))
        assertEquals(1, Regex("<path").findAll(svg).count()) // 리본만, 닫힌 구역 채움 없음
    }
}
