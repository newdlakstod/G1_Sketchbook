package com.g1.sketchbook.vector

/** 벡터 스케치북 한 획의 점 하나 — [w]는 그 지점의 선 굵기(px), 그릴 때 속도로 계산해 점마다 같이
 *  저장한다(펜 속도-굵기 로직과 같은 느낌을 벡터로 재현하기 위함). */
data class VectorPoint(val x: Float, val y: Float, val w: Float)

/** 획 하나 — 점 목록, 양 끝 마감 모양, 그리고 채움/테두리를 각각 따로 지정한다(일러스트레이터의
 *  패스처럼 fill과 stroke가 별개). [color]는 채움(fill) 색 — [fillEnabled]=false면 안 쓰인다.
 *  [strokeColor]는 테두리 색으로, null이면 테두리 없음(이 옵션이 생기기 전과 같은 채움만 있는
 *  모양). [cap] 기본값 [VectorCap.BUTT]는 그 옵션이 생기기 전 저장된 획(예전 JSON에 "cap" 필드가
 *  없는 경우)의 생김새를 그대로 유지하기 위함 — 새로 그리는 획은 [VectorBrushView]가 기본값을
 *  다르게 준다. */
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
        sb.append("],\"cap\":\"").append(s.cap.name).append("\"")
            .append(",\"fillEnabled\":").append(s.fillEnabled)
            .append(",\"strokeColor\":").append(s.strokeColor ?: Long.MIN_VALUE)
            .append(",\"strokeWidthPx\":").append(s.strokeWidthPx)
            .append(",\"brushProfileId\":\"").append(s.brushProfileId ?: "").append("\"")
            .append("}")
    }
    sb.append("]}")
    return sb.toString()
}

private val strokeRegex = Regex(
    "\\{\"color\":(-?\\d+),\"points\":\\[(.*?)](?:,\"cap\":\"(\\w+)\")?" +
        "(?:,\"fillEnabled\":(true|false),\"strokeColor\":(-?\\d+),\"strokeWidthPx\":(-?[0-9.eE+-]+))?" +
        "(?:,\"brushProfileId\":\"(.*?)\")?\\}",
)
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
            // "cap" 필드가 없는(이 옵션이 생기기 전) 예전 JSON이거나 못 알아보는 값이면 BUTT로
            // 취급한다 — BUTT가 그 옵션이 생기기 전의 유일한 동작이었으므로 예전 그림의 생김새가
            // 그대로 유지된다.
            val cap = runCatching { VectorCap.valueOf(m.groups[3]?.value ?: "") }.getOrDefault(VectorCap.BUTT)
            // "fillEnabled"/"strokeColor"/"strokeWidthPx"가 없는(이 옵션이 생기기 전) 예전 JSON이면
            // "채움만, 테두리 없음"으로 취급 — 그 옵션이 생기기 전의 유일한 동작이라 예전 그림의
            // 생김새가 그대로 유지된다. Long.MIN_VALUE는 "테두리 없음" 센티널(실제 색 Long 값으로는
            // 절대 안 나오는 값 — SketchbookRepository의 vectorCanvasW/H와 같은 패턴).
            val fillEnabled = m.groups[4]?.value?.toBoolean() ?: true
            val strokeColor = m.groups[5]?.value?.toLong()?.takeIf { it != Long.MIN_VALUE }
            val strokeWidthPx = m.groups[6]?.value?.toFloat() ?: 2f
            val brushProfileId = m.groups[7]?.value?.ifBlank { null }
            VectorStroke(color, points, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId)
        }.toList()
        VectorPage(strokes)
    }.getOrNull()
}
