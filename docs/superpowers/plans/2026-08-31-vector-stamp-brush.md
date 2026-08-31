# 벡터 스탬프/패턴 브러시 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 SVG 파일을 임포트해서 "스탬프 브러시"로 등록하면, 벡터 캔버스에서 그 모양을 선을 따라 반복해서 찍는(진행 방향에 맞춰 회전) 새 브러시 타입으로 쓸 수 있게 한다.

**Architecture:** SVG는 이 프로젝트가 이미 쓰는 "채워진 다각형(Point 목록)" 표현으로 곧바로 변환해서 저장한다(곡선은 잘게 쪼갠 직선으로 근사) — 별도 곡선 렌더링 경로 없이 기존 `drawVectorPage`/`vectorPageToSvg`가 다루는 도형 개념 그대로. 획(`VectorStroke`)에 `brushProfileId` 필드 하나만 추가해서 "이 획은 지금 펜이 아니라 이 스탬프 브러시로 그려졌다"를 표시하고, 중심선 좌표(`points`)는 그대로 저장 — 렌더링 시점마다 중심선을 따라 스탬프를 다시 배치해서 그린다(라쏘 이동/크기조절이 자동으로 같이 작동).

**Tech Stack:** Kotlin, Jetpack Compose, Android SAF(파일 선택), Firebase Realtime Database, kotlin.test(JVM 유닛 테스트).

## Global Constraints

- SVG는 `<path>`/`<rect>`/`<circle>`/`<ellipse>` + `<g>`(자식으로 `translate`/`scale` transform만, `rotate`는 지원 안 함)까지만 지원한다. 그 외(그라디언트, 텍스트, 클리핑, 다른 transform 등)는 조용히 무시.
- 찍힌 스탬프 색은 항상 획의 펜 색으로 틴트 — SVG 원본 fill/stroke 색은 파싱하지 않는다.
- 스탬프 간격(`spacingPx`)과 크기(`sizePx`)는 브러시 프로필마다 고정값 — 그리는 속도와 무관.
- 스탬프 브러시로 그린 획은 `cap`/`fillEnabled`/`strokeColor`/`strokeWidthPx`를 무시한다(전부 지금 펜 전용 필드).
- 이 프로젝트는 Robolectric/모킹이 없어 `android.*` 의존 클래스는 로컬 유닛 테스트(JVM)에서 못 돌린다 — `StampBrushRepository`/`BackupModels` 외 Backup 파일들/Compose 화면들은 유닛 테스트 대상이 아니다(컴파일 확인 + 수동 확인만). 순수 Kotlin 파일(`SvgPathParser.kt`, `SvgShapeParser.kt`, `StampBrush.kt`)만 TDD 대상.
- 커밋 메시지는 이 저장소의 기존 스타일(`feat:`/`fix:`/`refactor:` 등 conventional 접두사)을 따른다.

---

### Task 1: SvgPathParser — SVG path `d` 미니 언어 파서

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/SvgPathParser.kt`
- Test: `app/src/test/java/com/g1/sketchbook/vector/SvgPathParserTest.kt`

**Interfaces:**
- Produces: `fun parseSvgPath(d: String, steps: Int = 12): List<Point>` — SVG `<path d="...">`의 값 하나를 받아 잘게 쪼갠 직선들의 정점 목록으로 반환한다. `Point`는 `StrokeGeometry.kt`에 이미 있는 타입(같은 패키지, import 불필요). 이후 Task 2(`SvgShapeParser`)가 `<path>` 요소를 만날 때마다 이 함수를 부른다.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/g1/sketchbook/vector/SvgPathParserTest.kt`:

```kotlin
package com.g1.sketchbook.vector

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SvgPathParserTest {
    private fun assertPointsClose(expected: Point, actual: Point, tolerance: Float = 0.01f) {
        assertTrue(abs(expected.x - actual.x) < tolerance && abs(expected.y - actual.y) < tolerance,
            "expected $expected but was $actual")
    }

    @Test fun moveAndLineAbsolute() {
        val pts = parseSvgPath("M0,0 L10,0 L10,10 Z")
        assertEquals(listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f), Point(0f, 0f)), pts)
    }

    @Test fun lineRelative() {
        val pts = parseSvgPath("M0,0 l10,0 l0,10 z")
        assertEquals(listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f), Point(0f, 0f)), pts)
    }

    @Test fun horizontalAndVerticalLines() {
        val pts = parseSvgPath("M0,0 H10 V10 H0 Z")
        assertEquals(listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f), Point(0f, 10f), Point(0f, 0f)), pts)
    }

    @Test fun repeatedCommandWithoutRepeatingLetter() {
        // "L10,0 10,10"는 두 번째 좌표쌍 앞에 L이 생략된 것 — SVG 스펙상 같은 명령이 반복된다.
        val pts = parseSvgPath("M0,0 L10,0 10,10")
        assertEquals(listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f)), pts)
    }

    @Test fun adjacentNumbersWithoutSeparators() {
        // "1.2.3" -> "1.2"와 ".3" 두 숫자(소수점이 두 번째부터 새 숫자 시작을 뜻함) — 압축된 SVG에서 흔함.
        val pts = parseSvgPath("M0,0L1.2.3,0")
        assertEquals(listOf(Point(0f, 0f), Point(1.2f, 0.3f)), pts)
    }

    @Test fun cubicBezierEndsAtFinalControlPoint() {
        val pts = parseSvgPath("M0,0 C0,10 10,10 10,0", steps = 4)
        assertEquals(4, pts.size - 1) // moveTo 점 1개 + steps개
        assertPointsClose(Point(10f, 0f), pts.last())
    }

    @Test fun cubicBezierMidpointMatchesFormula() {
        // t=0.5에서의 3차 베지어 공식값과 비교(대칭 케이스라 y는 7.5가 되어야 함)
        val pts = parseSvgPath("M0,0 C0,10 10,10 10,0", steps = 2)
        assertPointsClose(Point(5f, 7.5f), pts[1])
    }

    @Test fun quadraticBezierEndsAtFinalPoint() {
        val pts = parseSvgPath("M0,0 Q5,10 10,0", steps = 4)
        assertPointsClose(Point(10f, 0f), pts.last())
        assertPointsClose(Point(5f, 5f), pts[2]) // t=0.5 지점, 대칭이라 y는 (0+2*10+0)/4=5
    }

    @Test fun smoothCubicReflectsPreviousControlPoint() {
        // 첫 C의 두번째 컨트롤포인트(10,10)를 S가 반사해서 이어받아야 부드럽게 이어짐 —
        // 정확한 좌표보다 "끝점까지 도달"과 "점 개수"만 확인.
        val pts = parseSvgPath("M0,0 C0,10 10,10 10,0 S20,-10 20,0", steps = 4)
        assertPointsClose(Point(20f, 0f), pts.last())
    }

    @Test fun arcFromZeroToTenStaysNearRadius() {
        // 반지름 5인 원호, (0,0)에서 (10,0)까지 — 중심은 (5,0) 근처여야 하고, 중간 지점들은
        // 중심에서 반지름(5)만큼 떨어져 있어야 한다.
        val pts = parseSvgPath("M0,0 A5,5 0 0 1 10,0", steps = 8)
        assertPointsClose(Point(10f, 0f), pts.last())
        for (p in pts) {
            val d = kotlin.math.sqrt((p.x - 5f) * (p.x - 5f) + p.y * p.y)
            assertTrue(abs(d - 5f) < 0.05f, "point $p not on radius-5 circle around (5,0)")
        }
    }

    @Test fun zeroRadiusArcDegeneratesToLine() {
        val pts = parseSvgPath("M0,0 A0,0 0 0 1 10,10")
        assertEquals(listOf(Point(0f, 0f), Point(10f, 10f)), pts)
    }

    @Test fun unknownCommandStopsGracefullyWithPartialResult() {
        val pts = parseSvgPath("M0,0 L10,0 X999,999")
        assertEquals(listOf(Point(0f, 0f), Point(10f, 0f)), pts)
    }

    @Test fun emptyPathReturnsEmptyList() {
        assertEquals(emptyList(), parseSvgPath(""))
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.SvgPathParserTest" --no-daemon`
Expected: FAIL — `parseSvgPath`가 아직 없어서 컴파일 에러.

- [ ] **Step 3: 구현 작성**

`app/src/main/java/com/g1/sketchbook/vector/SvgPathParser.kt` 전체:

