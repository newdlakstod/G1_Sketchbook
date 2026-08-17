package com.g1.sketchbook.data

import android.content.Context
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.GestureAction
import com.g1.sketchbook.ui.theme.Dimens

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

    /** 스케치북 화면에서 마지막으로 쓰던 브러시 색상/굵기/투명도 — 앱을 다시 켜도 이어서 쓸 수 있게
     *  저장한다(브러시 종류 자체나 지우개 선택 여부는 저장하지 않고 매번 펜으로 시작). */
    var brushColor: Long
        get() = prefs.getLong(KEY_BRUSH_COLOR, DefaultFavorites[0])
        set(value) = prefs.edit().putLong(KEY_BRUSH_COLOR, value).apply()

    fun brushSize(type: BrushType): Float = prefs.getFloat(KEY_BRUSH_SIZE_PREFIX + type.name, defaultBrushSize(type))
    fun setBrushSize(type: BrushType, value: Float) = prefs.edit().putFloat(KEY_BRUSH_SIZE_PREFIX + type.name, value).apply()

    fun brushOpacity(type: BrushType): Float = prefs.getFloat(KEY_BRUSH_OPACITY_PREFIX + type.name, 100f)
    fun setBrushOpacity(type: BrushType, value: Float) = prefs.edit().putFloat(KEY_BRUSH_OPACITY_PREFIX + type.name, value).apply()

    var eraserSize: Float
        get() = prefs.getFloat(KEY_ERASER_SIZE, Dimens.Brush.eraserWidth)
        set(value) = prefs.edit().putFloat(KEY_ERASER_SIZE, value).apply()

    private fun defaultBrushSize(type: BrushType): Float = when (type) {
        BrushType.PEN -> Dimens.Brush.penWidth
        BrushType.PENCIL -> Dimens.Brush.pencilWidth
        BrushType.CRAYON -> Dimens.Brush.crayonWidth
        BrushType.WATER -> Dimens.Brush.waterWidth
    }

    companion object {
        private const val KEY_NICK = "nickname"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_FAVS = "fav_colors"
        private const val KEY_GESTURE_2TAP = "gesture_2tap"
        private const val KEY_GESTURE_3TAP = "gesture_3tap"
        private const val KEY_GESTURE_LONGPRESS = "gesture_longpress"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_BRUSH_COLOR = "brush_color"
        private const val KEY_BRUSH_SIZE_PREFIX = "brush_size_"
        private const val KEY_BRUSH_OPACITY_PREFIX = "brush_opacity_"
        private const val KEY_ERASER_SIZE = "eraser_size"
        val DefaultFavorites = listOf(0xFF1E2D4CL, 0xFFACBDAAL, 0xFFE05454L, 0xFFE0A53CL, 0xFF6E9646L)
    }
}
