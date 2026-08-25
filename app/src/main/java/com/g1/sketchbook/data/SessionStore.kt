package com.g1.sketchbook.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.GestureAction
import com.g1.sketchbook.ui.theme.Dimens
import java.io.File
import java.io.FileOutputStream

/** Local persistence for user preferences that should survive app restarts. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("g1_session", Context.MODE_PRIVATE)
    private val avatarFile = File(context.filesDir, "avatar.png")

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

    /** 계정 이미지(갤러리에서 선택) — 파일이 없으면(한 번도 선택 안 했으면) 기본 실루엣 아이콘을
     *  대신 보여준다(호출부 책임, 여기선 null만 반환). */
    fun saveAvatarImage(bmp: Bitmap) {
        FileOutputStream(avatarFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    fun loadAvatarImage(): Bitmap? = if (avatarFile.exists()) BitmapFactory.decodeFile(avatarFile.absolutePath) else null

    /** 20 editable colour favourites (ARGB longs) — the toolbar shows the first 5 inline, the rest
     *  live in the "즐겨찾기 전체" grid popup ([FavoritesCount]). */
    var favoriteColors: List<Long>
        get() {
            val raw = prefs.getString(KEY_FAVS, null) ?: return DefaultFavorites
            return runCatching { raw.split(",").map { it.toLong() } }
                .getOrNull()?.takeIf { it.size == FavoritesCount } ?: DefaultFavorites
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

    var eraserOpacity: Float
        get() = prefs.getFloat(KEY_ERASER_OPACITY, 100f)
        set(value) = prefs.edit().putFloat(KEY_ERASER_OPACITY, value).apply()

    var eraserBlur: Float
        get() = prefs.getFloat(KEY_ERASER_BLUR, 0f)
        set(value) = prefs.edit().putFloat(KEY_ERASER_BLUR, value).apply()

    /** 마지막으로 구글 계정 백업과 설정값을 맞춘 시각(0 = 한 번도 안 함) — 클라우드 설정값의
     *  updatedAt과 비교해서 더 최신이면 받아올지 판단하는 데 쓴다. 설정값은 필드마다 따로가
     *  아니라 통째로 한 항목으로 동기화하므로, 이 값 하나면 충분하다. */
    var settingsSyncedAt: Long
        get() = prefs.getLong(KEY_SETTINGS_SYNCED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_SETTINGS_SYNCED_AT, value).apply()

    private fun defaultBrushSize(type: BrushType): Float = when (type) {
        BrushType.PEN -> Dimens.Brush.penWidth
        BrushType.PENCIL -> Dimens.Brush.pencilWidth
        BrushType.CRAYON -> Dimens.Brush.crayonWidth
        BrushType.WATER -> Dimens.Brush.waterWidth
    }

    companion object {
        private const val KEY_NICK = "nickname"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_FAVS = "fav_colors"
        private const val KEY_GESTURE_2TAP = "gesture_2tap"
        private const val KEY_GESTURE_3TAP = "gesture_3tap"
        private const val KEY_GESTURE_LONGPRESS = "gesture_longpress"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_BRUSH_COLOR = "brush_color"
        private const val KEY_BRUSH_SIZE_PREFIX = "brush_size_"
        private const val KEY_BRUSH_OPACITY_PREFIX = "brush_opacity_"
        private const val KEY_ERASER_SIZE = "eraser_size"
        private const val KEY_ERASER_OPACITY = "eraser_opacity"
        private const val KEY_ERASER_BLUR = "eraser_blur"
        private const val KEY_SETTINGS_SYNCED_AT = "settings_synced_at"
        const val FavoritesCount = 20
        val DefaultFavorites = listOf(
            0xFF1E2D4CL, 0xFFACBDAAL, 0xFFE05454L, 0xFFE0A53CL, 0xFF6E9646L,
            0xFF000000L, 0xFFFFFFFFL, 0xFF808080L, 0xFF2B4C9BL, 0xFF4DABF7L,
            0xFF4ECDC4L, 0xFF9775FAL, 0xFFCE7A7AL, 0xFFFF8FA3L, 0xFFFFD93DL,
            0xFF6B4226L, 0xFF2F5233L, 0xFF4B0082L, 0xFFD3D3D3L, 0xFFFF7F50L,
        )
    }
}
