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
