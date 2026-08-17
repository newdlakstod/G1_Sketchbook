package com.g1.sketchbook.data

import android.content.Context
import com.g1.sketchbook.brush.GestureAction

/** Local persistence for user preferences that should survive app restarts. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("g1_session", Context.MODE_PRIVATE)

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

    /** Gesture shortcuts — two/three-finger tap and long-press, each mapped to an action (default: off). */
    var twoFingerTapAction: GestureAction
        get() = readGesture(KEY_GESTURE_2TAP)
        set(value) = prefs.edit().putString(KEY_GESTURE_2TAP, value.name).apply()
    var threeFingerTapAction: GestureAction
        get() = readGesture(KEY_GESTURE_3TAP)
        set(value) = prefs.edit().putString(KEY_GESTURE_3TAP, value.name).apply()
    var longPressAction: GestureAction
        get() = readGesture(KEY_GESTURE_LONGPRESS)
        set(value) = prefs.edit().putString(KEY_GESTURE_LONGPRESS, value.name).apply()

    private fun readGesture(key: String): GestureAction =
        runCatching { GestureAction.valueOf(prefs.getString(key, null) ?: "NONE") }.getOrDefault(GestureAction.NONE)

    /** Sketchbook list grid column count (3/4/5), user-adjustable via the list's hamburger menu. */
    var gridColumns: Int
        get() = prefs.getInt(KEY_GRID_COLUMNS, 3).coerceIn(3, 5)
        set(value) = prefs.edit().putInt(KEY_GRID_COLUMNS, value.coerceIn(3, 5)).apply()

    companion object {
        private const val KEY_NICK = "nickname"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_FAVS = "fav_colors"
        private const val KEY_GESTURE_2TAP = "gesture_2tap"
        private const val KEY_GESTURE_3TAP = "gesture_3tap"
        private const val KEY_GESTURE_LONGPRESS = "gesture_longpress"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        val DefaultFavorites = listOf(0xFF1E2D4CL, 0xFFACBDAAL, 0xFFE05454L, 0xFFE0A53CL, 0xFF6E9646L)
    }
}