```kotlin
package com.g1.sketchbook.vector

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** SVG 경로 데이터(`<path d="...">`)의 미니 언어를 파싱해서 잘게 쪼갠 직선들로 근사한 정점 목록을
 *  만든다 — 곡선 자체를 유지하지 않고 바로 폴리곤 정점으로 변환하는 이유는, 이 프로젝트가 이미
 *  모든 벡터 도형을 "채워진 다각형"(`Point` 목록)으로 표현하기 때문(`strokeOutline`과 같은 표현).
 *  지원 명령: M/m L/l H/h V/v C/c S/s Q/q T/t A/a Z/z(대문자=절대, 소문자=상대). 명령 문자를 생략한
 *  좌표쌍 반복(SVG 스펙 규칙)도 지원. 알 수 없는 명령을 만나면 그 지점까지 파싱한 결과를 그대로
 *  반환한다(예외 없음 — 잘못된 파일 일부만 깨져도 나머지는 쓸 수 있게). [steps]는 곡선 하나를 몇
 *  개의 직선으로 근사할지. */
fun parseSvgPath(d: String, steps: Int = 12): List<Point> {
    val points = mutableListOf<Point>()
    if (d.isBlank()) return points
    var cx = 0f; var cy = 0f
    var startX = 0f; var startY = 0f
    var prevCtrlX = 0f; var prevCtrlY = 0f
    var prevCmd = ' '

    val tokens = tokenizeSvgPath(d)
    var i = 0
    fun hasNum() = i < tokens.size && (tokens[i][0].isDigit() || tokens[i][0] == '-' || tokens[i][0] == '+' || tokens[i][0] == '.')
    fun nextNum(): Float = tokens[i++].toFloat()

    while (i < tokens.size) {
        val tok = tokens[i]
        val cmd = if (tok.length == 1 && tok[0].isLetter()) {
            i++; tok[0]
        } else if (hasNum()) {
            when (prevCmd) { 'M' -> 'L'; 'm' -> 'l'; else -> prevCmd }
        } else return points

        when (cmd) {
            'M' -> { cx = nextNum(); cy = nextNum(); startX = cx; startY = cy; points.add(Point(cx, cy)) }
            'm' -> { cx += nextNum(); cy += nextNum(); startX = cx; startY = cy; points.add(Point(cx, cy)) }
            'L' -> { cx = nextNum(); cy = nextNum(); points.add(Point(cx, cy)) }
            'l' -> { cx += nextNum(); cy += nextNum(); points.add(Point(cx, cy)) }
            'H' -> { cx = nextNum(); points.add(Point(cx, cy)) }
            'h' -> { cx += nextNum(); points.add(Point(cx, cy)) }
            'V' -> { cy = nextNum(); points.add(Point(cx, cy)) }
            'v' -> { cy += nextNum(); points.add(Point(cx, cy)) }
            'C' -> {
                val x1 = nextNum(); val y1 = nextNum(); val x2 = nextNum(); val y2 = nextNum(); val x = nextNum(); val y = nextNum()
                addCubic(points, cx, cy, x1, y1, x2, y2, x, y, steps); prevCtrlX = x2; prevCtrlY = y2; cx = x; cy = y
            }
            'c' -> {
                val x1 = cx + nextNum(); val y1 = cy + nextNum(); val x2 = cx + nextNum(); val y2 = cy + nextNum(); val x = cx + nextNum(); val y = cy + nextNum()
                addCubic(points, cx, cy, x1, y1, x2, y2, x, y, steps); prevCtrlX = x2; prevCtrlY = y2; cx = x; cy = y
            }
            'S' -> {
                val x1 = if (prevCmd in "CcSs") 2 * cx - prevCtrlX else cx
                val y1 = if (prevCmd in "CcSs") 2 * cy - prevCtrlY else cy
                val x2 = nextNum(); val y2 = nextNum(); val x = nextNum(); val y = nextNum()
                addCubic(points, cx, cy, x1, y1, x2, y2, x, y, steps); prevCtrlX = x2; prevCtrlY = y2; cx = x; cy = y
            }
            's' -> {
                val x1 = if (prevCmd in "CcSs") 2 * cx - prevCtrlX else cx
                val y1 = if (prevCmd in "CcSs") 2 * cy - prevCtrlY else cy
                val x2 = cx + nextNum(); val y2 = cy + nextNum(); val x = cx + nextNum(); val y = cy + nextNum()
                addCubic(points, cx, cy, x1, y1, x2, y2, x, y, steps); prevCtrlX = x2; prevCtrlY = y2; cx = x; cy = y
            }
            'Q' -> {
                val x1 = nextNum(); val y1 = nextNum(); val x = nextNum(); val y = nextNum()
                addQuad(points, cx, cy, x1, y1, x, y, steps); prevCtrlX = x1; prevCtrlY = y1; cx = x; cy = y
            }
            'q' -> {
                val x1 = cx + nextNum(); val y1 = cy + nextNum(); val x = cx + nextNum(); val y = cy + nextNum()
                addQuad(points, cx, cy, x1, y1, x, y, steps); prevCtrlX = x1; prevCtrlY = y1; cx = x; cy = y
            }
            'T' -> {
                val x1 = if (prevCmd in "QqTt") 2 * cx - prevCtrlX else cx
                val y1 = if (prevCmd in "QqTt") 2 * cy - prevCtrlY else cy
                val x = nextNum(); val y = nextNum()
                addQuad(points, cx, cy, x1, y1, x, y, steps); prevCtrlX = x1; prevCtrlY = y1; cx = x; cy = y
            }
            't' -> {
                val x1 = if (prevCmd in "QqTt") 2 * cx - prevCtrlX else cx
                val y1 = if (prevCmd in "QqTt") 2 * cy - prevCtrlY else cy
                val x = cx + nextNum(); val y = cy + nextNum()
                addQuad(points, cx, cy, x1, y1, x, y, steps); prevCtrlX = x1; prevCtrlY = y1; cx = x; cy = y
            }
            'A' -> {
                val rx = nextNum(); val ry = nextNum(); val rot = nextNum()
                val largeArc = nextNum() != 0f; val sweep = nextNum() != 0f
                val x = nextNum(); val y = nextNum()
                addArc(points, cx, cy, rx, ry, rot, largeArc, sweep, x, y, steps); cx = x; cy = y
            }
            'a' -> {
                val rx = nextNum(); val ry = nextNum(); val rot = nextNum()
                val largeArc = nextNum() != 0f; val sweep = nextNum() != 0f
                val x = cx + nextNum(); val y = cy + nextNum()
                addArc(points, cx, cy, rx, ry, rot, largeArc, sweep, x, y, steps); cx = x; cy = y
            }
            'Z', 'z' -> { cx = startX; cy = startY; points.add(Point(cx, cy)) }
            else -> return points
        }
        prevCmd = cmd
    }
    return points
}

private fun tokenizeSvgPath(d: String): List<String> {
    val tokens = mutableListOf<String>()
    var i = 0
    val n = d.length
    while (i < n) {
        val c = d[i]
        when {
            c.isWhitespace() || c == ',' -> i++
            c.isLetter() -> { tokens.add(c.toString()); i++ }
            c == '-' || c == '+' || c.isDigit() || c == '.' -> {
                val start = i
                if (c == '-' || c == '+') i++
                var sawDot = false
                while (i < n && (d[i].isDigit() || (d[i] == '.' && !sawDot))) {
                    if (d[i] == '.') sawDot = true
                    i++
                }
                if (i < n && (d[i] == 'e' || d[i] == 'E')) {
                    i++
                    if (i < n && (d[i] == '-' || d[i] == '+')) i++
                    while (i < n && d[i].isDigit()) i++
                }
                tokens.add(d.substring(start, i))
            }
            else -> i++
        }
    }
    return tokens
}

private fun addCubic(points: MutableList<Point>, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, steps: Int) {
    for (s in 1..steps) {
        val t = s.toFloat() / steps
        val mt = 1f - t
        val x = mt * mt * mt * x0 + 3 * mt * mt * t * x1 + 3 * mt * t * t * x2 + t * t * t * x3
        val y = mt * mt * mt * y0 + 3 * mt * mt * t * y1 + 3 * mt * t * t * y2 + t * t * t * y3
        points.add(Point(x, y))
    }
}

private fun addQuad(points: MutableList<Point>, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, steps: Int) {
    for (s in 1..steps) {
        val t = s.toFloat() / steps
        val mt = 1f - t
        val x = mt * mt * x0 + 2 * mt * t * x1 + t * t * x2
        val y = mt * mt * y0 + 2 * mt * t * y1 + t * t * y2
        points.add(Point(x, y))
    }
}

/** SVG 1.1 스펙 부록 F.6.5의 "끝점 → 중심" 타원호 변환을 그대로 구현 — [x0],[y0]에서 [x],[y]까지
 *  반지름 [rxIn]/[ryIn], x축 회전 [xAxisRotDeg]도, [largeArc]/[sweep] 플래그로 정해지는 타원호를
 *  중심각 기반으로 바꿔서 [steps]개 직선으로 근사한다. */
private fun addArc(
    points: MutableList<Point>, x0: Float, y0: Float,
    rxIn: Float, ryIn: Float, xAxisRotDeg: Float, largeArc: Boolean, sweep: Boolean,
    x: Float, y: Float, steps: Int,
) {
    if (rxIn == 0f || ryIn == 0f || (x0 == x && y0 == y)) { points.add(Point(x, y)); return }
    var rx = abs(rxIn).toDouble(); var ry = abs(ryIn).toDouble()
    val phi = Math.toRadians(xAxisRotDeg.toDouble())
    val cosPhi = cos(phi); val sinPhi = sin(phi)
    val dx2 = (x0 - x) / 2.0; val dy2 = (y0 - y) / 2.0
    val x1p = cosPhi * dx2 + sinPhi * dy2
    val y1p = -sinPhi * dx2 + cosPhi * dy2
    val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if (lambda > 1.0) { val s = sqrt(lambda); rx *= s; ry *= s }
    val sign = if (largeArc != sweep) 1.0 else -1.0
    val num = max(0.0, rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p)
    val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    val coef = if (den == 0.0) 0.0 else sign * sqrt(num / den)
    val cxp = coef * (rx * y1p / ry)
    val cyp = coef * -(ry * x1p / rx)
    val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x) / 2.0
    val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y) / 2.0

    fun angleBetween(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val dot = ux * vx + uy * vy
        val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
        var ang = acos((dot / len).coerceIn(-1.0, 1.0))
        if (ux * vy - uy * vx < 0) ang = -ang
        return ang
    }
    val theta1 = angleBetween(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    var deltaTheta = angleBetween((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if (!sweep && deltaTheta > 0) deltaTheta -= 2 * Math.PI
    if (sweep && deltaTheta < 0) deltaTheta += 2 * Math.PI

    for (s in 1..steps) {
        val t = theta1 + deltaTheta * s / steps
        val px = cosPhi * rx * cos(t) - sinPhi * ry * sin(t) + cx
        val py = sinPhi * rx * cos(t) + cosPhi * ry * sin(t) + cy
        points.add(Point(px.toFloat(), py.toFloat()))
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.SvgPathParserTest" --no-daemon`
Expected: PASS, 13개 테스트 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/SvgPathParser.kt app/src/test/java/com/g1/sketchbook/vector/SvgPathParserTest.kt
git commit -m "feat(vector): add an SVG path-data (d attribute) parser"
```

---

### Task 2: SvgShapeParser — SVG 문서 → 정규화된 다각형 목록

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/SvgShapeParser.kt`
- Test: `app/src/test/java/com/g1/sketchbook/vector/SvgShapeParserTest.kt`

