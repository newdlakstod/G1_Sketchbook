# 색상 라이브러리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 21색 팔레트를 "색상 라이브러리"(7색씩 묶음, 최대 30개 생성) 중 3개를 골라 합친 것으로 재구성하고, 즉시선택 즐겨찾기를 5색에서 3색으로 줄인다.

**Architecture:** `ColorLibrary` 데이터 클래스 + 순수 함수(합치기/토글/CRUD/직렬화)를 새 파일에 모아 유닛 테스트로 검증한다. `SessionStore`는 이 순수 함수들을 감싸는 얇은 저장 계층이 되고, 지금 저장되던 `paletteColors`는 라이브러리+선택 id에서 매번 계산하는 파생값(`val`, 더 이상 직접 저장 안 함)으로 바뀐다. UI(`BrushControls.kt`)의 "팔레트" 그리드는 그 파생값을 그대로 보여주고, 새 "관리" 버튼으로 라이브러리 목록(체크박스 최대 3개 선택)·개별 라이브러리 편집(색상 수정/이름 변경/삭제/새로 만들기) 화면을 연다. 계정 백업은 라이브러리 목록을 기존 공유노트 참조와 같은 툼스톤 리스트 동기화 패턴으로 맞춘다.

**Tech Stack:** Kotlin, Jetpack Compose, Firebase Realtime Database.

## Global Constraints

- 라이브러리 하나 = 색상 정확히 7개(`LibraryColorCount`), 사용자가 이름을 짓는다.
- 라이브러리 최대 개수: 30개(`MaxLibraries`).
- 팔레트를 구성하는 활성 라이브러리는 최대 3개(`ActiveLibraryCount`), 선택 순서가 곧 팔레트 순서. 이미 3개가 선택된 상태에서 새로 하나를 고르면 **가장 먼저 선택했던 것**이 자동 해제된다(선입선출).
- 활성 라이브러리가 3개 미만이면 팔레트는 그만큼만(7색 또는 14색) 보여준다 — 21칸을 억지로 안 채운다.
- 언제든지 라이브러리 안의 색상 7개 중 하나를 개별로 바꿀 수 있고, 이름도 언제든 바꿀 수 있고, 언제든 삭제할 수 있다.
- 기존 21색 팔레트(`SessionStore.paletteColors`) 저장 데이터는 마이그레이션하지 않고 버린다 — 한 번도 라이브러리를 저장한 적 없으면 `DefaultFavorites`(기존 21색) 를 7개씩 3등분해서 기본 라이브러리 3개("라이브러리 1/2/3")를 만들고 그 3개를 자동으로 활성 상태로 선택한다(첫 실행 화면이 기존 21색 팔레트와 똑같이 보임).
- 즉시선택 즐겨찾기 개수는 5 → 3(`QuickFavoritesCount`). 기존에 저장된 5개짜리 데이터는 크기가 안 맞아 자동으로 무효화되고 새 기본값(3개)으로 시작 — 별도 마이그레이션 코드 불필요(기존 "크기 안 맞으면 기본값" 로직 그대로 재사용).
- 이 저장소는 최근 벡터(SVG) 캔버스 기능이 통째로 제거됐다(v2.18.0) — 벡터 관련 파일/로직은 존재하지 않는다. 이 계획의 어떤 태스크도 벡터 관련 코드를 참조하지 않는다.
- 라이브러리 CRUD·팔레트 파생 로직은 Android 의존 없는 순수 Kotlin이라 유닛 테스트 대상. `SessionStore`(SharedPreferences)·`BrushControls.kt`(Compose UI)·백업 동기화(Firebase)는 이 프로젝트 관례상 컴파일 확인만.

---

### Task 1: ColorLibrary — 데이터 모델·순수 함수·직렬화

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/data/ColorLibrary.kt`
- Test: `app/src/test/java/com/g1/sketchbook/data/ColorLibraryTest.kt`

**Interfaces:**
- Consumes: `SessionStore.DefaultFavorites`(기존, 같은 패키지 `com.g1.sketchbook.data`, import 불필요).
- Produces: `data class ColorLibrary(id: String, name: String, colors: List<Long>)`, `const val LibraryColorCount = 7`, `const val MaxLibraries = 30`, `const val ActiveLibraryCount = 3`, `fun deriveLibraryPalette(libraries: List<ColorLibrary>, activeIds: List<String>): List<Long>`, `fun toggleActiveLibrary(activeIds: List<String>, id: String, maxActive: Int = ActiveLibraryCount): List<String>`, `fun addLibrary(libraries: List<ColorLibrary>, name: String): List<ColorLibrary>`, `fun renameLibrary(libraries: List<ColorLibrary>, id: String, name: String): List<ColorLibrary>`, `fun updateLibraryColor(libraries: List<ColorLibrary>, id: String, index: Int, color: Long): List<ColorLibrary>`, `fun removeLibrary(libraries: List<ColorLibrary>, id: String): List<ColorLibrary>`, `fun newLibraryId(): String`, `fun serializeLibraries(libraries: List<ColorLibrary>): String`, `fun parseLibraries(raw: String): List<ColorLibrary>?`. Task 2(`SessionStore`)·Task 4(화면 3개)·Task 5(백업 동기화)가 전부 이 함수들을 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/g1/sketchbook/data/ColorLibraryTest.kt`:

