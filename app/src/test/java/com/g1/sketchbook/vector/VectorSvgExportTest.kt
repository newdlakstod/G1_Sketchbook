package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class VectorSvgExportTest {
    @Test fun emptyPageIsAnEmptySvgCanvas() {
        val svg = vectorPageToSvg(VectorPage(emptyList()), 1024)
        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.contains("width=\"1024\""))
        assertTrue(svg.contains("viewBox=\"0 0 1024 1024\""))
        assertTrue(svg.contains("</svg>"))
        assertTrue(svg.contains("<path").not())
    }

    @Test fun oneStrokeBecomesOneFilledPath() {
        val page = VectorPage(listOf(VectorStroke(-65536L /* opaque red */, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)))))
        val svg = vectorPageToSvg(page, 100)
        assertEquals(1, Regex("<path").findAll(svg).count())
        assertTrue(svg.contains("fill=\"#ff0000\""))
        assertTrue(svg.contains("d=\"M0.0,2.0 L10.0,2.0 L10.0,-2.0 L0.0,-2.0 Z\""))
    }

    @Test fun strokeWithFewerThanTwoPointsIsSkipped() {
        val page = VectorPage(listOf(VectorStroke(-65536L, listOf(VectorPoint(5f, 5f, 4f)))))
        val svg = vectorPageToSvg(page, 100)
        assertTrue(svg.contains("<path").not())
    }
}
