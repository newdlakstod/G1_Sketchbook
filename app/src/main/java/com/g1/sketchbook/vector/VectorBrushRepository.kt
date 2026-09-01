package com.g1.sketchbook.vector

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.random.Random

private fun String.jsonEscaped(): String = buildString {
    for (c in this@jsonEscaped) when (c) {
        '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n")
        '\r' -> append("\\r"); '\t' -> append("\\t"); else -> append(c)
    }
}

fun encodeVectorBrushProfile(profile: VectorBrushProfile): String {
    val kind = if (profile is ArtBrushProfile) "ART" else "PATTERN"
    val common = "{\"id\":\"${profile.id.jsonEscaped()}\",\"name\":\"${profile.name.jsonEscaped()}\",\"type\":\"$kind\",\"originalSvg\":\"${profile.originalSvg.jsonEscaped()}\""
    val shapes = when (profile) { is ArtBrushProfile -> profile.shapes; is PatternBrushProfile -> profile.shapes }
    val tail = (profile as? PatternBrushProfile)?.let { ",\"spacingPx\":${it.spacingPx},\"sizePx\":${it.sizePx}" } ?: ""
    return common + ",\"shapes\":[" + shapes.joinToString(",") { shape ->
        "[" + shape.joinToString(",") { "{\"x\":${it.x},\"y\":${it.y}}" } + "]"
    } + "]" + tail + "}"
}

private fun jsonString(json: String, key: String): String? {
    val start = json.indexOf("\"$key\"")
    if (start < 0) return null
    var i = json.indexOf(':', start) + 1
    if (i <= 0 || json.getOrNull(i) != '"') return null
    i++
    return buildString {
        while (i < json.length) {
            val c = json[i++]
            if (c == '"') return@buildString
            if (c == '\\' && i < json.length) {
                when (val e = json[i++]) { 'n' -> append('\n'); 'r' -> append('\r'); 't' -> append('\t'); else -> append(e) }
            } else append(c)
        }
    }.takeIf { i <= json.length && json.getOrNull(i - 1) == '"' }
}

private fun jsonNumber(json: String, key: String, fallback: Float = 0f): Float {
    val start = json.indexOf("\"$key\"").takeIf { it >= 0 } ?: return fallback
    val colon = json.indexOf(':', start).takeIf { it >= 0 } ?: return fallback
    return Regex("-?[0-9.eE+]+").find(json, colon + 1)?.value?.toFloatOrNull() ?: fallback
}

private val shapeRegex = Regex("\\[\\s*(?:\\{\\\"x\\\":-?[0-9.eE+]+,\\\"y\\\":-?[0-9.eE+]+\\}\\s*,?)+\\s*\\]")
private val pointRegex = Regex("\\{\\\"x\\\":(-?[0-9.eE+]+),\\\"y\\\":(-?[0-9.eE+]+)\\}")

fun decodeVectorBrushProfile(json: String): VectorBrushProfile? = runCatching {
    val id = jsonString(json, "id") ?: return null
    val name = jsonString(json, "name") ?: return null
    val type = jsonString(json, "type")
    val kind = when (type) { null, "PATTERN" -> VectorBrushKind.PATTERN; "ART" -> VectorBrushKind.ART; else -> return null }
    val svg = jsonString(json, "originalSvg") ?: ""
    val shapesStart = json.indexOf("\"shapes\":[")
    val shapes = if (shapesStart < 0) emptyList() else shapeRegex.findAll(json.substring(shapesStart)).map { match ->
        pointRegex.findAll(match.value).map { Point(it.groupValues[1].toFloat(), it.groupValues[2].toFloat()) }.toList()
    }.toList()
    if (kind == VectorBrushKind.ART) ArtBrushProfile(id, name, shapes, svg)
    else PatternBrushProfile(id, name, shapes, jsonNumber(json, "spacingPx", 24f), jsonNumber(json, "sizePx", 32f), svg)
}.getOrNull()

/** Typed profile persistence. Legacy stamps stay in [StampBrushRepository]. */
class VectorBrushRepository(context: Context) {
    private val context = context
    private val root = File(context.filesDir, "vector_brushes_v2").apply { mkdirs() }
    private fun file(id: String) = File(root, vectorBrushFileName(id))
    fun list(): List<VectorBrushProfile> {
        val typed = root.listFiles()?.mapNotNull { decodeVectorBrushProfile(it.readText()) } ?: emptyList()
        val byId = typed.associateBy { it.id }.toMutableMap()
        StampBrushRepository(context).list().forEach { legacy ->
            if (legacy.id !in byId) byId[legacy.id] = legacy.copy(originalSvg = StampBrushRepository(context).originalSvgText(legacy.id).orEmpty())
        }
        return byId.values.toList()
    }
    fun get(id: String): VectorBrushProfile? = file(id).takeIf { it.exists() }?.let { decodeVectorBrushProfile(it.readText()) }
        ?: StampBrushRepository(context).get(id)?.let { it.copy(originalSvg = StampBrushRepository(context).originalSvgText(id).orEmpty()) }
    fun importArt(name: String, svgText: String): ArtBrushProfile? = parseSvgArtDocument(svgText)?.let { shapes ->
        ArtBrushProfile(newId("art"), name, normalizeArtBrush(ArtBrushProfile("", name, shapes, svgText)).shapes, svgText).also { writeProfile(it) }
    }
    fun importPattern(name: String, svgText: String): PatternBrushProfile? = parseSvgDocument(svgText)?.let { shapes ->
        PatternBrushProfile(newId("pattern"), name, shapes, originalSvg = svgText).also { writeProfile(it) }
    }
    fun importFromRemote(id: String, name: String, type: String?, svgText: String, spacingPx: Float = 24f, sizePx: Float = 32f): VectorBrushProfile? {
        val kind = when (type) { null, "PATTERN" -> VectorBrushKind.PATTERN; "ART" -> VectorBrushKind.ART; else -> return null }
        val profile = if (kind == VectorBrushKind.ART) parseSvgArtDocument(svgText)?.let { raw ->
            val normalized = normalizeArtBrush(ArtBrushProfile(id, name, raw, svgText))
            normalized
        } else parseSvgDocument(svgText)?.let { PatternBrushProfile(id, name, it, spacingPx, sizePx, svgText) }
        return profile?.takeIf { writeProfile(it) }
    }
    private fun writeProfile(profile: VectorBrushProfile): Boolean {
        val target = file(profile.id); val temp = File(root, ".${vectorBrushFileName(profile.id)}.tmp")
        return runCatching {
            temp.writeText(encodeVectorBrushProfile(profile))
            check(decodeVectorBrushProfile(temp.readText())?.id == profile.id)
            runCatching { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                .getOrElse { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            true
        }.getOrElse { temp.delete(); false }
    }
    private fun newId(prefix: String) = "${prefix}_" + (1..8).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[Random.nextInt(32)] }.joinToString("")
}

fun vectorBrushFileName(id: String): String = "brush_" + id.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 0xff) } + ".json"
