package com.g1.sketchbook.readmode.curl

import com.g1.sketchbook.readmode.curl.math.Vec2
import com.g1.sketchbook.readmode.curl.math.clamp
import kotlin.math.pow

class CurlAnimator {
    private var from = Vec2(1f, 0.5f)
    private var to = from
    private var startNanos = 0L
    private var durationNanos = 1L
    private var settlingPhase = CurlPhase.Idle
    private var lastState = CurlState()

    var isRunning: Boolean = false
        private set

    fun start(
        from: Vec2,
        to: Vec2,
        startNanos: Long,
        durationNanos: Long,
        phase: CurlPhase,
    ) {
        require(durationNanos > 0L)
        require(phase == CurlPhase.SettlingToNext || phase == CurlPhase.SettlingToOrigin)
        this.from = from
        this.to = to
        this.startNanos = startNanos
        this.durationNanos = durationNanos
        this.settlingPhase = phase
        lastState = CurlState.at(phase, from)
        isRunning = true
    }

    fun sample(nowNanos: Long): CurlState {
        if (!isRunning) return lastState

        val linear = clamp((nowNanos - startNanos).toFloat() / durationNanos.toFloat())
        val eased = 1f - (1f - linear).pow(3)
        val position = if (linear >= 1f) {
            to
        } else {
            Vec2(
                x = from.x + (to.x - from.x) * eased,
                y = from.y + (to.y - from.y) * eased,
            )
        }
        val phase = if (linear < 1f) {
            settlingPhase
        } else if (settlingPhase == CurlPhase.SettlingToNext) {
            CurlPhase.Completed
        } else {
            CurlPhase.Idle
        }
        lastState = CurlState.at(phase, position)
        isRunning = linear < 1f
        return lastState
    }

    fun cancel(state: CurlState = CurlState()) {
        lastState = state
        isRunning = false
    }
}
