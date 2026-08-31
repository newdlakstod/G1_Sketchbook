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

    /** 툴바에 항상 보이는 빠른 접근 색상 5개 — [paletteColors](21개)와 서로 독립이다(2026-08-31,
     *  예전엔 같은 리스트의 앞 5개를 그대로 썼는데 분리 요청으로 갈라짐). 아직 한 번도 따로 저장한
     *  적 없으면(분리 직후 첫 실행 포함) 그 시점의 [paletteColors] 앞 5개를 그대로 복사해서
     *  시작한다 — 마이그레이션 코드 없이 이 기본값 계산만으로 "기존 21개 중 앞 5개를 즐겨찾기로"
     *  요구사항이 자연스럽게 만족된다. */
    var quickFavorites: List<Long>
        get() {
            val raw = prefs.getString(KEY_QUICK_FAVS, null)
            val parsed = raw?.let { runCatching { it.split(",").map { s -> s.toLong() } }.getOrNull() }
            return parsed?.takeIf { it.size == QuickFavoritesCount } ?: paletteColors.take(QuickFavoritesCount)
        }
        set(value) = prefs.edit().putString(KEY_QUICK_FAVS, value.joinToString(",")).apply()

    /** "즐겨찾기 전체" 그리드에 보이는 색상 21개 — [quickFavorites]와 독립. 분리 이전엔 이 저장
     *  키([KEY_FAVS])가 곧 "즐겨찾기"였다 — 그 키를 그대로 재사용해서, 이미 저장돼 있던 21개가
     *  자동으로(코드 변경 없이) 팔레트가 된다. */
    var paletteColors: List<Long>
        get() {
            val raw = prefs.getString(KEY_FAVS, null) ?: return DefaultFavorites
            return runCatching { raw.split(",").map { it.toLong() } }
                .getOrNull()?.takeIf { it.size == PaletteCount } ?: DefaultFavorites
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

    /** 스케치북 목록 표지 크기 — 크게/작게 중 사용자가 고르면, 실제 한 줄에 몇 개가 들어가는지는 화면
     *  폭에 맞춰 자동으로 계산된다(GridCells.Adaptive, 2026-08-27). 예전엔 3/4/5열을 직접 골랐었다 —
     *  기존 사용자의 그 값은 Int로 저장돼 있어 같은 키를 Boolean으로 읽으면 크래시 나므로 새 키를 쓴다. */
    var largeCovers: Boolean
        get() = prefs.getBoolean(KEY_COVER_SIZE_LARGE, true)
        set(value) = prefs.edit().putBoolean(KEY_COVER_SIZE_LARGE, value).apply()

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
        private const val KEY_QUICK_FAVS = "quick_fav_colors"
        private const val KEY_GESTURE_2TAP = "gesture_2tap"
        private const val KEY_GESTURE_3TAP = "gesture_3tap"
        private const val KEY_GESTURE_LONGPRESS = "gesture_longpress"
        private const val KEY_GRID_COLUMNS = "grid_columns" // 더 안 씀(옛 3/4/5열 값, Int) — largeCovers가 새 키를 씀
        private const val KEY_COVER_SIZE_LARGE = "cover_size_large"
        private const val KEY_BRUSH_COLOR = "brush_color"
        private const val KEY_BRUSH_SIZE_PREFIX = "brush_size_"
        private const val KEY_BRUSH_OPACITY_PREFIX = "brush_opacity_"
        private const val KEY_ERASER_SIZE = "eraser_size"
        private const val KEY_ERASER_OPACITY = "eraser_opacity"
        private const val KEY_ERASER_BLUR = "eraser_blur"
        private const val KEY_SETTINGS_SYNCED_AT = "settings_synced_at"
        // 색상 피커 카드 폭(260dp, BrushControls.ColorPickerCard)에 24dp 스와치+8dp 간격이 한 줄에
        // 7개 들어가서(FavoritesGrid) 7×3줄 = 21 — 그리드 칸 수가 바뀌면 이 값도 같이 맞춰야 한다.
        const val PaletteCount = 21
        const val QuickFavoritesCount = 5
        val DefaultFavorites = listOf(
            0xFF1E2D4CL, 0xFFACBDAAL, 0xFFE05454L, 0xFFE0A53CL, 0xFF6E9646L,
            0xFF000000L, 0xFFFFFFFFL, 0xFF808080L, 0xFF2B4C9BL, 0xFF4DABF7L,
            0xFF4ECDC4L, 0xFF9775FAL, 0xFFCE7A7AL, 0xFFFF8FA3L, 0xFFFFD93DL,
            0xFF6B4226L, 0xFF2F5233L, 0xFF4B0082L, 0xFFD3D3D3L, 0xFFFF7F50L,
            0xFFB8860BL,
        )
    }
}
