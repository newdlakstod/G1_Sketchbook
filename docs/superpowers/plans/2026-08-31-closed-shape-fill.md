# 벡터 캔버스 폐곡선 채우기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 벡터 캔버스의 "채우기"를 "획 리본을 채울지"에서 "획이 그리는 도중 자기 자신과 교차해 닫힌 구역이 생기면 그 내부를 실시간으로 자동 채울지"로 재정의한다.

**Architecture:** 새 순수 Kotlin 함수(`selfIntersectionFills`)가 획의 중심선 점 목록에서 자기 교차로 생기는 닫힌 다각형들을 찾는다(저장 시 미리 계산 안 함 — 기존 `strokeOutline`/`contentBounds`와 같은 "렌더링 시점마다 계산하는 순수 함수" 패턴). 리본(`strokeOutline`)은 이제 `fillEnabled`와 무관하게 항상 채워지고, `fillEnabled`가 켜져 있으면 그 위에 닫힌 구역들을 추가로 채워 그린다 — 그리기 화면·썸네일·SVG 내보내기가 전부 같은 계산을 공유하므로 그리는 도중에도(매 프레임 다시 그릴 때 현재 점 목록으로 다시 계산) 자연스럽게 실시간으로 보인다.

**Tech Stack:** Kotlin, Jetpack Compose, Android View(`Canvas`/`Path`).

## Global Constraints

- `fillEnabled`(기존 필드, 그대로 재사용)의 의미가 바뀐다: "리본을 채울지"가 아니라 "이 획의 자기교차 폐곡선을 채울지". 리본 자체는 이 값과 무관하게 항상 `VectorStroke.color`로 채워진다(스탬프 브러시 획 제외 — `brushProfileId`가 있으면 기존 스탬프 렌더링 그대로, 이 기능 대상 아님).
- 판정 범위는 **한 획(하나의 `VectorStroke`) 안에서의 자기 교차만** — 서로 다른 여러 획이 합쳐서 만드는 닫힌 영역은 대상 아님.
- 한 획 안에 자기 교차가 여러 번 있으면(소용돌이 등) 각 닫힌 구역을 전부 독립적으로 채운다.
- 채움 색은 새 필드 `fillColor: Long?`(기본 null) — null이면 `color`(리본 색)를 그대로 쓴다.
- UI는 최소한만: 기존 "브러시 설정" 톱니바퀴 메뉴의 "채우기" 토글을 그대로 재사용(의미만 바뀜), 켜져 있을 때 그 아래 채움 색 스와치 한 줄만 추가. 전체 UI 개편은 이번 계획 범위 밖.
- `selfIntersectionFills`/`segmentIntersection`은 Android 의존 없는 순수 Kotlin이라 유닛 테스트 대상(`StrokeGeometry.kt`에 추가). `VectorRenderer.kt`/`VectorSvgExport.kt`/`VectorBrushView.kt`/`VectorCanvasScreen.kt`는 이 프로젝트 관례상 컴파일 확인 + 기존 테스트 재실행(해당하는 경우)까지만 — 새 테스트 불필요.

---

### Task 1: StrokeGeometry — 선분 교차 판정 + 자기교차 폐곡선 찾기

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/StrokeGeometry.kt`
- Test: `app/src/test/java/com/g1/sketchbook/vector/StrokeGeometryTest.kt`

**Interfaces:**
- Consumes: `VectorPoint`(기존, `VectorPage.kt`), `Point`(기존, 같은 파일).
- Produces: `internal fun segmentIntersection(p1: VectorPoint, p2: VectorPoint, p3: VectorPoint, p4: VectorPoint): Point?`, `fun selfIntersectionFills(points: List<VectorPoint>): List<List<Point>>`. Task 3(`VectorRenderer`)·Task 4(`VectorSvgExport`)·Task 5(`VectorBrushView`의 지우개)가 `selfIntersectionFills`를 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/g1/sketchbook/vector/StrokeGeometryTest.kt`의 클래스 맨 끝(마지막 `}` 앞)에 추가:

