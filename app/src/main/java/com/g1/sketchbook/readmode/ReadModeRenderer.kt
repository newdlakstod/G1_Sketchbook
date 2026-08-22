package com.g1.sketchbook.readmode

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.g1.sketchbook.readmode.curl.CurlAnimator
import com.g1.sketchbook.readmode.curl.CurlGeometry
import com.g1.sketchbook.readmode.curl.CurlPhase
import com.g1.sketchbook.readmode.curl.CurlState
import com.g1.sketchbook.readmode.curl.PageCamera
import com.g1.sketchbook.readmode.curl.PageMesh
import com.g1.sketchbook.readmode.curl.ShadowStrip
import com.g1.sketchbook.readmode.curl.ShaderSources
import com.g1.sketchbook.readmode.curl.TextureLoader
import com.g1.sketchbook.readmode.curl.math.Vec2
import com.g1.sketchbook.readmode.curl.math.clamp
import java.nio.Buffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** The bitmaps needed to render one spread — supplied fresh every time [ReadModeRenderer.setSpread]
 *  is called (spread change). [staticLeft] is null in portrait (single-page) mode. [turningBack] is
 *  the sketchbook's paper texture: pages here are single-sided digital drawings, not physical
 *  double-sided sheets, so the back of a turning leaf just shows blank paper rather than another
 *  page's content. */
class SpreadTextures(
    val turningFront: Bitmap,
    val turningBack: Bitmap,
    val nextRight: Bitmap,
    val staticLeft: Bitmap?,
)

/** Adapted from PageCurlDemo's `PageCurlRenderer`. Where the demo hardcoded two asset textures and
 *  a fixed single-page aspect, this renders whatever `SpreadTextures` it's last given, at either a
 *  single-page aspect (portrait) or a two-page-wide aspect (landscape) — the curl math itself
 *  (`CurlGeometry`/`PageMesh`/`PageCamera`) already takes width/height as parameters, so neither
 *  needed to change to support both. Only the *right* page curls; a landscape spread's left page is
 *  a second, static (non-deforming) quad. */
class ReadModeRenderer : GLSurfaceView.Renderer {
    private val geometry = CurlGeometry()
    private val animator = CurlAnimator()
    private val pageMesh = PageMesh()
    private val nextPageMesh = PageMesh(1, 1)
    private val staticLeftMesh = PageMesh(1, 1)
    private val shadowStrip = ShadowStrip.create()
    private val camera = PageCamera()
    private val projection = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvp = FloatArray(16)
    private val rightMvp = FloatArray(16)
    private val leftMvp = FloatArray(16)

    private var state = CurlState()
    private var program = 0
    private var shadowProgram = 0
    private var pageGpu: MeshGpu? = null
    private var nextPageGpu: MeshGpu? = null
    private var staticLeftGpu: MeshGpu? = null
    private var shadowGpu: ShadowGpu? = null
    private var turningFrontTexture = 0
    private var turningBackTexture = 0
    private var nextRightTexture = 0
    private var staticLeftTexture = 0
    private var mvpLocation = -1
    private var frontTextureLocation = -1
    private var backTextureLocation = -1
    private var staticPageLocation = -1
    private var shadowMvpLocation = -1

    private var pageWidth = PORTRAIT_WIDTH
    private var pageHeight = PORTRAIT_HEIGHT
    private var landscape = false
    private var hasStaticLeft = false
    private var pendingSpread: SpreadTextures? = null
    private var lastSpread: SpreadTextures? = null
    private var lastSurfaceWidth = 0
    private var lastSurfaceHeight = 0