**Interfaces:**
- Consumes: `parseSvgPath(d, steps)` (Task 1), `Point`(기존, `StrokeGeometry.kt`).
- Produces: `fun parseSvgDocument(svgText: String): List<List<Point>>?` — SVG 문서 전체 텍스트를 받아 도형별 다각형 목록을 반환. 파싱할 도형이 하나도 없거나(빈 SVG, 지원 안 하는 요소만 있음) 최상위 `<svg>` 태그 자체가 없으면 `null`(호출부가 "임포트 실패"로 처리). 반환값은 이미 정규화(경계상자 중심이 원점, 가장 긴 변이 길이 1)돼 있다. Task 3(`StampBrushProfile`)이 이 함수로 임포트 시점에 파싱해서 저장한다.

이 파일은 정규식 기반 XML 속성 추출(이 프로젝트가 이미 `VectorPage.kt`에서 쓰는 방식과 같은 "완전한 XML 파서 대신 우리가 필요한 만�큼만 정규식으로 뽑기")을 쓴다 — 일반 XML 파서를 새로 끌어오지 않는다.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/g1/sketchbook/vector/SvgShapeParserTest.kt`:

```kotlin
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
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.SvgShapeParserTest" --no-daemon`
Expected: FAIL — `parseSvgDocument`가 아직 없어서 컴파일 에러.

- [ ] **Step 3: 구현 작성**

`app/src/main/java/com/g1/sketchbook/vector/SvgShapeParser.kt` 전체:

```kotlin
package com.g1.sketchbook.vector

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** SVG 문서 텍스트 전체를 파싱해서 도형별 다각형 목록으로 바꾼다 — `<path>`/`<rect>`/`<circle>`/
 *  `<ellipse>`와, `translate`/`scale`만 걸린 `<g>` 그룹(자식들에 그 이동/배율을 적용)을 지원한다.
 *  `rotate`가 걸린 `<g>`는 그 그룹 전체를 건너뛴다(에러 아님 — 나머지 도형은 그대로 씀). 일반 XML
 *  파서 대신 이 프로젝트가 이미 쓰는 방식대로 정규식으로 태그를 하나씩 찾아 처리한다. 결과는
 *  [normalizeShapes]로 정규화(경계상자 중심 원점, 가장 긴 변 길이 1)해서 반환 — 도형이 하나도
 *  없거나 `<svg` 태그 자체가 없으면 null. */
fun parseSvgDocument(svgText: String): List<List<Point>>? {
    if (!svgText.contains("<svg")) return null
    val shapes = mutableListOf<List<Point>>()
    parseElements(svgText, 1f, 0f, 0f, shapes)
    if (shapes.isEmpty()) return null
    return normalizeShapes(shapes)
}

private val tagRegex = Regex("<(path|rect|circle|ellipse|g)\\b([^>/]*?)(/>|>(.*?)</\\1>)", RegexOption.DOT_MATCHES_ALL)
private val attrRegex = Regex("(\\w[\\w-]*)\\s*=\\s*\"([^\"]*)\"")

private fun attrs(raw: String): Map<String, String> =
    attrRegex.findAll(raw).associate { it.groupValues[1] to it.groupValues[2] }

/** [scale]/[dx]/[dy]는 지금까지 누적된 조상 `<g translate/scale>`의 효과 — 자식 도형의 좌표를 이
 *  누적값으로 변환해서 [into]에 최종(정규화 전) 좌표로 추가한다. */
private fun parseElements(xml: String, scale: Float, dx: Float, dy: Float, into: MutableList<List<Point>>) {
    for (m in tagRegex.findAll(xml)) {
        val tag = m.groupValues[1]
        val rawAttrs = attrs(m.groupValues[2])
        when (tag) {
            "path" -> {
                val d = rawAttrs["d"] ?: continue
                val pts = parseSvgPath(d)
                if (pts.size >= 2) into.add(pts.map { Point(it.x * scale + dx, it.y * scale + dy) })
            }
            "rect" -> {
                val x = rawAttrs["x"]?.toFloatOrNull() ?: 0f
                val y = rawAttrs["y"]?.toFloatOrNull() ?: 0f
                val w = rawAttrs["width"]?.toFloatOrNull() ?: continue
                val h = rawAttrs["height"]?.toFloatOrNull() ?: continue
                val poly = listOf(Point(x, y), Point(x + w, y), Point(x + w, y + h), Point(x, y + h))
                into.add(poly.map { Point(it.x * scale + dx, it.y * scale + dy) })
            }
            "circle" -> {
                val cx = rawAttrs["cx"]?.toFloatOrNull() ?: 0f
                val cy = rawAttrs["cy"]?.toFloatOrNull() ?: 0f
                val r = rawAttrs["r"]?.toFloatOrNull() ?: continue
                into.add(ellipsePolygon(cx, cy, r, r).map { Point(it.x * scale + dx, it.y * scale + dy) })
            }
            "ellipse" -> {
                val cx = rawAttrs["cx"]?.toFloatOrNull() ?: 0f
                val cy = rawAttrs["cy"]?.toFloatOrNull() ?: 0f
                val rx = rawAttrs["rx"]?.toFloatOrNull() ?: continue
                val ry = rawAttrs["ry"]?.toFloatOrNull() ?: continue
                into.add(ellipsePolygon(cx, cy, rx, ry).map { Point(it.x * scale + dx, it.y * scale + dy) })
            }
            "g" -> {
                val transform = rawAttrs["transform"]
                if (transform != null && transform.contains("rotate")) continue // 회전 그룹은 건너뜀
                var gScale = 1f; var gDx = 0f; var gDy = 0f
                transform?.let { t ->
                    Regex("translate\\(([-\\d.]+)[ ,]+([-\\d.]+)\\)").find(t)?.let { tm ->
                        gDx = tm.groupValues[1].toFloat(); gDy = tm.groupValues[2].toFloat()
                    }
                    Regex("scale\\(([-\\d.]+)\\)").find(t)?.let { sm -> gScale = sm.groupValues[1].toFloat() }
                }
                val inner = m.groupValues[4]
                // 부모 스케일/이동에 이 그룹 자신의 translate/scale을 이어붙인다(부모 먼저 적용된 좌표계 위에).
                parseElements(inner, scale * gScale, dx + gDx * scale, dy + gDy * scale, into)
            }
        }
    }
}

private fun ellipsePolygon(cx: Float, cy: Float, rx: Float, ry: Float, sides: Int = 24): List<Point> =
    (0 until sides).map { i ->
        val t = 2 * PI * i / sides
        Point(cx + rx * cos(t).toFloat(), cy + ry * sin(t).toFloat())
    }

/** 모든 도형을 합친 경계상자를 계산해서, 그 중심이 원점에 오고 가장 긴 변이 길이 1이 되도록
 *  전부 같은 배율로 스케일+평행이동한다. */
private fun normalizeShapes(shapes: List<List<Point>>): List<List<Point>> {
    val all = shapes.flatten()
    val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
    val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
    val w = maxX - minX; val h = maxY - minY
    val longest = maxOf(w, h).takeIf { it > 0f } ?: 1f
    val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f
    return shapes.map { shape -> shape.map { Point((it.x - cx) / longest, (it.y - cy) / longest) } }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.SvgShapeParserTest" --no-daemon`
Expected: PASS, 11개 테스트 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/SvgShapeParser.kt app/src/test/java/com/g1/sketchbook/vector/SvgShapeParserTest.kt
git commit -m "feat(vector): parse SVG documents into normalized polygon shapes"
```

---

### Task 3: StampBrush — 데이터 모델, JSON 직렬화, 찍기 알고리즘

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/StampBrush.kt`
- Test: `app/src/test/java/com/g1/sketchbook/vector/StampBrushTest.kt`