```kotlin
package com.g1.sketchbook.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorLibraryTest {
    private val libA = ColorLibrary("a", "A", listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L))
    private val libB = ColorLibrary("b", "B", listOf(11L, 12L, 13L, 14L, 15L, 16L, 17L))
    private val libC = ColorLibrary("c", "C", listOf(21L, 22L, 23L, 24L, 25L, 26L, 27L))

    @Test fun derivePaletteConcatenatesInOrder() {
        assertEquals(libB.colors + libA.colors, deriveLibraryPalette(listOf(libA, libB, libC), listOf("b", "a")))
    }

    @Test fun derivePaletteSkipsMissingIds() {
        assertEquals(libA.colors, deriveLibraryPalette(listOf(libA), listOf("a", "missing")))
    }

    @Test fun derivePaletteOfEmptyActiveIdsIsEmpty() {
        assertEquals(emptyList(), deriveLibraryPalette(listOf(libA, libB), emptyList()))
    }

    @Test fun toggleActivatesWhenNotPresent() {
        assertEquals(listOf("a"), toggleActiveLibrary(emptyList(), "a"))
    }

    @Test fun toggleDeactivatesWhenPresent() {
        assertEquals(listOf("a", "c"), toggleActiveLibrary(listOf("a", "b", "c"), "b"))
    }

    @Test fun toggleEvictsOldestWhenAtMax() {
        assertEquals(listOf("b", "c", "d"), toggleActiveLibrary(listOf("a", "b", "c"), "d"))
    }

    @Test fun toggleRespectsCustomMax() {
        assertEquals(listOf("y"), toggleActiveLibrary(listOf("x"), "y", maxActive = 1))
    }

    @Test fun addLibraryAppendsWithSevenStarterColors() {
        val result = addLibrary(listOf(libA), "새 라이브러리")
        assertEquals(2, result.size)
        assertEquals("새 라이브러리", result[1].name)
        assertEquals(7, result[1].colors.size)
    }

    @Test fun addLibraryDoesNothingAtCap() {
        val full = (1..MaxLibraries).map { ColorLibrary("id$it", "L$it", libA.colors) }
        assertEquals(full, addLibrary(full, "넘침"))
    }

    @Test fun renameLibraryChangesOnlyTheMatchingOne() {
        val result = renameLibrary(listOf(libA, libB), "a", "새 이름")
        assertEquals("새 이름", result[0].name)
        assertEquals("B", result[1].name)
    }

    @Test fun updateLibraryColorChangesOnlyThatSlot() {
        val result = updateLibraryColor(listOf(libA), "a", 2, 999L)
        assertEquals(listOf(1L, 2L, 999L, 4L, 5L, 6L, 7L), result[0].colors)
    }

    @Test fun removeLibraryDropsIt() {
        assertEquals(listOf(libB), removeLibrary(listOf(libA, libB), "a"))
    }

    @Test fun serializeAndParseRoundTrip() {
        val libraries = listOf(libA, libB, libC)
        assertEquals(libraries, parseLibraries(serializeLibraries(libraries)))
    }

    @Test fun parseLibrariesOfEmptyStringIsEmptyList() {
        assertEquals(emptyList(), parseLibraries(""))
    }

    @Test fun parseLibrariesHandlesNameWithCommas() {
        val withComma = listOf(ColorLibrary("x", "이름, 쉼표 포함", libA.colors))
        assertEquals(withComma, parseLibraries(serializeLibraries(withComma)))
    }

    @Test fun parseLibrariesReturnsNullForGarbage() {
        assertEquals(null, parseLibraries("완전히 잘못된 형식\u0002모자란필드"))
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.data.ColorLibraryTest" --no-daemon`
Expected: FAIL — `ColorLibrary` 등이 아직 없어서 컴파일 에러.

- [ ] **Step 3: 구현 작성**

`app/src/main/java/com/g1/sketchbook/data/ColorLibrary.kt` 전체:

```kotlin
package com.g1.sketchbook.data

import kotlin.random.Random

/** 색상 라이브러리 하나 — 항상 정확히 [LibraryColorCount]개의 색을 담는다. 사용자가 이름을 붙이고
 *  여러 개(최대 [MaxLibraries]) 만들 수 있다. 팔레트는 이 중 최대 [ActiveLibraryCount]개를 골라
 *  이어붙인 것([deriveLibraryPalette] 참고) — 라이브러리 자체가 저장의 단일 진실 공급원이고, 팔레트
 *  색상 리스트 자체는 따로 저장하지 않는다. */
data class ColorLibrary(
    val id: String,
    val name: String,
    val colors: List<Long>,
)

const val LibraryColorCount = 7
const val MaxLibraries = 30
const val ActiveLibraryCount = 3

/** [activeIds]가 가리키는 라이브러리들을 그 순서 그대로 이어붙여 팔레트 색상 목록을 만든다 —
 *  존재하지 않는 id는 조용히 건너뛴다(예: 다른 기기에서 삭제된 라이브러리가 아직 이 기기의
 *  activeIds에 남아있는 경우). */
fun deriveLibraryPalette(libraries: List<ColorLibrary>, activeIds: List<String>): List<Long> {
    val byId = libraries.associateBy { it.id }
    return activeIds.mapNotNull { byId[it] }.flatMap { it.colors }
}

/** 라이브러리 [id]를 활성 목록에서 토글한다 — 이미 들어있으면 빼고, 없으면 맨 뒤에 추가하되 이미
 *  [maxActive]개가 차 있으면 가장 먼저 선택했던(맨 앞) 것부터 밀어낸다(선입선출). */
fun toggleActiveLibrary(activeIds: List<String>, id: String, maxActive: Int = ActiveLibraryCount): List<String> {
    if (id in activeIds) return activeIds - id
    val next = activeIds + id
    return if (next.size > maxActive) next.drop(next.size - maxActive) else next
}

/** 새 라이브러리를 뒤에 추가한다 — 이미 [MaxLibraries]개면 그대로 반환(호출부가 UI에서 "추가" 버튼을
 *  비활성화해서 막는 게 우선이지만, 이 함수 자체도 안전망으로 상한을 지킨다). 처음 색은
 *  [SessionStore.DefaultFavorites]의 앞 [LibraryColorCount]개를 복사해서 시작한다(전부 같은 색이
 *  아니게, 바로 편집할 수 있게). */
fun addLibrary(libraries: List<ColorLibrary>, name: String): List<ColorLibrary> {
    if (libraries.size >= MaxLibraries) return libraries
    val startColors = SessionStore.DefaultFavorites.take(LibraryColorCount)
    return libraries + ColorLibrary(newLibraryId(), name, startColors)
}

fun renameLibrary(libraries: List<ColorLibrary>, id: String, name: String): List<ColorLibrary> =
    libraries.map { if (it.id == id) it.copy(name = name) else it }

/** 라이브러리 [id]의 [index]번째 색만 바꾼다(0..[LibraryColorCount]-1). */
fun updateLibraryColor(libraries: List<ColorLibrary>, id: String, index: Int, color: Long): List<ColorLibrary> =
    libraries.map { lib ->
        if (lib.id != id) lib
        else lib.copy(colors = lib.colors.mapIndexed { i, c -> if (i == index) color else c })
    }

fun removeLibrary(libraries: List<ColorLibrary>, id: String): List<ColorLibrary> =
    libraries.filter { it.id != id }

private val LibraryIdChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
fun newLibraryId(): String = "lib_" + (1..8).map { LibraryIdChars[Random.nextInt(LibraryIdChars.length)] }.joinToString("")

// SessionStore가 로컬 저장에 쓰는 손수 직렬화 — 이 프로젝트는 범용 JSON 파서를 안 씀. 라이브러리
// 이름에 쉼표 등 흔한 문자가 들어가도 안전하도록, 화면에 절대 안 보이는 제어문자를 구분자로 쓴다.
private const val RecordSeparator = "\u0001"
private const val FieldSeparator = "\u0002"

fun serializeLibraries(libraries: List<ColorLibrary>): String =
    libraries.joinToString(RecordSeparator) { "${it.id}$FieldSeparator${it.name}$FieldSeparator${it.colors.joinToString(",")}" }

/** [serializeLibraries]의 역함수 — 형식이 깨져 있으면(손상된 데이터 등) null. */
fun parseLibraries(raw: String): List<ColorLibrary>? {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        raw.split(RecordSeparator).map { record ->
            val parts = record.split(FieldSeparator)
            val colors = parts[2].split(",").map { it.toLong() }
            ColorLibrary(id = parts[0], name = parts[1], colors = colors)
        }
    }.getOrNull()
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.data.ColorLibraryTest" --no-daemon`
Expected: PASS, 16개 테스트 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/data/ColorLibrary.kt app/src/test/java/com/g1/sketchbook/data/ColorLibraryTest.kt
git commit -m "feat(data): add color library model, palette derivation, and CRUD"
```

---

### Task 2: SessionStore — 라이브러리 저장 + 팔레트 파생값화

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/data/SessionStore.kt`