    /** GL-thread only — wrap calls in `queueEvent` from the view (see `ReadModeSurface`). Queues the
     *  new spread's bitmaps for upload on the next draw and resets any in-flight curl state. */
    fun setSpread(textures: SpreadTextures, landscape: Boolean) {
        this.landscape = landscape
        hasStaticLeft = textures.staticLeft != null
        pageWidth = if (landscape) LANDSCAPE_SPREAD_WIDTH else PORTRAIT_WIDTH
        pageHeight = PORTRAIT_HEIGHT
        pendingSpread = textures
        lastSpread = textures
        state = CurlState()
        animator.cancel(state)
        if (lastSurfaceWidth > 0 && lastSurfaceHeight > 0) recomputeCamera(lastSurfaceWidth, lastSurfaceHeight)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        releaseGlObjects()
        program = createProgram(ShaderSources.PAGE_VERTEX, ShaderSources.PAGE_FRAGMENT)
        shadowProgram = createProgram(ShaderSources.SHADOW_VERTEX, ShaderSources.SHADOW_FRAGMENT)
        mvpLocation = GLES30.glGetUniformLocation(program, "uMvp")
        frontTextureLocation = GLES30.glGetUniformLocation(program, "uFrontTexture")
        backTextureLocation = GLES30.glGetUniformLocation(program, "uBackTexture")
        staticPageLocation = GLES30.glGetUniformLocation(program, "uStaticPage")
        shadowMvpLocation = GLES30.glGetUniformLocation(shadowProgram, "uMvp")

        pageGpu = MeshGpu(pageMesh, dynamic = true)
        nextPageGpu = MeshGpu(nextPageMesh, dynamic = false)
        staticLeftGpu = MeshGpu(staticLeftMesh, dynamic = false)
        shadowGpu = ShadowGpu(shadowStrip)

        GLES30.glClearColor(0.89f, 0.86f, 0.79f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        pendingSpread = lastSpread
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        lastSurfaceWidth = width
        lastSurfaceHeight = height
        recomputeCamera(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingSpread?.let { uploadSpread(it); pendingSpread = null }
        if (animator.isRunning) state = animator.sample(System.nanoTime())
        if (state.drawsTurningPage) {
            geometry.deform(pageMesh, state.dragPosition, rightPageWidth(), pageHeight)
            geometry.updateShadow(shadowStrip, geometry.parameters(state.dragPosition), rightPageWidth(), pageHeight)
            pageGpu?.updateDynamic(pageMesh)
            shadowGpu?.update(shadowStrip)
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)

        if (hasStaticLeft) {
            GLES30.glUniformMatrix4fv(mvpLocation, 1, false, leftMvp, 0)
            bindTextures(staticLeftTexture, staticLeftTexture)
            GLES30.glUniform1f(staticPageLocation, 1f)
            staticLeftGpu?.draw(staticLeftMesh.indexCount)
        }

        GLES30.glUniformMatrix4fv(mvpLocation, 1, false, rightMvp, 0)
        bindTextures(nextRightTexture, nextRightTexture)
        GLES30.glUniform1f(staticPageLocation, 1f)
        nextPageGpu?.draw(nextPageMesh.indexCount)

        if (state.drawsTurningPage) {
            GLES30.glUseProgram(shadowProgram)
            GLES30.glUniformMatrix4fv(shadowMvpLocation, 1, false, rightMvp, 0)
            shadowGpu?.draw(shadowStrip.indexCount)

            GLES30.glUseProgram(program)
            GLES30.glUniformMatrix4fv(mvpLocation, 1, false, rightMvp, 0)
            bindTextures(turningFrontTexture, turningBackTexture)
            GLES30.glUniform1f(staticPageLocation, 0f)
            pageGpu?.draw(pageMesh.indexCount)
        }

        GLES30.glBindVertexArray(0)
        GLES30.glUseProgram(0)
    }

    fun onDragStart(position: Vec2) {
        animator.cancel()
        state = CurlState.at(CurlPhase.Dragging, sanitize(position))
    }

    fun onDrag(position: Vec2) {
        if (state.phase == CurlPhase.Dragging) state = CurlState.at(CurlPhase.Dragging, sanitize(position))
    }

    /** True once the settle animation lands on [CurlPhase.Completed] — `ReadModeSurface` polls this
     *  every frame while settling so it can tell the Compose layer to advance the spread index. */
    val didCompleteTurn: Boolean get() = state.phase == CurlPhase.Completed

    fun onDragEnd(complete: Boolean) {
        if (state.phase != CurlPhase.Dragging) return
        val phase = if (complete) CurlPhase.SettlingToNext else CurlPhase.SettlingToOrigin
        val target = if (complete) CurlState.completionTarget(state.dragPosition.y) else Vec2(1f, 0.5f)
        val duration = if (complete) COMPLETE_DURATION_NANOS else CANCEL_DURATION_NANOS
        animator.start(state.dragPosition, target, System.nanoTime(), duration, phase)
        state = CurlState.at(phase, state.dragPosition)
    }

    fun cancelDrag() = onDragEnd(complete = false)

    private fun rightPageWidth(): Float = if (landscape) pageWidth / 2f else pageWidth

    private fun recomputeCamera(width: Int, height: Int) {
        val viewAspect = width.toFloat() / height.coerceAtLeast(1)
        val cameraDistance = camera.distanceFor(pageWidth, pageHeight, viewAspect)
        Matrix.perspectiveM(projection, 0, camera.verticalFieldOfViewDegrees, viewAspect, camera.nearPlane, camera.farPlane)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, cameraDistance, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewMatrix, 0)

        val model = FloatArray(16)
        val rightOffsetX = if (hasStaticLeft) rightPageWidth() / 2f else 0f
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, rightOffsetX, 0f, 0f)
        Matrix.multiplyMM(rightMvp, 0, mvp, 0, model, 0)
        if (hasStaticLeft) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, -rightPageWidth() / 2f, 0f, 0f)
            Matrix.multiplyMM(leftMvp, 0, mvp, 0, model, 0)
        }
    }

    private fun uploadSpread(textures: SpreadTextures) {
        val rightWidth = rightPageWidth()

        pageMesh.resetFlat(rightWidth, pageHeight)
        pageMesh.uploadMutableAttributes()

        nextPageMesh.resetFlat(rightWidth, pageHeight)
        for (vertex in 0 until nextPageMesh.vertexCount) nextPageMesh.positions[vertex * 3 + 2] = NEXT_PAGE_DEPTH
        nextPageMesh.uploadMutableAttributes()
        nextPageGpu?.updateDynamic(nextPageMesh)

        if (hasStaticLeft) {
            staticLeftMesh.resetFlat(rightWidth, pageHeight)
            staticLeftMesh.uploadMutableAttributes()
            staticLeftGpu?.updateDynamic(staticLeftMesh)
        }

        turningFrontTexture = replaceTexture(turningFrontTexture, textures.turningFront)
        turningBackTexture = replaceTexture(turningBackTexture, textures.turningBack)
        nextRightTexture = replaceTexture(nextRightTexture, textures.nextRight)
        textures.staticLeft?.let { staticLeftTexture = replaceTexture(staticLeftTexture, it) }
    }

    private fun replaceTexture(existing: Int, bitmap: Bitmap): Int =
        if (existing == 0) TextureLoader.loadBitmap(bitmap) else { TextureLoader.updateBitmap(existing, bitmap); existing }

    private fun sanitize(position: Vec2): Vec2 = Vec2(clamp(position.x, -0.15f, 1f), clamp(position.y))

    private fun bindTextures(front: Int, back: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, front)
        GLES30.glUniform1i(frontTextureLocation, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, back)
        GLES30.glUniform1i(backTextureLocation, 1)
    }

    private fun releaseGlObjects() {
        pageGpu?.release(); nextPageGpu?.release(); staticLeftGpu?.release(); shadowGpu?.release()
        pageGpu = null; nextPageGpu = null; staticLeftGpu = null; shadowGpu = null
        if (program != 0) GLES30.glDeleteProgram(program)
        if (shadowProgram != 0) GLES30.glDeleteProgram(shadowProgram)
        TextureLoader.release(turningFrontTexture)
        TextureLoader.release(turningBackTexture)
        TextureLoader.release(nextRightTexture)
        TextureLoader.release(staticLeftTexture)
        program = 0; shadowProgram = 0
        turningFrontTexture = 0; turningBackTexture = 0; nextRightTexture = 0; staticLeftTexture = 0
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource, "page vertex")
        val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource, "page fragment")
        val result = GLES30.glCreateProgram()
        GLES30.glAttachShader(result, vertex)
        GLES30.glAttachShader(result, fragment)
        GLES30.glLinkProgram(result)
        val linked = IntArray(1)
        GLES30.glGetProgramiv(result, GLES30.GL_LINK_STATUS, linked, 0)
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        if (linked[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(result)
            GLES30.glDeleteProgram(result)
            error("Unable to link page shader program: $log")
        }
        return result
    }

    private fun compileShader(type: Int, source: String, label: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Unable to compile $label shader: $log")
        }
        return shader
    }

    private class MeshGpu(mesh: PageMesh, dynamic: Boolean) {
        private val vertexArray = IntArray(1)
        private val buffers = IntArray(5)
        private val usage = if (dynamic) GLES30.GL_DYNAMIC_DRAW else GLES30.GL_STATIC_DRAW

        init {
            GLES30.glGenVertexArrays(1, vertexArray, 0)
            GLES30.glGenBuffers(buffers.size, buffers, 0)
            GLES30.glBindVertexArray(vertexArray[0])
            uploadAttribute(0, 3, buffers[0], mesh.positionBuffer, mesh.positions.size * Float.SIZE_BYTES)
            uploadAttribute(1, 2, buffers[1], mesh.uvBuffer, mesh.uvs.size * Float.SIZE_BYTES)
            uploadAttribute(2, 1, buffers[2], mesh.shadeBuffer, mesh.shade.size * Float.SIZE_BYTES)
            uploadAttribute(3, 1, buffers[3], mesh.sideBuffer, mesh.side.size * Float.SIZE_BYTES)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[4])
            GLES30.glBufferData(
                GLES30.GL_ELEMENT_ARRAY_BUFFER,
                mesh.indices.size * Int.SIZE_BYTES,
                mesh.indexBuffer,
                GLES30.GL_STATIC_DRAW,
            )
            GLES30.glBindVertexArray(0)
        }

        fun updateDynamic(mesh: PageMesh) {
            updateBuffer(buffers[0], mesh.positionBuffer, mesh.positions.size * Float.SIZE_BYTES)
            updateBuffer(buffers[2], mesh.shadeBuffer, mesh.shade.size * Float.SIZE_BYTES)
            updateBuffer(buffers[3], mesh.sideBuffer, mesh.side.size * Float.SIZE_BYTES)
        }

        fun draw(indexCount: Int) {
            GLES30.glBindVertexArray(vertexArray[0])
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
        }

        fun release() {
            GLES30.glDeleteBuffers(buffers.size, buffers, 0)
            GLES30.glDeleteVertexArrays(1, vertexArray, 0)
        }

        private fun uploadAttribute(
            location: Int,
            components: Int,
            bufferId: Int,
            data: Buffer,
            bytes: Int,
        ) {
            data.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufferId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, bytes, data, usage)
            GLES30.glEnableVertexAttribArray(location)
            GLES30.glVertexAttribPointer(location, components, GLES30.GL_FLOAT, false, 0, 0)
        }

        private fun updateBuffer(bufferId: Int, data: Buffer, bytes: Int) {
            data.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufferId)
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, bytes, data)
        }
    }

    private class ShadowGpu(strip: ShadowStrip) {
        private val vertexArray = IntArray(1)
        private val buffers = IntArray(3)

        init {
            GLES30.glGenVertexArrays(1, vertexArray, 0)
            GLES30.glGenBuffers(buffers.size, buffers, 0)
            GLES30.glBindVertexArray(vertexArray[0])

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                strip.positions.size * Float.SIZE_BYTES,
                strip.positionBuffer,
                GLES30.GL_DYNAMIC_DRAW,
            )
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[1])
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                strip.alpha.size * Float.SIZE_BYTES,
                strip.alphaBuffer,
                GLES30.GL_DYNAMIC_DRAW,
            )
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, 0, 0)

            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[2])
            GLES30.glBufferData(
                GLES30.GL_ELEMENT_ARRAY_BUFFER,
                strip.indices.size * Int.SIZE_BYTES,
                strip.indexBuffer,
                GLES30.GL_STATIC_DRAW,
            )
            GLES30.glBindVertexArray(0)
        }

        fun update(strip: ShadowStrip) {
            strip.positionBuffer.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
            GLES30.glBufferSubData(
                GLES30.GL_ARRAY_BUFFER,
                0,
                strip.positions.size * Float.SIZE_BYTES,
                strip.positionBuffer,
            )
            strip.alphaBuffer.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[1])
            GLES30.glBufferSubData(
                GLES30.GL_ARRAY_BUFFER,
                0,
                strip.alpha.size * Float.SIZE_BYTES,
                strip.alphaBuffer,
            )
        }

        fun draw(indexCount: Int) {
            GLES30.glBindVertexArray(vertexArray[0])
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
        }

        fun release() {
            GLES30.glDeleteBuffers(buffers.size, buffers, 0)
            GLES30.glDeleteVertexArrays(1, vertexArray, 0)
        }
    }

    private companion object {
        const val PORTRAIT_WIDTH = 2f
        const val PORTRAIT_HEIGHT = 8f / 3f
        const val LANDSCAPE_SPREAD_WIDTH = PORTRAIT_WIDTH * 2f
        const val NEXT_PAGE_DEPTH = -0.03f
        const val COMPLETE_DURATION_NANOS = 280_000_000L
        const val CANCEL_DURATION_NANOS = 220_000_000L
    }
}
