package com.g1.sketchbook.vector

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtBrushTest {
    private val unitBox = listOf(
        Point(0f, -1f), Point(1f, -1f), Point(1f, 1f), Point(0f, 1f),
    )

    @Test fun straightPathStretchesOneSourceAcrossFullLength() {
        val profile = ArtBrushProfile("a", "chalk", listOf(unitBox))
        val mapped = mapArtBrush(
            profile,
            PathGeometry(listOf(PathPoint(10f, 20f, 1f), PathPoint(110f, 20f, 1f))),
            StrokeStyle(width = 10f),
        )

        assertEquals(1, mapped.size, message = "art deformation keeps one output shape; it must not stamp repeats")
        assertEquals(4, mapped.single().size)
        val all = mapped.flatten()
        assertEquals(10f, all.minOf { it.x }, .01f)
        assertEquals(110f, all.maxOf { it.x }, .01f)
        assertEquals(15f, all.minOf { it.y }, .01f)
        assertEquals(25f, all.maxOf { it.y }, .01f)
    }

    @Test fun curvedTargetOffsetsSourceAlongLocalNormal() {
        val profile = ArtBrushProfile("a", "curve", listOf(listOf(Point(.5f, 1f))))
        val mapped = mapArtBrush(
            profile,
            PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(100f, 0f, 1f), PathPoint(100f, 100f, 1f))),
            StrokeStyle(width = 20f),
        ).single().single()

        assertTrue(mapped.x < 100f && mapped.y > 0f, "corner normal should point up-left, was $mapped")
        assertEquals(7.071f, abs(mapped.x - 100f), .01f)
        assertEquals(7.071f, abs(mapped.y), .01f)
    }

    @Test fun reversedTargetMapsSourceStartToReversedPathStart() {
        val profile = ArtBrushProfile("a", "reverse", listOf(listOf(Point(0f, 0f), Point(1f, 0f))))
        val mapped = mapArtBrush(
            profile,
            PathGeometry(listOf(PathPoint(100f, 0f, 1f), PathPoint(0f, 0f, 1f))),
            StrokeStyle(width = 10f),
        ).single()

        assertEquals(100f, mapped.first().x, .01f)
        assertEquals(0f, mapped.last().x, .01f)
    }

    @Test fun widthFactorNarrowsTheArtBrushAtTheEnd() {
        val profile = ArtBrushProfile("a", "taper", listOf(unitBox))
        val mapped = mapArtBrush(
            profile,
            PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(100f, 0f, .5f))),
            StrokeStyle(width = 20f),
        )

        assertEquals(5f, mapped.flatten().filter { it.x > 99f }.maxOf { abs(it.y) }, .01f)
    }

    @Test fun multipleShapesStaySeparateAndEachVertexMapsOnce() {
        val profile = ArtBrushProfile(
            "a", "two parts", listOf(
                listOf(Point(0f, 0f), Point(1f, 0f)),
                listOf(Point(.25f, -.5f), Point(.75f, .5f), Point(1f, 0f)),
            ),
        )
        val mapped = mapArtBrush(
            profile,
            PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(100f, 0f, 1f))),
            StrokeStyle(width = 10f),
        )

        assertEquals(listOf(2, 3), mapped.map { it.size })
    }

    @Test fun closedAndDuplicatePathInputsAreFiniteAndDeterministic() {
        val profile = ArtBrushProfile("a", "closed", listOf(unitBox))
        val geometry = PathGeometry(
            listOf(
                PathPoint(0f, 0f, 1f), PathPoint(0f, 0f, 1f),
                PathPoint(20f, 0f, 1f), PathPoint(20f, 20f, 1f), PathPoint(0f, 20f, 1f),
            ),
            closed = true,
        )

        val first = mapArtBrush(profile, geometry, StrokeStyle(width = 8f))
        assertTrue(first.flatten().all { it.x.isFinite() && it.y.isFinite() })
        assertEquals(first, mapArtBrush(profile, geometry, StrokeStyle(width = 8f)))
    }

    @Test fun subEpsilonDuplicateDoesNotDropTheSourceStartVertex() {
        val profile = ArtBrushProfile("a", "near duplicate", listOf(listOf(Point(0f, 0f), Point(1f, 0f))))
        val mapped = mapArtBrush(
            profile,
            PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(.00001f, 0f, 1f), PathPoint(100f, 0f, 1f))),
            StrokeStyle(width = 8f),
        ).single()

        assertEquals(2, mapped.size, "every valid source vertex must map once despite sampled near-duplicates")
        assertEquals(0f, mapped.first().x, .01f)
        assertEquals(100f, mapped.last().x, .01f)
    }

    @Test fun degenerateSourceOrTargetReturnsEmptyInsteadOfInvalidCoordinates() {
        val degenerateSource = ArtBrushProfile("a", "line", listOf(listOf(Point(5f, 0f), Point(5f, 1f))))
        assertTrue(normalizeArtBrush(degenerateSource).shapes.isEmpty())

        val result = mapArtBrush(
            ArtBrushProfile("a", "ok", listOf(unitBox)),
            PathGeometry(listOf(PathPoint(2f, 2f, 1f), PathPoint(2f, 2f, 1f))),
            StrokeStyle(width = 10f),
        )
        assertTrue(result.isEmpty())
    }

    @Test fun artSvgParsingKeepsRawShapeTopologyWhileLegacyStampParserStaysNormalized() {
        val svg = """<svg><rect x="10" y="20" width="20" height="10"/><circle cx="50" cy="25" r="5"/></svg>"""
        val artShapes = parseSvgArtDocument(svg)!!
        val stampShapes = parseSvgDocument(svg)!!

        assertEquals(listOf(4, 24), artShapes.map { it.size })
        assertEquals(listOf(4, 24), stampShapes.map { it.size })
        assertEquals(10f, artShapes.flatten().minOf { it.x }, .01f)
        assertTrue(stampShapes.flatten().minOf { it.x } < 0f)
    }
}
