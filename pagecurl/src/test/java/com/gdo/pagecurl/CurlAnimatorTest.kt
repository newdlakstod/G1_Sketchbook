package com.gdo.pagecurl

import com.gdo.pagecurl.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlAnimatorTest {
    @Test
    fun settleStartsAtReleasePositionAndEndsAtTarget() {
        val animator = CurlAnimator()
        animator.start(
            from = Vec2(0.6f, 0.4f),
            to = Vec2(-0.1f, 0.5f),
            startNanos = 100L,
            durationNanos = 100L,
            phase = CurlPhase.SettlingToNext,
        )

        val start = animator.sample(100L)
        val end = animator.sample(200L)
        assertEquals(Vec2(0.6f, 0.4f), start.dragPosition)
        assertEquals(CurlPhase.SettlingToNext, start.phase)
        assertEquals(Vec2(-0.1f, 0.5f), end.dragPosition)
        assertEquals(CurlPhase.Completed, end.phase)
    }

    @Test
    fun easeOutProgressMovesMonotonicallyWithoutOvershoot() {
        val animator = CurlAnimator()
        animator.start(
            from = Vec2(0.8f, 0.25f),
            to = Vec2(1f, 0.5f),
            startNanos = 0L,
            durationNanos = 100L,
            phase = CurlPhase.SettlingToOrigin,
        )

        val samples = listOf(0L, 25L, 50L, 75L, 100L).map { animator.sample(it) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b.dragPosition.x >= a.dragPosition.x })
        assertTrue(samples.all { it.dragPosition.x in 0.8f..1f })
        assertEquals(CurlPhase.Idle, samples.last().phase)
    }

    @Test
    fun settleKeepsTheGrabAnchorCapturedAtPointerDown() {
        val animator = CurlAnimator()
        animator.start(
            from = Vec2(0.4f, 0.8f),
            to = Vec2(-1f, 0.8f),
            startNanos = 0L,
            durationNanos = 100L,
            phase = CurlPhase.SettlingToNext,
            grabAnchorY = 0.2f,
        )

        assertEquals(0.2f, animator.sample(50L).grabAnchorY, 0f)
        assertEquals(0.2f, animator.sample(100L).grabAnchorY, 0f)
    }
}
