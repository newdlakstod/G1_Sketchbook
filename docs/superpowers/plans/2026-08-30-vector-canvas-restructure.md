# 벡터 캔버스 구조 개편 (페이지 → 단일 캔버스) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 벡터 스케치북을 "여러 페이지"에서 "책 한 권 = 캔버스 한 장(생성 시 무한 또는 커스텀 크기 선택)"으로 재구조화하고, 두 손가락 핀치/패닝으로 확대·이동하며, "전체"/"라쏘 선택" 두 방식으로 SVG를 내보낼 수 있게 한다.

**Architecture:** 기존 래스터 브러시(`BrushView.kt`)가 이미 쓰고 있는 Matrix 기반 팬/줌 패턴(`disp`/`inv`/`userM`/`userScale`/`pinching`)을 `VectorBrushView`에 그대로 이식한다. 페이지 인덱스를 없애는 대신 책 하나당 캔버스(획 목록) 하나로 저장 구조를 단순화하고, Firebase 동기화도 인덱스 맵 대신 단일 필드로 바꾼다. 미리보기(썸네일)·SVG 내보내기는 새로 추가하는 순수 Kotlin 함수 `contentBounds`/`pointsBounds`(둘 다 `StrokeGeometry.kt`, 유닛 테스트 가능)가 계산한 경계상자를 기준으로 렌더링한다.

**Tech Stack:** Kotlin, Jetpack Compose, Android View(커스텀 `VectorBrushView`), Firebase Realtime Database, kotlin.test(JVM 유닛 테스트).

## Global Constraints

- 이 계획은 오직 캔버스 구조 개편만 다룬다 — 브러시 굵기 슬라이더, 실시간 스무딩, 선 선택 후 굵기 조절, 폐곡선 면칠하기는 다루지 않는다(다음 스펙).
- 벡터 스케치북은 아직 배포 전(에뮬레이터 테스트만)이라 기존 테스트 데이터 마이그레이션은 하지 않는다.
- 확대 배율 범위는 기존 래스터 캔버스와 동일한 `Dimens.Canvas.minZoom`(0.3)~`maxZoom`(5)을 그대로 재사용한다.
- 커스텀 캔버스 크기 직접 입력은 64~4096px로 clamp한다.
- 이 프로젝트는 Robolectric/모킹이 없어 `android.*` 의존 클래스는 로컬 유닛 테스트(JVM)에서 스텁이 던진다 — `SketchbookRepository`/`BackupRepository`/`BackupSync`/Compose 화면들은 유닛 테스트 대상이 아니다(컴파일 확인 + 수동 확인만). 순수 Kotlin 파일(`StrokeGeometry.kt`, `VectorSvgExport.kt`)만 TDD 대상.
- 커밋 메시지는 이 저장소의 기존 스타일(`fix:`/`feat:`/`refactor:` 등 conventional 접두사)을 따른다.

---

### Task 1: StrokeGeometry — 경계상자·라쏘 판정 순수 함수

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/StrokeGeometry.kt`
- Test: `app/src/test/java/com/g1/sketchbook/vector/StrokeGeometryTest.kt`

**Interfaces:**
- Produces: `data class Bounds(minX: Float, minY: Float, maxX: Float, maxY: Float)` (`width`/`height` 게터 포함), `fun contentBounds(strokes: List<VectorStroke>): Bounds?`, `fun pointsBounds(points: List<Point>): Bounds?`, `fun strokeTouchesLasso(stroke: VectorStroke, lasso: List<Point>): Boolean` — 이후 모든 태스크가 이 네 개를 가져다 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/g1/sketchbook/vector/StrokeGeometryTest.kt` 맨 끝(마지막 `}` 앞)에 추가:

```kotlin
    @Test fun contentBoundsOfEmptyStrokesIsNull() {
        assertEquals(null, contentBounds(emptyList()))
    }

    @Test fun contentBoundsWrapsAllPointsWithHalfWidthPadding() {
        val strokes = listOf(
            VectorStroke(0L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f))),
            VectorStroke(0L, listOf(VectorPoint(5f, -20f, 2f), VectorPoint(5f, 20f, 2f))),
        )
        val bounds = contentBounds(strokes)!!
        assertEquals(-2f, bounds.minX); assertEquals(-21f, bounds.minY)
        assertEquals(12f, bounds.maxX); assertEquals(21f, bounds.maxY)
    }

    @Test fun pointsBoundsOfEmptyListIsNull() {
        assertEquals(null, pointsBounds(emptyList()))
    }

    @Test fun pointsBoundsWrapsAllPoints() {
        val bounds = pointsBounds(listOf(Point(3f, 5f), Point(-1f, 8f), Point(2f, -4f)))!!
        assertEquals(-1f, bounds.minX); assertEquals(-4f, bounds.minY)
        assertEquals(3f, bounds.maxX); assertEquals(8f, bounds.maxY)
    }

    @Test fun strokeTouchingLassoIsSelected() {
        val lasso = listOf(Point(0f, -5f), Point(10f, -5f), Point(10f, 5f), Point(0f, 5f))
        val stroke = VectorStroke(0L, listOf(VectorPoint(5f, 0f, 2f), VectorPoint(50f, 50f, 2f)))
        assertTrue(strokeTouchesLasso(stroke, lasso))
    }

    @Test fun strokeEntirelyOutsideLassoIsNotSelected() {
        val lasso = listOf(Point(0f, -5f), Point(10f, -5f), Point(10f, 5f), Point(0f, 5f))
        val stroke = VectorStroke(0L, listOf(VectorPoint(50f, 50f, 2f), VectorPoint(60f, 60f, 2f)))
        assertFalse(strokeTouchesLasso(stroke, lasso))
    }
```

**Step 1의 나머지**: `StrokeGeometryTest.kt` 파일 맨 위 import 목록에 이미 `assertTrue`/`assertFalse`/`assertEquals`가 있는지 확인 — 지금 파일은 이미 셋 다 임포트돼 있다(그대로 둠).

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.StrokeGeometryTest" --no-daemon`
Expected: FAIL — `contentBounds`/`pointsBounds`/`strokeTouchesLasso`/`Bounds`가 아직 없어서 컴파일 에러.

- [ ] **Step 3: 최소 구현 작성**

`StrokeGeometry.kt` 맨 끝(마지막 `}` 뒤)에 추가:

