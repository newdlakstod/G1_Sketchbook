package com.g1.sketchbook.data

import android.content.Context
import com.g1.sketchbook.data.model.SketchbookRef
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tiny local persistence: which room the app should reopen into, plus the list of sketchbooks
 * the user has created/joined so they can re-enter without re-typing the code.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("g1_session", Context.MODE_PRIVATE)

    var currentRoomId: String?
        get() = prefs.getString(KEY_ROOM, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_ROOM) else putString(KEY_ROOM, value)
        }.apply()

    /** User's chosen nickname (null until they set it after first sign-in). */
    var nickname: String?
        get() = prefs.getString(KEY_NICK, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_NICK) else putString(KEY_NICK, value)
        }.apply()

    /** Theme preference: "system" | "light" | "dark". */
    var themeMode: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /** Emoji avatar. */
    var avatar: String
        get() = prefs.getString(KEY_AVATAR, "🦆") ?: "🦆"
        set(value) = prefs.edit().putString(KEY_AVATAR, value).apply()

    /** Five editable colour favourites (ARGB longs) for the brush toolbar. */
    var favoriteColors: List<Long>
        get() {
            val raw = prefs.getString(KEY_FAVS, null) ?: return DefaultFavorites
            return runCatching { raw.split(",").map { it.toLong() } }
                .getOrNull()?.takeIf { it.size == 5 } ?: DefaultFavorites
        }
        set(value) = prefs.edit().putString(KEY_FAVS, value.joinToString(",")).apply()

    /** Sketchbooks the user knows about, most-recently-opened first. */
    fun sketchbooks(): List<SketchbookRef> {
        val raw = prefs.getString(KEY_BOOKS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SketchbookRef(o.getString("id"), o.optString("name"))
            }
        }.getOrDefault(emptyList())
    }

    /** Adds/refreshes a sketchbook, moving it to the front (most recent). */
    fun rememberSketchbook(ref: SketchbookRef) {
        val list = sketchbooks().filter { it.id != ref.id }.toMutableList()
        list.add(0, ref)
        save(list.take(MAX_BOOKS))
    }

    fun forgetSketchbook(id: String) {
        save(sketchbooks().filter { it.id != id })
    }

    private fun save(list: List<SketchbookRef>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs.edit().putString(KEY_BOOKS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_ROOM = "current_room"
        private const val KEY_BOOKS = "sketchbooks"
        private const val KEY_NICK = "nickname"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_FAVS = "fav_colors"
        private const val MAX_BOOKS = 30
        val DefaultFavorites = listOf(0xFF1E2D4CL, 0xFFACBDAAL, 0xFFE05454L, 0xFFE0A53CL, 0xFF6E9646L)
    }
}
