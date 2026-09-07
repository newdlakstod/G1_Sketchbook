# PageCurl 이식 가이드

`pagecurl/`은 Compose 앱에서 사용하는 Android Library다. 공개 선언은 세 개다.

- `PageCurlBitmapSource`: GL thread에서 페이지 Bitmap을 공급하는 interface
- `BitmapListPageSource`: 이미 준비한 Bitmap 목록용 구현
- `PageCurl`: controlled `pageIndex`를 받는 Composable

`onPageChanged`와 `onError`는 `PageCurl`의 callback parameter이며 별도 공개 선언이 아니다.

## 빌드 전제와 root plugin

이 모듈은 JDK 17, Android SDK 35, minSdk 24를 사용한다. consuming project의 `settings.gradle.kts`가 `google()`, `mavenCentral()`, `gradlePluginPortal()`을 찾을 수 있어야 한다. 모듈의 plugin 선언에는 version이 없으므로 consuming root `build.gradle.kts`에 다음 plugin과 호환 version을 선언한다. 아래 값은 이 모듈을 검증한 조합이다.

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

기존 project가 다른 Android Gradle Plugin/Kotlin 조합을 사용한다면 `com.android.library`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose` 세 plugin을 그 조합의 동일한 호환 version으로 제공해야 한다. `pagecurl/build.gradle.kts`의 Compose BOM과 AndroidX dependency는 module이 직접 선언하므로 새 dependency를 추가할 필요는 없다.

## Gradle 연결

개발 중 두 앱이 같은 물리 `pagecurl/` 폴더를 공유한다면 대상 앱의 `settings.gradle.kts`에 상대 경로를 연결한다.

```kotlin
include(":pagecurl")
project(":pagecurl").projectDir = file("../pagecurl")
```

별도 project로 전달할 때는 `pagecurl/` 폴더를 그 project root에 복사하고 기본 module 경로를 연결한다.

```kotlin
include(":pagecurl")
```

두 형태 모두 app module의 `build.gradle.kts`에 같은 dependency를 추가한다.

```kotlin
dependencies {
    implementation(project(":pagecurl"))
}
```

공유 경로는 재빌드할 때 같은 source 변경을 받는다. 독립 복사본은 자동 동기화되지 않으므로 새 version을 반영할 때 `pagecurl/` 폴더를 다시 교체한다.

## Compose 사용

```kotlin
var currentPage by rememberSaveable { mutableIntStateOf(0) }
val source = remember(pageBitmaps) { BitmapListPageSource(pageBitmaps) }

PageCurl(
    source = source,
    pageIndex = currentPage,
    modifier = Modifier.fillMaxSize(),
    onPageChanged = { currentPage = it },
    onError = { error -> Log.e("PageCurl", "Page curl failed", error) },
)
```

`pageCount`는 UI/GL 어느 thread에서도 읽을 수 있는 immutable, thread-safe metadata다. `getPageBitmap`만 GL thread에서 호출된다. `pageIndex`는 항상 `0 until source.pageCount` 범위여야 한다. `BitmapListPageSource`는 전달받은 목록 구조를 snapshot하지만 각 Bitmap object를 복사하지 않는다.

`pageAspectRatio`는 페이지의 `width / height` 값이며 source 수명 동안 바뀌지 않아야 한다. 지원 범위는 `0.25..4.0`이고, 생략하면 기본값 `3 / 4`를 사용한다. A4나 모바일·데스크톱처럼 비율이 다른 문서는 source에서 실제 비율을 명시한다.

```kotlin
override val pageAspectRatio: Float = pageWidth.toFloat() / pageHeight
```

## 원본 stroke를 요청 크기로 렌더링하기

작은 preview나 screenshot을 확대하지 말고, 저장된 원본 좌표의 stroke를 요청받은 Bitmap에 다시 그린다. 다음 예제의 model은 immutable snapshot이어야 하며 GL thread와 동시에 수정하지 않는다.

```kotlin
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.gdo.pagecurl.PageCurlBitmapSource

data class StrokePoint(val x: Float, val y: Float)
data class Stroke(
    val color: Int,
    val width: Float,
    val points: List<StrokePoint>,
)
data class StrokePage(
    val originalWidth: Float,
    val originalHeight: Float,
    val strokes: List<Stroke>,
)

class StrokePageSource(pages: List<StrokePage>) : PageCurlBitmapSource {
    private val pages = pages.map { page ->
        page.copy(strokes = page.strokes.map { it.copy(points = it.points.toList()) })
    }