```kotlin

/** [strokes] 전체를 감싸는 최소 사각형(각 점의 굵기 절반만큼 바깥으로 확장) — 획이 하나도 없으면
 *  null. 벡터 캔버스 미리보기/썸네일과 "전체" 내보내기(무한 캔버스)가 이 경계상자를 기준으로 삼는다. */
data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

fun contentBounds(strokes: List<VectorStroke>): Bounds? {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (stroke in strokes) {
        for (p in stroke.points) {
            val half = p.w / 2f
            if (p.x - half < minX) minX = p.x - half
            if (p.y - half < minY) minY = p.y - half
            if (p.x + half > maxX) maxX = p.x + half
            if (p.y + half > maxY) maxY = p.y + half
        }
    }
    return if (minX > maxX) null else Bounds(minX, minY, maxX, maxY)
}

/** [points] 전체를 감싸는 최소 사각형 — 라쏘 폴리곤 자체의 내보내기 영역을 계산하는 데 쓴다
 *  ([contentBounds]와 달리 폭 개념이 없는 순수 점 목록용). */
fun pointsBounds(points: List<Point>): Bounds? {
    if (points.isEmpty()) return null
    var minX = points[0].x; var minY = points[0].y
    var maxX = points[0].x; var maxY = points[0].y
    for (p in points) {
        if (p.x < minX) minX = p.x; if (p.y < minY) minY = p.y
        if (p.x > maxX) maxX = p.x; if (p.y > maxY) maxY = p.y
    }
    return Bounds(minX, minY, maxX, maxY)
}

/** 라쏘 다각형 [lasso] 안에 [stroke]의 점이 하나라도 들어가면 선택된 것으로 본다 — 손가락으로
 *  정확히 완전히 두르기는 어려우니, 살짝 스치기만 해도 선택되는 관대한 판정. */
fun strokeTouchesLasso(stroke: VectorStroke, lasso: List<Point>): Boolean =
    stroke.points.any { pointInPolygon(it.x, it.y, lasso) }
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.StrokeGeometryTest" --no-daemon`
Expected: PASS, 11개 테스트(기존 6 + 신규 5... 실제로는 6개 추가) 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/StrokeGeometry.kt app/src/test/java/com/g1/sketchbook/vector/StrokeGeometryTest.kt
git commit -m "feat(vector): add content/points bounds and lasso hit-test"
```

---

### Task 2: VectorSvgExport — 경계상자 기반 내보내기로 재작성

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorSvgExport.kt`
- Test: `app/src/test/java/com/g1/sketchbook/vector/VectorSvgExportTest.kt`

**Interfaces:**
- Consumes: `Bounds` (Task 1).
- Produces: `fun vectorPageToSvg(page: VectorPage, region: Bounds): String` — 기존 `(page, sizePx: Int)` 시그니처를 **대체**한다(사이즈 하나의 정사각형 가정을 버림). 이후 `SketchbookRepository`(썸네일과는 무관, 저장은 안 씀)와 `VectorCanvasScreen`의 내보내기 버튼이 이 새 시그니처로 호출한다.

- [ ] **Step 1: 실패하는 테스트로 교체**

`VectorSvgExportTest.kt` 전체를 이걸로 교체:

```kotlin
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
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorSvgExportTest" --no-daemon`
Expected: FAIL — `vectorPageToSvg(page, sizePx: Int)`가 `Bounds`를 받지 않아 컴파일 에러.

- [ ] **Step 3: 구현 교체**

`VectorSvgExport.kt` 전체를 이걸로 교체:

```kotlin
package com.g1.sketchbook.vector

/** [page]에서 [region]에 해당하는 부분만 아이콘용 SVG 문서 텍스트로 직렬화한다 — 획 하나 =
 *  `<path>` 하나(채워진 다각형, `stroke-width` 아님), 그린 순서 그대로 유지. viewBox는 항상
 *  "0 0 width height"로 시작하도록 [region]만큼 좌표를 평행이동한다(내보낸 SVG가 원본 캔버스
 *  좌표계를 몰라도 되게). 점이 하나도 [region] 안에 없는 획은 통째로 건너뛴다 — 부분적으로만
 *  겹치는 획은 지금은 잘라내지 않고 그대로 포함한다(잘라내기는 이 스펙 범위 밖). 색은
 *  [VectorStroke.color]의 ARGB Long에서 알파를 버리고 RGB만 "#rrggbb"로 쓴다(펜은 항상 불투명). */
fun vectorPageToSvg(page: VectorPage, region: Bounds): String {
    val w = region.width; val h = region.height
    val sb = StringBuilder()
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(w)
        .append("\" height=\"").append(h)
        .append("\" viewBox=\"0 0 ").append(w).append(' ').append(h).append("\">")
    for (stroke in page.strokes) {
        val outline = strokeOutline(stroke.points)
        if (outline.isEmpty()) continue
        val touchesRegion = outline.any { it.x >= region.minX && it.x <= region.maxX && it.y >= region.minY && it.y <= region.maxY }
        if (!touchesRegion) continue
        sb.append("<path d=\"M")
        outline.forEachIndexed { i, p ->
            val x = p.x - region.minX; val y = p.y - region.minY
            if (i == 0) sb.append(x).append(',').append(y)
            else sb.append(" L").append(x).append(',').append(y)
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

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorSvgExportTest" --no-daemon`
Expected: PASS, 5개 테스트 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorSvgExport.kt app/src/test/java/com/g1/sketchbook/vector/VectorSvgExportTest.kt
git commit -m "refactor(vector): svg export takes an arbitrary region instead of a fixed square"
```

---

### Task 3: VectorRenderer — 그려진 내용 경계상자 기준 미리보기 렌더링

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorRenderer.kt`

**Interfaces:**
- Consumes: `contentBounds` (Task 1).
- Produces: `fun renderVectorPage(page: VectorPage, sizePx: Int): Bitmap` — 시그니처는 그대로, 내부 동작만 "정사각 캔버스 전체"에서 "그려진 내용 경계상자(8% 여백) 기준 fit"으로 바뀐다. Task 5(`SketchbookRepository`)가 그대로 이 시그니처로 호출한다.

이 파일은 유닛 테스트 대상이 아니다(android.graphics 의존, Global Constraints 참고) — 컴파일 확인 + Task 9까지 끝난 뒤 에뮬레이터에서 수동 확인.

- [ ] **Step 1: 구현 교체**

`VectorRenderer.kt`의 `renderVectorPage` 함수만 교체(파일 나머지 — `drawVectorPage` — 는 그대로 둔다):

```kotlin
package com.g1.sketchbook.vector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.min

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

private const val PREVIEW_PADDING_RATIO = 0.08f

/** 목록/캐러셀 미리보기용 — [page]를 [sizePx]×[sizePx] 흰 배경 비트맵으로 렌더링한다. 캔버스가
 *  무한이든 커스텀이든 상관없이, 항상 [contentBounds]로 계산한 "그려진 내용의 경계상자"에 8% 여백을
 *  더해 정사각형 안에 맞춘다(letterbox, 가운데 정렬) — 캔버스 자체의 크기/경계는 이 렌더링과 무관.
 *  빈 캔버스(경계상자 없음)는 흰 배경만 있는 빈 비트맵으로 폴백. */
fun renderVectorPage(page: VectorPage, sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val bounds = contentBounds(page.strokes) ?: return bmp
    val padX = bounds.width * PREVIEW_PADDING_RATIO
    val padY = bounds.height * PREVIEW_PADDING_RATIO
    val left = bounds.minX - padX; val top = bounds.minY - padY
    val w = bounds.width + padX * 2f; val h = bounds.height + padY * 2f
    val scale = min(sizePx / w, sizePx / h)
    canvas.save()
    canvas.translate((sizePx - w * scale) / 2f, (sizePx - h * scale) / 2f)
    canvas.scale(scale, scale)
    canvas.translate(-left, -top)
    drawVectorPage(canvas, page)
    canvas.restore()
    return bmp
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 에러 없이 조용히 끝남.

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorRenderer.kt
git commit -m "feat(vector): render previews from content bounds instead of a fixed square canvas"
```

---