**Interfaces:**
- Consumes: `Point`(기존).
- Produces: `data class StampBrushProfile(id: String, name: String, shapes: List<List<Point>>, spacingPx: Float = 24f, sizePx: Float = 32f)`, `fun StampBrushProfile.toJson(): String`, `fun stampBrushProfileFromJson(json: String): StampBrushProfile?`, `fun stampPolygons(profile: StampBrushProfile, points: List<VectorPoint>): List<List<Point>>`. Task 6(`VectorRenderer`)·Task 7(`VectorSvgExport`)이 `stampPolygons`를 쓰고, Task 8(`StampBrushRepository`)이 `toJson`/`fromJson`을 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/g1/sketchbook/vector/StampBrushTest.kt`:

```kotlin
package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StampBrushTest {
    private val square = listOf(Point(-0.5f, -0.5f), Point(0.5f, -0.5f), Point(0.5f, 0.5f), Point(-0.5f, 0.5f))

    @Test fun roundTripsThroughJson() {
        val profile = StampBrushProfile("id1", "내 브러시", listOf(square), spacingPx = 20f, sizePx = 16f)
        assertEquals(profile, stampBrushProfileFromJson(profile.toJson()))
    }

    @Test fun malformedJsonReturnsNull() {
        assertNull(stampBrushProfileFromJson("not json"))
    }

    @Test fun stampPolygonsPlacesOneStampPerSpacingInterval() {
        // 길이 100인 수평선, 간격 25 -> 0,25,50,75,100 다섯 지점에 찍힘.
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 25f, sizePx = 10f)
        val centerline = listOf(VectorPoint(0f, 0f, 4f), VectorPoint(100f, 0f, 4f))
        val stamped = stampPolygons(profile, centerline)
        assertEquals(5, stamped.size)
    }

    @Test fun stampIsScaledToSizePx() {
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 100f, sizePx = 10f)
        val centerline = listOf(VectorPoint(0f, 0f, 4f), VectorPoint(100f, 0f, 4f))
        val stamped = stampPolygons(profile, centerline)
        val first = stamped[0]
        val w = first.maxOf { it.x } - first.minOf { it.x }
        assertTrue(kotlin.math.abs(w - 10f) < 0.01f) // 정규화된 사각형(폭 1) * sizePx(10) = 10
    }

    @Test fun stampIsCenteredOnCenterlinePoint() {
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 100f, sizePx = 10f)
        val centerline = listOf(VectorPoint(50f, 30f, 4f), VectorPoint(150f, 30f, 4f))
        val stamped = stampPolygons(profile, centerline)
        val first = stamped[0]
        val cx = (first.minOf { it.x } + first.maxOf { it.x }) / 2f
        val cy = (first.minOf { it.y } + first.maxOf { it.y }) / 2f
        assertTrue(kotlin.math.abs(cx - 50f) < 0.01f && kotlin.math.abs(cy - 30f) < 0.01f)
    }

    @Test fun tooShortCenterlineProducesNoStamps() {
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 100f, sizePx = 10f)
        val centerline = listOf(VectorPoint(0f, 0f, 4f), VectorPoint(1f, 0f, 4f))
        assertEquals(0, stampPolygons(profile, centerline).size)
    }

    @Test fun singlePointCenterlineProducesNoStamps() {
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 10f, sizePx = 10f)
        assertEquals(0, stampPolygons(profile, listOf(VectorPoint(0f, 0f, 4f))).size)
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.StampBrushTest" --no-daemon`
Expected: FAIL — 아직 아무것도 없어서 컴파일 에러.

- [ ] **Step 3: 구현 작성**

`app/src/main/java/com/g1/sketchbook/vector/StampBrush.kt` 전체:

```kotlin
package com.g1.sketchbook.vector

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 사용자가 임포트한 SVG 하나 = 스탬프 브러시 프로필 하나. [shapes]는 이미 [parseSvgDocument]로
 *  정규화(경계상자 중심 원점, 가장 긴 변 길이 1)된 다각형 목록 — 찍을 때 [sizePx]만큼 스케일만
 *  하면 된다. [spacingPx]/[sizePx] 둘 다 그리는 속도와 무관한 고정값. */
data class StampBrushProfile(
    val id: String,
    val name: String,
    val shapes: List<List<Point>>,
    val spacingPx: Float = 24f,
    val sizePx: Float = 32f,
)

/** [VectorPage.toJson]과 같은 손수 문자열 조립 방식(이 프로젝트는 범용 JSON 파서를 안 씀) —
 *  `shapes`의 각 도형을 `{"points":[...]}` 하나로 감싸서, [VectorPage.strokeRegex]와 똑같은
 *  "비탐욕 정규식으로 `]}` 앞까지 잘라내기" 트릭을 그대로 재사용할 수 있게 한다(중첩 배열을 직접
 *  파싱하는 것보다 이미 검증된 패턴을 반복하는 쪽이 안전). */
fun StampBrushProfile.toJson(): String {
    val sb = StringBuilder("{\"id\":\"").append(id).append("\",\"name\":\"").append(name)
        .append("\",\"spacingPx\":").append(spacingPx).append(",\"sizePx\":").append(sizePx)
        .append(",\"shapes\":[")
    shapes.forEachIndexed { si, shape ->
        if (si > 0) sb.append(',')
        sb.append("{\"points\":[")
        shape.forEachIndexed { pi, p ->
            if (pi > 0) sb.append(',')
            sb.append("{\"x\":").append(p.x).append(",\"y\":").append(p.y).append('}')
        }
        sb.append("]}")
    }
    sb.append("]}")
    return sb.toString()
}

private val stampMetaRegex = Regex("\"id\":\"(.*?)\",\"name\":\"(.*?)\",\"spacingPx\":(-?[0-9.eE+-]+),\"sizePx\":(-?[0-9.eE+-]+)")
private val shapeRegex = Regex("\\{\"points\":\\[(.*?)]\\}")
private val stampPointRegex = Regex("\\{\"x\":(-?[0-9.eE+-]+),\"y\":(-?[0-9.eE+-]+)\\}")

fun stampBrushProfileFromJson(json: String): StampBrushProfile? = runCatching {
    val meta = stampMetaRegex.find(json) ?: return null
    val shapesText = json.substringAfter("\"shapes\":[")
    val shapes = shapeRegex.findAll(shapesText).map { sm ->
        stampPointRegex.findAll(sm.groupValues[1]).map { pm -> Point(pm.groupValues[1].toFloat(), pm.groupValues[2].toFloat()) }.toList()
    }.toList()
    StampBrushProfile(meta.groupValues[1], meta.groupValues[2], shapes, meta.groupValues[3].toFloat(), meta.groupValues[4].toFloat())
}.getOrNull()

/** [points](획의 중심선)를 따라 호 길이 기준 [StampBrushProfile.spacingPx]마다 [StampBrushProfile.shapes]를
 *  하나씩 찍는다 — 각 지점에서: 정규화된 스탬프 다각형들을 그 지점의 진행 방향(접선) 각도만큼
 *  회전 -> [StampBrushProfile.sizePx]만큼 스케일 -> 그 지점 좌표로 평행이동. 중심선 전체 길이가
 *  [StampBrushProfile.spacingPx]보다 짧으면(또는 점이 2개 미만이면) 찍을 자리가 없어 빈 목록. */
fun stampPolygons(profile: StampBrushProfile, points: List<VectorPoint>): List<List<Point>> {
    if (points.size < 2 || profile.shapes.isEmpty()) return emptyList()
    // 누적 호 길이 테이블 — 중심선을 따라 이동한 총 거리 하나씩.
    val cumulative = DoubleArray(points.size)
    for (i in 1 until points.size) {
        val dx = (points[i].x - points[i - 1].x).toDouble(); val dy = (points[i].y - points[i - 1].y).toDouble()
        cumulative[i] = cumulative[i - 1] + hypot(dx, dy)
    }
    val totalLen = cumulative.last()
    if (totalLen < profile.spacingPx) return emptyList()

    val result = mutableListOf<List<Point>>()
    var target = 0.0
    var seg = 1
    while (target <= totalLen) {
        while (seg < points.size - 1 && cumulative[seg] < target) seg++
        val segStart = cumulative[seg - 1]; val segEnd = cumulative[seg]
        val segLen = segEnd - segStart
        val t = if (segLen > 0.0) ((target - segStart) / segLen).toFloat() else 0f
        val p0 = points[seg - 1]; val p1 = points[seg]
        val px = p0.x + (p1.x - p0.x) * t
        val py = p0.y + (p1.y - p0.y) * t
        val angle = kotlin.math.atan2((p1.y - p0.y).toDouble(), (p1.x - p0.x).toDouble()).toFloat()
        val cosA = cos(angle); val sinA = sin(angle)
        for (shape in profile.shapes) {
            result.add(shape.map { local ->
                val sx = local.x * profile.sizePx; val sy = local.y * profile.sizePx
                Point(px + sx * cosA - sy * sinA, py + sx * sinA + sy * cosA)
            })
        }
        target += profile.spacingPx
    }
    return result
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.StampBrushTest" --no-daemon`
Expected: PASS, 7개 테스트 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/StampBrush.kt app/src/test/java/com/g1/sketchbook/vector/StampBrushTest.kt
git commit -m "feat(vector): add stamp brush profile model, json, and stamping algorithm"
```

---

### Task 4: VectorPage — `brushProfileId` 필드 추가

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorPage.kt`
- Test: `app/src/test/java/com/g1/sketchbook/vector/VectorPageTest.kt`

**Interfaces:**
- Produces: `VectorStroke.brushProfileId: String?`(기본 `null`). Task 6·7·9가 이 필드로 스탬프 획인지 분기한다.

- [ ] **Step 1: 실패하는 테스트 추가**

`VectorPageTest.kt` 맨 끝(마지막 `}` 앞)에 추가:

```kotlin
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
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorPageTest" --no-daemon`
Expected: FAIL — `brushProfileId` 파라미터가 없어서 컴파일 에러.

- [ ] **Step 3: `VectorStroke`에 필드 추가**

`VectorPage.kt`의 `VectorStroke` 데이터 클래스를 이걸로 교체:

```kotlin
data class VectorStroke(
    val color: Long,
    val points: List<VectorPoint>,
    val cap: VectorCap = VectorCap.BUTT,
    val fillEnabled: Boolean = true,
    val strokeColor: Long? = null,
    val strokeWidthPx: Float = 2f,
    /** null이면 지금 펜(cap/fillEnabled/strokeColor/strokeWidthPx 그대로 적용). 아니면 이 id의
     *  스탬프 브러시로 그려진 획 — 이때는 위 네 필드를 무시하고 [points]를 중심선 삼아
     *  [stampPolygons]로 다시 계산해서 그린다(전부 [color]로 틴트). 참조하는 브러시가 삭제된
     *  경우 렌더링 시점에 못 찾으면 지금 펜(리본, [color])으로 폴백. */
    val brushProfileId: String? = null,
)
```

- [ ] **Step 4: JSON 쓰기에 필드 추가**

`toJson()` 안의 스트로크 조립 부분(`"],\"cap\":..."`로 시작하는 줄)을 이걸로 교체:

```kotlin
        sb.append("],\"cap\":\"").append(s.cap.name).append("\"")
            .append(",\"fillEnabled\":").append(s.fillEnabled)
            .append(",\"strokeColor\":").append(s.strokeColor ?: Long.MIN_VALUE)
            .append(",\"strokeWidthPx\":").append(s.strokeWidthPx)
            .append(",\"brushProfileId\":\"").append(s.brushProfileId ?: "").append("\"")
            .append("}")
```

