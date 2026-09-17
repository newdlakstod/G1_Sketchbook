# 판화 질감 브러시(리소 거친/젖은) 구현 계획

> **에이전트 작업자용:** 필수 서브 스킬 — superpowers:subagent-driven-development(추천) 또는
> superpowers:executing-plans로 이 계획을 태스크 단위로 실행할 것. 단계는 체크박스(`- [ ]`)로
> 진행 상황을 추적한다.

**목표:** 캔버스 절대좌표에 고정된 질감 타일을 `BitmapShader`로 스탬프하는 방식의 새 브러시
2종(Litho Rough=거친 질감, Litho Wet=젖은 질감)을 추가한다.

**아키텍처:** Android 의존 없는 순수 함수로 256×256 그레이스케일 노이즈(+선택적 블러)를
생성하고, `BrushView`가 이를 `ALPHA_8` 비트맵으로 한 번만 만들어 캐싱한 뒤, 현재 브러시
색으로 입힌 컬러 버전을 `BitmapShader(REPEAT, REPEAT)`로 감싸 원/선을 그리는 `Paint`에
건다. 셰이더는 캔버스 절대 좌표 기준으로 반복되므로 같은 자리는 항상 같은 무늬가 나온다.

**기술 스택:** Kotlin, Android `Canvas`/`Bitmap`/`BitmapShader`, Jetpack Compose(UI만).

## 전역 제약(스펙에서 그대로 가져옴 — 임의로 바꾸지 말 것)

- 질감 타일 크기: 256×256px, `Bitmap.Config.ALPHA_8`.
- 노이즈 생성 시드는 `BrushView` 인스턴스 생성 시 각 텍스처마다 독립적으로
  `Random.nextLong()`으로 한 번 뽑아 그 세션 동안 고정한다(기기 간·재실행 간 동일함은
  요구되지 않음).
- Rough = 블러 반경 0(원본 노이즈 그대로), Wet = 블러 반경 8(분리형 박스 블러 후 대비를
  0~255로 재정규화).
- 두 브러시 다 `content`에 직접 그린다(PENCIL/CRAYON과 동일 — `strokeLayer`/`composite()`
  안 씀).
- 강도 조절은 기존 불투명도 슬라이더(`inkAlpha()`)를 그대로 재사용 — 별도 슬라이더 추가 안
  함.
- `scaleFor()`: 둘 다 `2f`(크레파스와 동일).
- 스탬프 간격(`seg()`의 `spacing`)은 기존 `else -> r * 0.20f` 기본값을 그대로 쓴다 — 새
  분기 추가 안 함.
- 붓 크기 범위: 크레파스(`crayonMinWidth`/`crayonMaxWidth`)와 동일한 값.
- 라벨: "리소 거친" / "리소 젖은"(사용자가 나중에 바꿀 수 있음, 지금은 이 값으로 확정).
- 타일 생성 실패(극단적 저메모리) 시 셰이더 없는 단색 채우기로 대체.
- 범위 밖(이번 계획에 넣지 않음): 나머지 8종 브러시, 실제 텍스처 이미지 에셋, 강도 전용
  슬라이더, 최종 브러시 아이콘 그림, 세션 간/기기 간 질감 타일 동일성 보장.

---

## Task 1: 순수 노이즈 텍스처 생성 함수

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/brush/LithoTexture.kt`
- Test: `app/src/test/java/com/g1/sketchbook/brush/LithoTextureTest.kt`

**Interfaces:**
- Produces: `internal fun generateGrainTexture(size: Int, seed: Long, blurRadius: Int): ByteArray`
  — `size × size` 그레이스케일 밝기 배열(각 바이트는 부호 없는 0~255 값, 읽을 때는
  `byte.toInt() and 0xFF`). Task 2가 이 함수를 호출한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/g1/sketchbook/brush/LithoTextureTest.kt`:

```kotlin
package com.g1.sketchbook.brush

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LithoTextureTest {
    @Test
    fun sameSeedProducesSameTexture() {
        val a = generateGrainTexture(size = 32, seed = 42L, blurRadius = 0)
        val b = generateGrainTexture(size = 32, seed = 42L, blurRadius = 0)
        assertEquals(a.toList(), b.toList())
    }

    @Test
    fun zeroBlurRadiusKeepsRawNoise() {
        val size = 16
        val rnd = kotlin.random.Random(99L)
        val expected = ByteArray(size * size) { rnd.nextInt(0, 256).toByte() }
        val actual = generateGrainTexture(size = size, seed = 99L, blurRadius = 0)
        assertEquals(expected.toList(), actual.toList())
    }

    @Test
    fun blurReducesNeighborVariance() {
        val size = 64
        val raw = generateGrainTexture(size = size, seed = 5L, blurRadius = 0)
        val blurred = generateGrainTexture(size = size, seed = 5L, blurRadius = 8)

        fun neighborVariance(bytes: ByteArray): Double {
            var sumSq = 0.0
            var n = 0
            for (y in 0 until size) {
                for (x in 0 until size - 1) {
                    val a = bytes[y * size + x].toInt() and 0xFF
                    val b = bytes[y * size + x + 1].toInt() and 0xFF
                    sumSq += (a - b).toDouble() * (a - b)
                    n++
                }
            }
            return sumSq / n
        }

        assertTrue(neighborVariance(blurred) < neighborVariance(raw))
    }

    @Test
    fun stretchContrastExpandsRangeToFull() {
        val bytes = generateGrainTexture(size = 64, seed = 11L, blurRadius = 8)
        val values = bytes.map { it.toInt() and 0xFF }
        // 블러로 좁아진 대비를 재정규화하므로, 최댓값은 255 근처까지, 최솟값은 0 근처까지
        // 다시 늘어나야 한다(안 그러면 뭉개져서 거의 안 보이게 됨).
        assertTrue(values.max() > 200)
        assertTrue(values.min() < 55)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

실행: `./gradlew :app:testDebugUnitTest --tests "com.g1.sketchbook.brush.LithoTextureTest"`
기대 결과: `generateGrainTexture`가 없어서 컴파일 실패(Unresolved reference).

- [ ] **Step 3: 구현**

`app/src/main/java/com/g1/sketchbook/brush/LithoTexture.kt`(새 파일):

```kotlin
package com.g1.sketchbook.brush