```kotlin
    @Test fun crossingSegmentsIntersectAtExpectedPoint() {
        val hit = segmentIntersection(
            VectorPoint(0f, 0f, 1f), VectorPoint(10f, 10f, 1f),
            VectorPoint(0f, 10f, 1f), VectorPoint(10f, 0f, 1f),
        )
        assertEquals(Point(5f, 5f), hit)
    }

    @Test fun parallelSegmentsDoNotIntersect() {
        val hit = segmentIntersection(
            VectorPoint(0f, 0f, 1f), VectorPoint(10f, 0f, 1f),
            VectorPoint(0f, 10f, 1f), VectorPoint(10f, 10f, 1f),
        )
        assertEquals(null, hit)
    }

    @Test fun segmentsThatWouldCrossOnlyIfExtendedDoNotIntersect() {
        // 두 선분이 놓인 직선끼리는 교차하지만, 그 교차점이 각 선분의 실제 구간(0~1) 밖에 있음.
        val hit = segmentIntersection(
            VectorPoint(0f, 0f, 1f), VectorPoint(1f, 1f, 1f),
            VectorPoint(5f, 0f, 1f), VectorPoint(5f, -1f, 1f),
        )
        assertEquals(null, hit)
    }

    @Test fun selfIntersectionFillsReturnsEmptyForFewerThanFourPoints() {
        assertEquals(emptyList(), selfIntersectionFills(listOf(VectorPoint(0f, 0f, 1f), VectorPoint(10f, 0f, 1f), VectorPoint(10f, 10f, 1f))))
    }

    @Test fun selfIntersectionFillsReturnsEmptyWhenNoCrossing() {
        val points = listOf(VectorPoint(0f, 0f, 1f), VectorPoint(10f, 0f, 1f), VectorPoint(10f, 10f, 1f), VectorPoint(0f, 10f, 1f))
        assertEquals(emptyList(), selfIntersectionFills(points))
    }

    @Test fun bowtieShapeProducesOneTriangularFill() {
        // (0,0)->(10,10)->(0,10)->(10,0): 첫 세그먼트(대각선 /)와 세번째 세그먼트(대각선 \)가
        // 정확히 (5,5)에서 교차 — 손 계산으로 확인된 값.
        val points = listOf(VectorPoint(0f, 0f, 1f), VectorPoint(10f, 10f, 1f), VectorPoint(0f, 10f, 1f), VectorPoint(10f, 0f, 1f))
        val fills = selfIntersectionFills(points)
        assertEquals(listOf(listOf(Point(5f, 5f), Point(10f, 10f), Point(0f, 10f))), fills)
    }

    @Test fun twoSequentialBowtiesEachProduceTheirOwnFill() {
        // 첫 4점이 bowtie 하나(교차 (5,5)), 그다음 4점이 +100 평행이동한 두번째 bowtie(교차 (105,5)).
        val points = listOf(
            VectorPoint(0f, 0f, 1f), VectorPoint(10f, 10f, 1f), VectorPoint(0f, 10f, 1f), VectorPoint(10f, 0f, 1f),
            VectorPoint(100f, 0f, 1f), VectorPoint(110f, 10f, 1f), VectorPoint(100f, 10f, 1f), VectorPoint(110f, 0f, 1f),
        )
        val fills = selfIntersectionFills(points)
        assertEquals(
            listOf(
                listOf(Point(5f, 5f), Point(10f, 10f), Point(0f, 10f)),
                listOf(Point(105f, 5f), Point(110f, 10f), Point(100f, 10f)),
            ),
            fills,
        )
    }
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.StrokeGeometryTest" --no-daemon`
Expected: FAIL — `segmentIntersection`/`selfIntersectionFills`가 아직 없어서 컴파일 에러.

- [ ] **Step 3: 구현 작성**

`StrokeGeometry.kt`의 `strokeTouchesLasso` 함수(파일 맨 끝) 바로 아래에 추가:

```kotlin
/** 두 선분(p1→p2, p3→p4)이 교차하면 그 교차 좌표, 아니면 null — 각 선분을 0~1로 매개변수화하는
 *  표준 공식(t/u)으로 판정, 그 범위(양 끝 포함) 안에서만 교차로 인정한다. 평행하거나(분모가 0에
 *  가까움) 겹쳐도 교차로 안 본다(길이가 있는 겹침은 이 스펙 범위 밖). [internal]인 이유: 이 파일
 *  안에서만 쓰지만([selfIntersectionFills]), 유닛 테스트에서 직접 검증하기 위해 `private`이
 *  아니라 `internal`로 둔다(같은 모듈의 테스트 소스셋에서 접근 가능). */
internal fun segmentIntersection(p1: VectorPoint, p2: VectorPoint, p3: VectorPoint, p4: VectorPoint): Point? {
    val d1x = p2.x - p1.x; val d1y = p2.y - p1.y
    val d2x = p4.x - p3.x; val d2y = p4.y - p3.y
    val denom = d1x * d2y - d1y * d2x
    if (kotlin.math.abs(denom) < 1e-6f) return null
    val t = ((p3.x - p1.x) * d2y - (p3.y - p1.y) * d2x) / denom
    val u = ((p3.x - p1.x) * d1y - (p3.y - p1.y) * d1x) / denom
    if (t < 0f || t > 1f || u < 0f || u > 1f) return null
    return Point(p1.x + t * d1x, p1.y + t * d1y)
}

/** [points](획의 중심선)가 자기 자신과 교차해서 만드는 닫힌 구역들을 찾는다 — 원, 8자, 소용돌이
 *  등 손으로 닫힌 도형을 그리면 그 구역마다 다각형 하나씩 반환한다(자기 교차가 없으면 빈 목록).
 *  점 목록을 순서대로 훑으면서, 세그먼트 i가 그 이전(바로 앞 세그먼트는 끝점을 공유하니 제외)의
 *  아직 안 쓰인 세그먼트 j와 교차하면 그 두 교차점 사이(정확히는 교차점부터 세그먼트 i의 교차점
 *  까지, 원래 점들은 [j+1..i]) 를 다각형 하나로 만들고, 다음 탐색은 세그먼트 i부터 이어서(j 이전
 *  구간은 이미 다 쓰였으니 건너뛰고) 계속한다 — 그래서 소용돌이처럼 교차가 여러 번 있어도 구간이
 *  겹치지 않게 각각 한 번씩만 다각형이 된다. 각 다각형은 [교차점, 원래 점들...]로만 이뤄지며(첫
 *  점을 마지막에 다시 안 붙임), 마지막 점에서 다시 그 교차점으로 닫는 건 렌더러의 `path.close()`가
 *  담당한다([strokeOutline]과 같은 컨벤션) — 교차점이 세그먼트 i 위의 한 점이라 이 마지막 변은
 *  세그먼트 i의 일부 구간일 뿐이라 항상 유효하다. */
fun selfIntersectionFills(points: List<VectorPoint>): List<List<Point>> {
    if (points.size < 4) return emptyList()
    val result = mutableListOf<List<Point>>()
    var startSeg = 0
    var i = 0
    while (i < points.size - 1) {
        var found: Pair<Int, Point>? = null
        for (j in startSeg until i - 1) {
            val hit = segmentIntersection(points[j], points[j + 1], points[i], points[i + 1])
            if (hit != null) { found = j to hit; break }
        }
        if (found != null) {
            val (j, hit) = found
            val polygon = mutableListOf(hit)
            for (k in (j + 1)..i) polygon.add(Point(points[k].x, points[k].y))
            result.add(polygon)
            startSeg = i
        }
        i++
    }
    return result
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.StrokeGeometryTest" --no-daemon`
Expected: PASS — 기존 테스트 전부 + 신규 7개 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/StrokeGeometry.kt app/src/test/java/com/g1/sketchbook/vector/StrokeGeometryTest.kt
git commit -m "feat(vector): find self-intersecting closed regions in a stroke's centerline"
```

---

### Task 2: VectorPage — `fillColor` 필드 추가

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorPage.kt`
- Test: `app/src/test/java/com/g1/sketchbook/vector/VectorPageTest.kt`

**Interfaces:**
- Produces: `VectorStroke.fillColor: Long?`(기본 `null`). Task 3·4·5·6이 이 필드를 읽고 쓴다.

- [ ] **Step 1: 실패하는 테스트 추가**

`VectorPageTest.kt` 맨 끝(마지막 `}` 앞)에 추가:

```kotlin
    @Test fun fillColorRoundTripsThroughJson() {
        val page = VectorPage(listOf(
            VectorStroke(-65536L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), fillColor = -16711936L),
        ))
        assertEquals(page, vectorPageFromJson(page.toJson()))
    }

    @Test fun jsonWithoutFillColorFieldDefaultsToNull() {
        val json = "{\"strokes\":[{\"color\":-65536,\"points\":[{\"x\":0.0,\"y\":0.0,\"w\":4.0},{\"x\":10.0,\"y\":0.0,\"w\":4.0}],\"cap\":\"ROUND\",\"fillEnabled\":true,\"strokeColor\":-9223372036854775808,\"strokeWidthPx\":2.0,\"brushProfileId\":\"stamp-1\"}]}"
        val decoded = vectorPageFromJson(json)!!.strokes[0]
        assertEquals(null, decoded.fillColor)
        assertEquals("stamp-1", decoded.brushProfileId)
    }
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorPageTest" --no-daemon`
Expected: FAIL — `fillColor` 파라미터가 없어서 컴파일 에러.

- [ ] **Step 3: `VectorStroke`에 필드 추가**

`VectorPage.kt`의 `VectorStroke` 데이터 클래스를 이걸로 교체:

```kotlin
data class VectorStroke(
    val color: Long,
    val points: List<VectorPoint>,
    val cap: VectorCap = VectorCap.BUTT,
    /** 리본을 채울지가 아니라, 이 획이 자기 자신과 교차해서 만드는 닫힌 구역을 채울지 — 리본
     *  자체는 이 값과 무관하게 항상 [color]로 채워진다([VectorRenderer.drawVectorPage] 참고).
     *  true여도 자기 교차가 없으면(대부분의 획) 시각적으로 아무 효과 없음. */
    val fillEnabled: Boolean = true,
    val strokeColor: Long? = null,
    val strokeWidthPx: Float = 2f,
    /** null이면 지금 펜(cap/fillEnabled/strokeColor/strokeWidthPx 그대로 적용). 아니면 이 id의
     *  스탬프 브러시로 그려진 획 — 이때는 위 네 필드를 무시하고 [points]를 중심선 삼아
     *  [stampPolygons]로 다시 계산해서 그린다(전부 [color]로 틴트). 참조하는 브러시가 삭제된
     *  경우 렌더링 시점에 못 찾으면 지금 펜(리본, [color])으로 폴백. */
    val brushProfileId: String? = null,
    /** [fillEnabled]로 채워지는 자기교차 폐곡선의 색 — null이면 [color](리본 색)를 그대로 쓴다. */
    val fillColor: Long? = null,
)
```

- [ ] **Step 4: JSON 쓰기에 필드 추가**

`toJson()` 안의 스트로크 조립 부분을 이걸로 교체:

```kotlin
        sb.append("],\"cap\":\"").append(s.cap.name).append("\"")
            .append(",\"fillEnabled\":").append(s.fillEnabled)
            .append(",\"strokeColor\":").append(s.strokeColor ?: Long.MIN_VALUE)
            .append(",\"strokeWidthPx\":").append(s.strokeWidthPx)
        if (s.brushProfileId != null) sb.append(",\"brushProfileId\":\"").append(s.brushProfileId).append('"')
        if (s.fillColor != null) sb.append(",\"fillColor\":").append(s.fillColor)
        sb.append("}")
```

- [ ] **Step 5: JSON 읽기에 필드 추가**

`strokeRegex`를 이걸로 교체(기존 값 뒤에 옵션 그룹 하나 추가):

```kotlin
private val strokeRegex = Regex(
    "\\{\"color\":(-?\\d+),\"points\":\\[(.*?)](?:,\"cap\":\"(\\w+)\")?" +
        "(?:,\"fillEnabled\":(true|false),\"strokeColor\":(-?\\d+),\"strokeWidthPx\":(-?[0-9.eE+-]+))?" +
        "(?:,\"brushProfileId\":\"(.*?)\")?" +
        "(?:,\"fillColor\":(-?\\d+))?\\}",
)
```

`vectorPageFromJson`에서 `VectorStroke(color, points, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId)`를 만드는 줄을 이걸로 교체:

```kotlin
            val brushProfileId = m.groups[7]?.value?.ifBlank { null }
            val fillColor = m.groups[8]?.value?.toLong()
            VectorStroke(color, points, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId, fillColor)
```

(그 위에 있던 `val cap = ...`부터 `val brushProfileId = ...`까지는 그대로 둔다 — 이 두 줄만 교체.)

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorPageTest" --no-daemon`
Expected: PASS — 기존 10개 + 신규 2개 = 12개 전부 통과.

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorPage.kt app/src/test/java/com/g1/sketchbook/vector/VectorPageTest.kt
git commit -m "feat(vector): add fillColor field for closed-region fill"
```

---

### Task 3: VectorRenderer — 리본은 항상 채우고, 닫힌 구역도 채우기

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorRenderer.kt`

**Interfaces:**
- Consumes: `selfIntersectionFills`(Task 1), `VectorStroke.fillColor`(Task 2).
- Produces: `drawVectorPage`(시그니처는 그대로, 내부 동작만 변경).

이 파일은 유닛 테스트 대상이 아니다(android.graphics 의존) — 컴파일 확인만.

- [ ] **Step 1: 구현 교체**

`VectorRenderer.kt`의 `drawVectorPage` 함수(문서 주석 포함)를 이걸로 교체(`renderVectorPage`/`PREVIEW_PADDING_RATIO`는 그대로):