- [ ] **Step 5: JSON 읽기에 필드 추가**

`strokeRegex`를 이걸로 교체(기존 값 뒤에 옵션 그룹 하나 추가):

```kotlin
private val strokeRegex = Regex(
    "\\{\"color\":(-?\\d+),\"points\":\\[(.*?)](?:,\"cap\":\"(\\w+)\")?" +
        "(?:,\"fillEnabled\":(true|false),\"strokeColor\":(-?\\d+),\"strokeWidthPx\":(-?[0-9.eE+-]+))?" +
        "(?:,\"brushProfileId\":\"(.*?)\")?\\}",
)
```

`vectorPageFromJson`에서 `VectorStroke(color, points, cap, fillEnabled, strokeColor, strokeWidthPx)`를 만드는 줄을 이걸로 교체:

```kotlin
            val brushProfileId = m.groups[7]?.value?.ifBlank { null }
            VectorStroke(color, points, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId)
```

(그 위에 있던 `val cap = ...`/`val fillEnabled = ...`/`val strokeColor = ...`/`val strokeWidthPx = ...` 네 줄은 그대로 둔다 — 그 다음에 이 두 줄만 추가.)

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorPageTest" --no-daemon`
Expected: PASS, 기존 8개 + 신규 2개 = 10개 전부 통과.

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorPage.kt app/src/test/java/com/g1/sketchbook/vector/VectorPageTest.kt
git commit -m "feat(vector): add brushProfileId field to mark stamp-brush strokes"
```

---

### Task 5: VectorRenderer — 스탬프 획 렌더링 분기

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorRenderer.kt`

**Interfaces:**
- Consumes: `stampPolygons`(Task 3), `VectorStroke.brushProfileId`(Task 4).
- Produces: `fun drawVectorPage(canvas: Canvas, page: VectorPage, stampBrushes: Map<String, StampBrushProfile> = emptyMap())` — **시그니처가 바뀐다**(새 파라미터 추가, 기본값 있어서 기존 2-인자 호출은 그대로 컴파일되지만 스탬프 획을 못 그림). `fun renderVectorPage(page: VectorPage, sizePx: Int, stampBrushes: Map<String, StampBrushProfile> = emptyMap()): Bitmap` — 마찬가지로 새 파라미터 추가. Task 9(`VectorBrushView`)·Task 10(`SketchbookRepository`)가 이 두 함수를 실제 브러시 맵과 함께 부른다.

이 파일은 유닛 테스트 대상이 아니다(android.graphics 의존) — 컴파일 확인만.

- [ ] **Step 1: 구현 교체**

`VectorRenderer.kt`의 `drawVectorPage`/`renderVectorPage` 시그니처와 본문을 이걸로 교체(파일의 다른 부분, `import`/`PREVIEW_PADDING_RATIO`는 그대로):

```kotlin
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
        if (stroke.fillEnabled) {
            fillPaint.color = stroke.color.toInt()
            canvas.drawPath(path, fillPaint)
        }
        stroke.strokeColor?.let { sc ->
            strokePaint.color = sc.toInt()
            strokePaint.strokeWidth = stroke.strokeWidthPx
            canvas.drawPath(path, strokePaint)
        }
    }
}

private const val PREVIEW_PADDING_RATIO = 0.08f

fun renderVectorPage(page: VectorPage, sizePx: Int, stampBrushes: Map<String, StampBrushProfile> = emptyMap()): Bitmap {
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
    drawVectorPage(canvas, page, stampBrushes)
    canvas.restore()
    return bmp
}
```

**설계 메모**: `contentBounds`(경계상자 계산, 미리보기 letterbox 기준)는 손 몸체 중심선 기준 그대로 두고 스탬프 자체의 퍼진 범위는 반영하지 않는다 — 스탬프가 중심선보다 살짝 넘칠 수 있지만, 미리보기 여백(8%)이 어느 정도 흡수하고 이 스펙 범위 밖(Global Constraints에 없음)이라 손대지 않는다.

- [ ] **Step 2: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 이 파일 자체는 깨끗. `SketchbookRepository.kt`가 `renderVectorPage`를 옛 2-인자로 부르는 건 기본값이 있어 그대로 컴파일된다(다만 Task 9에서 실제 브러시 맵을 넘기도록 갱신).

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorRenderer.kt
git commit -m "feat(vector): render stamp-brush strokes by stamping shapes along the centerline"
```

---

### Task 6: VectorSvgExport — 스탬프 획 내보내기 분기

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorSvgExport.kt`

**Interfaces:**
- Consumes: `stampPolygons`(Task 3).
- Produces: `fun vectorPageToSvg(page: VectorPage, region: Bounds, stampBrushes: Map<String, StampBrushProfile> = emptyMap()): String` — 새 파라미터 추가(기본값 있음).

- [ ] **Step 1: 구현 교체**

`VectorSvgExport.kt`의 `vectorPageToSvg` 함수를 이걸로 교체(`colorHex`는 그대로):

```kotlin
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
        if (!touchesRegion) continue
        sb.append("<path d=\"M")
        outline.forEachIndexed { i, p ->
            val x = p.x - region.minX; val y = p.y - region.minY
            if (i == 0) sb.append(x).append(',').append(y)
            else sb.append(" L").append(x).append(',').append(y)
        }
        sb.append(" Z\" fill=\"")
        if (stroke.fillEnabled) sb.append(colorHex(stroke.color)) else sb.append("none")
        sb.append('"')
        stroke.strokeColor?.let { sc ->
            sb.append(" stroke=\"").append(colorHex(sc)).append("\" stroke-width=\"").append(stroke.strokeWidthPx).append('"')
        }
        sb.append("/>")
    }
    sb.append("</svg>")
    return sb.toString()
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남.

- [ ] **Step 3: 기존 유닛 테스트로 회귀 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorSvgExportTest" --no-daemon`
Expected: PASS — `stampBrushes` 기본값이 빈 맵이라 `brushProfileId=null`인 기존 테스트들은 전부 예전과 같은 경로(리본)를 그대로 탄다.

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorSvgExport.kt
git commit -m "feat(vector): export stamp-brush strokes as individual stamped paths"
```

---

### Task 7: StampBrushRepository — 로컬 저장소

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/StampBrushRepository.kt`

**Interfaces:**
- Consumes: `StampBrushProfile`/`toJson`/`stampBrushProfileFromJson`(Task 3), `parseSvgDocument`(Task 2).
- Produces: `class StampBrushRepository(context: Context)`와 메서드 `list(): List<StampBrushProfile>`, `importFromSvg(name: String, svgText: String): StampBrushProfile?`(파싱 실패 시 null), `rename(id: String, name: String)`, `delete(id: String)`, `get(id: String): StampBrushProfile?`. Task 9(`SketchbookRepository`)·Task 12(`VectorCanvasScreen`)가 이 클래스를 쓴다.

이 파일은 `android.content.Context`에 의존해 유닛 테스트 대상이 아니다 — 컴파일 확인만.

- [ ] **Step 1: 구현 작성**

`app/src/main/java/com/g1/sketchbook/vector/StampBrushRepository.kt` 전체:

```kotlin
package com.g1.sketchbook.vector

import android.content.Context
import java.io.File
import kotlin.random.Random

/** 사용자가 임포트한 스탬프 브러시 — 로컬 파일(브러시 하나당 JSON 파일 하나) + 목록/이름은
 *  `SharedPreferences`에 id만 순서대로 저장. 이 프로젝트의 다른 로컬 저장소(`SketchbookRepository`
 *  등)와 같은 "로컬 파일 + 목록은 prefs" 패턴. */
class StampBrushRepository(context: Context) {
    private val prefs = context.getSharedPreferences("g1_stamp_brushes", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "vector_brushes").apply { mkdirs() }

    private fun file(id: String) = File(root, "$id.json")

    private fun ids(): List<String> = prefs.getString(KEY_IDS, null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    private fun saveIds(list: List<String>) { prefs.edit().putString(KEY_IDS, list.joinToString(",")).apply() }

    fun list(): List<StampBrushProfile> = ids().mapNotNull { get(it) }

    fun get(id: String): StampBrushProfile? {
        val f = file(id)
        if (!f.exists()) return null
        return stampBrushProfileFromJson(f.readText())
    }

    /** [svgText]를 파싱해서 새 프로필로 저장 — 지원 안 하는/손상된 SVG면 아무것도 저장하지 않고
     *  null을 반환한다(호출부가 토스트로 실패를 알림). */
    fun importFromSvg(name: String, svgText: String): StampBrushProfile? {
        val shapes = parseSvgDocument(svgText) ?: return null
        val id = newId()
        val profile = StampBrushProfile(id, name, shapes)
        file(id).writeText(profile.toJson())
        saveIds(ids() + id)
        return profile
    }

    fun rename(id: String, name: String) {
        val current = get(id) ?: return
        file(id).writeText(current.copy(name = name).toJson())
    }

    /** 간격/크기 수정 — 이름 변경과 별개 메서드로 분리(둘 다 있는 편집 팝업이 각각 부름). */
    fun updateSpacingAndSize(id: String, spacingPx: Float, sizePx: Float) {
        val current = get(id) ?: return
        file(id).writeText(current.copy(spacingPx = spacingPx, sizePx = sizePx).toJson())
    }

    fun delete(id: String) {
        file(id).delete()
        saveIds(ids() - id)
    }

    private fun newId(): String {
        val a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return "stamp_" + (1..8).map { a[Random.nextInt(a.length)] }.joinToString("")
    }

    companion object { private const val KEY_IDS = "ids" }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남.

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/StampBrushRepository.kt
git commit -m "feat(vector): add local storage for imported stamp brush profiles"
```