import kotlin.random.Random

/** [size]×[size] 그레이스케일 노이즈 질감을 만든다 — 각 바이트는 0~255 밝기(부호 없는 값으로
 *  다루려면 `byte.toInt() and 0xFF`). [seed]가 같으면 항상 같은 결과(결정론적) — [BrushView]가
 *  세션마다 한 번 뽑은 시드를 넘겨서, 캔버스 위 같은 자리를 두 번 칠하면 항상 같은 무늬가
 *  겹쳐 보이게 한다("리소그래피 판" 느낌, 2026-09-17). [blurRadius]가 0이면 픽셀별 무작위
 *  노이즈 그대로(거친 질감), 0보다 크면 분리형 박스 블러(가로 패스 → 세로 패스)를 적용한 뒤
 *  블러로 좁아진 대비를 최소~최대값 기준 0~255 범위로 다시 늘려 편다(젖은 질감 — 안 그러면
 *  뭉개져서 거의 안 보이게 됨). */
internal fun generateGrainTexture(size: Int, seed: Long, blurRadius: Int): ByteArray {
    val rnd = Random(seed)
    val raw = ByteArray(size * size) { rnd.nextInt(0, 256).toByte() }
    if (blurRadius <= 0) return raw
    return stretchContrast(boxBlur(raw, size, size, blurRadius))
}

/** 분리형(가로 다음 세로) 박스 블러 — 캔버스 밖으로 나가는 이웃은 평균에서 제외한다(가장자리를
 *  어둡게/밝게 왜곡하지 않기 위함). */
private fun boxBlur(src: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
    val horizontal = ByteArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var sum = 0
            var count = 0
            for (dx in -radius..radius) {
                val sx = x + dx
                if (sx in 0 until w) {
                    sum += src[y * w + sx].toInt() and 0xFF
                    count++
                }
            }
            horizontal[y * w + x] = (sum / count).toByte()
        }
    }
    val out = ByteArray(w * h)
    for (x in 0 until w) {
        for (y in 0 until h) {
            var sum = 0
            var count = 0
            for (dy in -radius..radius) {
                val sy = y + dy
                if (sy in 0 until h) {
                    sum += horizontal[sy * w + x].toInt() and 0xFF
                    count++
                }
            }
            out[y * w + x] = (sum / count).toByte()
        }
    }
    return out
}

