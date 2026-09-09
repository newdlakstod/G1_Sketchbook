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
