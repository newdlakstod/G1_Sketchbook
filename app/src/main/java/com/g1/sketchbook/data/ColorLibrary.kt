package com.g1.sketchbook.data

import kotlin.random.Random

/** 색상 라이브러리 하나 — 항상 정확히 [LibraryColorCount]개의 색을 담는다. 사용자가 이름을 붙이고
 *  여러 개(최대 [MaxLibraries]) 만들 수 있다. 팔레트는 이 중 최대 [ActiveLibraryCount]개를 골라
 *  이어붙인 것([deriveLibraryPalette] 참고) — 라이브러리 자체가 저장의 단일 진실 공급원이고, 팔레트
 *  색상 리스트 자체는 따로 저장하지 않는다. */
data class ColorLibrary(
    val id: String,
    val name: String,
    val colors: List<Long>,
)

const val LibraryColorCount = 7
const val MaxLibraries = 30
const val ActiveLibraryCount = 3

/** [activeIds]가 가리키는 라이브러리들을 그 순서 그대로 이어붙여 팔레트 색상 목록을 만든다 —
 *  존재하지 않는 id는 조용히 건너뛴다(예: 다른 기기에서 삭제된 라이브러리가 아직 이 기기의
 *  activeIds에 남아있는 경우). */
fun deriveLibraryPalette(libraries: List<ColorLibrary>, activeIds: List<String>): List<Long> {
    val byId = libraries.associateBy { it.id }
    return activeIds.mapNotNull { byId[it] }.flatMap { it.colors }
}

/** 라이브러리 [id]를 활성 목록에서 토글한다 — 이미 들어있으면 빼고, 없으면 맨 뒤에 추가하되 이미
 *  [maxActive]개가 차 있으면 가장 먼저 선택했던(맨 앞) 것부터 밀어낸다(선입선출). */
fun toggleActiveLibrary(activeIds: List<String>, id: String, maxActive: Int = ActiveLibraryCount): List<String> {
    if (id in activeIds) return activeIds - id
    val next = activeIds + id
    return if (next.size > maxActive) next.drop(next.size - maxActive) else next
}

/** 새 라이브러리를 뒤에 추가한다 — 이미 [MaxLibraries]개면 그대로 반환(호출부가 UI에서 "추가" 버튼을
 *  비활성화해서 막는 게 우선이지만, 이 함수 자체도 안전망으로 상한을 지킨다). 처음 색은
 *  [SessionStore.DefaultFavorites]의 앞 [LibraryColorCount]개를 복사해서 시작한다(전부 같은 색이
 *  아니게, 바로 편집할 수 있게). */
fun addLibrary(libraries: List<ColorLibrary>, name: String): List<ColorLibrary> {
    if (libraries.size >= MaxLibraries) return libraries
    val startColors = SessionStore.DefaultFavorites.take(LibraryColorCount)
    return libraries + ColorLibrary(newLibraryId(), name, startColors)
}

fun renameLibrary(libraries: List<ColorLibrary>, id: String, name: String): List<ColorLibrary> =
    libraries.map { if (it.id == id) it.copy(name = name) else it }

/** 라이브러리 [id]의 [index]번째 색만 바꾼다(0..[LibraryColorCount]-1). */
fun updateLibraryColor(libraries: List<ColorLibrary>, id: String, index: Int, color: Long): List<ColorLibrary> =
    libraries.map { lib ->
        if (lib.id != id) lib
        else lib.copy(colors = lib.colors.mapIndexed { i, c -> if (i == index) color else c })
    }

fun removeLibrary(libraries: List<ColorLibrary>, id: String): List<ColorLibrary> =
    libraries.filter { it.id != id }

private val LibraryIdChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
fun newLibraryId(): String = "lib_" + (1..8).map { LibraryIdChars[Random.nextInt(LibraryIdChars.length)] }.joinToString("")

// SessionStore가 로컬 저장에 쓰는 손수 직렬화 — 이 프로젝트는 범용 JSON 파서를 안 씀. 라이브러리
// 이름에 쉼표 등 흔한 문자가 들어가도 안전하도록, 화면에 절대 안 보이는 제어문자를 구분자로 쓴다.
private const val RecordSeparator = "\u0001"
private const val FieldSeparator = "\u0002"

fun serializeLibraries(libraries: List<ColorLibrary>): String =
    libraries.joinToString(RecordSeparator) { "${it.id}$FieldSeparator${it.name}$FieldSeparator${it.colors.joinToString(",")}" }

/** [serializeLibraries]의 역함수 — 형식이 깨져 있으면(손상된 데이터 등) null. */
fun parseLibraries(raw: String): List<ColorLibrary>? {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        raw.split(RecordSeparator).map { record ->
            val parts = record.split(FieldSeparator)
            val colors = parts[2].split(",").map { it.toLong() }
            ColorLibrary(id = parts[0], name = parts[1], colors = colors)
        }
    }.getOrNull()
}