**Interfaces:**
- Consumes: `ColorLibrary`/`deriveLibraryPalette`/`serializeLibraries`/`parseLibraries`(Task 1, 같은 패키지, import 불필요).
- Produces: `SessionStore.libraries: List<ColorLibrary>`(var), `SessionStore.activeLibraryIds: List<String>`(var), `SessionStore.paletteColors: List<Long>`(이제 `val`, 파생값 — **기존에 `var`였던 걸 `val`로 바꾸는 것 자체가 의도된 변경**: 어디선가 `session.paletteColors = ...`로 대입하는 코드가 남아있으면 컴파일 에러가 나야 하고, 그게 Task 4에서 고칠 대상이다). `SessionStore.QuickFavoritesCount = 3`(기존 5에서 변경). `SessionStore.PaletteCount` 상수는 삭제.

이 파일은 `android.content.Context`(SharedPreferences) 의존이라 유닛 테스트 대상이 아니다 — 컴파일 확인만. 단, 이 태스크가 끝난 후 `session.paletteColors = ...` 형태의 대입 코드가 다른 파일에 남아있으면 프로젝트 전체 컴파일이 실패하는데, 그건 이 태스크의 버그가 아니라 Task 4가 아직 안 끝나서다 — Step 4의 컴파일 확인은 **이 파일 자체만**(`SessionStore.kt`) 대상으로 하고, 프로젝트 전체 컴파일 확인은 Task 4 이후로 미룬다.

- [ ] **Step 1: `quickFavorites`/`paletteColors` 교체**

`SessionStore.kt`에서 다음 블록(기존 `quickFavorites`/`paletteColors` var 선언, 주석 포함)을 찾는다:

```kotlin
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
```

이걸로 교체:

```kotlin
    /** 툴바에 항상 보이는 빠른 접근 색상 3개 — [paletteColors]와 서로 독립이다. 아직 한 번도 따로
     *  저장한 적 없으면 그 시점의 [paletteColors] 앞 3개를 그대로 복사해서 시작한다. */
    var quickFavorites: List<Long>
        get() {
            val raw = prefs.getString(KEY_QUICK_FAVS, null)
            val parsed = raw?.let { runCatching { it.split(",").map { s -> s.toLong() } }.getOrNull() }
            return parsed?.takeIf { it.size == QuickFavoritesCount } ?: paletteColors.take(QuickFavoritesCount)
        }
        set(value) = prefs.edit().putString(KEY_QUICK_FAVS, value.joinToString(",")).apply()

    /** 색상 라이브러리 전체 목록 — 각각 정확히 [LibraryColorCount]개 색. [activeLibraryIds]가
     *  가리키는 최대 [ActiveLibraryCount]개가 합쳐져 실제 "팔레트"([paletteColors])가 된다 —
     *  팔레트 자체는 저장하지 않고 매번 이 값에서 계산한다(단일 진실 공급원은 라이브러리들 자체).
     *  한 번도 저장한 적 없으면(첫 실행, 또는 라이브러리 개념이 생기기 전 버전에서 올라온 경우)
     *  [DefaultFavorites] 21색을 [LibraryColorCount]개씩 3등분해서 기본 라이브러리 3개를 만든다
     *  (사용자 결정: 예전 21색 팔레트 데이터는 마이그레이션하지 않고 버림). */
    var libraries: List<ColorLibrary>
        get() {
            val raw = prefs.getString(KEY_LIBRARIES, null) ?: return defaultLibraries()
            return parseLibraries(raw) ?: defaultLibraries()
        }
        set(value) = prefs.edit().putString(KEY_LIBRARIES, serializeLibraries(value)).apply()

    /** 팔레트를 구성하는 라이브러리 id — 선택한 순서대로, 최대 [ActiveLibraryCount]개. 한 번도
     *  저장한 적 없으면 [libraries]의 처음 [ActiveLibraryCount]개를 자동으로 선택한다(기본
     *  라이브러리 3개가 막 만들어진 직후 포함 — 그 결과 첫 실행 화면은 예전 21색 팔레트와 똑같이
     *  보인다). */
    var activeLibraryIds: List<String>
        get() {
            val raw = prefs.getString(KEY_ACTIVE_LIBRARIES, null)
            val parsed = raw?.split(",")?.filter { it.isNotBlank() }
            return parsed ?: libraries.take(ActiveLibraryCount).map { it.id }
        }
        set(value) = prefs.edit().putString(KEY_ACTIVE_LIBRARIES, value.joinToString(",")).apply()

    /** [libraries]/[activeLibraryIds]를 조합한 실제 팔레트 색상(선택한 라이브러리가 3개 미만이면
     *  그만큼만) — 저장하지 않고 매번 계산한다. */
    val paletteColors: List<Long> get() = deriveLibraryPalette(libraries, activeLibraryIds)

    private fun defaultLibraries(): List<ColorLibrary> =
        DefaultFavorites.chunked(LibraryColorCount).mapIndexed { i, colors ->
            ColorLibrary(id = "default-${i + 1}", name = "라이브러리 ${i + 1}", colors = colors)
        }
```

- [ ] **Step 2: 상수 교체**

`companion object` 안의 다음 블록을 찾는다:

```kotlin
        // 색상 피커 카드 폭(260dp, BrushControls.ColorPickerCard)에 24dp 스와치+8dp 간격이 한 줄에
        // 7개 들어가서(FavoritesGrid) 7×3줄 = 21 — 그리드 칸 수가 바뀌면 이 값도 같이 맞춰야 한다.
        const val PaletteCount = 21
        const val QuickFavoritesCount = 5
```

이걸로 교체:

```kotlin
        private const val KEY_LIBRARIES = "color_libraries"
        private const val KEY_ACTIVE_LIBRARIES = "active_color_libraries"
        const val QuickFavoritesCount = 3
```

- [ ] **Step 3: `KEY_FAVS` 상수는 그대로 둔다**

`private const val KEY_FAVS = "fav_colors"`는 지우지 않는다 — 더 이상 이 파일 안에서 안 쓰이지만, 예전 버전이 저장해둔 잔여 데이터를 다른 코드가 참조하지 않는지 확인하는 차원에서 상수 자체는 남겨도 무해하다(경고만 뜰 수 있음, 컴파일 에러 아님). 만약 `-Werror` 등으로 미사용 경고가 빌드를 막는다면 그때 지운다 — 지금은 그대로 둔다.

