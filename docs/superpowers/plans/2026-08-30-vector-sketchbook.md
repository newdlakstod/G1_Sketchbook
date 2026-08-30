# 벡터(SVG) 스케치북 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 아이콘처럼 확대해도 안 깨지는, 처음부터 벡터로 그리는 새 스케치북 타입을 추가한다.

**Architecture:** 기존 `SketchbookRepository`(목록/페이지/백업 동기화 틀)를 재사용하되, 저장 포맷(획 점 목록의 JSON)과 그리기 View(`VectorBrushView`, 기존 `BrushView`와 완전히 분리)만 새로 만든다. 획은 "굵기가 변하는 리본" 채워진 다각형으로 표현하고, 이 다각형 계산은 Android 의존 없는 순수 함수로 뽑아 유닛 테스트한다.

**Tech Stack:** Kotlin, Jetpack Compose, `android.graphics.Canvas`/`Path`(커스텀 View), `kotlin.test`(유닛 테스트, 이 프로젝트는 org.json 등 Android 전용 클래스를 로컬 유닛 테스트에서 안전하게 못 써서 순수 Kotlin 문자열 처리로 JSON을 직접 만들고 읽는다).

## Global Constraints

- 스펙: `docs/superpowers/specs/2026-08-30-vector-sketchbook-design.md`
- 브랜치: `vector-drawing`(이미 생성됨, master에서 분기) — 이 계획의 모든 커밋은 이 브랜치에서.
- 벡터 브러시는 펜 하나만, 속도에 따라 굵기 변화. 수채화/연필/크레용/도형/채우기 없음.
- 캔버스는 항상 정사각(1024×1024px 고정), 사이즈 선택 단계 없음.
- 지우개는 픽셀이 아니라 획 단위(탭하면 그 획 전체 삭제).
- 되돌리기(undo)만 지원, redo 없음(스펙에 없음 — 추가하지 않는다).
- 벡터 + 공유(실시간 협업) 조합은 지원 안 함.
- 읽기모드(페이지 넘김 GL 애니메이션)는 벡터 스케치북에 안 띄운다.
- 색상은 기존 `SessionStore.favoriteColors`/브러시 팔레트를 재사용.
- 내보내기는 `.svg` 텍스트를 만들어 기존 `saveToGallery`와 같은 패턴(MediaStore)으로 저장.
- 백업 동기화는 기존 개인 스케치북과 동일하게 자동(항목 단위, last-write-wins) — `BackupSync.kt`/`BackupRepository.kt`/`BackupModels.kt`에 벡터 페이지 전용 경로 추가.

---

### Task 1: 벡터 데이터 모델 + JSON 직렬화

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorPage.kt`
- Test: `app/src/test/java/com/g1/sketchbook/vector/VectorPageTest.kt`

**Interfaces:**
- Produces: `VectorPoint(x: Float, y: Float, w: Float)`, `VectorStroke(color: Long, points: List<VectorPoint>)`, `VectorPage(strokes: List<VectorStroke>)`, `fun VectorPage.toJson(): String`, `fun vectorPageFromJson(json: String): VectorPage?`(파싱 실패 시 null).

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/g1/sketchbook/vector/VectorPageTest.kt`:

```kotlin
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorPageTest"`
Expected: FAIL to compile — `VectorPage`/`VectorPoint`/`VectorStroke`/`toJson`/`vectorPageFromJson` don't exist yet.

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/g1/sketchbook/vector/VectorPage.kt`:

```kotlin
package com.g1.sketchbook.vector

/** 벡터 스케치북 한 획의 점 하나 — [w]는 그 지점의 선 굵기(px), 그릴 때 속도로 계산해 점마다 같이
 *  저장한다(펜 속도-굵기 로직과 같은 느낌을 벡터로 재현하기 위함). */
data class VectorPoint(val x: Float, val y: Float, val w: Float)

/** 획 하나 — 단색(펜만 지원하므로 그라디언트 없음), 점 목록. */
data class VectorStroke(val color: Long, val points: List<VectorPoint>)

/** 벡터 스케치북 페이지 하나 = 획 목록 전체. */
data class VectorPage(val strokes: List<VectorStroke>)

/** 이 프로젝트의 로컬 유닛 테스트는 org.json 같은 Android 전용 클래스를 안전하게 못 쓴다(스텁이라
 *  실행 시 예외) — 그래서 이 파일이 직접 만들고 읽는 순수 Kotlin 문자열 처리로 직렬화한다. 우리가
 *  직접 만들고 읽는 고정된 스키마라 범용 JSON 파서가 필요 없다. */
fun VectorPage.toJson(): String {
    val sb = StringBuilder("{\"strokes\":[")
    strokes.forEachIndexed { si, s ->
        if (si > 0) sb.append(',')
        sb.append("{\"color\":").append(s.color).append(",\"points\":[")
        s.points.forEachIndexed { pi, p ->
            if (pi > 0) sb.append(',')
            sb.append("{\"x\":").append(p.x).append(",\"y\":").append(p.y).append(",\"w\":").append(p.w).append('}')
        }
        sb.append("]}")
    }
    sb.append("]}")
    return sb.toString()
}

private val strokeRegex = Regex("\\{\"color\":(-?\\d+),\"points\":\\[(.*?)]}")
private val pointRegex = Regex("\\{\"x\":(-?[0-9.eE+-]+),\"y\":(-?[0-9.eE+-]+),\"w\":(-?[0-9.eE+-]+)}")

/** [json]이 이 파일의 [VectorPage.toJson] 형식이 아니면(손상된 파일, 미래 포맷 등) null — 호출부는
 *  null을 "빈 페이지"로 취급한다(스펙의 에러 처리 방침). */
