package com.g1.sketchbook.vector

/** Fixed-schema, pure-Kotlin JSON codec for the independently stored v2 document. */
fun encodeVectorDocument(document: VectorDocument): String {
    require(document.version == 2) { "Unsupported vector document version" }
    val ids = mutableSetOf<String>()
    return buildString {
        append("{\"version\":2,\"objects\":[")
        document.objects.forEachIndexed { index, vectorObject ->
            if (index > 0) append(',')
            require(vectorObject.id.isNotBlank()) { "Object id is required" }
            require(ids.add(vectorObject.id)) { "Object ids must be unique" }
            when (vectorObject) {
                is LegacyStrokeObject -> appendLegacyObject(vectorObject)
                is EditablePathObject -> appendEditableObject(vectorObject)
            }
        }
        append("]}")
    }
}

fun decodeVectorDocument(text: String): VectorDocument? = runCatching {
    val root = JsonReader(text).read() as JsonObject
    val version = root.required("version").asInt()
    require(version == 2) { "Unsupported vector document version" }
    val ids = mutableSetOf<String>()
    val objects = root.required("objects").asArray().values.map { value ->
        val objectValue = value.asObject()
        val id = objectValue.required("id").asString()
        require(id.isNotBlank()) { "Object id is required" }
        require(ids.add(id)) { "Object ids must be unique" }
        when (objectValue.required("type").asString()) {
            "legacy" -> decodeLegacyObject(id, objectValue.required("stroke").asObject())
            "editable" -> decodeEditableObject(id, objectValue)
            else -> error("Unknown vector object type")
        }
    }
    VectorDocument(version, objects)
}.getOrNull()

/** A valid v2 document always wins; invalid or missing v2 never causes a write to either source. */
fun decodeStoredVectorDocument(v2Text: String?, legacyText: String?): VectorDocument? {
    v2Text?.let(::decodeVectorDocument)?.let { return it }
    return legacyText?.let(::vectorPageFromJson)?.let(::legacyPageAsDocument)
}

private fun StringBuilder.appendLegacyObject(value: LegacyStrokeObject) {
    val stroke = value.stroke
    require(stroke.points.size >= 2) { "Legacy strokes need at least two points" }
    append("{\"type\":\"legacy\",\"id\":")
    appendJsonString(value.id)
    append(",\"stroke\":{\"color\":${stroke.color},\"points\":[")
    stroke.points.forEachIndexed { index, point ->
        if (index > 0) append(',')
        appendFinitePoint(point.x, point.y, point.w, "w")
    }
    append("],\"cap\":")
    appendJsonString(stroke.cap.name)
    append(",\"fillEnabled\":${stroke.fillEnabled},\"strokeColor\":")
    appendNullableLong(stroke.strokeColor)
    append(",\"strokeWidthPx\":")
    appendFinite(stroke.strokeWidthPx)
    append(",\"brushProfileId\":")
    appendNullableString(stroke.brushProfileId)
    append(",\"fillColor\":")
    appendNullableLong(stroke.fillColor)
    append("}}")
}

private fun StringBuilder.appendEditableObject(value: EditablePathObject) {
    append("{\"type\":\"editable\",\"id\":")
    appendJsonString(value.id)
    append(",\"geometry\":{\"points\":[")
    value.geometry.points.forEachIndexed { index, point ->
        if (index > 0) append(',')
        require(point.widthFactor.isFinite() && point.widthFactor in 0.05f..1f) { "Invalid width factor" }
        appendFinitePoint(point.x, point.y, point.widthFactor, "widthFactor")
    }
    append("],\"closed\":${value.geometry.closed}},\"appearance\":")
    appendAppearance(value.appearance)
    append(",\"transform\":")
    appendTransform(value.transform)
    append('}')
}

private fun StringBuilder.appendAppearance(value: PathAppearance) {
    append("{\"fill\":{\"enabled\":${value.fill.enabled},\"color\":${value.fill.color}},\"stroke\":{\"enabled\":${value.stroke.enabled},\"color\":${value.stroke.color},\"width\":")
    appendFinite(value.stroke.width)
    append(",\"cap\":")
    appendJsonString(value.stroke.cap.name)
    append(",\"join\":")
    appendJsonString(value.stroke.join.name)
    append("},\"brush\":{\"kind\":")
    appendJsonString(value.brush.kind.name)
    append(",\"profileId\":")
    appendNullableString(value.brush.profileId)
    append("}}")
}

private fun StringBuilder.appendTransform(value: ObjectTransform) {
    append("{\"translateX\":"); appendFinite(value.translateX)
    append(",\"translateY\":"); appendFinite(value.translateY)
    append(",\"scaleX\":"); appendFinite(value.scaleX)
    append(",\"scaleY\":"); appendFinite(value.scaleY)
    append(",\"rotationDegrees\":"); appendFinite(value.rotationDegrees)
    append('}')
}