- [ ] **Step 4: 이 파일 자체의 컴파일 확인(전체 프로젝트 아님)**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: **이 시점엔 실패해도 정상** — `BrushControls.kt`/화면 3개/백업 파일들이 아직 옛 `paletteColors`/`onEditPalette`/`PaletteCount`를 참조해서 에러가 날 것이다. 에러 메시지에 `SessionStore.kt` 자체의 문법 오류(오타, 괄호 안 맞음 등)가 있는지만 확인하고, "paletteColors를 var가 아니라 val로 쓰려고 했다"/"PaletteCount를 못 찾는다" 류의 에러는 Task 4·5에서 해결될 것이므로 지금은 무시한다.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/data/SessionStore.kt
git commit -m "feat(data): back palette with selectable color libraries, cut quick favorites to 3"
```

(이 커밋 시점엔 프로젝트 전체가 컴파일 안 되는 상태다 — Task 3·4·5까지 끝나야 다시 컴파일된다. 이는 계획대로다: 각 태스크가 독립적으로 온전한 상태를 만들기엔 이 5개 파일이 서로 강하게 얽혀 있어서, 순서대로 이어지는 하나의 흐름으로 처리한다.)

---

### Task 3: BrushControls — 라이브러리 관리 UI

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/brush/BrushControls.kt`

**Interfaces:**
- Consumes: `ColorLibrary`/`deriveLibraryPalette`(Task 1), `SessionStore.libraries`/`activeLibraryIds`(Task 2, 이 파일은 직접 안 쓰고 호출부가 값만 넘겨줌).
- Produces: `BrushControls(...)`의 새 파라미터 `libraries: List<ColorLibrary>`, `activeLibraryIds: List<String>`, `onToggleActiveLibrary: (String) -> Unit`, `onCreateLibrary: (String) -> Unit`, `onRenameLibrary: (String, String) -> Unit`, `onDeleteLibrary: (String) -> Unit`, `onEditLibraryColor: (String, Int, Long) -> Unit` — 기존 `palette`/`onEditPalette` 파라미터를 대체한다. Task 4(화면 3개)가 이 파라미터들을 배선한다.

이 파일은 Compose UI라 유닛 테스트 대상이 아니다 — 컴파일 확인 + 수동 확인(팔레트 버튼 → 관리 버튼 → 라이브러리 체크/편집/추가/삭제).

- [ ] **Step 1: import 추가**

파일 맨 위 import 블록에서 `import androidx.compose.foundation.layout.height` 줄 바로 아래에 추가:

```kotlin
import androidx.compose.foundation.layout.heightIn
```

`import androidx.compose.material3.AlertDialog` 줄 바로 아래에 추가:

```kotlin
import androidx.compose.material3.Checkbox
```

`import androidx.compose.material3.Surface` 줄 바로 아래에 추가:

```kotlin
import androidx.compose.material3.TextField
```

`import com.g1.sketchbook.R` 줄 바로 아래에 추가:

```kotlin
import com.g1.sketchbook.data.ColorLibrary
import com.g1.sketchbook.data.MaxLibraries
import com.g1.sketchbook.data.deriveLibraryPalette
```

- [ ] **Step 2: `BrushControls` 파라미터 교체**

다음 두 파라미터(주석 포함):

```kotlin
    /** "즐겨찾기 전체" 그리드에 보이는 21색 — [quickFavorites]와 독립(2026-08-31 분리, 예전엔 같은
     *  리스트의 앞 5개를 인라인으로 보여줬었음). */
    palette: List<Long> = BrushPalette,
    onEditPalette: (Int, Long) -> Unit = { _, _ -> },
```

이걸로 교체:

```kotlin
    /** 팔레트를 구성하는 색상 라이브러리 전체 — [activeLibraryIds]가 가리키는 최대 3개를 이어붙인
     *  게 실제 팔레트다(2026-09-08, 21색 고정 리스트에서 라이브러리 여러 개 중 3개 선택 방식으로
     *  바뀜). */
    libraries: List<ColorLibrary> = emptyList(),
    activeLibraryIds: List<String> = emptyList(),
    onToggleActiveLibrary: (String) -> Unit = {},
    onCreateLibrary: (String) -> Unit = {},
    onRenameLibrary: (String, String) -> Unit = { _, _ -> },
    onDeleteLibrary: (String) -> Unit = {},
    onEditLibraryColor: (String, Int, Long) -> Unit = { _, _, _ -> },
```

또한 `quickFavorites: List<Long> = BrushPalette.take(5),` 줄을 `quickFavorites: List<Long> = BrushPalette.take(3),`로 바꾼다(파라미터 기본값일 뿐 — 실제 호출부는 항상 명시적으로 넘기므로 거의 안 쓰이지만, 일관성을 위해 맞춘다).

- [ ] **Step 3: 파생 팔레트 계산 추가**

`BrushControls` 함수 본문 맨 앞, `var colorWheelOpen by remember { mutableStateOf(false) }` 줄 바로 위에 추가:

```kotlin
    val palette = remember(libraries, activeLibraryIds) { deriveLibraryPalette(libraries, activeLibraryIds) }
    var libraryManagerOpen by remember { mutableStateOf(false) }
    var editingLibraryId by remember { mutableStateOf<String?>(null) }
    var editingColorAt by remember { mutableIntStateOf(-1) }
    val editingLibrary = libraries.firstOrNull { it.id == editingLibraryId }
```

- [ ] **Step 4: "팔레트" 버튼 블록 교체**

다음 블록을 찾는다:

```kotlin
                // 팔레트(21개) 그리드 — 즐겨찾기 5개와는 별개인 색상 모음, 여기서 고르거나 등록.
                Box {
                    IconBtn(Icons.Filled.Palette, "팔레트",
                        tint = if (favoritesGridOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { favoritesGridOpen = !favoritesGridOpen })
                    if (favoritesGridOpen) Popup(popupAnchor, { favoritesGridOpen = false }, PopupProperties(focusable = true)) {
                        FavoritesGridPopup(palette, color, erasing, onColor, onEditPalette,
                            onEyedrop = { favoritesGridOpen = false; onToggleEyedrop() })
                    }
                }
```

이걸로 교체:

