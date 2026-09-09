# 이미지 스포이드로 라이브러리 색 채우기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 색상 라이브러리 칸을 편집할 때, 갤러리에서 고른 이미지를 확대/이동하며 스포이드로 색을 뽑아 그 칸(과 같은 라이브러리의 다른 칸들)에 바로 저장할 수 있게 한다.

**Architecture:** 새 파일 `ImageEyedropper.kt`에 (1) 화면 터치 좌표 → 실제 비트맵 픽셀 좌표 역변환을 하는 순수 함수와 (2) 그 좌표를 실제로 쓰는 전체화면 오버레이 컴포저블(`ImageEyedropperOverlay`)을 담는다. 진입점은 기존 `ColorPickerCard`에 새 선택적 파라미터(`librarySwatch`)를 추가해 "이미지에서" 버튼을 붙이는 것 하나뿐이라, 세 화면(Sketchbook/Diary/SharedBook)이나 `ColorLibraryDetailPopup`은 건드리지 않는다. 이미지 디코딩·색 저장은 기존 `decodeCoverBitmap`/`onEditLibraryColor` 경로를 그대로 재사용한다.

**Tech Stack:** Kotlin, Jetpack Compose, Android `ActivityResultContracts.PickVisualMedia`(Photo Picker).

## Global Constraints

- 진입점: `ColorLibraryDetailPopup`에서 칸을 탭해 열리는 `ColorPickerCard` 안, 기존 "스포이드"(캔버스에서 뽑기) 버튼 옆에 "이미지에서" 버튼 — 라이브러리 칸 편집 맥락(`librarySwatch != null`)일 때만 보인다. 즉시선택 즐겨찾기·팔레트 그리드 편집 등 다른 `ColorPickerCard` 용도에서는 안 보인다.
- 이미지는 기기 갤러리(Android 표준 Photo Picker)에서만 가져온다 — 카메라 촬영 없음, 별도 런타임 권한 요청 없음.
- 이미지를 고르면 전체화면 오버레이가 뜬다: 핀치로 확대(1~5배)·한 손가락 드래그로 이동 가능한 이미지, 지금 편집 중인 라이브러리의 7칸 스와치 줄(대상 칸 강조 표시), 손가락으로 이미지를 누르는 동안 뜨는 확대 미리보기(루페 — 기존 `EyedropFloatingPreview` 재사용), 손을 떼면 그 색이 강조된 대상 칸에 즉시 저장(별도 확인 단계 없음).
- 대상 칸은 오버레이 안 스와치 줄에서 다른 칸을 탭해 바꿀 수 있다 — 이미지와 확대/이동 상태는 유지된다. 처음 대상은 "이미지에서"를 누르기 직전 `ColorPickerCard`가 편집 중이던 칸.
- "완료"를 누르면 오버레이가 닫히고 `ColorPickerCard`(그리고 그 아래 `ColorLibraryDetailPopup`)로 돌아간다. 색은 매번 뽑을 때마다 이미 저장돼 있으므로 별도 "저장" 동작 없음.
- 이미지 디코딩은 새로 만들지 않고 기존 `decodeCoverBitmap(context, uri, maxDim)`(`SketchbookScreens.kt`, internal)을 재사용하되 `maxDim = 1200`으로 호출(표지 사진 1600보다 작게 — 색 뽑기엔 그 정도 해상도면 충분, 메모리 절약).
- 색 저장은 새 동기화 로직을 만들지 않고 기존 `onEditLibraryColor(libraryId, index, color)` 콜백을 그대로 호출해 처리한다.
- 이번 스펙에서 다루지 않는 것: 카메라 촬영, 고른 이미지 보관/재사용, 스와치 칸이 아닌 다른 대상(즉시선택 즐겨찾기 등)에 이미지 스포이드 적용, 전용 undo.
- 화면 좌표 → 비트맵 픽셀 좌표 역변환은 Android 의존 없는 순수 함수로 만들어 유닛 테스트 대상으로 분리한다. 나머지(Compose UI, Photo Picker 연동, 실제 디코딩)는 이 프로젝트 관례상 컴파일 확인 + 수동 확인.

---

