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
    fun nextNum(): Float {
        if (i >= tokens.size) throw IndexOutOfBoundsException()
        return tokens[i++].toFloat()
    }

    while (i < tokens.size) {
        try {
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
        } catch (e: Exception) {
            return points
        }
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