```kotlin
                // 팔레트 — 선택된 라이브러리(최대 3개)의 색을 이어붙인 목록에서 고른다. "관리"
                // 버튼으로 라이브러리 목록(체크박스로 최대 3개 선택)을 열고, 그 안에서 라이브러리
                // 하나를 탭하면 그 7색을 편집(색 바꾸기/이름 변경/삭제)할 수 있다.
                Box {
                    IconBtn(Icons.Filled.Palette, "팔레트",
                        tint = if (favoritesGridOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { favoritesGridOpen = !favoritesGridOpen })
                    if (favoritesGridOpen) Popup(popupAnchor, { favoritesGridOpen = false }, PopupProperties(focusable = true)) {
                        FavoritesGridPopup(palette, color, erasing, onColor,
                            onEyedrop = { favoritesGridOpen = false; onToggleEyedrop() },
                            onManage = { favoritesGridOpen = false; libraryManagerOpen = true })
                    }
                    if (libraryManagerOpen) Popup(popupAnchor, { libraryManagerOpen = false }, PopupProperties(focusable = true)) {
                        ColorLibraryManagerPopup(libraries, activeLibraryIds,
                            onToggleActive = onToggleActiveLibrary,
                            onCreate = onCreateLibrary,
                            onOpenLibrary = { lib -> libraryManagerOpen = false; editingLibraryId = lib.id })
                    }
                    editingLibrary?.let { lib ->
                        Popup(popupAnchor, { editingLibraryId = null; editingColorAt = -1 }, PopupProperties(focusable = true)) {
                            ColorLibraryDetailPopup(lib,
                                onColorTap = { i -> editingColorAt = i },
                                onRename = { newName -> onRenameLibrary(lib.id, newName) },
                                onDelete = { onDeleteLibrary(lib.id); editingLibraryId = null },
                                onClose = { editingLibraryId = null; editingColorAt = -1 })
                        }
                    }
                    if (editingColorAt >= 0 && editingLibrary != null) {
                        Popup(popupAnchor, { editingColorAt = -1 }, PopupProperties(focusable = true)) {
                            ColorPickerCard(editingLibrary.colors[editingColorAt],
                                onColor = { newColor -> onEditLibraryColor(editingLibrary.id, editingColorAt, newColor) },
                                onEyedrop = { editingColorAt = -1; onToggleEyedrop() })
                        }
                    }
                }
```

- [ ] **Step 5: `FavoritesGridPopup` 시그니처·내용 교체**

다음 함수 전체를 찾는다:

```kotlin
/** 팔레트 21개를 [ColorPickerCard]와 같은 폭(260dp)의 그리드로 보여주는 팝업 — 즐겨찾기 5개와는
 *  독립된 [palette] 리스트(2026-08-31 분리). 탭하면 선택, 이미 선택된 칸을 다시 탭하면 그 칸의
 *  색을 바꾸는 색상휠이 뜬다(즐겨찾기 인라인 스와치와 동일한 편집 방식). */
@Composable
private fun FavoritesGridPopup(
    palette: List<Long>, color: Long, erasing: Boolean,
    onColor: (Long) -> Unit, onEditPalette: (Int, Long) -> Unit, onEyedrop: () -> Unit,
) {
    var editAt by remember { mutableIntStateOf(-1) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Box(Modifier.width(260.dp).padding(16.dp)) {
            FavoritesGrid(palette) { i, c ->
                val on = !erasing && c == color
                val interaction = remember { MutableInteractionSource() }
                Box {
                    Box(
                        Modifier.size(FavoriteSwatchSize).clip(CircleShape).indication(interaction, LocalIndication.current)
                            .background(Color(c))
                            .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                            .clickable(interactionSource = interaction, indication = null, onClickLabel = "팔레트 색상 ${i + 1}") {
                                if (on) editAt = i else onColor(c)
                            },
                    )
                    if (editAt == i) Popup(AboveAnchor(0, 0), { editAt = -1 }, PopupProperties(focusable = true)) {
                        ColorPickerCard(c,
                            onColor = { newColor -> onColor(newColor); onEditPalette(i, newColor) },
                            onEyedrop = { editAt = -1; onEyedrop() })
                    }
                }
            }
        }
    }
}
```

이걸로 교체(이제 라이브러리 관리 화면이 편집을 전담하므로, 이 그리드는 선택 전용으로 단순해진다 + 맨 위에 "관리" 버튼 한 줄 추가):

```kotlin
/** 팔레트(활성 라이브러리 최대 3개를 이어붙인 색상)를 [ColorPickerCard]와 같은 폭(260dp)의
 *  그리드로 보여주는 팝업 — 탭하면 그 색을 고른다. 색상 자체를 편집하거나 어떤 라이브러리를
 *  쓸지 고르려면 위쪽 "관리" 버튼으로 [ColorLibraryManagerPopup]을 연다(2026-09-08, 예전엔 이
 *  팝업 안에서 칸을 다시 탭해 바로 편집했는데, 라이브러리 개념이 생기면서 편집은 그쪽으로 옮김). */
@Composable
private fun FavoritesGridPopup(
    palette: List<Long>, color: Long, erasing: Boolean,
    onColor: (Long) -> Unit, onEyedrop: () -> Unit, onManage: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Column(Modifier.width(260.dp).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("팔레트", style = MaterialTheme.typography.labelLarge)
                IconBtn(Icons.Filled.Tune, "라이브러리 관리", onClick = onManage)
            }
            Spacer(Modifier.height(8.dp))
            if (palette.isEmpty()) {
                Text("선택된 라이브러리가 없어요 — \"관리\"에서 최대 3개를 골라주세요.", style = MaterialTheme.typography.bodySmall)
            } else {
                FavoritesGrid(palette) { i, c ->
                    val on = !erasing && c == color
                    Box(
                        Modifier.size(FavoriteSwatchSize).clip(CircleShape)
                            .background(Color(c))
                            .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                            .clickable(onClickLabel = "팔레트 색상 ${i + 1}") { onColor(c) },
                    )
                }
            }
        }
    }
}

/** 색상 라이브러리 목록 — 체크박스로 최대 [com.g1.sketchbook.data.ActiveLibraryCount]개를 골라
 *  팔레트를 구성한다. 각 줄을 탭하면 그 라이브러리의 7색 편집 화면([ColorLibraryDetailPopup])이
 *  열린다. 맨 아래 "새 라이브러리" 버튼은 [MaxLibraries]에 도달하면 비활성화된다. */
@Composable
private fun ColorLibraryManagerPopup(
    libraries: List<ColorLibrary>, activeLibraryIds: List<String>,
    onToggleActive: (String) -> Unit, onCreate: (String) -> Unit, onOpenLibrary: (ColorLibrary) -> Unit,
) {
    var createOpen by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Column(Modifier.width(260.dp).padding(16.dp)) {
            Text("색상 라이브러리 (${activeLibraryIds.size}/3 선택됨)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                libraries.forEach { lib ->
                    val active = lib.id in activeLibraryIds
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onOpenLibrary(lib) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = active, onCheckedChange = { onToggleActive(lib.id) })
                        Spacer(Modifier.width(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            lib.colors.forEach { c -> Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c))) }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(lib.name, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (createOpen) {
                TextField(value = nameDraft, onValueChange = { nameDraft = it }, singleLine = true, placeholder = { Text("라이브러리 이름") })
                Row {
                    TextButton(onClick = {
                        onCreate(nameDraft.ifBlank { "라이브러리 ${libraries.size + 1}" })
                        nameDraft = ""; createOpen = false
                    }) { Text("추가") }
                    TextButton(onClick = { createOpen = false; nameDraft = "" }) { Text("취소") }
                }
            } else {
                TextButton(onClick = { createOpen = true }, enabled = libraries.size < MaxLibraries) {
                    Text(if (libraries.size < MaxLibraries) "+ 새 라이브러리" else "최대 ${MaxLibraries}개까지 만들 수 있어요")
                }
            }
        }
    }
}

/** 라이브러리 하나의 7색 편집 화면 — 이름 변경(입력하는 즉시 반영), 색상 하나씩 탭해서 바꾸기,
 *  삭제. */
@Composable
private fun ColorLibraryDetailPopup(
    library: ColorLibrary, onColorTap: (Int) -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit, onClose: () -> Unit,
) {
    var nameDraft by remember(library.id) { mutableStateOf(library.name) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Column(Modifier.width(260.dp).padding(16.dp)) {
            TextField(
                value = nameDraft, onValueChange = { nameDraft = it; onRename(it) }, singleLine = true,
                textStyle = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                library.colors.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(FavoriteSwatchSize).clip(CircleShape).background(Color(c))
                            .border(1.dp, Color(0x33000000), CircleShape)
                            .clickable(onClickLabel = "${i + 1}번째 색") { onColorTap(i) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                TextButton(onClick = onDelete) { Text("삭제", color = Color(0xFFE85555)) }
                TextButton(onClick = onClose) { Text("닫기") }
            }
        }
    }
}
```