### Task 1: 화면 좌표 → 비트맵 픽셀 좌표 역변환 (순수 함수 + TDD)

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/brush/ImageEyedropper.kt`
- Test: `app/src/test/java/com/g1/sketchbook/brush/ImageEyedropperTest.kt`

**Interfaces:**
- Produces: `fun imageEyedropperPixel(touchX: Float, touchY: Float, boxWidth: Float, boxHeight: Float, bitmapWidth: Int, bitmapHeight: Int, zoom: Float, panX: Float, panY: Float): Pair<Int, Int>?` — Task 2가 실제 터치 처리에서 이 함수를 그대로 호출한다.

이미지는 `ContentScale.Crop`(박스를 꽉 채우도록, 큰 쪽 비율 기준으로 맞추고 남는 쪽은 잘림)으로 먼저 맞춘 뒤, 그 위에 사용자의 추가 확대(`zoom`)와 이동(`panX`/`panY`, 화면 px, 박스 중심 기준 — Compose `Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX; translationY = panY }`가 기본으로 박스 중심을 피벗점 삼아 적용하는 것과 정확히 같은 변환)이 겹쳐 적용된 상태다. 이 함수는 그 반대 방향(화면 터치 → 원본 비트맵 픽셀)으로 계산한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/g1/sketchbook/brush/ImageEyedropperTest.kt`:

```kotlin
package com.g1.sketchbook.brush

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageEyedropperTest {
    @Test fun noZoomNoPanSameAspectMapsCornerToCorner() {
        // 박스와 비트맵이 정확히 같은 크기(정사각형)면 baseScale=1, 좌표가 그대로 대응돼야 한다.
        assertEquals(0 to 0, imageEyedropperPixel(0f, 0f, 100f, 100f, 100, 100, 1f, 0f, 0f))
        assertEquals(99 to 99, imageEyedropperPixel(99f, 99f, 100f, 100f, 100, 100, 1f, 0f, 0f))
        assertEquals(50 to 50, imageEyedropperPixel(50f, 50f, 100f, 100f, 100, 100, 1f, 0f, 0f))
    }

    @Test fun cropFitScalesUpNarrowerBitmapToFillBox() {
        // 비트맵이 박스보다 세로로 긴 경우, Crop은 가로 기준(더 큰 비율)으로 맞추고 위아래를 자른다.
        // 박스 200x100, 비트맵 100x200 → baseScale = max(200/100, 100/200) = 2.0.
        // 박스 중앙(100,50)은 비트맵 중앙(50,100)에 대응해야 한다.
        assertEquals(50 to 100, imageEyedropperPixel(100f, 50f, 200f, 100f, 100, 200, 1f, 0f, 0f))
    }

    @Test fun zoomInMapsHalfTheScreenDistanceFromCenter() {
        // 박스=비트맵=100x100, zoom=2일 때 중심에서 10px 떨어진 터치는 비트맵 중심에서 5px 떨어진
        // 지점에 대응해야 한다(줌인하면 같은 화면 거리가 원본에서는 더 가까운 거리를 가리킴).
        assertEquals(55 to 50, imageEyedropperPixel(60f, 50f, 100f, 100f, 100, 100, 2f, 0f, 0f))
    }

    @Test fun panShiftsWhichBitmapPointIsUnderACenterTouch() {
        // 박스=비트맵=100x100, panX=20으로 이미지를 오른쪽으로 20px 옮겼다면, 화면 중심에서 오른쪽
        // 으로 20px 옮겨 누른 지점이 원래(팬 이전) 중심이 가리키던 비트맵 지점과 같아야 한다.
        assertEquals(50 to 50, imageEyedropperPixel(70f, 50f, 100f, 100f, 100, 100, 1f, 20f, 0f))
    }

    @Test fun outOfBitmapBoundsReturnsNull() {
        // zoom=1, pan=0일 때 박스 밖(0보다 작은 좌표에 대응하는 지점)을 누르면 null.
        assertNull(imageEyedropperPixel(-5f, 50f, 100f, 100f, 100, 100, 1f, 0f, 0f))
    }

    @Test fun zeroBoxOrBitmapSizeReturnsNull() {
        assertNull(imageEyedropperPixel(0f, 0f, 0f, 100f, 100, 100, 1f, 0f, 0f))
        assertNull(imageEyedropperPixel(0f, 0f, 100f, 100f, 0, 100, 1f, 0f, 0f))
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.brush.ImageEyedropperTest" --no-daemon`
Expected: FAIL — `imageEyedropperPixel`이 아직 없어서 컴파일 에러.

- [ ] **Step 3: 구현 작성**

