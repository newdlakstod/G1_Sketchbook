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