- [ ] **Step 6: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: **이 시점에도 실패할 수 있다** — `BrushControls`를 호출하는 화면 3개가 아직 옛 `palette`/`onEditPalette` 인자를 넘기고 있어서다. 에러 메시지에 `BrushControls.kt` **자체**의 문법 오류가 있는지만 확인한다(괄호, 타입 불일치 등) — "호출부가 palette라는 이름의 인자를 못 찾는다" 류의 에러는 Task 4에서 해결된다.

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/brush/BrushControls.kt
git commit -m "feat(brush): add color library management UI to the palette popup"
```

---

### Task 4: 화면 3개 — 라이브러리 상태 배선

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/share/SharedBookScreen.kt`

**Interfaces:**
- Consumes: `ColorLibrary`/`toggleActiveLibrary`/`addLibrary`/`renameLibrary`/`updateLibraryColor`/`removeLibrary`(Task 1, FQN으로 인라인 참조 — 이 세 파일은 `com.g1.sketchbook.data` 패키지가 아니라서 새 import 줄을 각각 추가하는 대신 `com.g1.sketchbook.data.toggleActiveLibrary(...)` 식으로 그대로 쓴다), `BrushControls`의 새 파라미터들(Task 3), `SessionStore.libraries`/`activeLibraryIds`(Task 2).

이 세 파일은 Compose UI라 유닛 테스트 대상이 아니다 — 컴파일 확인 + 수동 확인.

이번 태스크가 끝나면 프로젝트 전체가 다시 컴파일된다(Task 2·3에서 일부러 깨뜨려 둔 상태가 여기서 복구됨).

- [ ] **Step 1: `SketchbookScreens.kt` 수정**

다음 두 줄을 찾는다:

```kotlin
    var quickFavorites by remember { mutableStateOf(session.quickFavorites) }
    var palette by remember { mutableStateOf(session.paletteColors) }
```

이걸로 교체:

```kotlin
    var quickFavorites by remember { mutableStateOf(session.quickFavorites) }
    var libraries by remember { mutableStateOf(session.libraries) }
    var activeLibraryIds by remember { mutableStateOf(session.activeLibraryIds) }
```

다음 네 줄(`BrushControls(...)` 호출부 안)을 찾는다:

```kotlin
            quickFavorites = quickFavorites,
            onEditQuickFavorite = { i, c -> val nf = quickFavorites.toMutableList(); nf[i] = c; quickFavorites = nf; session.quickFavorites = nf },
            palette = palette,
            onEditPalette = { i, c -> val nf = palette.toMutableList(); nf[i] = c; palette = nf; session.paletteColors = nf },
```

이걸로 교체:

```kotlin
            quickFavorites = quickFavorites,
            onEditQuickFavorite = { i, c -> val nf = quickFavorites.toMutableList(); nf[i] = c; quickFavorites = nf; session.quickFavorites = nf },
            libraries = libraries,
            activeLibraryIds = activeLibraryIds,
            onToggleActiveLibrary = { id ->
                val next = com.g1.sketchbook.data.toggleActiveLibrary(activeLibraryIds, id)
                activeLibraryIds = next; session.activeLibraryIds = next
            },
            onCreateLibrary = { name ->
                val next = com.g1.sketchbook.data.addLibrary(libraries, name)
                libraries = next; session.libraries = next
            },
            onRenameLibrary = { id, name ->
                val next = com.g1.sketchbook.data.renameLibrary(libraries, id, name)
                libraries = next; session.libraries = next
            },
            onDeleteLibrary = { id ->
                libraries = com.g1.sketchbook.data.removeLibrary(libraries, id); session.libraries = libraries
                if (id in activeLibraryIds) { activeLibraryIds = activeLibraryIds - id; session.activeLibraryIds = activeLibraryIds }
            },
            onEditLibraryColor = { id, i, c ->
                val next = com.g1.sketchbook.data.updateLibraryColor(libraries, id, i, c)
                libraries = next; session.libraries = next
            },
```

- [ ] **Step 2: `DiaryScreens.kt` 수정**

다음 블록을 찾는다(nullable `session`을 쓰는 패턴):

```kotlin
    var quickFavorites by remember(session) {
        mutableStateOf(session?.quickFavorites ?: com.g1.sketchbook.data.SessionStore.DefaultFavorites.take(com.g1.sketchbook.data.SessionStore.QuickFavoritesCount))
    }
```

바로 다음 줄들에 있는 `palette` 선언(정확한 텍스트, 주변 줄 확인 후):

```kotlin
    var palette by remember(session) {
        mutableStateOf(session?.paletteColors ?: com.g1.sketchbook.data.SessionStore.DefaultFavorites)
    }
```

이걸로 교체:

```kotlin
    var libraries by remember(session) {
        mutableStateOf(session?.libraries ?: emptyList())
    }
    var activeLibraryIds by remember(session) {
        mutableStateOf(session?.activeLibraryIds ?: emptyList())
    }
```

`BrushControls(...)` 호출부 안의 다음 블록을 찾는다:

```kotlin
            quickFavorites = quickFavorites,
            onEditQuickFavorite = { i, c ->
                val nf = quickFavorites.toMutableList(); nf[i] = c; quickFavorites = nf
                session?.let { it.quickFavorites = nf }
            },
            palette = palette,
            onEditPalette = { i, c ->
                val nf = palette.toMutableList(); nf[i] = c; palette = nf
                session?.let { it.paletteColors = nf }
            },
```

(정확한 줄바꿈·이어지는 코드는 실제 파일을 읽어서 확인 — 위는 브리핑 기준 형태다.) 이걸로 교체:

