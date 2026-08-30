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

private val strokeRegex = Regex("\\{\"color\":(-?\\d+),\"points\":\\[(.*?)]\\}")
private val pointRegex = Regex("\\{\"x\":(-?[0-9.eE+-]+),\"y\":(-?[0-9.eE+-]+),\"w\":(-?[0-9.eE+-]+)\\}")

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