---

### Task 8: SketchbookRepository — 미리보기 렌더링에 스탬프 브러시 반영

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt`

**Interfaces:**
- Consumes: `StampBrushRepository`(Task 7).

이 파일은 `android.content.Context`에 의존해 유닛 테스트 대상이 아니다 — 컴파일 확인만.

- [ ] **Step 1: `saveVectorCanvas`가 스탬프 브러시 맵을 넘기도록 수정**

`SketchbookRepository.kt`의 `saveVectorCanvas` 함수(현재 `renderVectorPage(page, VECTOR_PREVIEW_SIZE)`를 부르는 부분)를 이걸로 교체:

```kotlin
    fun saveVectorCanvas(id: String, page: VectorPage) {
        vectorCanvasFile(id).writeText(page.toJson())
        val stampBrushes = com.g1.sketchbook.vector.StampBrushRepository(context).list().associateBy { it.id }
        FileOutputStream(vectorPreviewFile(id)).use {
            renderVectorPage(page, VECTOR_PREVIEW_SIZE, stampBrushes).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
```

**설계 메모**: `SketchbookRepository`는 이미 생성자로 `context: Context`를 받으므로(`class SketchbookRepository(private val context: Context)`), 여기서 그 `context`로 `StampBrushRepository`를 바로 만들어 쓴다 — `SketchbookSync.saveVectorCanvasSynced`나 다른 호출부에 새 파라미터를 안 뚫어도 된다.

- [ ] **Step 2: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남.

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt
git commit -m "feat(sketchbook): render vector previews with the user's stamp brushes"
```

---

### Task 9: VectorBrushView — 스탬프 브러시로 그리기

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorBrushView.kt`

**Interfaces:**
- Consumes: `StampBrushProfile`(Task 3), `drawVectorPage`(Task 5, 새 시그니처).
- Produces: `VectorBrushView.brushProfileId: String?`(새로 그릴 획에 적용, 기본 null), `VectorBrushView.stampBrushes: Map<String, StampBrushProfile>`(렌더링에 쓸 조회용 맵, Task 12가 채워 넣음).

이 파일은 유닛 테스트 대상이 아니다(android.view/graphics 의존) — 컴파일 확인 + Task 12까지 끝난 뒤 에뮬레이터에서 수동 확인.

- [ ] **Step 1: 새 필드 추가**

`VectorBrushView.kt`에서 `var strokeWidthPx: Float = 2f` 선언 바로 아래에 추가:

```kotlin
    /** 다음에 그릴 획에 적용할 스탬프 브러시 — null이면 지금 펜. [VectorCanvasScreen]의 브러시
     *  스와치 패널에서 고른다. */
    var brushProfileId: String? = null
    /** id로 [StampBrushProfile]을 찾는 조회용 맵 — 그리기·지우개 히트테스트·undo 미리보기 전부
     *  이 맵으로 렌더링한다. [VectorCanvasScreen]이 [com.g1.sketchbook.vector.StampBrushRepository]에서
     *  읽어 채워 넣는다(이 파일 자체는 저장소를 모른다 — 순수 뷰). */
    var stampBrushes: Map<String, StampBrushProfile> = emptyMap()
```

- [ ] **Step 2: 새로 그리는 획에 `brushProfileId` 적용**

`onTouchEvent`의 `Tool.DRAW`/`ACTION_UP` 분기(현재 407번째 줄대, `val stroke = VectorStroke(color, cur, cap, fillEnabled, strokeColor, strokeWidthPx)`)를 이걸로 교체:

```kotlin
                        Tool.DRAW -> {
                            val cur = current; current = null
                            if (cur != null && cur.size >= 2) {
                                val stroke = VectorStroke(color, cur, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId)
                                committed.add(stroke)
                                history.add(UndoOp.Drew(stroke))
                                onStrokeEnd?.invoke()
                            }
                        }
```

- [ ] **Step 3: `onDraw`가 `stampBrushes` 맵을 넘기도록 수정**

`onDraw` 안의 `drawVectorPage(canvas, VectorPage(committed.filterIndexed { i, _ -> i !in selSet }))`을 `drawVectorPage(canvas, VectorPage(committed.filterIndexed { i, _ -> i !in selSet }), stampBrushes)`로,

`drawVectorPage(canvas, VectorPage(committed))`을 `drawVectorPage(canvas, VectorPage(committed), stampBrushes)`로,

`current?.let { pts -> if (pts.size >= 2) drawVectorPage(canvas, VectorPage(listOf(VectorStroke(color, pts, cap, fillEnabled, strokeColor, strokeWidthPx)))) }`을

```kotlin
        current?.let { pts -> if (pts.size >= 2) drawVectorPage(canvas, VectorPage(listOf(VectorStroke(color, pts, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId))), stampBrushes) }
```

로, `drawVectorPage(canvas, VectorPage(selIdx.map { committed[it] }))`을 `drawVectorPage(canvas, VectorPage(selIdx.map { committed[it] }), stampBrushes)`로 각각 교체(총 4곳 — 선택 제외 배경, 커밋된 전체, 그리는 중인 획 미리보기, 선택 이동 중 미리보기).

- [ ] **Step 4: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorBrushView.kt
git commit -m "feat(vector): draw new strokes with the selected stamp brush"
```

---

### Task 10: 계정 백업 동기화 — 스탬프 브러시 목록

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt`

**Interfaces:**
- Consumes: `StampBrushProfile`/`parseSvgDocument` 대신 원본 SVG 텍스트 자체를 올린다(스펙: "파싱된 다각형 자체를 큰 배열로 안 올리고 원본 SVG 텍스트만 올려서 페이로드를 가볍게 유지") — 그래서 `RemoteStampBrush`는 원본 SVG 텍스트를 들고, 받는 쪽(각 기기)이 `StampBrushRepository.importFromSvg`로 다시 파싱한다. `StampBrushRepository`(Task 7)의 `importFromSvg(name, svgText)`를 재사용.
- Produces: `RemoteStampBrush` data class, `RemoteSnapshot.stampBrushes: List<RemoteStampBrush>`, `BackupRepository.pushStampBrush(uid, brush)`/`deleteStampBrush(uid, id)`, `reconcileStampBrushes(context, uid)`(Task 12가 로그인/동기화 트리거 시점에 부른다).

이 세 파일은 Firebase/Android 의존이라 유닛 테스트 대상이 아니다 — 컴파일 확인만. `StampBrushProfile`은 로컬 저장 시 이미 `shapes`(파싱된 다각형)까지 들고 있지만, 백업 페이로드에는 원본 SVG 텍스트를 별도로 같이 저장해 둬야 한다 — 그래서 `StampBrushRepository`에 원본 SVG 텍스트를 같이 보관하는 보조 파일을 추가한다(파싱된 `shapes` JSON과는 별개로, id당 `.svg` 원본 파일도 저장).

- [ ] **Step 1: `StampBrushRepository`가 원본 SVG 텍스트도 같이 보관하도록 보강**

`StampBrushRepository.kt`의 `importFromSvg`를 이걸로 교체(원본 텍스트를 `{id}.svg`로 같이 저장):

```kotlin
    fun importFromSvg(name: String, svgText: String): StampBrushProfile? {
        val shapes = parseSvgDocument(svgText) ?: return null
        val id = newId()
        val profile = StampBrushProfile(id, name, shapes)
        file(id).writeText(profile.toJson())
        File(root, "$id.svg").writeText(svgText)
        saveIds(ids() + id)
        return profile
    }
```

같은 파일에 원본 텍스트 조회/삭제/원격에서 받은 데이터로 그대로 심는 메서드를 추가(클래스 안, `delete` 메서드 바로 아래):

```kotlin
    fun originalSvgText(id: String): String? = File(root, "$id.svg").takeIf { it.exists() }?.readText()

    /** 원격 백업에서 받은 항목을 그대로 로컬에 심는다 — [id]를 새로 만들지 않고 원격이 정한 그대로
     *  써야 다음 동기화 때 같은 항목으로 인식된다(툼스톤 비교가 id 기준이라). */
    fun importFromRemote(id: String, name: String, svgText: String, spacingPx: Float, sizePx: Float): StampBrushProfile? {
        val shapes = parseSvgDocument(svgText) ?: return null
        val profile = StampBrushProfile(id, name, shapes, spacingPx, sizePx)
        file(id).writeText(profile.toJson())
        File(root, "$id.svg").writeText(svgText)
        if (id !in ids()) saveIds(ids() + id)
        return profile
    }
```

`delete` 메서드를 이걸로 교체(`.svg` 원본도 같이 지움):

```kotlin
    fun delete(id: String) {
        file(id).delete()
        File(root, "$id.svg").delete()
        saveIds(ids() - id)
    }
```

- [ ] **Step 2: `BackupModels.kt`에 `RemoteStampBrush` 추가**

`RemoteSharedBookRef` data class 바로 아래(그 doc comment 포함 블록 다음)에 추가:

```kotlin
/** 스탬프 브러시 하나의 백업용 표현 — 파싱된 다각형([com.g1.sketchbook.vector.StampBrushProfile.shapes])은
 *  안 올리고 원본 [svgText]만 올려서 페이로드를 가볍게 유지한다(받는 기기가 다시 파싱). [deleted]는
 *  툼스톤 — 이 기기에서 지운 항목을 다른 기기에도 지우라고 알리는 용도([RemoteSharedBookRef]와 같은 패턴). */
data class RemoteStampBrush(
    val id: String,
    val name: String,
    val svgText: String,
    val spacingPx: Float,
    val sizePx: Float,
    val updatedAt: Long,
    val deleted: Boolean,
)
```

`RemoteSnapshot` data class를 이걸로 교체(마지막에 `stampBrushes` 필드 추가):

```kotlin
data class RemoteSnapshot(
    val sketchbooks: List<RemoteSketchbook>,
    val diary: Map<String, RemoteDiaryDay>,
    val settings: RemoteSettings?,
    val sharedBooks: List<RemoteSharedBookRef>,
    val stampBrushes: List<RemoteStampBrush>,
)
```

- [ ] **Step 3: `BackupRepository.kt`에 push/delete/pull 추가**

`pushSharedBookRef`/`deleteSharedBookRef` 함수 바로 아래에 추가(같은 스타일 — `root.child(uid).child(...)`):

```kotlin
    /** 원본 SVG 텍스트만 올린다(파싱된 다각형은 안 올림 — 받는 기기가 [com.g1.sketchbook.vector.parseSvgDocument]로
     *  다시 파싱). */
    fun pushStampBrush(uid: String, brush: RemoteStampBrush) {
        root.child(uid).child("stampBrushes").child(brush.id).setValue(
            mapOf(
                "name" to brush.name, "svgText" to brush.svgText,
                "spacingPx" to brush.spacingPx, "sizePx" to brush.sizePx,
                "updatedAt" to brush.updatedAt, "deleted" to false,
            ),
        )
    }

    /** 툼스톤 — 하드 삭제하면 다른 기기가 "원래 없었음"으로 잘못 읽고 되살린다([deleteSharedBookRef]와 동일 이유). */
    fun deleteStampBrush(uid: String, id: String, updatedAt: Long) {
        root.child(uid).child("stampBrushes").child(id).setValue(
            mapOf("deleted" to true, "updatedAt" to updatedAt),
        )
    }
```

`pullAll` 함수 안, `val sharedBooks = ...` 블록 바로 다음(`return RemoteSnapshot(...)` 줄 이전)에 추가:

```kotlin
        val stampBrushes = snap.child("stampBrushes").children.mapNotNull { c ->
            val id = c.key ?: return@mapNotNull null
            RemoteStampBrush(
                id = id,
                name = c.child("name").getValue(String::class.java) ?: "",
                svgText = c.child("svgText").getValue(String::class.java) ?: "",
                spacingPx = c.child("spacingPx").getValue(Double::class.java)?.toFloat() ?: 24f,
                sizePx = c.child("sizePx").getValue(Double::class.java)?.toFloat() ?: 32f,
                updatedAt = c.child("updatedAt").getValue(Long::class.java) ?: 0L,
                deleted = c.child("deleted").getValue(Boolean::class.java) ?: false,
            )
        }
```

`return RemoteSnapshot(sketchbooks, diary, settings, sharedBooks)`를 `return RemoteSnapshot(sketchbooks, diary, settings, sharedBooks, stampBrushes)`로 교체.

- [ ] **Step 4: `BackupSync.kt`에 `reconcileStampBrushes` 추가**

`reconcileBackup` 함수의 마지막 줄(`reconcileSharedBooks(sketchbookRepo, backup, uid, remote.sharedBooks)`) 바로 다음에 한 줄 추가:

```kotlin
    reconcileStampBrushes(context, backup, uid, remote.stampBrushes)
```

`reconcileSharedBooks` 함수 바로 아래에 새 함수 추가(같은 툼스톤 방식 — add-if-remote-and-not-local, delete-if-remote-tombstoned, push-if-local-and-not-remote-or-remote-tombstoned):

```kotlin
/** [com.g1.sketchbook.vector.StampBrushRepository]의 로컬 스탬프 브러시 목록과 원격 `stampBrushes`를
 *  맞춘다 — [reconcileSharedBooks]와 같은 툼스톤 방식: 원격에만 있고 로컬에 없으면 받아서 임포트,
 *  원격에서 지워졌으면(deleted=true) 로컬에서도 지움, 로컬에만 있으면(또는 원격이 이미 지운 걸
 *  로컬은 아직 갖고 있으면) 원격에 올린다. */
private fun reconcileStampBrushes(context: Context, backup: BackupRepository, uid: String, remote: List<RemoteStampBrush>) {
    val local = com.g1.sketchbook.vector.StampBrushRepository(context)
    val remoteById = remote.associateBy { it.id }
    val localIds = local.list().map { it.id }.toSet()

    for (r in remote) {
        if (r.deleted) {
            if (r.id in localIds) local.delete(r.id)
        } else if (r.id !in localIds) {
            local.importFromRemote(r.id, r.name, r.svgText, r.spacingPx, r.sizePx)
        }
    }
    for (profile in local.list()) {
        val r = remoteById[profile.id]
        if (r == null || r.deleted) {
            val svgText = local.originalSvgText(profile.id) ?: continue
            backup.pushStampBrush(uid, RemoteStampBrush(profile.id, profile.name, svgText, profile.spacingPx, profile.sizePx, System.currentTimeMillis(), false))
        }
    }
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남.

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/StampBrushRepository.kt app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt
git commit -m "feat(backup): sync imported stamp brushes across devices via tombstone list"
```

---

### Task 11: VectorCanvasScreen — 브러시 스와치 패널 임포트/관리 UI

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorCanvasScreen.kt`

**Interfaces:**
- Consumes: `StampBrushRepository`(Task 7), `StampBrushProfile`(Task 3), `RemoteStampBrush`/`BackupRepository.pushStampBrush`/`deleteStampBrush`(Task 10), `VectorBrushView.brushProfileId`/`stampBrushes`(Task 9).

이 파일은 Compose UI라 유닛 테스트 대상이 아니다 — 컴파일 확인 + 에뮬레이터/기기에서 수동 확인(파일 선택 → 이름 입력 → 스와치에 나타남 → 선택해서 그리기 → 길게 눌러 편집/삭제).

- [ ] **Step 1: import 추가**

파일 맨 위 import 블록에서 `import androidx.compose.foundation.layout.height` 줄 바로 아래에 추가:

```kotlin
import androidx.compose.foundation.layout.width
```

`import androidx.compose.foundation.background` 줄 바로 아래에 추가:

```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
```

`import androidx.compose.material3.TextButton` 줄 바로 아래에 추가:

```kotlin
import androidx.compose.material3.TextField
```

`import androidx.compose.ui.viewinterop.AndroidView` 줄 바로 아래에 추가:

```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
```

- [ ] **Step 2: 스탬프 모양 미리보기 그리기 함수 추가**

`drawBrushSwatchPreview` 함수 바로 아래에 추가:

```kotlin
/** 임포트된 스탬프 브러시의 스와치 미리보기 — [drawBrushSwatchPreview]("기본"용 장식 곡선)와 달리
 *  실제로 파싱해서 정규화해 둔 모양([StampBrushProfile.shapes], 중심 원점·가장 긴 변 길이 1)을
 *  스와치 박스 크기에 맞춰 그대로 그린다. */
private fun DrawScope.drawStampShapePreview(shapes: List<List<Point>>, color: Color) {
    val cx = size.width / 2f; val cy = size.height / 2f
    val scale = size.minDimension * 0.85f
    for (shape in shapes) {
        if (shape.isEmpty()) continue
        val path = Path()
        shape.forEachIndexed { i, p ->
            val x = cx + p.x * scale; val y = cy + p.y * scale
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color = color)
    }
}
```

- [ ] **Step 3: `VectorCanvasScreen`에 `@OptIn` 추가, 상태·저장소 추가**

`@Composable\nfun VectorCanvasScreen(bookId: String, book: Sketchbook, myUid: String, onBack: () -> Unit) {`를 이걸로 교체:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VectorCanvasScreen(bookId: String, book: Sketchbook, myUid: String, onBack: () -> Unit) {
```

`var brushProfile by remember { mutableStateOf(BrushProfiles[0]) }` 줄을 이걸로 교체:

```kotlin
    val stampRepo = remember { StampBrushRepository(context) }
    var stampBrushes by remember { mutableStateOf(stampRepo.list()) }
    var selectedStampBrushId by remember { mutableStateOf<String?>(null) }
    var editingBrush by remember { mutableStateOf<StampBrushProfile?>(null) }
    var pendingImportSvgText by remember { mutableStateOf<String?>(null) }
    var importNameDraft by remember { mutableStateOf("") }
    var importNameDialogOpen by remember { mutableStateOf(false) }
```

**설계 메모**: `BrushProfile`/`BrushProfiles`(기본 하나만 있던 기존 뼈대)는 그대로 둔다 — "기본" 항목의 이름 표시(`"기본"`)에 계속 쓰인다. 스탬프 브러시는 별개 목록(`stampBrushes`)으로 관리해서 "기본은 항상 첫 항목"이라는 지금 UI 구조를 그대로 유지한다.

`fun exportRegion(page: VectorPage, region: Bounds) { ... }` 함수 바로 아래에 SVG 파일 선택기 런처를 추가:

```kotlin
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null || !text.contains("<svg")) {
            Toast.makeText(context, "SVG 파일을 읽을 수 없습니다", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        pendingImportSvgText = text
        importNameDraft = "브러시 ${stampBrushes.size + 1}"
        importNameDialogOpen = true
    }

    fun pushStampBrushAsync(profile: StampBrushProfile) {
        if (myUid.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val svg = stampRepo.originalSvgText(profile.id) ?: return@launch
            backup.pushStampBrush(
                myUid,
                com.g1.sketchbook.backup.RemoteStampBrush(profile.id, profile.name, svg, profile.spacingPx, profile.sizePx, System.currentTimeMillis(), false),
            )
        }
    }
```

- [ ] **Step 4: 이름 입력 다이얼로그 + 편집 다이얼로그 추가**

`IconButton(onClick = { saveCurrent(); onBack() }, ...)` 줄(맨 마지막 UI 요소) 바로 앞에 두 다이얼로그를 추가:

```kotlin
        if (importNameDialogOpen) {
            AlertDialog(
                onDismissRequest = { importNameDialogOpen = false; pendingImportSvgText = null },
                title = { Text("브러시 이름") },
                text = { TextField(value = importNameDraft, onValueChange = { importNameDraft = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        val svgText = pendingImportSvgText
                        importNameDialogOpen = false; pendingImportSvgText = null
                        if (svgText != null) {
                            val profile = stampRepo.importFromSvg(importNameDraft.ifBlank { "브러시" }, svgText)
                            if (profile != null) {
                                stampBrushes = stampRepo.list()
                                view?.stampBrushes = stampBrushes.associateBy { it.id }
                                selectedStampBrushId = profile.id
                                view?.brushProfileId = profile.id
                                tool = VectorBrushView.Tool.DRAW; view?.tool = tool
                                pushStampBrushAsync(profile)
                            } else {
                                Toast.makeText(context, "지원하지 않는 SVG 형식입니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("추가") }
                },
                dismissButton = { TextButton(onClick = { importNameDialogOpen = false; pendingImportSvgText = null }) { Text("취소") } },
            )
        }
        editingBrush?.let { brush ->
            var nameDraft by remember(brush.id) { mutableStateOf(brush.name) }
            var spacingDraft by remember(brush.id) { mutableStateOf(brush.spacingPx) }
            var sizeDraft by remember(brush.id) { mutableStateOf(brush.sizePx) }
            AlertDialog(
                onDismissRequest = { editingBrush = null },
                title = { Text("브러시 편집") },
                text = {
                    Column {
                        TextField(value = nameDraft, onValueChange = { nameDraft = it }, singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("간격", modifier = Modifier.weight(1f))
                            IconButton(onClick = { spacingDraft = (spacingDraft - 2f).coerceAtLeast(4f) }) { Icon(Icons.Filled.Remove, "간격 줄이기") }
                            Text("${spacingDraft.toInt()}px")
                            IconButton(onClick = { spacingDraft = (spacingDraft + 2f).coerceAtMost(200f) }) { Icon(Icons.Filled.Add, "간격 늘리기") }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("크기", modifier = Modifier.weight(1f))
                            IconButton(onClick = { sizeDraft = (sizeDraft - 2f).coerceAtLeast(4f) }) { Icon(Icons.Filled.Remove, "크기 줄이기") }
                            Text("${sizeDraft.toInt()}px")
                            IconButton(onClick = { sizeDraft = (sizeDraft + 2f).coerceAtMost(200f) }) { Icon(Icons.Filled.Add, "크기 늘리기") }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        stampRepo.rename(brush.id, nameDraft.ifBlank { brush.name })
                        stampRepo.updateSpacingAndSize(brush.id, spacingDraft, sizeDraft)
                        stampBrushes = stampRepo.list()
                        view?.stampBrushes = stampBrushes.associateBy { it.id }
                        editingBrush = null
                        stampBrushes.firstOrNull { it.id == brush.id }?.let { pushStampBrushAsync(it) }
                    }) { Text("저장") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            stampRepo.delete(brush.id)
                            stampBrushes = stampRepo.list()
                            view?.stampBrushes = stampBrushes.associateBy { it.id }
                            if (selectedStampBrushId == brush.id) { selectedStampBrushId = null; view?.brushProfileId = null }
                            editingBrush = null
                            if (myUid.isNotBlank()) scope.launch(Dispatchers.IO) { backup.deleteStampBrush(myUid, brush.id, System.currentTimeMillis()) }
                        }) { Text("삭제") }
                        TextButton(onClick = { editingBrush = null }) { Text("취소") }
                    }
                },
            )
        }
```

- [ ] **Step 5: 브러시 스와치 패널 `DropdownMenu`를 임포트/편집 가능하게 교체**

현재 브러시 스와치 `Box { IconButton(...) { Canvas... } DropdownMenu(...) { BrushProfiles.forEach { ... } } }` 블록 전체(정확히: `Box {`부터 `}`로 닫히는, `IconButton(onClick = { brushSwatchPanelOpen = true })`로 시작하는 블록)를 이걸로 교체:

```kotlin
            Box {
                IconButton(onClick = { brushSwatchPanelOpen = true }) {
                    Canvas(Modifier.size(24.dp)) {
                        val brush = stampBrushes.firstOrNull { it.id == selectedStampBrushId }
                        if (brush != null) drawStampShapePreview(brush.shapes, Color(color)) else drawBrushSwatchPreview(Color(color))
                    }
                }
                DropdownMenu(expanded = brushSwatchPanelOpen, onDismissRequest = { brushSwatchPanelOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(BrushProfiles[0].label) },
                        leadingIcon = { Canvas(Modifier.size(32.dp)) { drawBrushSwatchPreview(Color(color)) } },
                        trailingIcon = if (selectedStampBrushId == null) { { Icon(Icons.Filled.Check, null) } } else null,
                        onClick = { selectedStampBrushId = null; view?.brushProfileId = null; brushSwatchPanelOpen = false },
                    )
                    stampBrushes.forEach { brush ->
                        Row(
                            Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = { selectedStampBrushId = brush.id; view?.brushProfileId = brush.id; brushSwatchPanelOpen = false },
                                    onLongClick = { editingBrush = brush; brushSwatchPanelOpen = false },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Canvas(Modifier.size(32.dp)) { drawStampShapePreview(brush.shapes, Color(color)) }
                            Spacer(Modifier.width(12.dp))
                            Text(brush.name, modifier = Modifier.weight(1f))
                            if (brush.id == selectedStampBrushId) Icon(Icons.Filled.Check, null)
                        }
                    }
                    DropdownMenuItem(
                        text = { Text("추가...") },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = { brushSwatchPanelOpen = false; importLauncher.launch("image/svg+xml") },
                    )
                }
            }
```

- [ ] **Step 6: `AndroidView` 팩토리에 스탬프 브러시 상태 전달**

`AndroidView`의 `factory = { ctx -> VectorBrushView(ctx).also { ... } }` 블록 안, `it.scaleStrokeWidth = scaleStrokeWidth` 줄 바로 다음 줄에 추가:

```kotlin
                    it.brushProfileId = selectedStampBrushId
                    it.stampBrushes = stampBrushes.associateBy { b -> b.id }
```

- [ ] **Step 7: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남.

- [ ] **Step 8: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/vector/VectorCanvasScreen.kt
git commit -m "feat(vector): add SVG import and management UI to the brush swatch panel"
```

---

### Task 12: 전체 빌드·테스트 검증

**Files:** (없음 — 검증 전용 태스크, 문제가 발견되면 그 문제가 있는 파일을 그 자리에서 고친다.)

- [ ] **Step 1: 전체 유닛 테스트 실행**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --no-daemon`
Expected: PASS — Task 1~4에서 추가한 신규 테스트(`SvgPathParserTest`, `SvgShapeParserTest`, `StampBrushTest`, `VectorPageTest`의 신규 케이스)와 기존 테스트(`StrokeGeometryTest`, `VectorSvgExportTest` 등) 전부 통과.

- [ ] **Step 2: 전체 디버그 빌드**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL — 이 플랜의 모든 태스크(SVG 파서, 스탬핑, 렌더링/내보내기 분기, 로컬 저장소, 백업 동기화, UI)가 서로 어긋남 없이 한 번에 컴파일됨을 최종 확인.

- [ ] **Step 3: 문제 발견 시 수정**

Step 1이나 2에서 실패가 나오면, 실패한 그 파일을 열어 원인을 고치고 관련 태스크의 커밋 스타일을 따라 별도 커밋으로 남긴다(예: `fix(vector): ...`). 실패가 없으면 이 태스크는 커밋할 변경사항이 없다 — 그대로 다음 단계(전체 브랜치 리뷰)로 진행.

---

## Self-Review

**스펙 커버리지 확인:**
- SVG 파싱 범위(path 전체 명령어 + rect/circle/ellipse + g translate/scale, rotate 제외) → Task 1, 2.
- 색상은 항상 펜 색 틴트(SVG 원본 색 무시) → Task 2(색상 속성 자체를 안 읽음), Task 5·6(항상 `stroke.color`로 채움).
- 간격·크기 고정값 → Task 3(`StampBrushProfile.spacingPx`/`sizePx`, 속도 인자 없음).
- 여러 개 임포트·이름·삭제 → Task 7(`StampBrushRepository`), Task 11(UI).
- 계정 백업 동기화 → Task 10.
- `VectorStroke.brushProfileId` + 하위호환 JSON → Task 4.
- 렌더링/내보내기 분기 → Task 5, 6.
- 브러시 스와치 패널 확장(+ 버튼, 편집 팝업, 실제 모양 미리보기) → Task 11.
- 이번 스펙에서 다루지 않는 것(캘리그래픽 등 다른 브러시, 원본 색 옵션, 속도 가변, 그룹 회전, 스탬프→펜 변환) → 계획에 포함된 어떤 태스크도 이 항목들을 구현하지 않음(의도적으로 제외).

**플레이스홀더 스캔:** 전체 태스크 재확인 — "TBD"/"나중에 구현"/구체 코드 없는 단계 없음. Task 10의 "이 파일에서 실제 시그니처가 다르면 조정" 문구는 Step 5 재작성 시 제거하고 실제 `BackupModels.kt`/`BackupRepository.kt`/`BackupSync.kt` 코드를 직접 읽어 정확한 시그니처로 교체 완료.

**타입 일관성 확인:** `StampBrushProfile(id, name, shapes, spacingPx, sizePx)` — Task 3에서 정의, Task 5·6·7·9·10·11 전부 같은 필드명으로 사용. `VectorStroke.brushProfileId: String?` — Task 4에서 정의, Task 5·6·9에서 같은 이름으로 참조. `stampPolygons(profile, points): List<List<Point>>` — Task 3에서 정의, Task 5·6에서 같은 시그니처로 호출. `VectorBrushView.brushProfileId`/`stampBrushes` — Task 9에서 정의, Task 11에서 같은 이름으로 설정.