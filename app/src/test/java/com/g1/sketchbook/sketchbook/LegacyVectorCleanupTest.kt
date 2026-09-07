package com.g1.sketchbook.sketchbook

import org.json.JSONArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LegacyVectorCleanupTest {
    @Test
    fun removesOnlyBooksExplicitlyMarkedAsVector() {
        val raw = """[
            {"id":"bitmap","name":"A"},
            {"id":"vector","name":"B","vector":true},
            {"id":"false","name":"C","vector":false}
        ]""".trimIndent()

        val result = assertNotNull(filterLegacyVectorBooks(raw))

        assertEquals(setOf("vector"), result.vectorBookIds)
        assertEquals(listOf("bitmap", "false"), idsIn(result.retainedJson))
    }

    @Test
    fun malformedMetadataIsNotRewritten() {
        assertNull(filterLegacyVectorBooks("not-json"))
    }

    @Test
    fun filteringIsIdempotent() {
        val first = assertNotNull(filterLegacyVectorBooks("""[{"id":"v","vector":true},{"id":"b"}]"""))

        val second = assertNotNull(filterLegacyVectorBooks(first.retainedJson))

        assertEquals(emptySet(), second.vectorBookIds)
        assertEquals(listOf("b"), idsIn(second.retainedJson))
    }

    private fun idsIn(raw: String): List<String> = JSONArray(raw).let { array ->
        (0 until array.length()).map { array.getJSONObject(it).getString("id") }
    }
}