/** 블러로 좁아진 [min, max] 범위를 [0, 255]로 선형 재매핑한다. */
private fun stretchContrast(src: ByteArray): ByteArray {
    var min = 255
    var max = 0
    for (b in src) {
        val v = b.toInt() and 0xFF
        if (v < min) min = v
        if (v > max) max = v
    }
    val range = (max - min).coerceAtLeast(1)
    return ByteArray(src.size) { i ->
        val v = src[i].toInt() and 0xFF
        (((v - min) * 255) / range).coerceIn(0, 255).toByte()
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

실행: `./gradlew :app:testDebugUnitTest --tests "com.g1.sketchbook.brush.LithoTextureTest"`
기대 결과: 4개 테스트 모두 `PASS`.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/brush/LithoTexture.kt app/src/test/java/com/g1/sketchbook/brush/LithoTextureTest.kt
git commit -m "feat(brush): add pure grain-texture generator for litho brushes"
```

---

## Task 2: BrushView — Litho Rough/Wet 브러시 타입과 그리기 파이프라인

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/brush/BrushView.kt`

**Interfaces:**
- Consumes: `generateGrainTexture(size: Int, seed: Long, blurRadius: Int): ByteArray`(Task 1).
- Produces: `enum class BrushType { PEN, PENCIL, CRAYON, WATER, LITHO_ROUGH, LITHO_WET }` — Task
  3(UI)과 Task 4(기본 굵기)가 이 두 새 값을 참조한다.

이 태스크는 Compose UI가 아니라 `View`/`Canvas` 렌더링 코드라 이 프로젝트 관례상 자동화된
단위 테스트 대신 컴파일 확인 + 수동 확인으로 검증한다(Task 1의 순수 함수만 TDD 대상).

- [ ] **Step 1: `BrushType`에 새 값 추가**

`app/src/main/java/com/g1/sketchbook/brush/BrushView.kt` 33번째 줄:

```kotlin
// 변경 전
enum class BrushType { PEN, PENCIL, CRAYON, WATER }

// 변경 후
enum class BrushType { PEN, PENCIL, CRAYON, WATER, LITHO_ROUGH, LITHO_WET }
```

- [ ] **Step 2: import 추가**

파일 상단 import 블록에 다음 두 줄을 추가(알파벳 순서 유지):

```kotlin
import android.graphics.BitmapShader
import android.graphics.Shader
```

`import android.graphics.Bitmap` 다음, `import android.graphics.BlendMode` 앞에
`BitmapShader`를 넣고, `import android.graphics.RectF` 다음에 `Shader`를 넣는다(현재 파일은
`android.graphics.*` import를 알파벳 순으로 정렬해 둠). 그리고 파일 상단에
`import java.nio.ByteBuffer`도 추가한다(다른 `android.*`/`kotlin.*` import들 다음, 마지막
import 줄로).

- [ ] **Step 3: 질감 타일 필드 추가**

`private val rnd = Random(7)` 선언부(기존 256번째 줄 근방) 바로 다음에 추가:

```kotlin
    // 리소 브러시(거친/젖은) 전용 질감 타일 — 인스턴스당 한 번만 만들어 재사용한다. 시드는
    // 세션마다 새로 뽑되(기기 간·재실행 간 동일함은 요구 안 됨), 한 번 뽑으면 이 BrushView가
    // 살아있는 동안 고정이라 캔버스 위 같은 자리를 두 번 칠하면 항상 같은 무늬가 겹쳐 보인다
    // ("리소그래피 판" 느낌, 2026-09-17).
    private val lithoRoughSeed = Random.nextLong()
    private val lithoWetSeed = Random.nextLong()
    private val LithoTileSize = 256
    private val LithoWetBlurRadius = 8

    /** 극단적 저메모리로 타일 생성(`Bitmap.createBitmap`)이 실패하면 null — 이 경우 스탬프
     *  함수가 셰이더 없는 단색 채우기로 대체한다. */
    private val lithoRoughTile: Bitmap? by lazy { runCatching { grainBitmap(lithoRoughSeed, 0) }.getOrNull() }
    private val lithoWetTile: Bitmap? by lazy { runCatching { grainBitmap(lithoWetSeed, LithoWetBlurRadius) }.getOrNull() }

    private fun grainBitmap(seed: Long, blurRadius: Int): Bitmap {
        val bytes = generateGrainTexture(LithoTileSize, seed, blurRadius)
        val bmp = Bitmap.createBitmap(LithoTileSize, LithoTileSize, Bitmap.Config.ALPHA_8)
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        return bmp
    }

    // 현재 색으로 입힌 셰이더 Paint 캐시 — color가 바뀔 때만 다시 만든다(256x256 비트맵이라
    // 매번 새로 만들어도 가볍지만, 스탬프마다 다시 만들 이유는 없음).
    private var lithoRoughPaint: Paint? = null
    private var lithoRoughPaintColor = 0
    private var lithoWetPaint: Paint? = null
    private var lithoWetPaintColor = 0

    /** [tile](ALPHA_8, 흑백 질감)을 지금 [color]로 입힌 뒤 반복 타일링 셰이더를 건 Paint를
     *  새로 만든다 — ALPHA_8 비트맵을 그리면 Paint의 색이 RGB로, 비트맵의 알파가 마스크로
     *  쓰이는 표준 동작을 이용한다. */
    private fun shaderPaintFor(tile: Bitmap): Paint {
        val colored = Bitmap.createBitmap(tile.width, tile.height, Bitmap.Config.ARGB_8888)
        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@BrushView.color or (0xFF shl 24) }
        Canvas(colored).drawBitmap(tile, 0f, 0f, tintPaint)
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = BitmapShader(colored, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }
```

- [ ] **Step 4: 스탬프 함수 추가**

`stampCrayon()` 함수가 끝나는 지점(현재 `stampWater()` 함수 시작 직전) 바로 앞에 추가:

```kotlin
    private fun stampLithoRough(x: Float, y: Float, r: Float) {
        val c = content ?: return
        val tile = lithoRoughTile
        if (tile == null) {
            fill.style = Paint.Style.FILL; fill.strokeWidth = 0f
            fill.color = withAlpha(color, inkAlpha())
            c.drawCircle(x, y, r, fill)
            markDirty(x, y, r)
            return
        }
        if (lithoRoughPaint == null || lithoRoughPaintColor != color) {
            lithoRoughPaint = shaderPaintFor(tile)
            lithoRoughPaintColor = color
        }
        val paint = lithoRoughPaint!!
        paint.alpha = (inkAlpha() * 255).toInt()
        c.drawCircle(x, y, r, paint)
        markDirty(x, y, r)
    }

    private fun stampLithoWet(x: Float, y: Float, r: Float) {
        val c = content ?: return
        val tile = lithoWetTile
        if (tile == null) {
            fill.style = Paint.Style.FILL; fill.strokeWidth = 0f
            fill.color = withAlpha(color, inkAlpha())
            c.drawCircle(x, y, r, fill)
            markDirty(x, y, r)
            return
        }
        if (lithoWetPaint == null || lithoWetPaintColor != color) {
            lithoWetPaint = shaderPaintFor(tile)
            lithoWetPaintColor = color
        }
        val paint = lithoWetPaint!!
        paint.alpha = (inkAlpha() * 255).toInt()
        c.drawCircle(x, y, r, paint)
        markDirty(x, y, r)
    }
```

(`fill`은 다른 스탬프 함수들과 공유하는 기존 `private val fill = Paint(...)` 필드, `withAlpha`/
`inkAlpha`/`markDirty`는 기존 private 함수 — 새로 만들 필요 없음.)

- [ ] **Step 5: 기존 확장 지점에 새 브랜치 추가**

`scaleFor()`(exhaustive `when`, 새 값을 안 넣으면 컴파일 에러가 나서 놓칠 수 없음):

```kotlin
// 변경 전
private fun scaleFor(): Float = when (brush) { BrushType.PEN -> 1f; BrushType.PENCIL -> 1f; BrushType.CRAYON -> 2f; BrushType.WATER -> 6f }

// 변경 후
private fun scaleFor(): Float = when (brush) {
    BrushType.PEN -> 1f; BrushType.PENCIL -> 1f; BrushType.CRAYON -> 2f; BrushType.WATER -> 6f
    BrushType.LITHO_ROUGH -> 2f; BrushType.LITHO_WET -> 2f
}
```

`stampDispatch()`:

```kotlin
// 변경 전
private fun stampDispatch(x: Float, y: Float, r: Float) {
    when (brush) { BrushType.PENCIL -> stampPencil(x, y, r); BrushType.CRAYON -> stampCrayon(x, y, r); BrushType.WATER -> stampWater(x, y, r); else -> {} }
}

// 변경 후
private fun stampDispatch(x: Float, y: Float, r: Float) {
    when (brush) {
        BrushType.PENCIL -> stampPencil(x, y, r)
        BrushType.CRAYON -> stampCrayon(x, y, r)
        BrushType.WATER -> stampWater(x, y, r)
        BrushType.LITHO_ROUGH -> stampLithoRough(x, y, r)
        BrushType.LITHO_WET -> stampLithoWet(x, y, r)
        else -> {}
    }
}
```

`strokeStart()`/`strokeMove()`의 `else -> stampDispatch(...)` 분기는 수정하지 않는다 — 이미
PENCIL/CRAYON처럼 두 새 타입도 그 분기로 자연히 들어가 `stampDispatch()`를 거친다.
`seg()`의 `spacing` 계산(`when (brush) { WATER -> ...; CRAYON -> ...; else -> r * 0.20f }`)도
수정하지 않는다 — 전역 제약대로 기존 `else` 기본값을 그대로 물려받는다.

- [ ] **Step 6: 컴파일 확인**

실행: `./gradlew compileDebugKotlin`
기대 결과: `BUILD SUCCESSFUL`(에러 없이 컴파일).

이 시점에서 `BrushType.LITHO_ROUGH`/`LITHO_WET`을 실제로 선택할 UI가 아직 없으므로(Task
3에서 추가), 수동 확인은 Task 3 완료 후에 함께 진행한다.

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/brush/BrushView.kt
git commit -m "feat(brush): add Litho Rough/Wet brush types with shader-tiled texture"
```

---

## Task 3: BrushControls/Dimens — 붓 선택 UI 통합 및 아이콘

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/brush/BrushControls.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/theme/Dimens.kt`
- Create: `app/src/main/res/drawable/brush_litho_rough.xml`
- Create: `app/src/main/res/drawable/brush_litho_wet.xml`

**Interfaces:**
- Consumes: `BrushType.LITHO_ROUGH`, `BrushType.LITHO_WET`(Task 2).
- Produces: `Dimens.Brush.lithoRoughWidth`/`lithoRoughMinWidth`/`lithoRoughMaxWidth`,
  `Dimens.Brush.lithoWetWidth`/`lithoWetMinWidth`/`lithoWetMaxWidth` — Task 4가 `lithoRoughWidth`/
  `lithoWetWidth`를 참조한다.

- [ ] **Step 1: `Dimens.kt`에 크기 상수 추가**

`app/src/main/java/com/g1/sketchbook/ui/theme/Dimens.kt`의 `object Brush` 블록,
`crayonMaxWidth` 선언 다음 줄에 추가(값은 전역 제약대로 크레파스와 동일):

```kotlin
        val crayonWidth = 8f
        val crayonMinWidth = 1.5f
        val crayonMaxWidth = 60f
        val lithoRoughWidth = 8f
        val lithoRoughMinWidth = 1.5f
        val lithoRoughMaxWidth = 60f
        val lithoWetWidth = 8f
        val lithoWetMinWidth = 1.5f
        val lithoWetMaxWidth = 60f
```

- [ ] **Step 2: 벡터 아이콘 2개 생성**

`app/src/main/res/drawable/brush_litho_rough.xml`(새 파일 — 흩뿌린 점, 최소 구분용 임시
아이콘):

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#FF000000" android:pathData="M6,7m-1.6,0a1.6,1.6 0,1 1,3.2,0a1.6,1.6 0,1 1,-3.2,0"/>
    <path android:fillColor="#FF000000" android:pathData="M12,5m-1.2,0a1.2,1.2 0,1 1,2.4,0a1.2,1.2 0,1 1,-2.4,0"/>
    <path android:fillColor="#FF000000" android:pathData="M18,8m-1.4,0a1.4,1.4 0,1 1,2.8,0a1.4,1.4 0,1 1,-2.8,0"/>
    <path android:fillColor="#FF000000" android:pathData="M5,14m-1.3,0a1.3,1.3 0,1 1,2.6,0a1.3,1.3 0,1 1,-2.6,0"/>
    <path android:fillColor="#FF000000" android:pathData="M12,13m-1.8,0a1.8,1.8 0,1 1,3.6,0a1.8,1.8 0,1 1,-3.6,0"/>
    <path android:fillColor="#FF000000" android:pathData="M19,15m-1,0a1,1 0,1 1,2,0a1,1 0,1 1,-2,0"/>
    <path android:fillColor="#FF000000" android:pathData="M8,19m-1.4,0a1.4,1.4 0,1 1,2.8,0a1.4,1.4 0,1 1,-2.8,0"/>
    <path android:fillColor="#FF000000" android:pathData="M15,20m-1.1,0a1.1,1.1 0,1 1,2.2,0a1.1,1.1 0,1 1,-2.2,0"/>
</vector>
```

`app/src/main/res/drawable/brush_litho_wet.xml`(새 파일 — 부드러운 얼룩 하나, 최소 구분용
임시 아이콘):

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#FF000000" android:pathData="M12,3.5c2.8,0 5,2 6.2,4.6c1.1,2.4 1,5 -0.3,7.2c-1.3,2.3 -3.8,4.2 -6.4,4.2c-2.7,0 -5.3,-1.6 -6.7,-4c-1.3,-2.3 -1.3,-5.1 0,-7.4c1.3,-2.4 3.7,-4.6 7.2,-4.6z"/>
</vector>
```

(둘 다 `ColorFilter.tint(...)`로 색을 입히는 기존 아이콘들과 같은 방식으로 쓰이므로 내부
색상은 검정 단색이면 충분 — 나중에 실제 그림으로 교체 가능.)

- [ ] **Step 3: `currentToolIcon()`에 새 브랜치 추가**

`app/src/main/java/com/g1/sketchbook/brush/BrushControls.kt`:

```kotlin
// 변경 전
private fun currentToolIcon(brush: BrushType, erasing: Boolean): Int = when {
    erasing -> R.drawable.brush_eraser
    brush == BrushType.PEN -> R.drawable.brush_pen
    brush == BrushType.PENCIL -> R.drawable.brush_pencil
    brush == BrushType.CRAYON -> R.drawable.brush_crayon
    else -> R.drawable.brush_water
}

// 변경 후
private fun currentToolIcon(brush: BrushType, erasing: Boolean): Int = when {
    erasing -> R.drawable.brush_eraser
    brush == BrushType.PEN -> R.drawable.brush_pen
    brush == BrushType.PENCIL -> R.drawable.brush_pencil
    brush == BrushType.CRAYON -> R.drawable.brush_crayon
    brush == BrushType.LITHO_ROUGH -> R.drawable.brush_litho_rough
    brush == BrushType.LITHO_WET -> R.drawable.brush_litho_wet
    else -> R.drawable.brush_water
}
```

- [ ] **Step 4: `brushSizeRange()`에 새 브랜치 추가(exhaustive — 안 넣으면 컴파일 에러)**

```kotlin
// 변경 전
internal fun brushSizeRange(brush: BrushType): ClosedFloatingPointRange<Float> = when (brush) {
    BrushType.PEN -> Dimens.Brush.penMinWidth..Dimens.Brush.penMaxWidth
    BrushType.PENCIL -> Dimens.Brush.pencilMinWidth..Dimens.Brush.pencilMaxWidth
    BrushType.CRAYON -> Dimens.Brush.crayonMinWidth..Dimens.Brush.crayonMaxWidth
    BrushType.WATER -> Dimens.Brush.waterMinWidth..Dimens.Brush.waterMaxWidth
}

// 변경 후
internal fun brushSizeRange(brush: BrushType): ClosedFloatingPointRange<Float> = when (brush) {
    BrushType.PEN -> Dimens.Brush.penMinWidth..Dimens.Brush.penMaxWidth
    BrushType.PENCIL -> Dimens.Brush.pencilMinWidth..Dimens.Brush.pencilMaxWidth
    BrushType.CRAYON -> Dimens.Brush.crayonMinWidth..Dimens.Brush.crayonMaxWidth
    BrushType.WATER -> Dimens.Brush.waterMinWidth..Dimens.Brush.waterMaxWidth
    BrushType.LITHO_ROUGH -> Dimens.Brush.lithoRoughMinWidth..Dimens.Brush.lithoRoughMaxWidth
    BrushType.LITHO_WET -> Dimens.Brush.lithoWetMinWidth..Dimens.Brush.lithoWetMaxWidth
}
```

- [ ] **Step 5: 툴바 펼침 상태의 붓 아이콘 Row를 스크롤 가능하게 바꾸고 새 브러시 2개 추가**

붓 종류가 앞으로 계속 늘어날 예정이라(이번 2종은 판화 질감 브러시 세트의 첫 증분), 붓
목록만 따로 가로 스크롤 가능한 고정 폭 컨테이너로 감싼다 — 지우개·접기 버튼은 스크롤과
무관하게 항상 보이도록 밖에 남긴다. `horizontalScroll`/`rememberScrollState`/`width`는 이미
파일 상단에 import돼 있다(추가 import 불필요).

```kotlin
// 변경 전
                if (brushCategoryExpanded) {
                    // Brush icons: tap to switch. 굵기/불투명도는 이제 브러시별 팝업이 아니라 항상
                    // 보이는 상단 바(ActiveToolSlidersBar)에서 조절하므로 여기선 그냥 단순 토글.
                    BrushBtn(!erasing && brush == BrushType.PEN, onClick = { onBrush(BrushType.PEN); openEraserPanel = false }) { t ->
                        Image(painterResource(R.drawable.brush_pen), "볼펜", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                    }
                    BrushBtn(!erasing && brush == BrushType.PENCIL, onClick = { onBrush(BrushType.PENCIL); openEraserPanel = false }) { t ->
                        Image(painterResource(R.drawable.brush_pencil), "연필", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                    }
                    BrushBtn(!erasing && brush == BrushType.CRAYON, onClick = { onBrush(BrushType.CRAYON); openEraserPanel = false }) { t ->
                        Image(painterResource(R.drawable.brush_crayon), "크레파스", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                    }
                    BrushBtn(!erasing && brush == BrushType.WATER, onClick = { onBrush(BrushType.WATER); openEraserPanel = false }) { t ->
                        Image(painterResource(R.drawable.brush_water), "수채화", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                    }
                    // 지우개만 자기 자신의 "경계 블러" 팝업을 가진다(굵기/불투명도는 다른 브러시와
                    // 동일하게 상단 바에서) — 이미 선택된 지우개를 다시 탭하면 블러 패널이 뜬다.
                    EraserBtnWithBlurPanel(erasing, eraserBlur, onEraserBlur, sizePopupAnchor,
                        panelOpen = openEraserPanel,
                        setPanelOpen = { o -> openEraserPanel = o },
                        onClick = { onToggleErase(); openEraserPanel = false },
                        onClear = { confirmClear = true }) { t ->
                        Image(painterResource(R.drawable.brush_eraser), "지우개", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                    }
                    IconBtn(Icons.Filled.ExpandLess, "붓 종류 접기", onClick = { brushCategoryExpanded = false })
                } else {

// 변경 후
                if (brushCategoryExpanded) {
                    // Brush icons: tap to switch. 굵기/불투명도는 이제 브러시별 팝업이 아니라 항상
                    // 보이는 상단 바(ActiveToolSlidersBar)에서 조절하므로 여기선 그냥 단순 토글.
                    // 붓 종류가 늘어날수록(판화 질감 브러시 추가 예정) 고정 Row로는 넘칠 수 있어
                    // 이 목록만 따로 가로 스크롤 가능한 고정 폭 컨테이너로 감쌌다(2026-09-17) —
                    // 지우개·접기 버튼은 스크롤과 무관하게 항상 보이도록 밖에 남긴다.
                    Row(
                        Modifier.width(200.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        BrushBtn(!erasing && brush == BrushType.PEN, onClick = { onBrush(BrushType.PEN); openEraserPanel = false }) { t ->
                            Image(painterResource(R.drawable.brush_pen), "볼펜", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                        }
                        BrushBtn(!erasing && brush == BrushType.PENCIL, onClick = { onBrush(BrushType.PENCIL); openEraserPanel = false }) { t ->
                            Image(painterResource(R.drawable.brush_pencil), "연필", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                        }
                        BrushBtn(!erasing && brush == BrushType.CRAYON, onClick = { onBrush(BrushType.CRAYON); openEraserPanel = false }) { t ->
                            Image(painterResource(R.drawable.brush_crayon), "크레파스", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                        }
                        BrushBtn(!erasing && brush == BrushType.WATER, onClick = { onBrush(BrushType.WATER); openEraserPanel = false }) { t ->
                            Image(painterResource(R.drawable.brush_water), "수채화", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                        }
                        BrushBtn(!erasing && brush == BrushType.LITHO_ROUGH, onClick = { onBrush(BrushType.LITHO_ROUGH); openEraserPanel = false }) { t ->
                            Image(painterResource(R.drawable.brush_litho_rough), "리소 거친", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                        }
                        BrushBtn(!erasing && brush == BrushType.LITHO_WET, onClick = { onBrush(BrushType.LITHO_WET); openEraserPanel = false }) { t ->
                            Image(painterResource(R.drawable.brush_litho_wet), "리소 젖은", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                        }
                    }
                    // 지우개만 자기 자신의 "경계 블러" 팝업을 가진다(굵기/불투명도는 다른 브러시와
                    // 동일하게 상단 바에서) — 이미 선택된 지우개를 다시 탭하면 블러 패널이 뜬다.
                    EraserBtnWithBlurPanel(erasing, eraserBlur, onEraserBlur, sizePopupAnchor,
                        panelOpen = openEraserPanel,
                        setPanelOpen = { o -> openEraserPanel = o },
                        onClick = { onToggleErase(); openEraserPanel = false },
                        onClear = { confirmClear = true }) { t ->
                        Image(painterResource(R.drawable.brush_eraser), "지우개", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                    }
                    IconBtn(Icons.Filled.ExpandLess, "붓 종류 접기", onClick = { brushCategoryExpanded = false })
                } else {
```

- [ ] **Step 6: `MiniBrushPopup`도 동일하게 스크롤 가능하게 바꾸고 새 브러시 2개 추가**

```kotlin
// 변경 전
@Composable
private fun MiniBrushPopup(current: BrushType, erasing: Boolean, onPick: (BrushType) -> Unit, onEraser: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                BrushType.PEN to R.drawable.brush_pen, BrushType.PENCIL to R.drawable.brush_pencil,
                BrushType.CRAYON to R.drawable.brush_crayon, BrushType.WATER to R.drawable.brush_water,
            ).forEach { (t, res) ->
                val tint = if (!erasing && t == current) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                Box(Modifier.size(48.dp).bounceClick { onPick(t) }, contentAlignment = Alignment.Center) {
                    Image(painterResource(res), null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(38.dp))
                }
            }
            val eraserTint = if (erasing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            Box(Modifier.size(48.dp).bounceClick { onEraser() }, contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.brush_eraser), null, colorFilter = ColorFilter.tint(eraserTint), modifier = Modifier.size(38.dp))
            }
        }
    }
}

// 변경 후
@Composable
private fun MiniBrushPopup(current: BrushType, erasing: Boolean, onPick: (BrushType) -> Unit, onEraser: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // 붓 종류가 늘어날수록(판화 질감 브러시 추가 예정) 고정 Row로는 넘칠 수 있어 이
            // 목록만 따로 가로 스크롤 가능한 고정 폭 컨테이너로 감쌌다(2026-09-17).
            Row(Modifier.width(200.dp).horizontalScroll(rememberScrollState())) {
                listOf(
                    BrushType.PEN to R.drawable.brush_pen, BrushType.PENCIL to R.drawable.brush_pencil,
                    BrushType.CRAYON to R.drawable.brush_crayon, BrushType.WATER to R.drawable.brush_water,
                    BrushType.LITHO_ROUGH to R.drawable.brush_litho_rough, BrushType.LITHO_WET to R.drawable.brush_litho_wet,
                ).forEach { (t, res) ->
                    val tint = if (!erasing && t == current) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    Box(Modifier.size(48.dp).bounceClick { onPick(t) }, contentAlignment = Alignment.Center) {
                        Image(painterResource(res), null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(38.dp))
                    }
                }
            }
            val eraserTint = if (erasing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            Box(Modifier.size(48.dp).bounceClick { onEraser() }, contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.brush_eraser), null, colorFilter = ColorFilter.tint(eraserTint), modifier = Modifier.size(38.dp))
            }
        }
    }
}
```

- [ ] **Step 7: 컴파일 확인**

실행: `./gradlew compileDebugKotlin`
기대 결과: `BUILD SUCCESSFUL`.

- [ ] **Step 8: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/brush/BrushControls.kt app/src/main/java/com/g1/sketchbook/ui/theme/Dimens.kt app/src/main/res/drawable/brush_litho_rough.xml app/src/main/res/drawable/brush_litho_wet.xml
git commit -m "feat(brush): wire Litho Rough/Wet into brush picker UI"
```

---

## Task 4: 나머지 `BrushType` 확장 지점(기본 굵기) 정리

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/data/SessionStore.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt`

**Interfaces:**
- Consumes: `BrushType.LITHO_ROUGH`, `BrushType.LITHO_WET`(Task 2),
  `Dimens.Brush.lithoRoughWidth`, `Dimens.Brush.lithoWetWidth`(Task 3).

`SketchbookScreens.kt`와 `SharedBookScreen.kt`는 `BrushType.entries.map { ... }`로 모든
브러시를 자동 순회해 기본 굵기 맵을 만들므로(각각 1090번째 줄, 150번째 줄 근방) 이 태스크와
무관하게 새 브러시를 자동으로 반영한다 — 수정 불필요. 반면 아래 두 곳은 `BrushType`을
하드코딩으로 나열하므로 직접 고쳐야 한다.

- [ ] **Step 1: `SessionStore.kt`의 `defaultBrushSize()`(exhaustive `when`, 안 고치면
  컴파일 에러)**

`app/src/main/java/com/g1/sketchbook/data/SessionStore.kt`:

```kotlin
// 변경 전
    private fun defaultBrushSize(type: BrushType): Float = when (type) {
        BrushType.PEN -> Dimens.Brush.penWidth
        BrushType.PENCIL -> Dimens.Brush.pencilWidth
        BrushType.CRAYON -> Dimens.Brush.crayonWidth
        BrushType.WATER -> Dimens.Brush.waterWidth
    }

// 변경 후
    private fun defaultBrushSize(type: BrushType): Float = when (type) {
        BrushType.PEN -> Dimens.Brush.penWidth
        BrushType.PENCIL -> Dimens.Brush.pencilWidth
        BrushType.CRAYON -> Dimens.Brush.crayonWidth
        BrushType.WATER -> Dimens.Brush.waterWidth
        BrushType.LITHO_ROUGH -> Dimens.Brush.lithoRoughWidth
        BrushType.LITHO_WET -> Dimens.Brush.lithoWetWidth
    }
```

- [ ] **Step 2: `DiaryScreens.kt`의 `sizeByBrush` 초기 맵에 두 브러시 추가**

이 맵은 exhaustive가 아니라(`sizeByBrush[brush] ?: 10f`처럼 null-safe fallback) 안 고쳐도
컴파일은 되지만, 안 고치면 다이어리 화면에서만 리소 브러시의 마지막 굵기가 기억되지 않고
매번 10f로 리셋되는 불일치가 생긴다 — 다른 화면(스케치북/공유노트)과 동작을 맞추기 위해
추가한다.

`app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt` 155번째 줄:

```kotlin
// 변경 전
    val sizeByBrush = remember { mutableStateMapOf(BrushType.PEN to Dimens.Brush.penWidth, BrushType.PENCIL to Dimens.Brush.pencilWidth, BrushType.CRAYON to Dimens.Brush.crayonWidth, BrushType.WATER to Dimens.Brush.waterWidth) }

// 변경 후
    val sizeByBrush = remember { mutableStateMapOf(BrushType.PEN to Dimens.Brush.penWidth, BrushType.PENCIL to Dimens.Brush.pencilWidth, BrushType.CRAYON to Dimens.Brush.crayonWidth, BrushType.WATER to Dimens.Brush.waterWidth, BrushType.LITHO_ROUGH to Dimens.Brush.lithoRoughWidth, BrushType.LITHO_WET to Dimens.Brush.lithoWetWidth) }
```

- [ ] **Step 3: 컴파일 확인**

실행: `./gradlew compileDebugKotlin`
기대 결과: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 전체 유닛 테스트 + 디버그 빌드 확인**

실행: `./gradlew testDebugUnitTest`
기대 결과: 전부 `PASS`(Task 1의 `LithoTextureTest` 포함).

실행: `./gradlew assembleDebug`
기대 결과: `BUILD SUCCESSFUL`.

수동 확인(에뮬레이터/기기, 사용자가 명시적으로 요청할 때만 진행): 붓 고르는 줄에서 "리소
거친"/"리소 젖은"을 선택해 그려보고 (1) 같은 자리를 두 번 겹쳐 칠했을 때 같은 무늬가
겹쳐 보이는지, (2) 색을 바꿔 그리면 즉시 새 색으로 반영되는지, (3) 불투명도를 낮추면
옅어지는지, (4) 되돌리기(undo)가 다른 브러시처럼 정상 동작하는지, (5) 붓 고르는 줄이
가로로 스크롤되는지 확인한다.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/data/SessionStore.kt app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt
git commit -m "fix(brush): cover Litho Rough/Wet in remaining default-size lookups"
```

---

## Self-Review(계획 작성자가 직접 확인)

1. **스펙 커버리지**: 스펙의 "질감 타일 생성"(Task 1+2), "그리기 파이프라인"(Task 2),
   "불투명도"(Task 2, `inkAlpha()` 재사용), "붓 크기 범위"(Task 3), "UI"(Task 3),
   "에러 처리"(Task 2의 `lithoRoughTile`/`lithoWetTile` null 대체), "테스트"(Task 1) 전부
   대응하는 태스크가 있다. 스펙에 없었지만 계획 작성 중 발견한 보완 사항(`SessionStore.kt`/
   `DiaryScreens.kt`의 나머지 `BrushType` 하드코딩 지점)은 Task 4로 별도 반영했다.
2. **플레이스홀더 스캔**: "TBD"/"추가 처리" 등 모호한 표현 없음 — 전 단계가 실제 코드/정확한
   값으로 작성됨.
3. **타입 일관성**: `generateGrainTexture`(Task 1) 시그니처가 Task 2의 호출부(`grainBitmap`)
   와 정확히 일치. `BrushType.LITHO_ROUGH`/`LITHO_WET`(Task 2)이 Task 3·4에서 동일한 이름으로
   쓰임. `Dimens.Brush.lithoRoughWidth`/`lithoWetWidth`(Task 3)가 Task 4에서 동일하게 참조됨.