```kotlin
            quickFavorites = quickFavorites,
            onEditQuickFavorite = { i, c ->
                val nf = quickFavorites.toMutableList(); nf[i] = c; quickFavorites = nf
                session?.let { it.quickFavorites = nf }
            },
            libraries = libraries,
            activeLibraryIds = activeLibraryIds,
            onToggleActiveLibrary = { id ->
                val next = com.g1.sketchbook.data.toggleActiveLibrary(activeLibraryIds, id)
                activeLibraryIds = next; session?.let { it.activeLibraryIds = next }
            },
            onCreateLibrary = { name ->
                val next = com.g1.sketchbook.data.addLibrary(libraries, name)
                libraries = next; session?.let { it.libraries = next }
            },
            onRenameLibrary = { id, name ->
                val next = com.g1.sketchbook.data.renameLibrary(libraries, id, name)
                libraries = next; session?.let { it.libraries = next }
            },
            onDeleteLibrary = { id ->
                libraries = com.g1.sketchbook.data.removeLibrary(libraries, id); session?.let { it.libraries = libraries }
                if (id in activeLibraryIds) {
                    activeLibraryIds = activeLibraryIds - id
                    session?.let { it.activeLibraryIds = activeLibraryIds }
                }
            },
            onEditLibraryColor = { id, i, c ->
                val next = com.g1.sketchbook.data.updateLibraryColor(libraries, id, i, c)
                libraries = next; session?.let { it.libraries = next }
            },
```

- [ ] **Step 3: `SharedBookScreen.kt` 수정**

`SketchbookScreens.kt`(Step 1)와 완전히 같은 패턴 — 다음 두 줄:

```kotlin
    var quickFavorites by remember { mutableStateOf(session.quickFavorites) }
    var palette by remember { mutableStateOf(session.paletteColors) }
```

을 Step 1과 똑같이(`libraries`/`activeLibraryIds`로) 교체하고, `BrushControls(...)` 호출부의 다음 두 줄:

```kotlin
                quickFavorites = quickFavorites,
                onEditQuickFavorite = { i, c -> val nf = quickFavorites.toMutableList(); nf[i] = c; quickFavorites = nf; session.quickFavorites = nf },
                palette = palette,
                onEditPalette = { i, c -> val nf = palette.toMutableList(); nf[i] = c; palette = nf; session.paletteColors = nf },
```

을 Step 1의 대체 블록과 같은 내용으로(들여쓰기만 이 파일의 기존 스타일에 맞춰) 교체한다.

- [ ] **Step 4: 전체 프로젝트 컴파일 + 전체 유닛 테스트 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남(Task 2에서 일부러 깨뜨려 둔 게 이제 전부 복구되어야 함).

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --no-daemon`
Expected: PASS — Task 1의 `ColorLibraryTest` 포함 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt app/src/main/java/com/g1/sketchbook/share/SharedBookScreen.kt
git commit -m "feat: wire color library state into the three brush-toolbar screens"
```

---

### Task 5: 계정 백업 — 라이브러리 목록 동기화

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt`

**Interfaces:**
- Consumes: `ColorLibrary`(Task 1), `SessionStore.libraries`/`activeLibraryIds`(Task 2).
- Produces: `RemoteColorLibrary` data class, `RemoteSnapshot.colorLibraries: List<RemoteColorLibrary>`, `RemoteSettings.activeLibraryIds: List<String>`(`paletteColors` 필드는 삭제), `BackupRepository.pushColorLibrary(uid, library)`/`deleteColorLibrary(uid, id, updatedAt)`.

이 세 파일은 Firebase/Android 의존이라 유닛 테스트 대상이 아니다 — 컴파일 확인만.

- [ ] **Step 1: `BackupModels.kt` 수정**

`RemoteSharedBookRef` 데이터 클래스 바로 아래에 추가:

```kotlin
/** 색상 라이브러리 하나의 백업용 표현 — [SessionStore.libraries]의 [ColorLibrary]와 1:1 대응.
 *  [deleted]는 툼스톤 — 이 기기에서 지운 라이브러리를 다른 기기에도 지우라고 알리는 용도
 *  ([RemoteSharedBookRef]와 같은 패턴). */
data class RemoteColorLibrary(
    val id: String,
    val name: String,
    val colors: List<Long>,
    val updatedAt: Long,
    val deleted: Boolean,
)
```

`RemoteSnapshot` 데이터 클래스를 이걸로 교체:

```kotlin
data class RemoteSnapshot(
    val sketchbooks: List<RemoteSketchbook>,
    val diary: Map<String, RemoteDiaryDay>,
    val settings: RemoteSettings?,
    val sharedBooks: List<RemoteSharedBookRef>,
    val colorLibraries: List<RemoteColorLibrary>,
)
```

`RemoteSettings` 데이터 클래스를 이걸로 교체(`paletteColors` 필드 삭제, `activeLibraryIds` 필드 추가):

```kotlin
data class RemoteSettings(
    val nickname: String?, val themeMode: String,
    /** 팔레트를 구성하는 라이브러리 id(선택 순서대로, 최대 3개) — 라이브러리 자체(이름·색상)는
     *  [RemoteColorLibrary] 목록으로 별도 동기화된다. */
    val activeLibraryIds: List<String>,
    /** 즐겨찾기 3색 — [activeLibraryIds]와 독립. */
    val quickFavorites: List<Long>,
    val gesture2Tap: String, val gesture3Tap: String, val gestureLongPress: String,
    val largeCovers: Boolean, val brushColor: Long,
    val brushSizes: Map<String, Float>, val brushOpacities: Map<String, Float>,
    val eraserSize: Float, val eraserOpacity: Float, val eraserBlur: Float,
    val avatarBase64: String?, val updatedAt: Long,
)
```

- [ ] **Step 2: `BackupRepository.kt` 수정**

`pushSettings` 함수 안의 다음 줄:

```kotlin
            "favoriteColors" to record.paletteColors, "quickFavorites" to record.quickFavorites, "gesture2Tap" to record.gesture2Tap,
```

이걸로 교체:

```kotlin
            "activeLibraryIds" to record.activeLibraryIds, "quickFavorites" to record.quickFavorites, "gesture2Tap" to record.gesture2Tap,
```

`pushSharedBookRef`/`deleteSharedBookRef` 함수 바로 아래에 추가:

```kotlin
    fun pushColorLibrary(uid: String, library: RemoteColorLibrary) {
        root.child(uid).child("colorLibraries").child(library.id).setValue(
            mapOf("name" to library.name, "colors" to library.colors, "updatedAt" to library.updatedAt, "deleted" to false),
        )
    }

    /** 툼스톤 — 하드 삭제하면 다른 기기가 "원래 없었음"으로 잘못 읽고 되살린다([deleteSharedBookRef]와 동일 이유). */
    fun deleteColorLibrary(uid: String, id: String, updatedAt: Long) {
        root.child(uid).child("colorLibraries").child(id).setValue(mapOf("deleted" to true, "updatedAt" to updatedAt))
    }
```

`pullAll` 함수 안, `settings` 블록에서 다음 줄:

```kotlin
            paletteColors = s.child("favoriteColors").children.mapNotNull { it.getValue(Long::class.java) },