`app/src/main/java/com/g1/sketchbook/brush/ImageEyedropper.kt` (이 시점엔 아래 함수만):

```kotlin
package com.g1.sketchbook.brush

import kotlin.math.max
import kotlin.math.roundToInt

/** 화면에 표시된 이미지 위 터치 좌표(박스 기준, px)를 실제 비트맵 픽셀 좌표로 되돌린다. 이미지는
 *  [ContentScale.Crop]으로 박스를 꽉 채우게 먼저 맞춘 뒤(큰 쪽 비율 기준, baseScale), 그 위에 사용자가
 *  추가로 확대한 배율([zoom])과 이동([panX]/[panY], 박스 중심을 피벗점으로 한 화면 px 이동 —
 *  `Modifier.graphicsLayer { scaleX = zoom; translationX = panX }`가 기본으로 적용하는 변환과 정확히
 *  같다)이 겹쳐 있다. 결과가 비트맵 범위를 벗어나면 null. */
fun imageEyedropperPixel(
    touchX: Float, touchY: Float,
    boxWidth: Float, boxHeight: Float,
    bitmapWidth: Int, bitmapHeight: Int,
    zoom: Float, panX: Float, panY: Float,
): Pair<Int, Int>? {
    if (boxWidth <= 0f || boxHeight <= 0f || bitmapWidth <= 0 || bitmapHeight <= 0) return null
    val baseScale = max(boxWidth / bitmapWidth, boxHeight / bitmapHeight)
    val dispW = bitmapWidth * baseScale
    val dispH = bitmapHeight * baseScale
    val baseOffsetX = (boxWidth - dispW) / 2f
    val baseOffsetY = (boxHeight - dispH) / 2f
    val cx = boxWidth / 2f
    val cy = boxHeight / 2f
    val bx = ((touchX - cx - panX) / zoom) + cx - baseOffsetX
    val by = ((touchY - cy - panY) / zoom) + cy - baseOffsetY
    val px = (bx / baseScale).roundToInt()
    val py = (by / baseScale).roundToInt()
    if (px !in 0 until bitmapWidth || py !in 0 until bitmapHeight) return null
    return px to py
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.brush.ImageEyedropperTest" --no-daemon`
Expected: PASS, 6개 테스트 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/brush/ImageEyedropper.kt app/src/test/java/com/g1/sketchbook/brush/ImageEyedropperTest.kt
git commit -m "feat(brush): add pure screen-to-bitmap pixel mapping for image eyedropper"
```

---

### Task 2: 전체화면 이미지 스포이드 오버레이 UI

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/brush/ImageEyedropper.kt` (Task 1이 만든 파일에 추가)

**Interfaces:**
- Consumes: `imageEyedropperPixel(...)`(Task 1, 같은 파일), `EyedropFloatingPreview(colorArgb, xPx, yPx)`(기존, `BrushControls.kt`에 이미 `internal` 아닌 public — 같은 패키지라 import 불필요), `com.g1.sketchbook.data.ColorLibrary`, `FavoriteSwatchSize`(기존 상수, `BrushControls.kt`).
- Produces: `data class LibrarySwatchContext(val library: ColorLibrary, val colorIndex: Int, val onEditColor: (Int, Long) -> Unit)`, `@Composable fun ImageEyedropperOverlay(bitmap: Bitmap, context: LibrarySwatchContext, onDone: () -> Unit)` — Task 3이 `ColorPickerCard`에서 이 두 가지를 그대로 쓴다.

이 컴포저블은 Compose UI라 유닛 테스트 대상이 아니다 — 컴파일 확인 + 수동 확인(핀치줌/이동, 한 손가락으로 색 뽑기, 대상 칸 전환, 완료).

- [ ] **Step 1: `LibrarySwatchContext` 데이터 클래스 추가**

`ImageEyedropper.kt`에 (Task 1의 함수 위나 아래, 파일 어디든) 추가:

```kotlin
import com.g1.sketchbook.data.ColorLibrary

/** [ColorPickerCard]가 "지금 라이브러리 칸을 편집하는 중"임을 [ImageEyedropperOverlay]에 알리는
 *  묶음 — 스와치 줄을 그리는 데 필요한 라이브러리 전체 색상 목록, 처음에 강조할 칸의 인덱스, 그리고
 *  어떤 칸이든 색이 바뀔 때 부를 콜백을 담는다. */
data class LibrarySwatchContext(
    val library: ColorLibrary,
    val colorIndex: Int,
    val onEditColor: (Int, Long) -> Unit,
)
```

