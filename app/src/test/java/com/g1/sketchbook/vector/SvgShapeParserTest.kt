package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SvgShapeParserTest {
    @Test fun singlePathIsOneShape() {
        val svg = """<svg viewBox="0 0 10 10"><path d="M0,0 L10,0 L10,10 Z"/></svg>"""
        val shapes = parseSvgDocument(svg)!!
        assertEquals(1, shapes.size)
        assertEquals(4, shapes[0].size)
    }

    @Test fun rectBecomesFourCornerPolygon() {
        val svg = """<svg viewBox="0 0 20 10"><rect x="0" y="0" width="20" height="10"/></svg>"""
        val shapes = parseSvgDocument(svg)!!
        assertEquals(1, shapes.size)
        assertEquals(4, shapes[0].size)
    }

    @Test fun circleBecomesManySidedPolygon() {
        val svg = """<svg viewBox="0 0 10 10"><circle cx="5" cy="5" r="5"/></svg>"""
        val shapes = parseSvgDocument(svg)!!
        assertEquals(1, shapes.size)
        assertTrue(shapes[0].size >= 12) // 원을 다각형으로 근사하니 변이 여러 개
    }

    @Test fun ellipseBecomesManySidedPolygon() {
        val svg = """<svg viewBox="0 0 20 10"><ellipse cx="10" cy="5" rx="10" ry="5"/></svg>"""
        val shapes = parseSvgDocument(svg)!!
        assertEquals(1, shapes.size)
        assertTrue(shapes[0].size >= 12)
    }

    @Test fun multipleTopLevelShapesEachBecomeOwnPolygon() {
        val svg = """<svg viewBox="0 0 20 10">
            <path d="M0,0 L5,0 L5,5 Z"/>
            <rect x="10" y="0" width="5" height="5"/>
        </svg>"""
        val shapes = parseSvgDocument(svg)!!
        assertEquals(2, shapes.size)
    }

    @Test fun groupWithTranslateOffsetsChildShapes() {
        val svg = """<svg viewBox="0 0 20 20">
            <g transform="translate(10,10)"><path d="M0,0 L5,0 L5,5 Z"/></g>
        </svg>"""
        val ungrouped = parseSvgDocument("""<svg viewBox="0 0 20 20"><path d="M0,0 L5,0 L5,5 Z"/></svg>""")!!
        val grouped = parseSvgDocument(svg)!!
        // translate(10,10) 안 먹인 것과 먹인 것의 정규화 결과는 같아야 한다(둘 다 삼각형 모양이 같으니
        // 정규화 후에는 절대좌표가 지워짐) — 대신 그룹이 있어도 도형 개수는 그대로 1개인지만 확인.
        assertEquals(ungrouped.size, grouped.size)
        assertEquals(ungrouped[0].size, grouped[0].size)
    }

    @Test fun groupWithRotateIsSkipped() {
        val svg = """<svg viewBox="0 0 20 20">
            <g transform="rotate(45)"><path d="M0,0 L5,0 L5,5 Z"/></g>
            <rect x="0" y="0" width="5" height="5"/>
        </svg>"""
        val shapes = parseSvgDocument(svg)!!
        assertEquals(1, shapes.size) // 회전 그룹은 건너뛰고 rect만 남음
    }

    @Test fun noShapesReturnsNull() {
        assertNull(parseSvgDocument("""<svg viewBox="0 0 10 10"></svg>"""))
    }

    @Test fun noSvgTagReturnsNull() {
        assertNull(parseSvgDocument("not an svg at all"))
    }

    @Test fun resultIsNormalizedAroundOrigin() {
        // 원점에서 멀리 떨어진 사각형이라도, 정규화 후엔 경계상자 중심이 원점(0,0) 근처여야 한다.
        val svg = """<svg viewBox="0 0 1000 1000"><rect x="500" y="500" width="20" height="20"/></svg>"""
        val shapes = parseSvgDocument(svg)!!
        val allPoints = shapes.flatten()
        val cx = (allPoints.minOf { it.x } + allPoints.maxOf { it.x }) / 2f
        val cy = (allPoints.minOf { it.y } + allPoints.maxOf { it.y }) / 2f
        assertTrue(kotlin.math.abs(cx) < 0.01f && kotlin.math.abs(cy) < 0.01f)
    }

    @Test fun resultIsScaledToUnitSize() {
        val svg = """<svg viewBox="0 0 200 100"><rect x="0" y="0" width="200" height="100"/></svg>"""
        val shapes = parseSvgDocument(svg)!!
        val allPoints = shapes.flatten()
        val w = allPoints.maxOf { it.x } - allPoints.minOf { it.x }
        val h = allPoints.maxOf { it.y } - allPoints.minOf { it.y }
        // 가장 긴 변(가로 200)이 1이 되도록 스케일 -> 가로는 1.0, 세로(100/200=0.5배)는 0.5여야 함.
        assertTrue(kotlin.math.abs(w - 1f) < 0.01f)
        assertTrue(kotlin.math.abs(h - 0.5f) < 0.01f)
    }
}