### Task 4: Sketchbook 데이터 모델 — 무한/커스텀 캔버스 필드

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt`

**Interfaces:**
- Produces: `Sketchbook.vectorInfinite: Boolean`, `Sketchbook.vectorCanvasW: Int?`, `Sketchbook.vectorCanvasH: Int?`; `SketchbookRepository.create(..., vectorInfinite: Boolean = false, vectorCanvasW: Int? = null, vectorCanvasH: Int? = null): Sketchbook`. Task 6(`SketchbookSync.createSynced`)·Task 7(`BackupSync`)·Task 10(마법사)이 이 필드/파라미터를 그대로 쓴다.

이 파일은 `android.content.Context`에 의존해 유닛 테스트 대상이 아니다 — 컴파일 확인만.

- [ ] **Step 1: `Sketchbook` 데이터 클래스에 필드 추가**

`SketchbookRepository.kt`의 `Sketchbook` 클래스(48번째 줄 부근) — `val vector: Boolean = false,` 줄 바로 뒤에 추가:

```kotlin
    /** 무한 캔버스 여부(벡터 책 전용, [vector]=true일 때만 의미 있음) — true면 [vectorCanvasW]/
     *  [vectorCanvasH]는 항상 null. 페이지 개념이 없는 벡터 책 하나가 곧 캔버스 한 장이다. */
    val vectorInfinite: Boolean = false,
    /** 커스텀(고정) 캔버스의 논리 가로·세로 — [vectorInfinite]=false일 때만 값이 있음. */
    val vectorCanvasW: Int? = null,
    val vectorCanvasH: Int? = null,
```

- [ ] **Step 2: `Catalog.sizes`에서 `"vector"` 항목 제거**

`Catalog.sizes` 목록(30번째 줄 부근)에서 `CanvasSize("vector", "벡터", 1024, 1024),` 줄을 삭제한다. 벡터 책은 더 이상 `sizeKey`로 캔버스 크기를 표현하지 않는다(대신 `vectorInfinite`/`vectorCanvasW`/`vectorCanvasH`).

- [ ] **Step 3: `list()`의 JSON 읽기에 새 필드 추가**

`list()` 안의 `Sketchbook(...)` 생성 호출(95~100번째 줄 부근)을 이걸로 교체:

```kotlin
                Sketchbook(o.getString("id"), o.getString("name"), o.getString("size"),
                    o.getString("bg"), o.optLong("createdAt"), o.optInt("pages", 1), o.optBoolean("fav", false),
                    o.optBoolean("shared", false), o.optString("code", "").ifBlank { null },
                    o.optLong("coverColor", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }, o.optInt("coverVer", 0),
                    o.optBoolean("vector", false),
                    vectorInfinite = o.optBoolean("vectorInfinite", false),
                    vectorCanvasW = o.optInt("vectorCanvasW", -1).takeIf { it > 0 },
                    vectorCanvasH = o.optInt("vectorCanvasH", -1).takeIf { it > 0 },
                    updatedAt = o.optLong("updatedAt", o.optLong("createdAt")))
```

- [ ] **Step 4: `create()` 시그니처 확장**

`create()` 함수(107번째 줄 부근)를 이걸로 교체:

```kotlin
    fun create(
        name: String, sizeKey: String, bgKey: String, shared: Boolean = false, code: String? = null,
        vector: Boolean = false, vectorInfinite: Boolean = false, vectorCanvasW: Int? = null, vectorCanvasH: Int? = null,
    ): Sketchbook {
        val fallback = if (shared) "공유 스케치북" else if (vector) "벡터 스케치북" else "우리 스케치북"
        // A sketchbook is a fixed MAX_PAGES-page notebook from the start (like a physical one) —
        // pages aren't added/removed later, just navigated. Blank pages are lazy (no file until drawn on).
        // (벡터 책은 pageCount를 안 쓰지만, 필드 자체는 다른 책들과 공유하는 구조라 그대로 채워 넣는다.)
        val sb = Sketchbook(newId(), name.ifBlank { fallback }, sizeKey, bgKey, System.currentTimeMillis(), MAX_PAGES,
            fav = false, shared = shared, code = code, vector = vector,
            vectorInfinite = vectorInfinite, vectorCanvasW = vectorCanvasW, vectorCanvasH = vectorCanvasH)
        save(list() + sb)
        File(root, sb.id).mkdirs()
        return sb
    }
```

- [ ] **Step 5: `save()`의 JSON 쓰기에 새 필드 추가**

`save()` 함수(261번째 줄 부근)의 `JSONObject()` 체인을 이걸로 교체:

```kotlin
            arr.put(JSONObject()
                .put("id", it.id).put("name", it.name).put("size", it.sizeKey)
                .put("bg", it.bgKey).put("createdAt", it.createdAt).put("pages", it.pageCount).put("fav", it.fav)
                .put("shared", it.shared).put("code", it.code ?: "")
                .put("coverColor", it.coverColor ?: Long.MIN_VALUE).put("coverVer", it.coverVersion)
                .put("vector", it.vector)
                .put("vectorInfinite", it.vectorInfinite)
                .put("vectorCanvasW", it.vectorCanvasW ?: -1).put("vectorCanvasH", it.vectorCanvasH ?: -1)
                .put("updatedAt", it.updatedAt))
```

- [ ] **Step 6: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 에러 없이 조용히 끝남 — 단, `saveVectorPage(id, index, page)`처럼 이미 삭제한 `"vector"` 카탈로그 항목을 쓰던 다른 파일들은 이 태스크 이후 아직 안 고쳤으니 여기서 컴파일 에러가 날 수 있다. 그 경우 Task 5가 그 파일들을 고치므로 이 Step은 "이 파일 자체에 새 문법 오류가 없는지"만 확인하는 용도로, 전체 빌드 에러는 무시하고 다음 태스크로 넘어간다.

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt
git commit -m "feat(sketchbook): add infinite/custom vector canvas fields, drop fixed vector catalog size"
```

---