```kotlin
/** [page]의 모든 획을 [canvas]에 그린다 — 지금 펜으로 그린 획은 [strokeOutline]으로 계산한 리본
 *  다각형을 [VectorStroke.color]로 항상 채우고([VectorStroke.fillEnabled]와 무관 — 리본은 펜이
 *  실제로 지나간 자리라 항상 보여야 함), [VectorStroke.strokeColor]가 있으면 그 위에 폴리곤
 *  테두리를 그 색·[VectorStroke.strokeWidthPx] 굵기로 덧그린다. [VectorStroke.fillEnabled]면 그
 *  다음으로 [selfIntersectionFills]로 찾은 자기교차 폐곡선들을 [VectorStroke.fillColor]
 *  (없으면 [VectorStroke.color])로 채워 리본 위에 덧그린다 — 손으로 닫힌 도형을 그리면 그 내부가
 *  자동으로 채워지는 효과. [VectorStroke.brushProfileId]가 [stampBrushes]에서 찾아지면 위 전부
 *  대신 [stampPolygons]로 계산한 도장들을 [VectorStroke.color]로 채워 그린다(못 찾으면 지금
 *  펜으로 폴백). 그린 순서 그대로라 나중 획이 위에 덮인다. `VectorBrushView.onDraw`와 썸네일
 *  렌더링([renderVectorPage])이 이 함수 하나를 같이 쓴다 — 그리기 중인 화면과 저장되는 썸네일이
 *  항상 같은 방식으로 그려진다. */
fun drawVectorPage(canvas: Canvas, page: VectorPage, stampBrushes: Map<String, StampBrushProfile> = emptyMap()) {
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    for (stroke in page.strokes) {
        val profile = stroke.brushProfileId?.let { stampBrushes[it] }
        if (profile != null) {
            fillPaint.color = stroke.color.toInt()
            for (shape in stampPolygons(profile, stroke.points)) {
                if (shape.isEmpty()) continue
                val path = Path()
                shape.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            continue
        }
        val outline = strokeOutline(stroke.points, stroke.cap)
        if (outline.isEmpty()) continue
        val path = Path()
        outline.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.close()
        fillPaint.color = stroke.color.toInt()
        canvas.drawPath(path, fillPaint)
        stroke.strokeColor?.let { sc ->
            strokePaint.color = sc.toInt()
            strokePaint.strokeWidth = stroke.strokeWidthPx
            canvas.drawPath(path, strokePaint)
        }
        if (stroke.fillEnabled) {
            fillPaint.color = (stroke.fillColor ?: stroke.color).toInt()
            for (region in selfIntersectionFills(stroke.points)) {
                if (region.isEmpty()) continue
                val fillPath = Path()
                region.forEachIndexed { i, p -> if (i == 0) fillPath.moveTo(p.x, p.y) else fillPath.lineTo(p.x, p.y) }
                fillPath.close()
                canvas.drawPath(fillPath, fillPaint)
            }
        }
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남.

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorRenderer.kt
git commit -m "feat(vector): always fill the ribbon and fill self-intersecting closed regions"
```

---

### Task 4: VectorSvgExport — 내보내기에도 같은 규칙 적용

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorSvgExport.kt`
- Test: `app/src/test/java/com/g1/sketchbook/vector/VectorSvgExportTest.kt`

**Interfaces:**
- Consumes: `selfIntersectionFills`(Task 1), `VectorStroke.fillColor`(Task 2).

- [ ] **Step 1: 기존 테스트 중 새 동작과 어긋나는 것 교체**

`VectorSvgExportTest.kt`에서 다음 테스트를 찾는다:

```kotlin
    @Test fun fillDisabledRendersFillNone() {
        val page = VectorPage(listOf(VectorStroke(-65536L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), fillEnabled = false)))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 100f, 100f))
        assertTrue(svg.contains("fill=\"none\""))
        assertTrue(svg.contains("fill=\"#ff0000\"").not())
    }
```

이걸로 교체(`fillEnabled`의 의미가 바뀌어서 리본은 이제 항상 채워짐):

```kotlin
    @Test fun ribbonAlwaysFillsRegardlessOfFillEnabled() {
        // fillEnabled는 더 이상 "리본을 채울지"가 아니라 "자기교차 폐곡선을 채울지"를 뜻한다 —
        // 리본 자체는 fillEnabled=false여도 항상 획 색으로 채워져야 한다.
        val page = VectorPage(listOf(VectorStroke(-65536L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), fillEnabled = false)))
        val svg = vectorPageToSvg(page, Bounds(0f, 0f, 100f, 100f))
        assertTrue(svg.contains("fill=\"#ff0000\""))
        assertTrue(svg.contains("fill=\"none\"").not())
    }
```

- [ ] **Step 2: 새 테스트 추가**

`VectorSvgExportTest.kt` 클래스 맨 끝(마지막 `}` 앞)에 추가:

```kotlin
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
```

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorSvgExportTest" --no-daemon`
Expected: FAIL — `ribbonAlwaysFillsRegardlessOfFillEnabled`는 지금 코드가 `fill="none"`을 쓰므로 실패, 새 테스트들도 아직 없는 동작이라 실패.

- [ ] **Step 4: 구현 작성**

`VectorSvgExport.kt`의 `vectorPageToSvg` 함수 전체를 이걸로 교체(`colorHex`는 그대로):