```

이걸로 교체:

```kotlin
            activeLibraryIds = s.child("activeLibraryIds").children.mapNotNull { it.getValue(String::class.java) },
```

`pullAll` 함수 안, `val sharedBooks = ...` 블록 바로 다음(`return RemoteSnapshot(...)` 줄 이전)에 추가:

```kotlin
        val colorLibraries = snap.child("colorLibraries").children.mapNotNull { c ->
            val id = c.key ?: return@mapNotNull null
            RemoteColorLibrary(
                id = id,
                name = c.child("name").getValue(String::class.java) ?: "",
                colors = c.child("colors").children.mapNotNull { it.getValue(Long::class.java) },
                updatedAt = c.child("updatedAt").getValue(Long::class.java) ?: 0L,
                deleted = c.child("deleted").getValue(Boolean::class.java) ?: false,
            )
        }
```

`return RemoteSnapshot(sketchbooks, diary, settings, sharedBooks)`를 `return RemoteSnapshot(sketchbooks, diary, settings, sharedBooks, colorLibraries)`로 교체.

- [ ] **Step 3: `BackupSync.kt` 수정**

파일 맨 위 import 블록에 추가:

```kotlin
import com.g1.sketchbook.data.ColorLibrary
```

`reconcileBackup` 함수의 마지막 줄(`reconcileSharedBooks(sketchbookRepo, backup, uid, remote.sharedBooks)`) 바로 다음에 한 줄 추가:

```kotlin
    reconcileColorLibraries(context, backup, uid, remote.colorLibraries)
```

`reconcileSharedBooks` 함수 바로 아래에 새 함수 추가:

```kotlin
/** [SessionStore.libraries]와 원격 `colorLibraries`를 맞춘다 — [reconcileSharedBooks]와 같은
 *  툼스톤 방식: 원격에만 있고 로컬에 없으면 받아서 추가, 원격에서 지워졌으면(deleted=true) 로컬
 *  에서도 삭제, 로컬에만 있거나(또는 원격이 이미 지운 걸 로컬은 아직 갖고 있으면) 원격에 올린다. */
private fun reconcileColorLibraries(context: Context, backup: BackupRepository, uid: String, remote: List<RemoteColorLibrary>) {
    val session = SessionStore(context)
    val local = session.libraries
    val remoteById = remote.associateBy { it.id }
    val localIds = local.map { it.id }.toSet()
    var next = local

    for (r in remote) {
        if (r.deleted) {
            if (r.id in localIds) next = next.filter { it.id != r.id }
        } else if (r.id !in localIds) {
            next = next + ColorLibrary(r.id, r.name, r.colors)
        }
    }
    if (next != local) session.libraries = next

    for (library in next) {
        val r = remoteById[library.id]
        if (r == null || r.deleted) {
            backup.pushColorLibrary(uid, RemoteColorLibrary(library.id, library.name, library.colors, System.currentTimeMillis(), false))
        }
    }
}
```

`syncSettingsUp` 함수 안의 다음 줄:

```kotlin
        paletteColors = session.paletteColors, quickFavorites = session.quickFavorites,
```

이걸로 교체:

```kotlin
        activeLibraryIds = session.activeLibraryIds, quickFavorites = session.quickFavorites,
```

`applyRemoteSettings` 함수 안의 다음 줄:

```kotlin
    if (r.paletteColors.size == SessionStore.PaletteCount) session.paletteColors = r.paletteColors
```

이걸로 교체(라이브러리 개수는 0~3개 어느 값이든 유효해서 크기 검사가 필요 없다 — 팔레트 자체가 아니라 "어떤 라이브러리를 쓸지"만 last-write-wins으로 받는다):

```kotlin
    session.activeLibraryIds = r.activeLibraryIds
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:compileDebugKotlin --no-daemon -q`
Expected: 조용히 끝남.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt
git commit -m "feat(backup): sync color libraries across devices via tombstone list"
```

---

### Task 6: 전체 빌드·테스트 검증

**Files:** (없음 — 검증 전용 태스크, 문제가 발견되면 그 문제가 있는 파일을 그 자리에서 고친다.)

- [ ] **Step 1: 전체 유닛 테스트 실행**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:testDebugUnitTest --no-daemon`
Expected: PASS — Task 1에서 추가한 `ColorLibraryTest`(16개)와 기존 테스트 전부 통과.

- [ ] **Step 2: 전체 디버그 빌드**

Run: `cd "c:\Joon's Room\claude code\App\G1_Sketchbook" && ./gradlew.bat :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 문제 발견 시 수정**

Step 1이나 2에서 실패가 나오면, 실패한 파일을 열어 원인을 고치고 `fix(...): ...` 스타일로 별도 커밋한다. 실패가 없으면 이 태스크는 커밋할 변경사항이 없다.

---

## Self-Review

**스펙 커버리지 확인:**
- 라이브러리 7색 고정, 최대 30개 → Task 1(`LibraryColorCount`, `MaxLibraries`) + Task 3(추가 버튼 상한 비활성화).
- 사용자가 이름 지음, 언제든 이름 변경 → Task 1(`renameLibrary`) + Task 3(`ColorLibraryDetailPopup`의 `TextField`).
- 언제든 개별 색상 수정 → Task 1(`updateLibraryColor`) + Task 3(`ColorLibraryDetailPopup`의 스와치 탭 → `ColorPickerCard`).
- 체크박스로 최대 3개 선택, 선입선출 → Task 1(`toggleActiveLibrary`) + Task 3(`ColorLibraryManagerPopup`의 `Checkbox`).
- 기존 21색 버리고 기본 라이브러리 3개 자동 생성+자동 선택 → Task 2(`defaultLibraries()`, `activeLibraryIds`의 기본값 계산).
- 즐겨찾기 5→3 → Task 2(`QuickFavoritesCount = 3`).
- 동기화(라이브러리는 툼스톤 리스트, 활성 id는 설정에 포함) → Task 5.
- 이번 스펙에서 다루지 않는 것(라이브러리당 색상 개수 변경, 라이브러리 공유, 활성 개수 3 아닌 값, 실제 마이그레이션) → 계획에 포함된 어떤 태스크도 이 항목들을 구현하지 않음(의도적으로 제외).

**플레이스홀더 스캔:** 전체 태스크 재확인 — "TBD"/"나중에 구현"/구체 코드 없는 단계 없음.

**타입 일관성 확인:** `ColorLibrary(id, name, colors)` — Task 1에서 정의, Task 2·3·4·5 전부 같은 필드명으로 사용. `deriveLibraryPalette(libraries, activeIds): List<Long>` — Task 1에서 정의, Task 2(`paletteColors` getter)·Task 3(팔레트 그리드)에서 같은 시그니처로 호출. `BrushControls`의 `libraries`/`activeLibraryIds`/`onToggleActiveLibrary`/`onCreateLibrary`/`onRenameLibrary`/`onDeleteLibrary`/`onEditLibraryColor` — Task 3에서 정의한 이름 그대로 Task 4의 세 호출부에서 동일하게 사용.
