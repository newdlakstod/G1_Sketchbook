package com.g1.sketchbook.preview

import com.g1.sketchbook.sketchbook.Sketchbook

internal val PreviewBooks = listOf(
    Sketchbook(
        id = "preview-personal-1",
        name = "Morning Notes",
        sizeKey = "a5",
        bgKey = "watercolor",
        createdAt = 1_776_556_800_000,
        pageCount = 9,
        fav = true,
    ),
    Sketchbook(
        id = "preview-personal-2",
        name = "Travel Sketches",
        sizeKey = "a4",
        bgKey = "kraft",
        createdAt = 1_774_915_200_000,
        pageCount = 6,
    ),
    Sketchbook(
        id = "preview-shared-1",
        name = "Draw Together",
        sizeKey = "a4",
        bgKey = "drawing",
        createdAt = 1_776_038_400_000,
        pageCount = 12,
        shared = true,
        code = "DAY123",
    ),
)

internal val PreviewDiaryDates = setOf(
    "2026-08-03",
    "2026-08-08",
    "2026-08-12",
    "2026-08-17",
    "2026-08-23",
)