```kotlin
/** [page]에서 [region]에 해당하는 부분만 아이콘용 SVG 문서 텍스트로 직렬화한다 — 지금 펜으로
 *  그린 획 하나는 리본 `<path>` 하나(항상 [VectorStroke.color]로 채움, [VectorStroke.fillEnabled]와
 *  무관) + [VectorStroke.fillEnabled]면 자기교차 폐곡선마다 [VectorStroke.fillColor](없으면
 *  [VectorStroke.color])로 채운 `<path>`가 추가로 붙는다. [VectorStroke.brushProfileId]가
 *  [stampBrushes]에서 찾아지면 위 전부 대신 찍힌 도장 하나하나를 각각 독립된 `<path>`로 풀어서
 *  쓴다(못 찾으면 지금 펜으로 폴백) — 획 하나가 여러 `<path>`가 될 수 있다는 뜻. 그린 순서 그대로
 *  유지. viewBox는 항상 "0 0 width height"로 시작하도록 [region]만큼 좌표를 평행이동한다(내보낸
 *  SVG가 원본 캔버스 좌표계를 몰라도 되게). 점이 하나도 [region] 안에 없는 도형은 건너뛴다 —
 *  부분적으로만 겹치는 도형은 지금은 잘라내지 않고 그대로 포함한다(잘라내기는 이 스펙 범위 밖).
 *  [VectorStroke.strokeColor]가 있으면 리본 위에 `stroke`/`stroke-width`도 같이 쓴다(SVG의
 *  fill·stroke 개념 그대로). 색은 ARGB Long에서 알파를 버리고 RGB만 "#rrggbb"로 쓴다(펜은 항상
 *  불투명). */
fun vectorPageToSvg(page: VectorPage, region: Bounds, stampBrushes: Map<String, StampBrushProfile> = emptyMap()): String {
    val w = region.width; val h = region.height
    val sb = StringBuilder()
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(w)
        .append("\" height=\"").append(h)
        .append("\" viewBox=\"0 0 ").append(w).append(' ').append(h).append("\">")
    for (stroke in page.strokes) {
        val profile = stroke.brushProfileId?.let { stampBrushes[it] }
        if (profile != null) {
            for (shape in stampPolygons(profile, stroke.points)) {
                if (shape.isEmpty()) continue
                val touches = shape.any { it.x >= region.minX && it.x <= region.maxX && it.y >= region.minY && it.y <= region.maxY }
                if (!touches) continue
                sb.append("<path d=\"M")
                shape.forEachIndexed { i, p ->
                    val x = p.x - region.minX; val y = p.y - region.minY
                    if (i == 0) sb.append(x).append(',').append(y) else sb.append(" L").append(x).append(',').append(y)
                }
                sb.append(" Z\" fill=\"").append(colorHex(stroke.color)).append("\"/>")
            }
            continue
        }
        val outline = strokeOutline(stroke.points, stroke.cap)
        if (outline.isEmpty()) continue
        val touchesRegion = outline.any { it.x >= region.minX && it.x <= region.maxX && it.y >= region.minY && it.y <= region.maxY }
        if (touchesRegion) {
            sb.append("<path d=\"M")
            outline.forEachIndexed { i, p ->
                val x = p.x - region.minX; val y = p.y - region.minY
                if (i == 0) sb.append(x).append(',').append(y)
                else sb.append(" L").append(x).append(',').append(y)
            }
            sb.append(" Z\" fill=\"").append(colorHex(stroke.color)).append('"')
            stroke.strokeColor?.let { sc ->
                sb.append(" stroke=\"").append(colorHex(sc)).append("\" stroke-width=\"").append(stroke.strokeWidthPx).append('"')
            }
            sb.append("/>")
        }
        if (stroke.fillEnabled) {
            val fillHex = colorHex(stroke.fillColor ?: stroke.color)
            for (closedRegion in selfIntersectionFills(stroke.points)) {
                if (closedRegion.isEmpty()) continue
                val touches = closedRegion.any { it.x >= region.minX && it.x <= region.maxX && it.y >= region.minY && it.y <= region.maxY }
                if (!touches) continue
                sb.append("<path d=\"M")
                closedRegion.forEachIndexed { i, p ->
                    val x = p.x - region.minX; val y = p.y - region.minY
                    if (i == 0) sb.append(x).append(',').append(y) else sb.append(" L").append(x).append(',').append(y)
                }
                sb.append(" Z\" fill=\"").append(fillHex).append("\"/>")
            }
        }
    }
    sb.append("</svg>")
    return sb.toString()
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorSvgExportTest" --no-daemon`
Expected: PASS — 기존 8개(1개는 새 이름/내용으로 교체됨) + 신규 3개 = 11개 전부 통과.

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorSvgExport.kt app/src/test/java/com/g1/sketchbook/vector/VectorSvgExportTest.kt
git commit -m "feat(vector): export self-intersecting closed regions as filled paths"
```

---

### Task 5: VectorBrushView — 채움 색 배선 + 지우개가 채운 영역도 인식

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorBrushView.kt`