private fun StringBuilder.appendFinitePoint(x: Float, y: Float, third: Float, thirdName: String) {
    append("{\"x\":"); appendFinite(x)
    append(",\"y\":"); appendFinite(y)
    append(",\"").append(thirdName).append("\":"); appendFinite(third)
    append('}')
}

private fun StringBuilder.appendFinite(value: Float) {
    require(value.isFinite()) { "Non-finite float" }
    append(value.toString())
}

private fun StringBuilder.appendNullableLong(value: Long?) {
    if (value == null) append("null") else append(value)
}

private fun StringBuilder.appendNullableString(value: String?) {
    if (value == null) append("null") else appendJsonString(value)
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { c ->
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}

private fun decodeLegacyObject(id: String, source: JsonObject): LegacyStrokeObject {
    val points = source.required("points").asArray().values.map { point ->
        val value = point.asObject()
        VectorPoint(value.required("x").asFiniteFloat(), value.required("y").asFiniteFloat(), value.required("w").asFiniteFloat())
    }
    require(points.size >= 2) { "Legacy strokes need at least two points" }
    return LegacyStrokeObject(id, VectorStroke(
        color = source.required("color").asLong(),
        points = points,
        cap = source.required("cap").asString().enumValue<VectorCap>(),
        fillEnabled = source.required("fillEnabled").asBoolean(),
        strokeColor = source.required("strokeColor").asNullableLong(),
        strokeWidthPx = source.required("strokeWidthPx").asFiniteFloat(),
        brushProfileId = source.required("brushProfileId").asNullableString(),
        fillColor = source.required("fillColor").asNullableLong(),
    ))
}

private fun decodeEditableObject(id: String, source: JsonObject): EditablePathObject {
    val geometryValue = source.required("geometry").asObject()
    val geometry = PathGeometry(
        points = geometryValue.required("points").asArray().values.map { point ->
            val value = point.asObject()
            val widthFactor = value.required("widthFactor").asFiniteFloat()
            require(widthFactor in 0.05f..1f) { "Invalid width factor" }
            PathPoint(value.required("x").asFiniteFloat(), value.required("y").asFiniteFloat(), widthFactor)
        },
        closed = geometryValue.required("closed").asBoolean(),
    )
    val appearanceValue = source.required("appearance").asObject()
    val fill = appearanceValue.required("fill").asObject()
    val stroke = appearanceValue.required("stroke").asObject()
    val brush = appearanceValue.required("brush").asObject()
    val transform = source.required("transform").asObject()
    return EditablePathObject(
        id = id,
        geometry = geometry,
        appearance = PathAppearance(
            fill = FillStyle(fill.required("enabled").asBoolean(), fill.required("color").asLong()),
            stroke = StrokeStyle(
                enabled = stroke.required("enabled").asBoolean(),
                color = stroke.required("color").asLong(),
                width = stroke.required("width").asFiniteFloat(),
                cap = stroke.required("cap").asString().enumValue<VectorCap>(),
                join = stroke.required("join").asString().enumValue<VectorJoin>(),
            ),
            brush = BrushStyle(
                kind = brush.required("kind").asString().enumValue<VectorBrushKind>(),
                profileId = brush.required("profileId").asNullableString(),
            ),
        ),
        transform = ObjectTransform(
            translateX = transform.required("translateX").asFiniteFloat(),
            translateY = transform.required("translateY").asFiniteFloat(),
            scaleX = transform.required("scaleX").asFiniteFloat(),
            scaleY = transform.required("scaleY").asFiniteFloat(),
            rotationDegrees = transform.required("rotationDegrees").asFiniteFloat(),
        ),
    )
}

private fun JsonObject.required(name: String): JsonValue = values[name] ?: error("Missing $name")
private fun JsonValue.asObject(): JsonObject = this as? JsonObject ?: error("Expected object")
private fun JsonValue.asArray(): JsonArray = this as? JsonArray ?: error("Expected array")
private fun JsonValue.asString(): String = (this as? JsonString)?.value ?: error("Expected string")
private fun JsonValue.asBoolean(): Boolean = (this as? JsonBoolean)?.value ?: error("Expected boolean")
private fun JsonValue.asLong(): Long = (this as? JsonNumber)?.raw?.toLongOrNull() ?: error("Expected long")
private fun JsonValue.asInt(): Int = (this as? JsonNumber)?.raw?.toIntOrNull() ?: error("Expected int")
private fun JsonValue.asFiniteFloat(): Float {
    val value = (this as? JsonNumber)?.raw?.toFloatOrNull() ?: error("Expected float")
    require(value.isFinite()) { "Non-finite float" }
    return value
}
private fun JsonValue.asNullableLong(): Long? = if (this === JsonNull) null else asLong()
private fun JsonValue.asNullableString(): String? = if (this === JsonNull) null else asString()
private inline fun <reified T : Enum<T>> String.enumValue(): T = enumValues<T>().firstOrNull { it.name == this }
    ?: error("Unknown enum")

private sealed interface JsonValue
private data class JsonObject(val values: Map<String, JsonValue>) : JsonValue
private data class JsonArray(val values: List<JsonValue>) : JsonValue
private data class JsonString(val value: String) : JsonValue
private data class JsonNumber(val raw: String) : JsonValue
private data class JsonBoolean(val value: Boolean) : JsonValue
private data object JsonNull : JsonValue

/** Deliberately small recursive reader: the codec owns a fixed schema, but still parses valid JSON escapes. */
private class JsonReader(private val text: String) {
    private var position = 0

    fun read(): JsonValue {
        skipWhitespace()
        val value = readValue()
        skipWhitespace()
        require(position == text.length) { "Trailing JSON content" }
        return value
    }

    private fun readValue(): JsonValue {
        require(position < text.length) { "Unexpected end" }
        return when (text[position]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> JsonString(readString())
            't' -> readLiteral("true", JsonBoolean(true))
            'f' -> readLiteral("false", JsonBoolean(false))
            'n' -> readLiteral("null", JsonNull)
            '-', in '0'..'9' -> readNumber()
            else -> error("Unexpected JSON token")
        }
    }

    private fun readObject(): JsonObject {
        expect('{'); skipWhitespace()
        val values = linkedMapOf<String, JsonValue>()
        if (consume('}')) return JsonObject(values)
        while (true) {
            require(position < text.length && text[position] == '"') { "Object key expected" }
            val key = readString()
            require(values.put(key, JsonNull) == null) { "Duplicate object key" }
            skipWhitespace(); expect(':'); skipWhitespace()
            values[key] = readValue()
            skipWhitespace()
            if (consume('}')) return JsonObject(values)
            expect(','); skipWhitespace()
        }
    }

    private fun readArray(): JsonArray {
        expect('['); skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (consume(']')) return JsonArray(values)
        while (true) {
            values += readValue()
            skipWhitespace()
            if (consume(']')) return JsonArray(values)
            expect(','); skipWhitespace()
        }
    }

    private fun readString(): String {
        expect('"')
        return buildString {
            while (true) {
                require(position < text.length) { "Unterminated string" }
                when (val c = text[position++]) {
                    '"' -> return@buildString
                    '\\' -> append(readEscape())
                    else -> {
                        require(c >= ' ') { "Control character in string" }
                        append(c)
                    }
                }
            }
        }
    }

    private fun readEscape(): Char = when (val escape = nextChar()) {
        '"', '\\', '/' -> escape
        'b' -> '\b'
        'f' -> '\u000C'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'u' -> {
            require(position + 4 <= text.length) { "Invalid unicode escape" }
            val digits = text.substring(position, position + 4)
            position += 4
            digits.toIntOrNull(16)?.toChar() ?: error("Invalid unicode escape")
        }
        else -> error("Invalid escape")
    }

    private fun readNumber(): JsonNumber {
        val start = position
        consume('-')
        if (consume('0')) {
            require(position == text.length || text[position] !in '0'..'9') { "Leading zero" }
        } else {
            require(position < text.length && text[position] in '1'..'9') { "Invalid number" }
            while (position < text.length && text[position] in '0'..'9') position++
        }
        if (consume('.')) {
            require(position < text.length && text[position] in '0'..'9') { "Invalid fraction" }
            while (position < text.length && text[position] in '0'..'9') position++
        }
        if (position < text.length && text[position] in charArrayOf('e', 'E')) {
            position++
            if (position < text.length && text[position] in charArrayOf('+', '-')) position++
            require(position < text.length && text[position] in '0'..'9') { "Invalid exponent" }
            while (position < text.length && text[position] in '0'..'9') position++
        }
        val raw = text.substring(start, position)
        require(raw.toDoubleOrNull()?.isFinite() == true) { "Non-finite number" }
        return JsonNumber(raw)
    }

    private fun <T : JsonValue> readLiteral(literal: String, value: T): T {
        require(text.regionMatches(position, literal, 0, literal.length)) { "Invalid literal" }
        position += literal.length
        return value
    }

    private fun expect(expected: Char) {
        require(nextChar() == expected) { "Expected $expected" }
    }

    private fun consume(expected: Char): Boolean = if (position < text.length && text[position] == expected) {
        position++
        true
    } else false

    private fun nextChar(): Char {
        require(position < text.length) { "Unexpected end" }
        return text[position++]
    }

    private fun skipWhitespace() {
        while (position < text.length && text[position] in charArrayOf(' ', '\n', '\r', '\t')) position++
    }
}