### Task 5: SketchbookRepository — 단일 캔버스 저장/로드

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt`

**Interfaces:**
- Consumes: `renderVectorPage` (Task 3, 시그니처 그대로).
- Produces: `loadVectorCanvas(id: String): VectorPage?`, `saveVectorCanvas(id: String, page: VectorPage)`, `loadVectorPreview(id: String): Bitmap?`, `vectorCanvasUpdatedAt(id: String): Long`, `setVectorCanvasUpdatedAt(id: String, timestamp: Long)` — 기존 `loadVectorPage`/`saveVectorPage`/`vectorPageUpdatedAt`/`setVectorPageUpdatedAt`(인덱스 있음)를 **대체**한다. Task 6(`SketchbookSync`)·Task 7(`BackupSync`)·Task 9(`VectorCanvasScreen`)·Task 11(`MainScreen`)이 이 이름들을 쓴다.

- [ ] **Step 1: 인덱스 있는 벡터 페이지 함수 4개를 단일 캔버스 함수로 교체**

`SketchbookRepository.kt`의 `vectorPageFile`/`loadVectorPage`/`saveVectorPage`/`vectorPageUpdatedAt`/`setVectorPageUpdatedAt`(174~198번째 줄 부근) 전체를 이걸로 교체:

```kotlin
    private fun vectorCanvasFile(id: String): File {
        val dir = File(root, id).apply { mkdirs() }
        return File(dir, "vector_canvas.json")
    }

    private fun vectorPreviewFile(id: String): File {
        val dir = File(root, id).apply { mkdirs() }
        return File(dir, "vector_preview.png")
    }

    fun loadVectorCanvas(id: String): VectorPage? {
        val f = vectorCanvasFile(id)
        if (!f.exists()) return null
        return vectorPageFromJson(f.readText())
    }

    /** JSON(진짜 저장 데이터)과 함께, 목록/캐러셀/읽기모드가 쓸 미리보기 PNG도 같이 렌더링해 둔다
     *  — 매번 획 목록을 파싱+렌더링하지 않고 캐시된 비트맵을 바로 읽게 하기 위함. PNG는 순수 캐시라
     *  JSON만 진짜 상태다. */
    fun saveVectorCanvas(id: String, page: VectorPage) {
        vectorCanvasFile(id).writeText(page.toJson())
        FileOutputStream(vectorPreviewFile(id)).use {
            renderVectorPage(page, VECTOR_PREVIEW_SIZE).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    fun loadVectorPreview(id: String): Bitmap? {
        val f = vectorPreviewFile(id)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    fun vectorCanvasUpdatedAt(id: String): Long = vectorCanvasFile(id).lastModified()

    fun setVectorCanvasUpdatedAt(id: String, timestamp: Long) { vectorCanvasFile(id).setLastModified(timestamp) }
```

- [ ] **Step 2: 미리보기 렌더 크기 상수 추가**

`class SketchbookRepository(...)` 선언 바로 위(85번째 줄 부근)에 추가:

```kotlin
private const val VECTOR_PREVIEW_SIZE = 512
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 이 파일 자체는 깨끗. `SketchbookSync.kt`/`BackupSync.kt`가 옛 함수 이름을 아직 부르고 있어 전체 빌드는 계속 에러 — Task 6·7에서 해결.

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt
git commit -m "refactor(sketchbook): collapse indexed vector pages into one canvas per book"
```

---

### Task 6: SketchbookSync — 단일 캔버스 저장 동기화 함수

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookSync.kt`

**Interfaces:**
- Consumes: `SketchbookRepository.create`(Task 4 확장 시그니처), `saveVectorCanvas`/`vectorCanvasUpdatedAt`(Task 5).
- Produces: `createSynced(..., vectorInfinite: Boolean = false, vectorCanvasW: Int? = null, vectorCanvasH: Int? = null): Sketchbook`, `saveVectorCanvasSynced(scope, repo, backup, uid, bookId, page: VectorPage)` — 기존 `saveVectorPageSynced(..., index: Int, ...)`를 **대체**한다. Task 7의 `backup.pushVectorCanvas`(아직 안 만듦, Task 7에서 추가)를 부른다. Task 9(`VectorCanvasScreen`)·Task 10(마법사)이 이 두 함수를 쓴다.

- [ ] **Step 1: `createSynced` 확장**

`SketchbookSync.kt`의 `createSynced` 함수(18번째 줄)를 이걸로 교체:

```kotlin
fun createSynced(
    scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String,
    name: String, sizeKey: String, bgKey: String, vector: Boolean = false,
    vectorInfinite: Boolean = false, vectorCanvasW: Int? = null, vectorCanvasH: Int? = null,
): Sketchbook {
    val book = repo.create(name, sizeKey, bgKey, vector = vector,
        vectorInfinite = vectorInfinite, vectorCanvasW = vectorCanvasW, vectorCanvasH = vectorCanvasH)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) { backup.pushSketchbookMeta(uid, book) }
    return book
}
```

- [ ] **Step 2: `saveVectorPageSynced`를 `saveVectorCanvasSynced`로 교체**

`saveVectorPageSynced` 함수(93~102번째 줄 부근) 전체를 이걸로 교체:

```kotlin
/** 벡터 캔버스판 [savePageSynced] — repo.saveVectorCanvas도 마찬가지로 미리보기 비트맵 렌더+PNG
 *  인코딩+JSON 디스크 쓰기라 매 붓질(onStrokeEnd)마다 메인 스레드에서 부르면 안 된다. 저장과 동시에
 *  바로 백업까지 밀어 올려서(다음 foreground 재동기화를 기다리지 않고) 그림이 곧장 클라우드에
 *  반영되게 한다. */
fun saveVectorCanvasSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, bookId: String, page: VectorPage) {
    scope.launch(Dispatchers.IO) {
        repo.saveVectorCanvas(bookId, page)
        if (uid.isNotBlank()) backup.pushVectorCanvas(uid, bookId, page.toJson(), repo.vectorCanvasUpdatedAt(bookId))
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: `backup.pushVectorCanvas`가 아직 없어서 에러 — Task 7에서 추가하므로 지금은 무시하고 다음 태스크로.

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookSync.kt
git commit -m "refactor(sketchbook): sync a single vector canvas instead of an indexed page"
```

---

### Task 7: Firebase 동기화 — 단일 `vectorCanvas` 필드

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt`

**Interfaces:**
- Consumes: `Sketchbook.vectorInfinite/vectorCanvasW/vectorCanvasH`(Task 4), `SketchbookRepository.loadVectorCanvas/saveVectorCanvas/vectorCanvasUpdatedAt/setVectorCanvasUpdatedAt`(Task 5).
- Produces: `RemoteSketchbook.vectorInfinite/vectorCanvasW/vectorCanvasH/vectorCanvas`, `BackupRepository.pushVectorCanvas(uid, bookId, strokesJson, updatedAt)` — 기존 `pushVectorPage(..., index, ...)`를 **대체**한다.

- [ ] **Step 1: `RemoteSketchbook` 필드 교체**

`BackupModels.kt`의 `RemoteSketchbook` 데이터 클래스에서:

```kotlin
    val vector: Boolean = false,
    val vectorPages: Map<Int, Pair<Long, String>> = emptyMap(), // index -> (updatedAt, strokes json)
```

를 이걸로 교체:

```kotlin
    val vector: Boolean = false,
    val vectorInfinite: Boolean = false,
    val vectorCanvasW: Int? = null,
    val vectorCanvasH: Int? = null,
    /** 벡터 책 하나 = 캔버스 하나이므로 더 이상 페이지 인덱스가 없다. */
    val vectorCanvas: Pair<Long, String>? = null, // (updatedAt, strokes json)
```

- [ ] **Step 2: `BackupRepository.pushSketchbookMeta`에 새 필드 추가**

`pushSketchbookMeta`(41~50번째 줄 부근)의 `mapOf(...)`에 아래 두 줄을 `"vector" to book.vector,` 뒤에 추가:

```kotlin
                "vector" to book.vector, "vectorInfinite" to book.vectorInfinite,
                "vectorCanvasW" to (book.vectorCanvasW ?: -1), "vectorCanvasH" to (book.vectorCanvasH ?: -1),
```

- [ ] **Step 3: `pushVectorPage`를 `pushVectorCanvas`로 교체**

```kotlin
    /** 벡터 캔버스는 이미 텍스트(JSON)라 base64 인코딩 없이 그대로 올린다 — 이미지보다 훨씬
     *  가볍다. [pushSketchbookPage]와 나란한 벡터 전용 경로. 책 하나당 캔버스 하나라 인덱스가 없다. */
    fun pushVectorCanvas(uid: String, bookId: String, strokesJson: String, updatedAt: Long) {
        root.child(uid).child("sketchbooks").child(bookId).child("vectorCanvas")
            .setValue(mapOf("updatedAt" to updatedAt, "strokes" to strokesJson))
    }
```

- [ ] **Step 4: `pullAll`의 벡터 파싱을 단일 필드로 교체**

`pullAll` 안의 `val vectorPages = c.child("vectorPages")...toMap()` 블록(144~149번째 줄 부근)을 이걸로 교체:

```kotlin
            val vectorCanvas = c.child("vectorCanvas").let { vc ->
                val updatedAt = vc.child("updatedAt").getValue(Long::class.java)
                val strokes = vc.child("strokes").getValue(String::class.java)
                if (updatedAt != null && strokes != null) updatedAt to strokes else null
            }
```

그리고 같은 함수 안의 `RemoteSketchbook(...)` 생성 호출에서 `vector = meta.child("vector")...` 줄을 이걸로 교체:

```kotlin
                vector = meta.child("vector").getValue(Boolean::class.java) ?: false,
                vectorInfinite = meta.child("vectorInfinite").getValue(Boolean::class.java) ?: false,
                vectorCanvasW = meta.child("vectorCanvasW").getValue(Int::class.java)?.takeIf { it > 0 },
                vectorCanvasH = meta.child("vectorCanvasH").getValue(Int::class.java)?.takeIf { it > 0 },
                vectorCanvas = vectorCanvas,
```

(그리고 바로 다음 줄이던 `vectorPages = vectorPages,`는 삭제한다.)

- [ ] **Step 5: `BackupSync.reconcileSketchbooks`를 단일 필드 기준으로 단순화**

`reconcileSketchbooks`(56~121번째 줄 부근)에서 두 군데를 고친다:

1. `SyncAction.PULL -> if (r != null) { repo.upsert(Sketchbook(...)) }` 블록을 이걸로 교체:

```kotlin
            SyncAction.PULL -> if (r != null) {
                repo.upsert(Sketchbook(id, r.name, r.sizeKey, r.bgKey, r.createdAt, r.pageCount, r.fav,
                    coverColor = r.coverColor, vector = r.vector, vectorInfinite = r.vectorInfinite,
                    vectorCanvasW = r.vectorCanvasW, vectorCanvasH = r.vectorCanvasH, updatedAt = r.updatedAt))
            }
```

2. `val isVector = ...` 부터 그 아래 `for (index in 0 until pageCount) { if (isVector) {...} else {...} }` 블록 전체(90~119번째 줄 부근)를 이걸로 교체:

```kotlin
        if (l?.vector == true || r?.vector == true) {
            val localAt = repo.vectorCanvasUpdatedAt(id).takeIf { it > 0L }
            when (decideSyncAction(localAt, r?.vectorCanvas?.first)) {
                SyncAction.PULL -> r?.vectorCanvas?.let { (remoteAt, strokesJson) ->
                    vectorPageFromJson(strokesJson)?.let { repo.saveVectorCanvas(id, it); repo.setVectorCanvasUpdatedAt(id, remoteAt) }
                }
                SyncAction.PUSH -> repo.loadVectorCanvas(id)?.let {
                    backup.pushVectorCanvas(uid, id, it.toJson(), repo.vectorCanvasUpdatedAt(id))
                }
                else -> {}
            }
        } else {
            val pageCount = maxOf(l?.pageCount ?: 0, r?.pageCount ?: MAX_PAGES)
            for (index in 0 until pageCount) {
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

- [ ] **Step 6: 전체 컴파일 + 기존 유닛 테스트 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q && ./gradlew.bat :app:testDebugUnitTest --no-daemon -q`
Expected: 컴파일 성공. `VectorPageTest`/`VectorSvgExportTest`/`StrokeGeometryTest`/`BackupModelsTest` 등 기존 테스트 전부 통과(이 태스크로 로직이 바뀐 `decideSyncAction` 자체는 없으니 회귀 없음).

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt
git commit -m "refactor(backup): sync vector canvases as a single field instead of an indexed page map"
```

---

### Task 8: VectorBrushView — 핀치 팬/줌 + 무한/커스텀 좌표계 + 라쏘

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorBrushView.kt`

**Interfaces:**
- Consumes: `strokeTouchesLasso`, `Point`(Task 1), `Dimens.Canvas.minZoom/maxZoom`(기존, `com.g1.sketchbook.ui.theme.Dimens`).
- Produces: `VectorBrushView.infinite: Boolean`, `.canvasW/.canvasH: Float`(커스텀일 때 논리 크기), `.tool: VectorBrushView.Tool`(`DRAW`/`ERASE`/`LASSO_EXPORT`), `.onLassoComplete: ((selected: List<VectorStroke>, lasso: List<Point>) -> Unit)?` — `erasing: Boolean`을 대체(대신 `tool`로 통합). Task 9(`VectorCanvasScreen`)가 이 필드들을 배선한다.

이 파일은 `android.view`/`android.graphics` 의존이라 유닛 테스트 대상이 아니다 — 컴파일 확인 + Task 9까지 끝난 뒤 에뮬레이터에서 수동 확인(핀치 확대/축소, 두 손가락 패닝, 라쏘로 영역 그리기).

- [ ] **Step 1: 파일 전체 교체**

`VectorBrushView.kt` 전체를 이걸로 교체:

```kotlin
package com.g1.sketchbook.vector

import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.g1.sketchbook.ui.theme.Dimens
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 벡터 스케치북의 그리기 View — 펜 하나만, 속도에 따라 굵기가 변한다. [infinite]=true면 경계 없는
 *  무한 캔버스, false면 [canvasW]×[canvasH] 논리 좌표의 고정 캔버스. 한 손가락은 그리기(또는
 *  [tool]에 따라 지우기/라쏘 선택), 두 손가락은 핀치 확대·축소와 패닝 — 기존 래스터 `BrushView`의
 *  Matrix 기반 팬/줌 패턴(disp/inv/userM/userScale/pinching)을 그대로 이식했다. */
class VectorBrushView(context: Context) : View(context) {
    /** [LASSO_RESIZE]는 이번 스펙(캔버스 구조 개편)에서는 자리만 마련해 두는 도구 모드다 — 실제
     *  동작(선택한 획들의 굵기를 균일하게 만들고 슬라이더로 조절)은 다음 스펙(편집 기능)에서
     *  구현한다. 지금은 [onTouchEvent]의 `when (tool)`에서 [ERASE]와 똑같이 아무 동작도 없다. */
    enum class Tool { DRAW, ERASE, LASSO_EXPORT, LASSO_RESIZE }

    var color: Long = 0xFF1E2D4CL
    var strokeWidthDp: Float = 8f
    var tool: Tool = Tool.DRAW
    var onStrokeEnd: (() -> Unit)? = null
    /** 라쏘를 다 그리고 손을 뗐을 때 호출 — [tool]이 [Tool.LASSO_EXPORT]일 때만 발생. 라쏘와 겹친
     *  획 목록과 라쏘 폴리곤 자체(내보내기 영역 계산용)를 같이 준다. 점 3개 미만인 라쏘는 무시. */
    var onLassoComplete: ((selected: List<VectorStroke>, lasso: List<Point>) -> Unit)? = null

    /** 무한 캔버스 여부 — 생성 직후 한 번만 설정하고 이후엔 안 바뀐다(스펙: 생성 시점에 고정). */
    var infinite: Boolean = false
    /** 커스텀(고정) 캔버스의 논리 크기 — [infinite]=false일 때만 의미 있음. */
    var canvasW: Float = 1024f
    var canvasH: Float = 1024f

    private val committed = mutableListOf<VectorStroke>()
    private var current: MutableList<VectorPoint>? = null
    private var lx = 0f; private var ly = 0f; private var lt = 0L
    private var smoothedSpeed = 0f
    private var lassoPts: MutableList<Point>? = null

    /** undo는 committed의 마지막 원소를 그냥 지우는 것만으론 안 된다 — eraseAt()도 committed에서
     *  직접 지우기 때문에, 지우개로 획 A를 지운 뒤 undo하면 "지금 committed의 마지막 원소"인 전혀
     *  다른 획 B가 대신 지워져 버린다(A는 영영 사라짐). 그리기/지우기 각각을 별도 이력으로 남겨서
     *  undo가 항상 "가장 최근에 일어난 단일 동작"만 정확히 되돌리게 한다. */
    private sealed class UndoOp {
        data class Drew(val stroke: VectorStroke) : UndoOp()
        data class Erased(val stroke: VectorStroke) : UndoOp()
    }
    private val history = mutableListOf<UndoOp>()

    val canUndo: Boolean get() = history.isNotEmpty()

    fun currentPage(): VectorPage = VectorPage(committed.toList())

    fun loadPage(page: VectorPage) {
        committed.clear(); committed.addAll(page.strokes)
        history.clear()
        current = null
        invalidate()
    }

    fun undo() {
        val op = history.removeLastOrNull() ?: return
        when (op) {
            is UndoOp.Drew -> committed.remove(op.stroke)
            is UndoOp.Erased -> committed.add(op.stroke)
        }
        invalidate()
        onStrokeEnd?.invoke()
    }

    // ---- 팬/줌 (BrushView.kt의 disp/inv/userM 패턴 이식) ----
    private val disp = Matrix()
    private val inv = Matrix()
    private val userM = Matrix()
    private var userScale = 1f
    private var pinching = false
    private var prevDist = 0f; private var prevMidX = 0f; private var prevMidY = 0f
    private var resyncPinchBaseline = false
    private var displayReady = false
    private val tmp = FloatArray(2)

    private fun computeDisplay() {
        if (width <= 0 || height <= 0) return
        disp.reset()
        if (infinite) {
            // 논리 원점(0,0)이 뷰 중앙에서 시작 — 경계가 없으니 "맞춤" 기준이 없다.
            disp.postTranslate(width / 2f, height / 2f)
        } else {
            val fitScale = min(width / canvasW, height / canvasH)
            disp.postScale(fitScale, fitScale)
            disp.postTranslate((width - canvasW * fitScale) / 2f, (height - canvasH * fitScale) / 2f)
        }
        disp.postConcat(userM)
        disp.invert(inv)
        displayReady = true
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        computeDisplay()
    }

    private fun mapPoint(x: Float, y: Float): FloatArray { tmp[0] = x; tmp[1] = y; inv.mapPoints(tmp); return tmp }

    private fun spacing(e: MotionEvent) = hypot((e.getX(0) - e.getX(1)).toDouble(), (e.getY(0) - e.getY(1)).toDouble()).toFloat()
    private fun midX(e: MotionEvent) = (e.getX(0) + e.getX(1)) / 2f
    private fun midY(e: MotionEvent) = (e.getY(0) + e.getY(1)) / 2f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!displayReady) computeDisplay()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pinching = false
                val p = mapPoint(event.x, event.y)
                when (tool) {
                    Tool.ERASE -> eraseAt(p[0], p[1])
                    Tool.LASSO_EXPORT -> { lassoPts = mutableListOf(Point(p[0], p[1])); invalidate() }
                    Tool.DRAW -> {
                        lx = p[0]; ly = p[1]; lt = SystemClock.uptimeMillis(); smoothedSpeed = 0f
                        current = mutableListOf(VectorPoint(p[0], p[1], widthFor(0f)))
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    current = null   // 두 손가락이 닿으면 그리다 만 획은 버린다(핀치 시작)
                    lassoPts = null
                    pinching = true
                    prevDist = spacing(event); prevMidX = midX(event); prevMidY = midY(event)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching && event.pointerCount >= 2) {
                    val d = spacing(event); val mx = midX(event); val my = midY(event)
                    if (resyncPinchBaseline) { resyncPinchBaseline = false; prevDist = d; prevMidX = mx; prevMidY = my }
                    if (prevDist > 0f) {
                        var ds = d / prevDist
                        val ns = (userScale * ds).coerceIn(Dimens.Canvas.minZoom, Dimens.Canvas.maxZoom)
                        ds = ns / userScale; userScale = ns
                        userM.postScale(ds, ds, mx, my)
                    }
                    userM.postTranslate(mx - prevMidX, my - prevMidY)
                    computeDisplay(); invalidate()
                    prevDist = d; prevMidX = mx; prevMidY = my
                } else when (tool) {
                    Tool.LASSO_EXPORT -> {
                        val p = mapPoint(event.x, event.y)
                        lassoPts?.add(Point(p[0], p[1])); invalidate()
                    }
                    Tool.DRAW -> {
                        val cur = current ?: return true
                        val p = mapPoint(event.x, event.y)
                        val now = SystemClock.uptimeMillis()
                        val dd = hypot((p[0] - lx).toDouble(), (p[1] - ly).toDouble()).toFloat()
                        val vRaw = dd / max(1L, now - lt)
                        smoothedSpeed += (vRaw - smoothedSpeed) * 0.35f
                        cur.add(VectorPoint(p[0], p[1], widthFor(smoothedSpeed)))
                        lx = p[0]; ly = p[1]; lt = now
                        invalidate()
                    }
                    Tool.ERASE -> {}
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) { pinching = false; prevDist = 0f } else resyncPinchBaseline = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pinching = false; prevDist = 0f
                when (tool) {
                    Tool.DRAW -> {
                        val cur = current; current = null
                        if (cur != null && cur.size >= 2) {
                            val stroke = VectorStroke(color, cur)
                            committed.add(stroke)
                            history.add(UndoOp.Drew(stroke))
                            onStrokeEnd?.invoke()
                        }
                    }
                    Tool.LASSO_EXPORT -> {
                        val lasso = lassoPts; lassoPts = null
                        if (lasso != null && lasso.size >= 3) {
                            val selected = committed.filter { strokeTouchesLasso(it, lasso) }
                            onLassoComplete?.invoke(selected, lasso)
                        }
                    }
                    Tool.ERASE -> {}
                }
                invalidate()
            }
        }
        return true
    }

    /** 기존 래스터 펜(`BrushView.penSeg`)과 같은 느낌의 속도-굵기 곡선 — 빠를수록 가늘게, 최대
     *  65%까지 얇아진다. */
    private fun widthFor(speed: Float): Float {
        val w = strokeWidthDp * (1f - min(0.65f, speed * 0.2f))
        return max(1f, w)
    }

    private fun eraseAt(x: Float, y: Float) {
        for (i in committed.indices.reversed()) {
            val outline = strokeOutline(committed[i].points)
            if (outline.isNotEmpty() && pointInPolygon(x, y, outline)) {
                val erased = committed[i]
                committed.removeAt(i)
                history.add(UndoOp.Erased(erased))
                invalidate()
                onStrokeEnd?.invoke()
                return
            }
        }
    }

    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFF3D6BFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.concat(disp)
        drawVectorPage(canvas, VectorPage(committed))
        current?.let { pts -> if (pts.size >= 2) drawVectorPage(canvas, VectorPage(listOf(VectorStroke(color, pts)))) }
        canvas.restore()
        lassoPts?.let { pts -> if (pts.size >= 2) drawLasso(canvas, pts) }
    }

    private fun drawLasso(canvas: android.graphics.Canvas, pts: List<Point>) {
        val path = Path()
        pts.forEachIndexed { i, p ->
            tmp[0] = p.x; tmp[1] = p.y; disp.mapPoints(tmp)
            if (i == 0) path.moveTo(tmp[0], tmp[1]) else path.lineTo(tmp[0], tmp[1])
        }
        path.close()
        canvas.drawPath(path, lassoPaint)
    }
}
```

**설계 메모(구현자가 알아야 할 것)**: `erasing: Boolean` 필드가 `tool: Tool`로 바뀌었다 — Task 9에서 `view?.erasing = ...` 대신 `view?.tool = VectorBrushView.Tool.ERASE`(등)로 배선해야 한다. `CANVAS_SIZE` 상수(companion object)도 사라졌다 — 고정 정사각형 가정 자체가 없어졌기 때문.

- [ ] **Step 2: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: `VectorCanvasScreen.kt`가 아직 옛 `erasing`/`CANVAS_SIZE`를 참조해 에러 — Task 9에서 해결하므로 지금은 무시.

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorBrushView.kt
git commit -m "feat(vector): pinch zoom/pan and infinite/custom coordinate space for the vector canvas"
```

---

### Task 9: VectorCanvasScreen — 페이지 넘김 제거, 내보내기/라쏘 배선

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorCanvasScreen.kt`

**Interfaces:**
- Consumes: `VectorBrushView.infinite/canvasW/canvasH/tool/onLassoComplete`(Task 8), `SketchbookRepository.loadVectorCanvas/loadVectorPreview`(Task 5), `saveVectorCanvasSynced`(Task 6), `vectorPageToSvg(page, region: Bounds)`(Task 2), `contentBounds`/`pointsBounds`(Task 1).
- Produces: `VectorCanvasScreen(bookId: String, book: Sketchbook, myUid: String, onBack: () -> Unit)` — **`startPage` 파라미터 제거**. Task 11(`SketchbookScreens.kt`/`MainScreen.kt`의 호출부)이 이 새 시그니처로 맞춘다.

이 파일은 Compose UI라 유닛 테스트 대상이 아니다 — 컴파일 확인 + 에뮬레이터 수동 확인(핀치 줌/팬, 지우개, 라쏘 내보내기, 전체 내보내기, undo).

- [ ] **Step 1: 파일 전체 교체**

`VectorCanvasScreen.kt` 전체를 이걸로 교체:

```kotlin
package com.g1.sketchbook.vector

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.g1.sketchbook.R
import com.g1.sketchbook.data.SessionStore
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository
import com.g1.sketchbook.sketchbook.saveVectorCanvasSynced
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.saveSvgToGallery

/** 벡터 스케치북 전용 캔버스 화면 — 책 한 권 = 캔버스 한 장(페이지 없음). 도구는 펜/지우개/
 *  내보내기용 라쏘 셋. 두 손가락 핀치로 확대·이동. */
@Composable
fun VectorCanvasScreen(bookId: String, book: Sketchbook, myUid: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SketchbookRepository(context) }
    val session = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
    var view by remember { mutableStateOf<VectorBrushView?>(null) }
    var color by remember { mutableStateOf(session.brushColor) }
    var tool by remember { mutableStateOf(VectorBrushView.Tool.DRAW) }
    var canUndo by remember { mutableStateOf(false) }
    val favorites = session.favoriteColors

    fun saveCurrent() {
        val v = view ?: return
        saveVectorCanvasSynced(scope, repo, backup, myUid, bookId, v.currentPage())
    }

    fun exportRegion(region: Bounds) {
        val v = view ?: return
        val svg = vectorPageToSvg(v.currentPage(), region)
        val status = saveSvgToGallery(context, svg, book.name)
        Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                VectorBrushView(ctx).also {
                    it.color = color
                    it.infinite = book.vectorInfinite
                    it.canvasW = (book.vectorCanvasW ?: 1024).toFloat()
                    it.canvasH = (book.vectorCanvasH ?: 1024).toFloat()
                    it.loadPage(repo.loadVectorCanvas(bookId) ?: VectorPage(emptyList()))
                    it.onStrokeEnd = { saveCurrent(); canUndo = it.canUndo }
                    view = it
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(16.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            favorites.take(5).forEach { swatch ->
                val selected = swatch == color && tool == VectorBrushView.Tool.DRAW
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(swatch))
                        .border(if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .bounceClick { color = swatch; view?.color = swatch; tool = VectorBrushView.Tool.DRAW; view?.tool = tool },
                )
            }
            IconButton(enabled = canUndo, onClick = { view?.undo(); canUndo = view?.canUndo ?: false }) {
                Icon(Icons.Filled.Undo, "되돌리기")
            }
            IconButton(onClick = {
                tool = if (tool == VectorBrushView.Tool.ERASE) VectorBrushView.Tool.DRAW else VectorBrushView.Tool.ERASE
                view?.tool = tool
            }) {
                Image(
                    painterResource(R.drawable.brush_eraser), "지우개(획 삭제)",
                    colorFilter = ColorFilter.tint(if (tool == VectorBrushView.Tool.ERASE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                )
            }
            IconButton(onClick = {
                tool = if (tool == VectorBrushView.Tool.LASSO_EXPORT) VectorBrushView.Tool.DRAW else VectorBrushView.Tool.LASSO_EXPORT
                view?.tool = tool
                view?.onLassoComplete = { _, lasso ->
                    pointsBounds(lasso)?.let { exportRegion(it) }
                    tool = VectorBrushView.Tool.DRAW; view?.tool = tool
                }
            }) {
                Icon(com.g1.sketchbook.brush.IconLassoLine, "라쏘로 영역 선택해 내보내기",
                    tint = if (tool == VectorBrushView.Tool.LASSO_EXPORT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = {
                val v = view ?: return@IconButton
                val whole = if (book.vectorInfinite) {
                    contentBounds(v.currentPage().strokes)?.let {
                        val padX = it.width * 0.05f; val padY = it.height * 0.05f
                        Bounds(it.minX - padX, it.minY - padY, it.maxX + padX, it.maxY + padY)
                    } ?: Bounds(0f, 0f, 64f, 64f)
                } else {
                    Bounds(0f, 0f, book.vectorCanvasW?.toFloat() ?: 1024f, book.vectorCanvasH?.toFloat() ?: 1024f)
                }
                exportRegion(whole)
            }) {
                Icon(com.g1.sketchbook.brush.IconImageSaveLine, "전체 내보내기")
            }
        }
        IconButton(onClick = { saveCurrent(); onBack() }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Icon(Icons.Filled.Close, "닫기")
        }
    }
}
```

**설계 메모**: 페이지 넘김 상단 ◀▶ 버튼과 `page`/`goTo`/`MAX_PAGES` 관련 코드가 전부 사라졌다 — 이제 `BoxWithConstraints`로 정사각형 뷰포트를 강제하던 것도 없앴다(핀치로 자유롭게 보므로 뷰가 화면을 꽉 채워도 된다). `IconLassoLine`은 이미 `brush/LineIcons.kt`에 있는 기존 아이콘(다른 화면의 올가미 선택 버튼과 동일)을 재사용한다.

- [ ] **Step 2: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 이 파일과 그 아래 의존 파일들은 깨끗. `SketchbookScreens.kt`/`MainScreen.kt`가 아직 옛 `startPage` 시그니처로 호출 중이라 전체 빌드는 에러 — Task 11에서 해결.

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorCanvasScreen.kt
git commit -m "feat(vector): drop page navigation, add whole/lasso export and pinch zoom to the canvas screen"
```

---

### Task 10: 생성 마법사 — 무한/커스텀 캔버스 선택 단계

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt`

**Interfaces:**
- Consumes: `createSynced(..., vectorInfinite, vectorCanvasW, vectorCanvasH)`(Task 6).

이 파일은 Compose UI라 유닛 테스트 대상이 아니다 — 컴파일 확인 + 에뮬레이터 수동 확인(무한/커스텀 각각 선택해 벡터 책 생성).

- [ ] **Step 1: 프리셋 목록 상수 추가**

`SketchbookScreens.kt` 파일 상단, `private fun CreateWizard(` 선언 바로 위에 추가:

```kotlin
private val VectorCanvasPresets = listOf(
    Triple("1:1", 1024, 1024), Triple("4:3", 1024, 768), Triple("3:4", 768, 1024), Triple("16:9", 1280, 720),
)
```

- [ ] **Step 2: `CreateWizard` 안에 캔버스 타입 상태 추가**

`CreateWizard` 함수 안, `var sizeKey by remember { mutableStateOf("a4") }` 바로 아래에 추가:

```kotlin
    var vectorInfinite by remember { mutableStateOf(true) }
    var vectorWStr by remember { mutableStateOf("1024") }
    var vectorHStr by remember { mutableStateOf("1024") }
```

- [ ] **Step 3: `finishVector()` 교체**

`finishVector()` 함수(302~304번째 줄 부근)를 이걸로 교체:

```kotlin
    fun finishVector() {
        val w = vectorWStr.toIntOrNull()?.coerceIn(64, 4096) ?: 1024
        val h = vectorHStr.toIntOrNull()?.coerceIn(64, 4096) ?: 1024
        repo?.let {
            // sizeKey="a4"는 벡터 책에서 안 쓰이는 값이다(Catalog엔 "vector" 항목이 더 이상 없음) —
            // 실제 캔버스 크기는 vectorInfinite/vectorCanvasW/vectorCanvasH가 대신 결정한다.
            createSynced(scope, it, backup, myUid, name.ifBlank { "벡터 스케치북" }, "a4", "watercolor",
                vector = true, vectorInfinite = vectorInfinite,
                vectorCanvasW = if (vectorInfinite) null else w, vectorCanvasH = if (vectorInfinite) null else h)
        }?.let(onCreated)
    }
```

- [ ] **Step 4: 다이얼로그에 캔버스 타입 선택 UI 추가**

`type == WType.VECTOR` 블록(397~409번째 줄 부근) 전체를 이걸로 교체:

```kotlin
        type == WType.VECTOR -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(Dimens.Wizard.cardRadius),
                title = { Text("벡터 스케치북") },
                text = {
                    Column {
                        OutlinedTextField(name, { name = it.take(20) }, singleLine = true,
                            placeholder = { Text("스케치북 이름") }, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = vectorInfinite, onClick = { vectorInfinite = true }, label = { Text("무한") })
                            FilterChip(selected = !vectorInfinite, onClick = { vectorInfinite = false }, label = { Text("커스텀 크기") })
                        }
                        if (!vectorInfinite) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                VectorCanvasPresets.forEach { (label, w, h) ->
                                    FilterChip(
                                        selected = vectorWStr == w.toString() && vectorHStr == h.toString(),
                                        onClick = { vectorWStr = w.toString(); vectorHStr = h.toString() },
                                        label = { Text(label) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(vectorWStr, { vectorWStr = it.filter(Char::isDigit).take(4) },
                                    singleLine = true, label = { Text("가로") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(vectorHStr, { vectorHStr = it.filter(Char::isDigit).take(4) },
                                    singleLine = true, label = { Text("세로") }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { finishVector() }) { Text("만들기") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
            )
        }
```

- [ ] **Step 5: `FilterChip` import 확인**

파일 상단 import 목록에 `import androidx.compose.material3.FilterChip`이 없으면 추가한다(다른 `androidx.compose.material3.*` import들 옆에).

- [ ] **Step 6: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 에러 없이 조용히 끝남(Task 11에서 마저 고칠 `VectorCanvasScreen(...)` 호출부 한 곳만 빼고).

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt
git commit -m "feat(sketchbook): add infinite/custom canvas choice to the vector creation wizard"
```

---

### Task 11: 호출부 정리 — `startPage` 제거, 홈 미리보기를 단일 캔버스로

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt`

**Interfaces:**
- Consumes: `VectorCanvasScreen(bookId, book, myUid, onBack)`(Task 9, `startPage` 없음), `SketchbookRepository.loadVectorPreview`(Task 5).

- [ ] **Step 1: `SketchbookScreens.kt`의 `VectorCanvasScreen` 호출 수정**

`SketchbookScreens.kt` 1080~1082번째 줄 부근:

```kotlin
    if (book.vector) {
        com.g1.sketchbook.vector.VectorCanvasScreen(bookId, book, myUid, startPage, onBack)
        return
    }
```

을 이걸로 교체:

```kotlin
    if (book.vector) {
        com.g1.sketchbook.vector.VectorCanvasScreen(bookId, book, myUid, onBack)
        return
    }
```

- [ ] **Step 2: 다른 `VectorCanvasScreen(` 호출부가 있는지 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && grep -rn "VectorCanvasScreen(" app/src/main/java`

Step 1에서 고친 한 곳 말고 다른 호출부가 나오면 같은 방식으로 `startPage` 인자를 제거한다.

- [ ] **Step 3: `MainScreen.kt`의 벡터 미리보기를 단일 캔버스 기준으로 교체**

`MainScreen.kt` 232~246번째 줄 부근, `if (book.vector) { ... }` 블록을 이걸로 교체:

```kotlin
                        if (book.vector) {
                            // 벡터 스케치북은 페이지 넘김 애니메이션(읽기모드) 자체가 스펙에서
                            // 제외돼서(전용 캔버스 화면의 읽기모드 버튼도 같은 이유로 없음) 여기서도
                            // PageCurl 대신 저장된 미리보기를 그냥 정지 이미지로 보여준다 — 페이지
                            // 개념 자체가 없어졌으니(2026-08-30) 페이지 넘김 제스처도 없음.
                            val side = minOf(maxWidth, maxHeight)
                            val previewBmp = remember(book.id) { repo?.loadVectorPreview(book.id) }
                            if (previewBmp != null) {
                                Image(
                                    bitmap = previewBmp.asImageBitmap(), contentDescription = null,
                                    modifier = Modifier.size(side).clip(RoundedCornerShape(16.dp)),
                                )
                            } else {
                                Box(Modifier.size(side).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                            }
                        } else {
```

(`else {` 이후 기존 PageCurl 분기는 그대로 둔다 — 이 태스크는 `if (book.vector) { ... }` 블록 내용만 바꾼다.)

- [ ] **Step 4: 전체 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 에러 없이 조용히 끝남.

- [ ] **Step 5: 전체 유닛 테스트 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --no-daemon -q`
Expected: PASS — `VectorPageTest`/`VectorSvgExportTest`/`StrokeGeometryTest`/`BackupModelsTest` 등 전부 통과.

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt
git commit -m "refactor: drop vector page index from call sites, show the single-canvas preview at home"
```

- [ ] **Step 7: 에뮬레이터에서 수동 확인 (사용자 검증 — 빌드+설치까지만)**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:assembleDebug --no-daemon -q && MSYS2_ARG_CONV_EXCL="*" adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`

빌드+설치까지만 하고, 실제 확인(무한/커스텀 벡터 책 만들기 → 그리기 → 핀치 줌/팬 → 지우개 → 라쏘 내보내기 → 전체 내보내기 → 홈 탭 미리보기)은 사용자가 직접 한다.