**Interfaces:**
- Consumes: `selfIntersectionFills`(Task 1), `VectorStroke.fillColor`(Task 2).
- Produces: `VectorBrushView.fillColor: Long?`(다음 획에 적용, 기본 null). Task 6이 이 필드를 설정한다.

이 파일은 유닛 테스트 대상이 아니다(android.view 의존) — 컴파일 확인만.

- [ ] **Step 1: `fillColor` 필드 추가**

`VectorBrushView.kt`에서 다음 두 줄:

```kotlin
    var fillEnabled: Boolean = true
    var strokeColor: Long? = null
```

을 이걸로 교체:

```kotlin
    var fillEnabled: Boolean = true
    /** [fillEnabled]로 채워지는 자기교차 폐곡선의 색 — null이면 [color](리본 색)를 그대로 쓴다.
     *  "다음에 그릴 획"에만 영향(기존 획은 자기 자신의 값 그대로 씀). */
    var fillColor: Long? = null
    var strokeColor: Long? = null
```

- [ ] **Step 2: 새로 그리는 획에 `fillColor` 적용**

`onTouchEvent`의 `Tool.DRAW`/`ACTION_UP` 분기에서:

```kotlin
                                val stroke = VectorStroke(color, cur, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId)
```

를 이걸로 교체:

```kotlin
                                val stroke = VectorStroke(color, cur, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId, fillColor)
```

`onDraw`에서 그리는 중인 획(`current`) 미리보기 줄:

```kotlin
        current?.let { pts -> if (pts.size >= 2) drawVectorPage(canvas, VectorPage(listOf(VectorStroke(color, pts, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId))), stampBrushes) }
```

를 이걸로 교체(그리는 도중에도 자기 교차가 생기면 바로 채워진 모습이 보이려면, 이 라이브 프리뷰용 `VectorStroke`에도 `fillColor`가 실려야 한다):

```kotlin
        current?.let { pts -> if (pts.size >= 2) drawVectorPage(canvas, VectorPage(listOf(VectorStroke(color, pts, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId, fillColor))), stampBrushes) }
```

- [ ] **Step 3: 지우개가 채워진 폐곡선 내부를 눌러도 지워지게**

`eraseAt` 함수를 이걸로 교체:

```kotlin
    private fun eraseAt(x: Float, y: Float) {
        for (i in committed.indices.reversed()) {
            val stroke = committed[i]
            val profile = stroke.brushProfileId?.let { stampBrushes[it] }
            val hit = if (profile != null) {
                stampPolygons(profile, stroke.points).any { it.isNotEmpty() && pointInPolygon(x, y, it) }
            } else {
                val outline = strokeOutline(stroke.points, stroke.cap)
                val hitRibbon = outline.isNotEmpty() && pointInPolygon(x, y, outline)
                val hitFill = stroke.fillEnabled && selfIntersectionFills(stroke.points).any { pointInPolygon(x, y, it) }
                hitRibbon || hitFill
            }
            if (hit) {
                committed.removeAt(i)
                history.add(UndoOp.Erased(stroke))
                invalidate()
                onStrokeEnd?.invoke()
                return
            }
        }
    }
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorBrushView.kt
git commit -m "feat(vector): wire fillColor and let the eraser hit closed-region fills"
```

---

### Task 6: VectorCanvasScreen — 채움 색 스와치 UI(최소)

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorCanvasScreen.kt`

**Interfaces:**
- Consumes: `VectorBrushView.fillColor`(Task 5).

이 파일은 Compose UI라 유닛 테스트 대상이 아니다 — 컴파일 확인 + 수동 확인(브러시 설정 톱니바퀴 → 채우기 켜기 → 색 스와치가 나타나는지, 골라서 닫힌 도형을 그리면 그 색으로 채워지는지).

이번 태스크는 UI를 최소한만 손댄다(사용자가 명시적으로 요청) — 새 다이얼로그를 만들지 않고 기존 "브러시 설정" 드롭다운 메뉴 안에 한 줄만 추가한다.

- [ ] **Step 1: 상태 변수 추가**

`var fillEnabled by remember { mutableStateOf(true) }` 줄 바로 다음 줄에 추가:

```kotlin
    var fillColor by remember { mutableStateOf<Long?>(null) }