    init {
        require(this.pages.isNotEmpty())
    }

    override val pageCount: Int = this.pages.size

    override fun getPageBitmap(
        pageIndex: Int,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Bitmap {
        require(pageIndex in pages.indices)
        require(requestedWidth > 0 && requestedHeight > 0)
        val page = pages[pageIndex]
        require(page.originalWidth > 0f && page.originalHeight > 0f)

        val bitmap = Bitmap.createBitmap(
            requestedWidth,
            requestedHeight,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(
            requestedWidth / page.originalWidth,
            requestedHeight / page.originalHeight,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        page.strokes.forEach { stroke ->
            val first = stroke.points.firstOrNull() ?: return@forEach
            paint.color = stroke.color
            paint.strokeWidth = stroke.width
            val path = Path().apply { moveTo(first.x, first.y) }
            stroke.points.drop(1).forEach { point -> path.lineTo(point.x, point.y) }
            canvas.drawPath(path, paint)
        }
        return bitmap
    }
}
```

source가 Bitmap을 새로 만드는 경우 앱이 소유권과 폐기를 관리한다. `PageCurl`은 반환된 Bitmap을 수정하거나 `recycle()`하지 않는다.

## lifecycle과 disposal

호출 앱은 `GLSurfaceView`를 직접 만들거나 pause/resume/release할 필요가 없다. `PageCurl` 내부가 현재 Compose `LifecycleOwner`의 resume/pause를 전달한다. AndroidView가 composition에서 제거되면 release hook이 surface를 영구 무효화하고, GL object 해제를 queue하며, document generation을 폐기한다. 이미 main thread에 post된 이전 page commit과 error callback도 release 뒤에는 전달되지 않는다.

새 source instance를 전달하면 진행 중 curl을 취소하고 generation을 먼저 바꾸므로 이전 source의 늦은 commit/error는 새 문서에 도달하지 않는다. 같은 source 내부 pixel이 바뀌었다면 기존 Bitmap을 수정하지 말고 immutable data로 만든 새 source instance를 전달한다. caller-owned Bitmap은 `PageCurl`이 사용하는 동안 수정하거나 recycle하지 말고, 가장 안전하게는 해당 화면/document lifecycle이 끝나 `PageCurl`이 composition에서 제거된 뒤 폐기한다.

## texture와 메모리

renderer는 현재 화면과 인접 전환에 필요한 page texture를 최대 5개까지 GPU에 유지하며, 불투명 1×1 blank texture는 별도로 둔다. RGBA texture의 base level은 대략 `width × height × 4` bytes이고 전체 mipmap chain은 base의 약 4/3이므로, 5개 상한의 GPU 사용량은 대략 `width × height × 26.7` bytes다. 예를 들어 1536×2048 page texture 다섯 장은 mipmap 포함 약 80 MiB다. source가 보유한 CPU Bitmap 메모리는 이 값과 별도다.

기기는 `GL_MAX_TEXTURE_SIZE`와 실제 page 표시 크기에 맞춰 요청 크기를 제한한다. source가 요청보다 작은 Bitmap을 반환하면 library는 확대하지 않으며 화질 저하는 source 책임이다. 큰 문서는 모든 page Bitmap을 한꺼번에 보관하지 말고 app 쪽 document/cache 정책으로 필요한 원본만 렌더링한다.

## 문제 위치와 오류 확인

```text
Bitmap content/quality       -> app's PageCurlBitmapSource
Page order/spread mapping    -> pagecurl/PageBookState.kt
Touch/grab anchor            -> pagecurl/PageCurlSurface.kt, DragInterpreter.kt
Paper deformation            -> pagecurl/CurlGeometry.kt
Texture/shader/shadow        -> pagecurl/PageCurlRenderer.kt, TextureLoader.kt, ShaderSources.kt
Compose lifecycle/rotation   -> pagecurl/PageCurl.kt
```

`onError`를 반드시 log에 기록한다. source render/decode 예외, recycle된 Bitmap, 0 크기, GPU texture limit 초과, GLES upload error 순으로 확인한다. 한 page upload가 실패하면 renderer는 해당 index에 불투명 blank texture를 사용하고 render loop를 계속한다.

## 이식 경계 검사

```powershell
./gradlew.bat --offline :pagecurl:verifyPortableModule
```

이 검사는 library `src/main`에 demo package/페이지 asset 이름이 남아 있거나 `src/main/assets` 아래에 파일이 있으면 실패한다.