- [ ] **Step 2: `ImageEyedropperOverlay` 컴포저블 작성**

같은 파일에 추가:

```kotlin
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.hypot

/** 갤러리에서 고른 [bitmap]을 핀치줌/드래그로 확대·이동하며, 손가락으로 누른 지점의 색을
 *  [context]가 가리키는 라이브러리 칸에 바로 저장하는 전체화면 오버레이. 대상 칸은 위쪽 스와치
 *  줄에서 바꿀 수 있고, 이미지·확대 상태는 대상을 바꿔도 유지된다. */
@Composable
fun ImageEyedropperOverlay(bitmap: Bitmap, context: LibrarySwatchContext, onDone: () -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableIntStateOf(context.colorIndex) }
    // 손가락으로 누르고 있는 동안의 미리보기 — null이면 지금 안 누르고 있는 것.
    var preview by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usesPlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val boxWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
                val boxHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(bitmap) {
                            awaitPointerEventScope {
                                // 이전 프레임의 2손가락 중점/거리 — 다음 프레임과 비교해 확대/이동 델타를 구한다
                                // (BrushView.onTouchEvent의 핀치줌 처리와 같은 방식, MotionEvent 대신 Compose의
                                // PointerEvent.changes를 씀).
                                var prevMidX = 0f; var prevMidY = 0f; var prevDist = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    when {
                                        pressed.size >= 2 -> {
                                            val a = pressed[0].position; val b = pressed[1].position
                                            val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
                                            val dist = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
                                            if (prevDist > 0f) {
                                                zoom = (zoom * (dist / prevDist)).coerceIn(1f, 5f)
                                                panX += mx - prevMidX; panY += my - prevMidY
                                            }
                                            prevMidX = mx; prevMidY = my; prevDist = dist
                                            preview = null
                                            pressed.forEach { it.consume() }
                                        }
                                        pressed.size == 1 -> {
                                            prevDist = 0f
                                            val p = pressed[0].position
                                            val picked = imageEyedropperPixel(
                                                p.x, p.y, boxWidthPx, boxHeightPx,
                                                bitmap.width, bitmap.height, zoom, panX, panY,
                                            )
                                            if (picked != null) {
                                                val (px, py) = picked
                                                val c = bitmap.getPixel(px, py)
                                                preview = Triple(c, p.x, p.y)
                                            }
                                            pressed[0].consume()
                                        }
                                        else -> {
                                            // 손을 뗌 — 누르고 있던 색이 있었으면 지금 대상 칸에 확정 저장.
                                            preview?.let { (c, _, _) -> context.onEditColor(targetIndex, (c.toLong() and 0xFFFFFFFF) or 0xFF000000L) }
                                            preview = null
                                            prevDist = 0f
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(), contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX; translationY = panY },
                    )
                }
                preview?.let { (c, x, y) -> EyedropFloatingPreview(c, x, y) }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                context.library.colors.forEachIndexed { i, col ->
                    val selected = i == targetIndex
                    Box(
                        Modifier.size(FavoriteSwatchSize).clip(CircleShape).background(Color(col))
                            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color(0x55FFFFFF), CircleShape)
                            .pointerInput(i) {
                                awaitPointerEventScope { while (true) { awaitPointerEvent(); targetIndex = i } }
                            },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDone) { Text("완료") }
            }
        }
    }
}
```

`import androidx.compose.foundation.layout.weight`도 위 import 목록에 빠져 있으면 추가한다(Column의 `Modifier.weight(1f)` 사용).

**주의 — 스와치 줄의 대상 전환 제스처:** 위 스와치 원 각각의 `pointerInput(i) { awaitPointerEventScope { while (true) { awaitPointerEvent(); targetIndex = i } } }`는 "이 원 위에서 포인터 이벤트가 발생하면 무조건 대상 전환"이라는 대략적인 처리다 — 실제로는 `Modifier.clickable(onClick = { targetIndex = i })`을 쓰는 게 이 프로젝트의 다른 스와치들(`ColorLibraryDetailPopup`의 칸 탭 등)과 일관되고 더 간단하다. 구현할 때 `clickable`로 바꿔라(위 pointerInput 스니펫은 자리만 잡아둔 것 — 그대로 쓰지 말 것).