fun vectorPageFromJson(json: String): VectorPage? {
    if (!json.contains("\"strokes\"")) return null
    return runCatching {
        val strokes = strokeRegex.findAll(json).map { m ->
            val color = m.groupValues[1].toLong()
            val points = pointRegex.findAll(m.groupValues[2]).map { pm ->
                VectorPoint(pm.groupValues[1].toFloat(), pm.groupValues[2].toFloat(), pm.groupValues[3].toFloat())
            }.toList()
            VectorStroke(color, points)
        }.toList()
        VectorPage(strokes)
    }.getOrNull()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorPageTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorPage.kt app/src/test/java/com/g1/sketchbook/vector/VectorPageTest.kt
git commit -m "feat(vector): stroke/page data model with JSON round-trip"
```

---

### Task 2: 획 외곽선 기하 계산 + hit-test

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/StrokeGeometry.kt`
- Test: `app/src/test/java/com/g1/sketchbook/vector/StrokeGeometryTest.kt`

**Interfaces:**
- Consumes: `VectorPoint`(Task 1).
- Produces: `Point(x: Float, y: Float)`, `fun strokeOutline(points: List<VectorPoint>): List<Point>`, `fun pointInPolygon(x: Float, y: Float, polygon: List<Point>): Boolean`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/g1/sketchbook/vector/StrokeGeometryTest.kt`:

```kotlin
package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class StrokeGeometryTest {
    @Test fun straightHorizontalStrokeMakesARectangle() {
        val outline = strokeOutline(listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)))
        assertEquals(
            listOf(Point(0f, 2f), Point(10f, 2f), Point(10f, -2f), Point(0f, -2f)),
            outline,
        )
    }

    @Test fun singlePointStrokeHasNoOutline() {
        assertEquals(emptyList(), strokeOutline(listOf(VectorPoint(0f, 0f, 4f))))
    }

    @Test fun emptyStrokeHasNoOutline() {
        assertEquals(emptyList(), strokeOutline(emptyList()))
    }

    @Test fun pointInsideRectangleIsInside() {
        val square = listOf(Point(0f, 2f), Point(10f, 2f), Point(10f, -2f), Point(0f, -2f))
        assertTrue(pointInPolygon(5f, 0f, square))
    }

    @Test fun pointOutsideRectangleIsOutside() {
        val square = listOf(Point(0f, 2f), Point(10f, 2f), Point(10f, -2f), Point(0f, -2f))
        assertFalse(pointInPolygon(5f, 5f, square))
        assertFalse(pointInPolygon(-1f, 0f, square))
    }

    @Test fun degeneratePolygonNeverContainsAPoint() {
        assertFalse(pointInPolygon(0f, 0f, emptyList()))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.g1.sketchbook.vector.StrokeGeometryTest"`
Expected: FAIL to compile — `Point`/`strokeOutline`/`pointInPolygon` don't exist yet.

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/g1/sketchbook/vector/StrokeGeometry.kt`:

```kotlin
package com.g1.sketchbook.vector

import kotlin.math.sqrt

/** 2D 점 하나 — `android.graphics.PointF`를 안 쓰는 이유는 이 파일이 Android 의존 없는 순수
 *  Kotlin이라 로컬 유닛 테스트(JVM)에서 그대로 돌아가야 하기 때문. */
data class Point(val x: Float, val y: Float)

/** 획의 점 목록(중심선 + 지점별 굵기)을 "굵기가 변하는 리본" 모양의 채워진 다각형 외곽선으로
 *  바꾼다 — 각 점에서 진행 방향에 수직인 법선 방향으로 굵기/2만큼 오프셋한 좌표를 위/아래 경계로
 *  삼아, 위쪽 경계를 순서대로 + 아래쪽 경계를 역순으로 이어 닫힌 다각형을 만든다(획 하나 =
 *  다각형 하나, `stroke-width` 아님 — 그려질 때도, SVG로 내보낼 때도 이 모양 그대로 채워 그린다).
 *  점이 2개 미만이면(찍기만 하고 안 그은 경우) 그릴 게 없어 빈 목록. */
fun strokeOutline(points: List<VectorPoint>): List<Point> {
    if (points.size < 2) return emptyList()
    val left = ArrayList<Point>(points.size)
    val right = ArrayList<Point>(points.size)
    for (i in points.indices) {
        val p = points[i]
        val (dx, dy) = if (i < points.size - 1) {
            points[i + 1].x - p.x to points[i + 1].y - p.y
        } else {
            p.x - points[i - 1].x to p.y - points[i - 1].y
        }
        val len = sqrt(dx * dx + dy * dy)
        val (nx, ny) = if (len < 0.0001f) 0f to 0f else -dy / len to dx / len
        val half = p.w / 2f
        left.add(Point(p.x + nx * half, p.y + ny * half))
        right.add(Point(p.x - nx * half, p.y - ny * half))
    }
    return left + right.asReversed()
}

/** 표준 ray-casting 알고리즘 — [polygon] 안에 [x],[y]가 들어있는지. 지우개(획 단위 삭제)가 탭 지점이
 *  어느 획의 [strokeOutline] 안에 들어가는지 판정하는 데 쓴다. */
fun pointInPolygon(x: Float, y: Float, polygon: List<Point>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val pi = polygon[i]; val pj = polygon[j]
        if ((pi.y > y) != (pj.y > y)) {
            val xIntersect = pi.x + (y - pi.y) / (pj.y - pi.y) * (pj.x - pi.x)
            if (x < xIntersect) inside = !inside
        }
        j = i
    }
    return inside
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.g1.sketchbook.vector.StrokeGeometryTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/StrokeGeometry.kt app/src/test/java/com/g1/sketchbook/vector/StrokeGeometryTest.kt
git commit -m "feat(vector): stroke outline geometry + point-in-polygon hit test"
```

---

### Task 3: SVG 내보내기 직렬화

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorSvgExport.kt`
- Test: `app/src/test/java/com/g1/sketchbook/vector/VectorSvgExportTest.kt`

**Interfaces:**
- Consumes: `VectorPage`, `VectorStroke`, `VectorPoint`(Task 1), `strokeOutline`(Task 2).
- Produces: `fun vectorPageToSvg(page: VectorPage, sizePx: Int): String`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/g1/sketchbook/vector/VectorSvgExportTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorSvgExportTest"`
Expected: FAIL to compile — `vectorPageToSvg` doesn't exist yet.

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/g1/sketchbook/vector/VectorSvgExport.kt`:

```kotlin
package com.g1.sketchbook.vector

/** [page]를 아이콘용 SVG 문서 텍스트로 직렬화한다 — 획 하나 = `<path>` 하나(채워진 다각형,
 *  `stroke-width` 아님), 그린 순서 그대로 뒤에 쓰여서 겹친 획의 z-order도 그대로 유지된다. 색은
 *  [VectorStroke.color]의 ARGB Long에서 알파를 버리고 RGB만 "#rrggbb"로 쓴다 — 펜은 항상 불투명
 *  잉크라(기존 래스터 펜과 동일 전제) 알파 채널을 SVG로 따로 표현할 필요가 없다. */
fun vectorPageToSvg(page: VectorPage, sizePx: Int): String {
    val sb = StringBuilder()
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(sizePx)
        .append("\" height=\"").append(sizePx)
        .append("\" viewBox=\"0 0 ").append(sizePx).append(' ').append(sizePx).append("\">")
    for (stroke in page.strokes) {
        val outline = strokeOutline(stroke.points)
        if (outline.isEmpty()) continue
        sb.append("<path d=\"M")
        outline.forEachIndexed { i, p ->
            if (i == 0) sb.append(p.x).append(',').append(p.y)
            else sb.append(" L").append(p.x).append(',').append(p.y)
        }
        sb.append(" Z\" fill=\"").append(colorHex(stroke.color)).append("\"/>")
    }
    sb.append("</svg>")
    return sb.toString()
}

private fun colorHex(argb: Long): String {
    val rgb = argb.toInt() and 0xFFFFFF
    return "#" + rgb.toString(16).padStart(6, '0')
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorSvgExportTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorSvgExport.kt app/src/test/java/com/g1/sketchbook/vector/VectorSvgExportTest.kt
git commit -m "feat(vector): serialize a page to an SVG document"
```

---

### Task 4: Android 렌더러 (Canvas에 획 그리기, 썸네일 비트맵 생성)

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorRenderer.kt`

**Interfaces:**
- Consumes: `VectorPage`, `VectorStroke`(Task 1), `strokeOutline`(Task 2).
- Produces: `fun drawVectorPage(canvas: android.graphics.Canvas, page: VectorPage)`, `fun renderVectorPage(page: VectorPage, sizePx: Int): android.graphics.Bitmap`.

이 파일은 `android.graphics.*`(실기기/에뮬레이터에서만 동작)를 쓰므로 로컬 유닛 테스트 대상이
아니다 — Task 6(`VectorBrushView`)의 화면 확인으로 같이 검증한다.

- [ ] **Step 1: Write the implementation**

`app/src/main/java/com/g1/sketchbook/vector/VectorRenderer.kt`:

```kotlin
package com.g1.sketchbook.vector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/** [page]의 모든 획을 [canvas]에 그린다 — 획 하나 = [strokeOutline]으로 계산한 다각형 하나를 그
 *  획의 색으로 채워 그린다(그린 순서 그대로라 나중 획이 위에 덮인다). `VectorBrushView.onDraw`와
 *  썸네일 렌더링([renderVectorPage])이 이 함수 하나를 같이 쓴다 — 그리기 중인 화면과 저장되는
 *  썸네일이 항상 같은 방식으로 그려진다. */
fun drawVectorPage(canvas: Canvas, page: VectorPage) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    for (stroke in page.strokes) {
        val outline = strokeOutline(stroke.points)
        if (outline.isEmpty()) continue
        val path = Path()
        outline.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.close()
        paint.color = stroke.color.toInt()
        canvas.drawPath(path, paint)
    }
}

/** 목록/캐러셀 썸네일용 — [page]를 [sizePx]×[sizePx] 흰 배경 비트맵으로 한 번 렌더링한다(벡터
 *  페이지는 종이 질감이 없어 배경은 항상 흰색). */
fun renderVectorPage(page: VectorPage, sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    drawVectorPage(canvas, page)
    return bmp
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew.bat compileDebugKotlin --no-daemon -q`
Expected: no output (success).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorRenderer.kt
git commit -m "feat(vector): render a page (or single frame) onto an Android Canvas"
```

---

### Task 5: `SketchbookRepository` — 벡터 타입·페이지 저장/읽기

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt`

**Interfaces:**
- Consumes: `VectorPage`, `VectorPage.toJson()`, `vectorPageFromJson()`(Task 1), `renderVectorPage()`(Task 4).
- Produces:
  - `Sketchbook.vector: Boolean = false`(새 필드)
  - `SketchbookRepository.create(name, sizeKey, bgKey, shared = false, code = null, vector = false): Sketchbook`(새 파라미터)
  - `fun loadVectorPage(id: String, index: Int): VectorPage?`
  - `fun saveVectorPage(id: String, index: Int, page: VectorPage)`
  - `fun vectorPageUpdatedAt(id: String, index: Int): Long`
  - `fun setVectorPageUpdatedAt(id: String, index: Int, timestamp: Long)`
  - `Catalog.sizes`에 `CanvasSize("vector", "벡터", 1024, 1024)` 추가.

이 파일은 `Context`/파일 I/O에 의존해 로컬 유닛 테스트 대상이 아니다(이 프로젝트의 기존 테스트도
`SketchbookRepository`를 직접 테스트하지 않는다 — 순수 로직만 뽑아 테스트하는 기존 관례를 따른다).

- [ ] **Step 1: `Catalog.sizes`에 정사각 벡터 캔버스 크기 추가**

`app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt:25-32`의 `Catalog.sizes`
목록에 한 줄 추가:

```kotlin
    val sizes = listOf(
        CanvasSize("a5", "A5", 148, 210),
        CanvasSize("a4", "A4", 210, 297),
        CanvasSize("a3", "A3", 297, 420),
        CanvasSize("desktop", "데스크톱", 1920, 1080),
        CanvasSize("mobile", "모바일", 390, 844),
        CanvasSize("tablet", "태블릿", 810, 1080),
        CanvasSize("vector", "벡터", 1024, 1024),
    )
```

- [ ] **Step 2: `Sketchbook`에 `vector` 필드 추가**

`app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt:43-61`의 `Sketchbook` data
class에 필드 추가(기존 `coverVersion`/`updatedAt` 사이):

```kotlin
data class Sketchbook(
    val id: String,
    val name: String,
    val sizeKey: String,
    val bgKey: String,
    val createdAt: Long,
    val pageCount: Int,
    val fav: Boolean = false,
    val shared: Boolean = false,   // a "draw together" book, grouped separately
    val code: String? = null,      // invite/session code for shared books
    val coverColor: Long? = null,  // custom solid cover colour (ARGB); null = default yellow
    val coverVersion: Int = 0,
    /** 처음부터 벡터(획 점 목록)로 그리는 스케치북 — [shared]와 동시에 켜지지 않는다(생성 마법사가
     *  그 조합을 만들지 않음). true면 페이지는 `page_{i}.png`가 아니라 `page_{i}.json`에 저장되고,
     *  [SketchbookRepository.loadVectorPage]/[saveVectorPage]로 읽고 쓴다. */
    val vector: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val size get() = Catalog.size(sizeKey)
    val dateLabel: String get() = java.text.SimpleDateFormat(
        "yyyy.MM.dd",
        java.util.Locale.getDefault(),
    ).format(java.util.Date(createdAt))
}
```

- [ ] **Step 3: `list()`/`save()`/`create()`/`upsert()`에 `vector` 필드 읽고 쓰기**

`list()`(`SketchbookRepository.kt:80-93`)의 `Sketchbook(...)` 생성 호출에 `vector` 인자 추가:

```kotlin
    fun list(): List<Sketchbook> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Sketchbook(o.getString("id"), o.getString("name"), o.getString("size"),
                    o.getString("bg"), o.optLong("createdAt"), o.optInt("pages", 1), o.optBoolean("fav", false),
                    o.optBoolean("shared", false), o.optString("code", "").ifBlank { null },
                    o.optLong("coverColor", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }, o.optInt("coverVer", 0),
                    o.optBoolean("vector", false),
                    o.optLong("updatedAt", o.optLong("createdAt")))
            }.sortedWith(compareByDescending<Sketchbook> { it.fav }.thenByDescending { it.createdAt })
        }.getOrDefault(emptyList())
    }
```

`create()`(`SketchbookRepository.kt:97-106`)에 `vector` 파라미터 추가:

```kotlin
    fun create(name: String, sizeKey: String, bgKey: String, shared: Boolean = false, code: String? = null, vector: Boolean = false): Sketchbook {
        val fallback = if (shared) "공유 스케치북" else if (vector) "벡터 스케치북" else "우리 스케치북"
        val sb = Sketchbook(newId(), name.ifBlank { fallback }, sizeKey, bgKey, System.currentTimeMillis(), MAX_PAGES,
            fav = false, shared = shared, code = code, vector = vector)
        save(list() + sb)
        File(root, sb.id).mkdirs()
        return sb
    }
```

`save()`(`SketchbookRepository.kt:225-236`)의 `JSONObject` 직렬화에 `vector` 필드 추가:

```kotlin
    private fun save(books: List<Sketchbook>) {
        val arr = JSONArray()
        books.forEach {
            arr.put(JSONObject()
                .put("id", it.id).put("name", it.name).put("size", it.sizeKey)
                .put("bg", it.bgKey).put("createdAt", it.createdAt).put("pages", it.pageCount).put("fav", it.fav)
                .put("shared", it.shared).put("code", it.code ?: "")
                .put("coverColor", it.coverColor ?: Long.MIN_VALUE).put("coverVer", it.coverVersion)
                .put("vector", it.vector)
                .put("updatedAt", it.updatedAt))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
```

- [ ] **Step 4: 벡터 페이지 read/write 함수 추가**

`SketchbookRepository.kt`의 `pageFile`/`loadPage`/`savePage`/`pageUpdatedAt`/`setPageUpdatedAt`
바로 아래(132-162줄 부근)에 나란히 추가:

```kotlin
    private fun vectorPageFile(id: String, index: Int): File {
        val dir = File(root, id).apply { mkdirs() }
        return File(dir, "page_$index.json")
    }

    fun loadVectorPage(id: String, index: Int): VectorPage? {
        val f = vectorPageFile(id, index)
        if (!f.exists()) return null
        return vectorPageFromJson(f.readText())
    }

    /** JSON(진짜 저장 데이터)과 함께, 같은 인덱스의 `page_{i}.png`에 렌더링한 비트맵도 같이 써서
     *  [loadPageThumb]/[loadPage] 등 기존 PNG 전용 썸네일 경로가 벡터 페이지에도 그대로 통한다 —
     *  `PagePanel`(3열 페이지 목록) 등 다른 화면을 벡터 인지하게 고칠 필요가 없다. PNG는 순수
     *  캐시라 JSON만 진짜 상태다. */
    fun saveVectorPage(id: String, index: Int, page: VectorPage) {
        vectorPageFile(id, index).writeText(page.toJson())
        FileOutputStream(pageFile(id, index)).use {
            renderVectorPage(page, Catalog.size("vector").pxW()).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    fun vectorPageUpdatedAt(id: String, index: Int): Long = vectorPageFile(id, index).lastModified()

    fun setVectorPageUpdatedAt(id: String, index: Int, timestamp: Long) { vectorPageFile(id, index).setLastModified(timestamp) }
```

파일 상단 import에 추가:

```kotlin
import com.g1.sketchbook.vector.VectorPage
import com.g1.sketchbook.vector.renderVectorPage
import com.g1.sketchbook.vector.toJson
import com.g1.sketchbook.vector.vectorPageFromJson
```

- [ ] **Step 5: `upsert()`가 벡터 필드를 보존하는지 확인**

`upsert(book: Sketchbook)`(`SketchbookRepository.kt:122-130`)은 이미 `Sketchbook` 객체를 통째로
받아 저장하므로 수정 불필요 — `book.vector`가 그대로 들어간다. 확인만 하고 넘어간다.

- [ ] **Step 6: Compile-check**

Run: `./gradlew.bat compileDebugKotlin --no-daemon -q`
Expected: no output (success).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt
git commit -m "feat(sketchbook): add vector book type + vector page storage"
```

---

### Task 6: `VectorBrushView` — 그리기 엔진

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorBrushView.kt`

**Interfaces:**
- Consumes: `VectorPoint`, `VectorStroke`, `VectorPage`(Task 1), `strokeOutline`, `pointInPolygon`(Task 2), `drawVectorPage`(Task 4).
- Produces: `class VectorBrushView(context: Context) : View(context)` — public API:
  - `var color: Long`
  - `var strokeWidthDp: Float`(기본 8f)
  - `var erasing: Boolean`
  - `var onStrokeEnd: (() -> Unit)?`
  - `fun currentPage(): VectorPage`
  - `fun loadPage(page: VectorPage)`
  - `fun undo()`
  - `val canUndo: Boolean`

커스텀 Android View라 유닛 테스트 대상이 아니다(기하 계산은 이미 Task 2에서 순수 함수로 테스트
완료) — Task 7과 함께 에뮬레이터/실기기로 확인한다.

- [ ] **Step 1: Write the implementation**

`app/src/main/java/com/g1/sketchbook/vector/VectorBrushView.kt`:

```kotlin
package com.g1.sketchbook.vector

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 벡터 스케치북의 그리기 View — 펜 하나만, 속도에 따라 굵기가 변한다. 캔버스는 항상
 *  [CANVAS_SIZE]×[CANVAS_SIZE] 정사각 논리 좌표(스펙: 캔버스 비율 정사각 고정)이고, 화면에 보이는
 *  실제 View 크기에 맞춰 [scale]만큼 축소/확대해서 그린다 — 기존 `BrushView`와 달리 확대/축소·화면
 *  회전 대응이 없는(스펙에 없음) 훨씬 단순한 좌표계다. */
class VectorBrushView(context: Context) : View(context) {
    companion object { const val CANVAS_SIZE = 1024f }

    var color: Long = 0xFF1E2D4CL
    var strokeWidthDp: Float = 8f
    var erasing: Boolean = false
    var onStrokeEnd: (() -> Unit)? = null

    private val committed = mutableListOf<VectorStroke>()
    private var current: MutableList<VectorPoint>? = null
    private var lx = 0f; private var ly = 0f; private var lt = 0L
    private var smoothedSpeed = 0f

    val canUndo: Boolean get() = committed.isNotEmpty()

    fun currentPage(): VectorPage = VectorPage(committed.toList())

    fun loadPage(page: VectorPage) {
        committed.clear(); committed.addAll(page.strokes)
        current = null
        invalidate()
    }

    fun undo() {
        if (committed.isEmpty()) return
        committed.removeAt(committed.size - 1)
        invalidate()
        onStrokeEnd?.invoke()
    }

    /** View px -> 1024×1024 논리 좌표. */
    private fun scale(): Float = CANVAS_SIZE / max(1, width).toFloat()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val s = scale()
        val x = event.x * s
        val y = event.y * s
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (erasing) {
                    eraseAt(x, y)
                    return true
                }
                lx = x; ly = y; lt = SystemClock.uptimeMillis(); smoothedSpeed = 0f
                current = mutableListOf(VectorPoint(x, y, widthFor(0f)))
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (erasing) return true
                val cur = current ?: return true
                val now = SystemClock.uptimeMillis()
                val dd = hypot((x - lx).toDouble(), (y - ly).toDouble()).toFloat()
                val vRaw = dd / max(1L, now - lt)
                smoothedSpeed += (vRaw - smoothedSpeed) * 0.35f
                cur.add(VectorPoint(x, y, widthFor(smoothedSpeed)))
                lx = x; ly = y; lt = now
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (erasing) return true
                val cur = current
                current = null
                if (cur != null && cur.size >= 2) {
                    committed.add(VectorStroke(color, cur))
                    onStrokeEnd?.invoke()
                }
                invalidate()
            }
        }
        return true
    }

    /** 기존 래스터 펜(`BrushView.penSeg`)과 같은 느낌의 속도-굵기 곡선 — 빠를수록 가늘게, 최대
     *  65%까지 얇아진다. */
    private fun widthFor(speed: Float): Float {
        val dp = strokeWidthDp * (1f - min(0.65f, speed * 0.2f))
        return max(1f, dp) * resources.displayMetrics.density
    }

    private fun eraseAt(x: Float, y: Float) {
        for (i in committed.indices.reversed()) {
            val outline = strokeOutline(committed[i].points)
            if (outline.isNotEmpty() && pointInPolygon(x, y, outline)) {
                committed.removeAt(i)
                invalidate()
                onStrokeEnd?.invoke()
                return
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = scale()
        canvas.save()
        canvas.scale(1f / s, 1f / s)
        drawVectorPage(canvas, VectorPage(committed))
        current?.let { pts -> if (pts.size >= 2) drawVectorPage(canvas, VectorPage(listOf(VectorStroke(color, pts)))) }
        canvas.restore()
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew.bat compileDebugKotlin --no-daemon -q`
Expected: no output (success).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorBrushView.kt
git commit -m "feat(vector): VectorBrushView — speed-tapered pen, stroke-level eraser, undo"
```

---

### Task 7: `.svg` 내보내기 저장 (Gallery.kt 확장)

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/ui/Gallery.kt`

**Interfaces:**
- Produces: `fun saveSvgToGallery(ctx: Context, svg: String, name: String): String`

- [ ] **Step 1: Write the implementation**

`app/src/main/java/com/g1/sketchbook/ui/Gallery.kt` 끝에 추가(기존 `saveToGallery`와 나란히):

```kotlin
/** .svg 텍스트 한 장을 기기에 저장 — MediaStore.Images가 아니라 Downloads 컬렉션을 쓴다(SVG는
 *  "사진"이 아니라서 Images로 넣으면 갤러리 앱이 손상된 이미지로 취급한다). [saveToGallery]와
 *  같은 함수 시그니처·반환 문구 패턴을 그대로 따른다. */
fun saveSvgToGallery(ctx: Context, svg: String, name: String): String = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.svg")
            put(MediaStore.Downloads.MIME_TYPE, "image/svg+xml")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/G1Sketchbook")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(svg.toByteArray()) }
            "다운로드에 저장했어요 ✨"
        } else "저장 실패"
    } else {
        val dir = java.io.File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "G1Sketchbook").apply { mkdirs() }
        val f = java.io.File(dir, "$name.svg")
        f.writeText(svg)
        "저장됨: ${f.absolutePath}"
    }
} catch (e: Exception) { "저장 실패: ${e.message}" }
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew.bat compileDebugKotlin --no-daemon -q`
Expected: no output (success).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/ui/Gallery.kt
git commit -m "feat(gallery): save an SVG file to the Downloads collection"
```

---

### Task 8: `VectorCanvasScreen` — 화면 (툴바, 페이지 저장/전환, 내보내기)

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorCanvasScreen.kt`

**Interfaces:**
- Consumes: `VectorBrushView`(Task 6), `vectorPageToSvg`(Task 3), `saveSvgToGallery`(Task 7),
  `SketchbookRepository.loadVectorPage/saveVectorPage`(Task 5), `Sketchbook`, `MAX_PAGES`.
- Produces: `@Composable fun VectorCanvasScreen(bookId: String, book: Sketchbook, onBack: () -> Unit)`

- [ ] **Step 1: Write the implementation**

`app/src/main/java/com/g1/sketchbook/vector/VectorCanvasScreen.kt`:

```kotlin
package com.g1.sketchbook.vector

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.g1.sketchbook.data.SessionStore
import com.g1.sketchbook.sketchbook.MAX_PAGES
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.saveSvgToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 벡터 스케치북 편집화면 — 기존 `BrushControls`보다 훨씬 단순한 툴바(색상 스와치, 되돌리기,
 *  지우개) 하나만. 페이지 넘김 애니메이션(읽기모드)은 스펙에서 제외됐다 — 여기서 페이지 전환은
 *  그냥 이전/다음 화살표로 인덱스만 바꾼다. */
@Composable
fun VectorCanvasScreen(bookId: String, book: Sketchbook, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SketchbookRepository(context) }
    val session = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<VectorBrushView?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var color by remember { mutableStateOf(session.brushColor) }
    var erasing by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    val favorites = session.favoriteColors

    fun saveCurrent() {
        val v = view ?: return
        repo.saveVectorPage(bookId, page, v.currentPage())
    }
    fun goTo(newPage: Int) {
        if (newPage == page || newPage !in 0 until MAX_PAGES) return
        saveCurrent()
        page = newPage
        view?.loadPage(repo.loadVectorPage(bookId, newPage) ?: VectorPage(emptyList()))
        canUndo = view?.canUndo ?: false
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            factory = { ctx ->
                VectorBrushView(ctx).also { v ->
                    v.loadPage(repo.loadVectorPage(bookId, page) ?: VectorPage(emptyList()))
                    v.onStrokeEnd = { canUndo = v.canUndo; saveCurrent() }
                    view = v
                }
            },
            update = { v -> v.color = color; v.erasing = erasing },
        )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(16.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            favorites.forEach { swatch ->
                val selected = swatch == color && !erasing
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(swatch))
                        .border(if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .bounceClick { color = swatch; erasing = false },
                )
            }
            IconButton(enabled = canUndo, onClick = { view?.undo(); canUndo = view?.canUndo ?: false }) {
                Icon(Icons.Filled.Undo, "되돌리기")
            }
            IconButton(onClick = { erasing = !erasing }) {
                Icon(Icons.Filled.Delete, "지우개(획 삭제)", tint = if (erasing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = {
                val v = view ?: return@IconButton
                val svg = vectorPageToSvg(v.currentPage(), VectorBrushView.CANVAS_SIZE.toInt())
                scope.launch(Dispatchers.IO) {
                    val status = saveSvgToGallery(context, svg, "${book.name}_p${page}")
                    Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(com.g1.sketchbook.brush.IconImageSaveLine, "이미지로 저장")
            }
        }
        Row(Modifier.align(Alignment.TopCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(enabled = page > 0, onClick = { goTo(page - 1) }) { Icon(Icons.Filled.ChevronLeft, "이전 페이지") }
            IconButton(enabled = page < MAX_PAGES - 1, onClick = { goTo(page + 1) }) { Icon(Icons.Filled.ChevronRight, "다음 페이지") }
        }
        IconButton(onClick = { saveCurrent(); onBack() }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Icon(Icons.Filled.Close, "닫기")
        }
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew.bat compileDebugKotlin --no-daemon -q`
Expected: no output (success). If it fails on a missing import, add the missing
`androidx.compose.*`/`com.g1.sketchbook.*` import for the exact symbol the error names — every type
used above is already listed in the import block.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorCanvasScreen.kt
git commit -m "feat(vector): VectorCanvasScreen — toolbar, page save/switch, SVG export"
```

---

### Task 9: 스케치북 목록/생성 마법사 통합 + 진입 라우팅 + 배지

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookSync.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt`

**Interfaces:**
- Consumes: `Sketchbook.vector`(Task 5), `VectorCanvasScreen`(Task 8).

- [ ] **Step 1: `createSynced`에 `vector` 파라미터 추가**

기존 개인 스케치북 생성(`finishPersonal`)은 `repo.create()`를 바로 안 부르고
`createSynced`(`SketchbookSync.kt:16-20`)를 거쳐서 만들자마자 메타를 백업에도 올린다 — 벡터도
같은 경로를 타야 새 기기가 다음 동기화 때 이 책을 바로 본다:

```kotlin
fun createSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, name: String, sizeKey: String, bgKey: String, vector: Boolean = false): Sketchbook {
    val book = repo.create(name, sizeKey, bgKey, vector = vector)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) { backup.pushSketchbookMeta(uid, book) }
    return book
}
```

- [ ] **Step 2: `WType`에 `VECTOR` 추가**

`SketchbookScreens.kt:275`:

```kotlin
enum class WType { PERSONAL, SHARED_NEW, SHARED_JOIN, VECTOR }
```

- [ ] **Step 3: `CreateWizard`에 벡터 이름-입력 단계 추가**

`CreateWizard`(`SketchbookScreens.kt:279` 부근) 안에 `finishPersonal()` 옆에 나란히 함수 추가 —
`finishPersonal()`과 같은 이유로 `repo.create()`를 바로 안 부르고 (Step 1의) `createSynced`를
거친다(방금 만든 벡터 책의 메타가 바로 백업에 올라가야 다른 기기가 다음 동기화 때 본다):

```kotlin
    fun finishVector() {
        repo?.let { createSynced(scope, it, backup, myUid, name.ifBlank { "벡터 스케치북" }, "vector", "watercolor", vector = true) }?.let(onCreated)
    }
```

`when { ... }` 분기(`SketchbookScreens.kt:331` 부근)에 `type == WType.VECTOR` 케이스를
`type == WType.SHARED_NEW` 바로 아래 추가 — 이번 세션에 이미 배경색/입력란 모양을 개인 카드와
통일한 `SHARED_NEW` 다이얼로그와 완전히 같은 스타일(제목만 다름):

```kotlin
        type == WType.VECTOR -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(Dimens.Wizard.cardRadius),
                title = { Text("벡터 스케치북 이름") },
                text = {
                    OutlinedTextField(name, { name = it.take(20) }, singleLine = true,
                        placeholder = { Text("스케치북 이름") }, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth())
                },
                confirmButton = { TextButton(onClick = { finishVector() }) { Text("만들기") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
            )
        }
```

- [ ] **Step 4: 목록 화면에 벡터 생성 버튼 추가**

`SketchbookListScreen`(`SketchbookScreens.kt:467` 부근) 시그니처에 `onNewVector: () -> Unit = {}`
추가, `actions` 블록(509줄 부근, `else` 브랜치 — 개인 탭)에 버튼 추가:

```kotlin
            } else {
                IconButton(onClick = onNewVector) { Icon(com.g1.sketchbook.brush.IconImageSaveLine, "벡터 스케치북 만들기") }
                IconButton(onClick = onNewPersonal) { Icon(Icons.Filled.Add, "스케치북 추가") }
            }
```

`SketchbookTab`(`SketchbookListScreen`을 부르는 상위 컴포저블, `SketchbookScreens.kt:257-259`
부근)에서 `wizardType`을 넘기는 세 줄 옆에 나란히 추가:

```kotlin
        onNewPersonal = { wizardType = WType.PERSONAL; creating = true },
        onNewShared = { wizardType = WType.SHARED_NEW; creating = true },
        onJoinShared = { wizardType = WType.SHARED_JOIN; creating = true },
        onNewVector = { wizardType = WType.VECTOR; creating = true },
```

그리고 이 함수 호출부에서 `SketchbookListScreen(...)`에도 `onNewVector = onNewVector`를 같이
전달한다.

- [ ] **Step 5: 벡터 스케치북을 열 때 `VectorCanvasScreen`으로 라우팅**

`SketchbookCanvasScreen`(`SketchbookScreens.kt:1052` 부근)의 `shared` 체크 바로 아래에 추가:

```kotlin
    if (book.shared && book.code != null) {
        com.g1.sketchbook.share.SharedBookScreen(bookId, book.code, myUid, myName, startPage, onBack)
        return
    }
    if (book.vector) {
        com.g1.sketchbook.vector.VectorCanvasScreen(bookId, book, onBack)
        return
    }
```

- [ ] **Step 6: 표지에 벡터 배지 추가**

`MainScreen.kt:475`(홈 캐러셀, `book.shared`일 때 🤝 배지를 그리는 자리) 바로 옆에 벡터용 분기
추가:

```kotlin
                                if (book.shared) {
                                    Text("🤝", fontSize = 15.sp, modifier = Modifier.align(Alignment.TopEnd)
                                        .padding(8.dp).background(Color(0x33000000), CircleShape).padding(horizontal = 4.dp, vertical = 2.dp))
                                } else if (book.vector) {
                                    Text("✏️", fontSize = 15.sp, modifier = Modifier.align(Alignment.TopEnd)
                                        .padding(8.dp).background(Color(0x33000000), CircleShape).padding(horizontal = 4.dp, vertical = 2.dp))
                                }
```

`SketchbookScreens.kt:679`(목록 그리드 카드의 메타 텍스트 줄, `book.shared`면
`"🤝 코드 · 날짜"`를 보여주는 자리)도 같은 방식으로 확장:

```kotlin
                val meta = when {
                    book.shared && book.code != null -> "🤝 ${book.code} · ${book.dateLabel}"
                    book.vector -> "✏️ 벡터 · ${book.dateLabel}"
                    else -> book.dateLabel
                }
```

- [ ] **Step 7: Compile-check**

Run: `./gradlew.bat compileDebugKotlin --no-daemon -q`
Expected: no output (success).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookSync.kt app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt
git commit -m "feat(sketchbook): wire vector type into wizard, list, routing, badges"
```

---

### Task 10: 백업 동기화 — 벡터 페이지

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt`

**Interfaces:**
- Consumes: `Sketchbook.vector`, `SketchbookRepository.loadVectorPage/saveVectorPage/vectorPageUpdatedAt/setVectorPageUpdatedAt`(Task 5), `VectorPage.toJson()`, `vectorPageFromJson()`(Task 1).

- [ ] **Step 1: `RemoteSketchbook`에 벡터 필드 추가**

`BackupModels.kt`의 `RemoteSketchbook` data class(5-13줄)에 필드 추가:

```kotlin
data class RemoteSketchbook(
    val id: String, val name: String, val sizeKey: String, val bgKey: String,
    val createdAt: Long, val pageCount: Int, val fav: Boolean, val coverColor: Long?,
    val updatedAt: Long, val deleted: Boolean,
    val coverBase64: String?, val coverUpdatedAt: Long?,
    val coverRemoved: Boolean = false,
    val pages: Map<Int, Pair<Long, String>>, // index -> (updatedAt, base64)
    /** 벡터 스케치북 여부·페이지(텍스트 그대로, base64 인코딩 없음) — [pages]와 상호 배타적으로
     *  쓰인다: vector=true인 책은 항상 pages가 비어 있고 vectorPages만 쓴다. */
    val vector: Boolean = false,
    val vectorPages: Map<Int, Pair<Long, String>> = emptyMap(), // index -> (updatedAt, strokes json)
)
```

- [ ] **Step 2: `pushSketchbookMeta`/`pushVectorPage`/`pullAll` 확장**

`BackupRepository.kt`의 `pushSketchbookMeta`(41-49줄)에 `vector` 필드 추가:

```kotlin
    fun pushSketchbookMeta(uid: String, book: Sketchbook) {
        root.child(uid).child("sketchbooks").child(book.id).child("meta").setValue(
            mapOf(
                "name" to book.name, "sizeKey" to book.sizeKey, "bgKey" to book.bgKey,
                "createdAt" to book.createdAt, "pageCount" to book.pageCount, "fav" to book.fav,
                "coverColor" to (book.coverColor ?: Long.MIN_VALUE), "updatedAt" to book.updatedAt,
                "vector" to book.vector,
            ),
        )
    }
```

`pushSketchbookPage` 바로 아래(67줄 부근)에 벡터 전용 push 함수 추가:

```kotlin
    /** 벡터 페이지는 이미 텍스트(JSON)라 base64 인코딩 없이 그대로 올린다 — 이미지보다 훨씬
     *  가볍다. [pushSketchbookPage]와 나란한 벡터 전용 경로. */
    fun pushVectorPage(uid: String, bookId: String, index: Int, strokesJson: String, updatedAt: Long) {
        root.child(uid).child("sketchbooks").child(bookId).child("vectorPages").child(index.toString())
            .setValue(mapOf("updatedAt" to updatedAt, "strokes" to strokesJson))
    }
```

`pullAll`의 `sketchbooks` 파싱(122-147줄 부근) — `RemoteSketchbook(...)` 생성 호출에
`vector`/`vectorPages` 읽어오기 추가:

```kotlin
        val sketchbooks = snap.child("sketchbooks").children.mapNotNull { c ->
            val id = c.key ?: return@mapNotNull null
            val meta = c.child("meta")
            val pages = c.child("pages").children.mapNotNull { pc ->
                val idx = pc.key?.toIntOrNull() ?: return@mapNotNull null
                val updatedAt = pc.child("updatedAt").getValue(Long::class.java) ?: return@mapNotNull null
                val image = pc.child("image").getValue(String::class.java) ?: return@mapNotNull null
                idx to (updatedAt to image)
            }.toMap()
            val vectorPages = c.child("vectorPages").children.mapNotNull { pc ->
                val idx = pc.key?.toIntOrNull() ?: return@mapNotNull null
                val updatedAt = pc.child("updatedAt").getValue(Long::class.java) ?: return@mapNotNull null
                val strokes = pc.child("strokes").getValue(String::class.java) ?: return@mapNotNull null
                idx to (updatedAt to strokes)
            }.toMap()
            RemoteSketchbook(
                id = id,
                name = meta.child("name").getValue(String::class.java) ?: "",
                sizeKey = meta.child("sizeKey").getValue(String::class.java) ?: "a4",
                bgKey = meta.child("bgKey").getValue(String::class.java) ?: "watercolor",
                createdAt = meta.child("createdAt").getValue(Long::class.java) ?: 0L,
                pageCount = meta.child("pageCount").getValue(Int::class.java) ?: MAX_PAGES,
                fav = meta.child("fav").getValue(Boolean::class.java) ?: false,
                coverColor = meta.child("coverColor").getValue(Long::class.java)?.takeIf { it != Long.MIN_VALUE },
                updatedAt = meta.child("updatedAt").getValue(Long::class.java) ?: 0L,
                deleted = c.child("deleted").getValue(Boolean::class.java) ?: false,
                coverBase64 = c.child("cover").child("image").getValue(String::class.java),
                coverUpdatedAt = c.child("cover").child("updatedAt").getValue(Long::class.java),
                coverRemoved = c.child("cover").child("removed").getValue(Boolean::class.java) ?: false,
                pages = pages,
                vector = meta.child("vector").getValue(Boolean::class.java) ?: false,
                vectorPages = vectorPages,
            )
        }
```

- [ ] **Step 3: `reconcileSketchbooks`에 벡터 페이지 동기화 분기 추가**

`BackupSync.kt`의 `reconcileSketchbooks`(54-100줄):

메타 PULL 분기(66줄)에 `vector` 전달:

```kotlin
            SyncAction.PULL -> if (r != null) {
                repo.upsert(Sketchbook(id, r.name, r.sizeKey, r.bgKey, r.createdAt, r.pageCount, r.fav,
                    coverColor = r.coverColor, vector = r.vector, updatedAt = r.updatedAt))
            }
```

페이지 동기화 루프(87-98줄)를 `book.vector` 여부로 분기:

```kotlin
        val isVector = l?.vector == true || r?.vector == true
        val pageCount = maxOf(l?.pageCount ?: 0, r?.pageCount ?: MAX_PAGES)
        for (index in 0 until pageCount) {
            if (isVector) {
                val localAt = repo.vectorPageUpdatedAt(id, index).takeIf { it > 0L }
                val remotePage = r?.vectorPages?.get(index)
                when (decideSyncAction(localAt, remotePage?.first)) {
                    SyncAction.PULL -> if (remotePage != null) {
                        vectorPageFromJson(remotePage.second)?.let {
                            repo.saveVectorPage(id, index, it)
                            repo.setVectorPageUpdatedAt(id, index, remotePage.first)
                        }
                    }
                    SyncAction.PUSH -> repo.loadVectorPage(id, index)?.let {
                        backup.pushVectorPage(uid, id, index, it.toJson(), repo.vectorPageUpdatedAt(id, index))
                    }
                    else -> {}
                }
            } else {
                val localPageAt = repo.pageUpdatedAt(id, index).takeIf { it > 0L }
                val remotePage = r?.pages?.get(index)
                when (decideSyncAction(localPageAt, remotePage?.first)) {
                    SyncAction.PULL -> if (remotePage != null) {
                        backup.decodeImage(remotePage.second)?.let { repo.savePage(id, index, it); repo.setPageUpdatedAt(id, index, remotePage.first) }
                    }
                    SyncAction.PUSH -> repo.loadPage(id, index)?.let { backup.pushSketchbookPage(uid, id, index, it, repo.pageUpdatedAt(id, index)) }
                    else -> {}
                }
            }
        }
```

파일 상단 import에 추가:

```kotlin
import com.g1.sketchbook.vector.toJson
import com.g1.sketchbook.vector.vectorPageFromJson
```

- [ ] **Step 4: Compile-check**

Run: `./gradlew.bat compileDebugKotlin --no-daemon -q`
Expected: no output (success).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt
git commit -m "feat(backup): sync vector pages as text (no base64), plus vector meta flag"
```

---

## 스코프 밖 (스펙과 동일, 이 계획에도 없음)

- 벡터 + 공유(실시간 협업) 조합.
- 읽기모드(페이지 넘김 애니메이션).
- 펜 외 다른 벡터 브러시(도형, 채우기, 텍스트 등).
- 기존 래스터 스케치북 ↔ 벡터 변환.
- SVG 가져오기(외부 SVG 파일을 불러와 편집).
- redo(스펙은 undo만 요구).