```

- [ ] **Step 2: `AndroidView` 팩토리에 배선**

`AndroidView`의 `factory = { ctx -> VectorBrushView(ctx).also { ... } }` 블록 안, `it.fillEnabled = fillEnabled` 줄 바로 다음 줄에 추가:

```kotlin
                    it.fillColor = fillColor
```

- [ ] **Step 3: "브러시 설정" 드롭다운에 채움 색 스와치 줄 추가**

`DropdownMenu(expanded = settingsMenuOpen, ...)` 안의 "채우기" `DropdownMenuItem`:

```kotlin
                    DropdownMenuItem(
                        text = { Text("채우기") },
                        trailingIcon = {
                            Switch(checked = fillEnabled, onCheckedChange = { fillEnabled = it; view?.fillEnabled = it })
                        },
                        onClick = { fillEnabled = !fillEnabled; view?.fillEnabled = fillEnabled },
                    )
```

바로 다음(같은 `DropdownMenu` 블록 안, 그 블록을 닫는 `}` 앞)에 추가:

```kotlin
                    if (fillEnabled) {
                        Text("채움 색", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            favorites.forEach { swatch ->
                                val selected = swatch == (fillColor ?: color)
                                Box(
                                    Modifier.size(24.dp).clip(CircleShape).background(Color(swatch))
                                        .border(if (selected) 2.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                        .bounceClick { fillColor = swatch; view?.fillColor = swatch },
                                )
                            }
                        }
                    }
```

(`Row`/`Box`/`Color`/`CircleShape`/`clip`/`background`/`border`/`bounceClick`/`Arrangement`는 이 파일에 이미 다 import돼 있다 — 새 import 불필요.)

- [ ] **Step 4: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorCanvasScreen.kt
git commit -m "feat(vector): add minimal fill-color swatch UI to the brush settings menu"
```

---

### Task 7: 전체 빌드·테스트 검증

**Files:** (없음 — 검증 전용 태스크, 문제가 발견되면 그 문제가 있는 파일을 그 자리에서 고친다.)

- [ ] **Step 1: 전체 유닛 테스트 실행**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --no-daemon`
Expected: PASS — Task 1·2·4에서 추가/교체한 신규 테스트와 기존 테스트 전부 통과.

- [ ] **Step 2: 전체 디버그 빌드**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 문제 발견 시 수정**

Step 1이나 2에서 실패가 나오면, 실패한 파일을 열어 원인을 고치고 `fix(vector): ...` 스타일로 별도 커밋한다. 실패가 없으면 이 태스크는 커밋할 변경사항이 없다.

---

## Self-Review

**스펙 커버리지 확인:**
- 판정 대상(한 획 안 자기교차만) → Task 1(`selfIntersectionFills`는 단일 `points` 목록만 받음, 다른 획과 무관).
- 실시간 → Task 3·5(그리는 중인 `current` 미리보기에도 같은 `drawVectorPage` 경로 적용, 매 `invalidate()`마다 재계산).
- 여러 교차 각각 독립 채움 → Task 1(`twoSequentialBowtiesEachProduceTheirOwnFill` 테스트).
- 선(리본)은 항상 보임 → Task 3(`fillPaint`로 무조건 채움, `fillEnabled` 체크 제거) + Task 4(SVG도 동일, `ribbonAlwaysFillsRegardlessOfFillEnabled` 테스트).
- 채움 색 별도 지정(기본값=리본 색) → Task 2(`fillColor` 필드) + Task 3·4(`stroke.fillColor ?: stroke.color`) + Task 6(스와치 UI).
- UI 최소화(기존 토글 재사용 + 스와치 한 줄) → Task 6.
- 스탬프 브러시 획은 대상 아님 → Task 3·4 모두 스탬프 분기(`profile != null`)는 그대로 두고 그 분기 밖에서만 새 로직 추가.
- 이번 스펙에서 다루지 않는 것(다른 획 합쳐진 영역, 리본 두께까지 고려한 정밀 경계, UI 전체 개편, 스탬프 획 자기교차) → 계획에 포함된 어떤 태스크도 이 항목들을 구현하지 않음(의도적으로 제외).

**플레이스홀더 스캔:** 전체 태스크 재확인 — "TBD"/"나중에 구현"/구체 코드 없는 단계 없음.

**타입 일관성 확인:** `VectorStroke(color, points, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId, fillColor)` — Task 2에서 정의한 순서 그대로 Task 5(VectorBrushView 두 생성 지점)에서 동일하게 사용. `selfIntersectionFills(points: List<VectorPoint>): List<List<Point>>` — Task 1에서 정의, Task 3·4·5에서 같은 시그니처로 호출. `VectorBrushView.fillColor: Long?` — Task 5에서 정의, Task 6에서 같은 이름으로 설정.