- [ ] **Step 3: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남(이 시점엔 `ImageEyedropperOverlay`/`LibrarySwatchContext`를 아무도 안 쓰지만, 정의 자체는 컴파일돼야 한다).

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/brush/ImageEyedropper.kt
git commit -m "feat(brush): add full-screen image eyedropper overlay"
```

---

### Task 3: `ColorPickerCard`에 "이미지에서" 진입점 연결

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/brush/BrushControls.kt`

**Interfaces:**
- Consumes: `LibrarySwatchContext`/`ImageEyedropperOverlay`(Task 2, 같은 패키지), `decodeCoverBitmap(context, uri, maxDim)`(기존, `com.g1.sketchbook.sketchbook` 패키지 — import 필요).
- Produces: `ColorPickerCard`의 새 선택적 파라미터 `librarySwatch: LibrarySwatchContext? = null`.

이 파일은 Compose UI라 유닛 테스트 대상이 아니다 — 컴파일 확인 + 수동 확인(라이브러리 칸 편집 → 이미지에서 → 갤러리 선택 → 색 뽑기 → 완료 → 라이브러리에 반영 확인).

- [ ] **Step 1: import 추가**

`BrushControls.kt` 상단 import 블록에 추가(적절한 알파벳 위치에):

```kotlin
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.ui.platform.LocalContext
import com.g1.sketchbook.sketchbook.decodeCoverBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```

이미 있는 import(`androidx.compose.runtime.rememberCoroutineScope` 등)와 겹치면 중복 추가하지 말 것 — 파일에 이미 있는지 먼저 확인.

- [ ] **Step 2: `ColorPickerCard` 시그니처에 파라미터 추가**

`internal fun ColorPickerCard(` 시그니처를 찾아서:

```kotlin
internal fun ColorPickerCard(
    color: Long, onColor: (Long) -> Unit,
    onEyedrop: (() -> Unit)? = null,
)
```

이걸로 교체:

```kotlin
internal fun ColorPickerCard(
    color: Long, onColor: (Long) -> Unit,
    onEyedrop: (() -> Unit)? = null,
    librarySwatch: LibrarySwatchContext? = null,
)
```

- [ ] **Step 3: 이미지 선택/디코딩 상태와 launcher 추가**

`ColorPickerCard` 함수 본문 맨 앞(`val init = remember { ... }` 줄 바로 위)에 추가:

```kotlin
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val decoded = withContext(Dispatchers.IO) { runCatching { decodeCoverBitmap(context, uri, 1200) }.getOrNull() }
            pickedBitmap = decoded
        }
    }
```

`android.graphics.Bitmap` import가 이 파일에 이미 있는지 확인하고(없으면 추가), `androidx.compose.runtime.rememberCoroutineScope`도 이미 있는지 확인 — 이미 이 파일 다른 곳에서 코루틴 스코프를 쓰고 있다면 그 import를 그대로 재사용.

- [ ] **Step 4: "이미지에서" 버튼과 오버레이 연결**

`ColorPickerCard` 함수 끝부분, 기존 스포이드 버튼 블록:

```kotlin
            if (onEyedrop != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(current)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    IconBtn(Icons.Filled.Colorize, "스포이드", onClick = onEyedrop)
                }
            }
```

이걸로 교체:

```kotlin
            if (onEyedrop != null || librarySwatch != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(current)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    if (onEyedrop != null) IconBtn(Icons.Filled.Colorize, "스포이드", onClick = onEyedrop)
                    if (librarySwatch != null) IconBtn(Icons.Filled.AddPhotoAlternate, "이미지에서") {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            }
            pickedBitmap?.let { bmp ->
                ImageEyedropperOverlay(bmp, librarySwatch!!, onDone = { pickedBitmap = null })
            }
```

(`librarySwatch!!`는 안전하다 — `pickedBitmap`은 "이미지에서" 버튼을 눌러야만 채워지고, 그 버튼은 `librarySwatch != null`일 때만 존재한다.)

- [ ] **Step 5: 라이브러리 칸 편집 호출부에 `librarySwatch` 채워 넘기기**

"팔레트" 버튼 블록 안, 다음 호출을 찾는다:

```kotlin
                    if (editingColorAt >= 0 && editingLibrary != null) {
                        Popup(popupAnchor, { editingColorAt = -1 }, PopupProperties(focusable = true)) {
                            ColorPickerCard(editingLibrary.colors[editingColorAt],
                                onColor = { newColor -> onEditLibraryColor(editingLibrary.id, editingColorAt, newColor) },
                                onEyedrop = { editingColorAt = -1; onToggleEyedrop() })
                        }
                    }
```

이걸로 교체:

```kotlin
                    if (editingColorAt >= 0 && editingLibrary != null) {
                        Popup(popupAnchor, { editingColorAt = -1 }, PopupProperties(focusable = true)) {
                            ColorPickerCard(editingLibrary.colors[editingColorAt],
                                onColor = { newColor -> onEditLibraryColor(editingLibrary.id, editingColorAt, newColor) },
                                onEyedrop = { editingColorAt = -1; onToggleEyedrop() },
                                librarySwatch = LibrarySwatchContext(editingLibrary, editingColorAt) { i, c ->
                                    onEditLibraryColor(editingLibrary.id, i, c)
                                })
                        }
                    }
```

다른 두 `ColorPickerCard` 호출부(즉시선택 즐겨찾기 편집, 팔레트 그리드 편집)는 그대로 둔다 — `librarySwatch`는 기본값 `null`이라 안 건드려도 컴파일된다.

- [ ] **Step 6: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남.

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/brush/BrushControls.kt
git commit -m "feat(brush): wire image eyedropper into the library swatch color picker"
```

---

### Task 4: 전체 빌드·테스트 검증

**Files:** (없음 — 검증 전용 태스크)

- [ ] **Step 1: 전체 유닛 테스트 실행**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --no-daemon`
Expected: PASS — Task 1의 `ImageEyedropperTest`(6개) 포함 전부 통과.

- [ ] **Step 2: 전체 디버그 빌드**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 문제 발견 시 수정**

실패가 나오면 원인 파일을 고쳐 `fix(...): ...` 스타일로 별도 커밋. 실패 없으면 이 태스크는 커밋할 변경사항 없음.

---

## Self-Review

**스펙 커버리지 확인:**
- 갤러리에서만 가져오기(카메라 없음) → Task 3(`PickVisualMedia`만 사용).
- 라이브러리 칸 편집 중인 `ColorPickerCard`에서만 진입, 다른 용도에선 버튼 안 보임 → Task 3(`librarySwatch` 파라미터 게이팅).
- 핀치줌(1~5배)+드래그 이동 → Task 2(`zoom`/`panX`/`panY` 상태, 2-포인터 분기).
- 스와치 줄로 대상 전환, 이미지/확대 상태 유지 → Task 2(`targetIndex`가 `Dialog` 안에서만 바뀌고 이미지는 안 바뀜).
- 손가락 누르는 동안 루페 미리보기, 떼면 확정 저장 → Task 2(`preview` 상태 + `EyedropFloatingPreview` 재사용, 0-포인터 분기에서 커밋).
- 완료 시 오버레이만 닫힘, 별도 저장 없음 → Task 3(`onDone = { pickedBitmap = null }`, 색은 이미 매번 저장됨).
- 기존 `decodeCoverBitmap`/`onEditLibraryColor` 재사용 → Task 3.
- 다루지 않는 것(카메라, 이미지 보관, 다른 대상 적용, 전용 undo) → 어떤 태스크도 구현 안 함.

**플레이스홀더 스캔:** Task 2 Step 2의 스와치 pointerInput 스니펫에 "그대로 쓰지 말 것 — clickable로 바꿔라"라는 명시적 경고를 남겨뒀다 — 이는 미완성 코드가 아니라 구현자에게 주는 명확한 대체 지시사항이다(정확한 clickable 코드까지 박아두면 이 파일 다른 곳의 실제 `clickable` import/시그니처와 안 맞을 수 있어 자리만 잡아둠). 그 외 플레이스홀더 없음.

**타입 일관성 확인:** `LibrarySwatchContext(library: ColorLibrary, colorIndex: Int, onEditColor: (Int, Long) -> Unit)` — Task 2에서 정의, Task 3에서 정확히 같은 필드 순서로 생성(`LibrarySwatchContext(editingLibrary, editingColorAt) { i, c -> ... }`). `imageEyedropperPixel`의 9개 파라미터 순서·타입이 Task 1(정의+테스트)과 Task 2(호출부) 사이에 동일하게 유지됨.
