package com.gdo.pagecurl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.gdo.pagecurl.math.Vec2
import com.gdo.pagecurl.math.clamp
import java.nio.Buffer
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal data class PagePixelSize(val width: Int, val height: Int)

internal fun requestedPagePixelSize(
    viewportWidth: Int,
    viewportHeight: Int,
    layoutMode: PageLayoutMode,
    maxTextureSize: Int,
    pageAspectRatio: Float = 3f / 4f,
): PagePixelSize {
    require(viewportWidth > 0 && viewportHeight > 0)
    require(maxTextureSize > 0)
    requireSupportedPageAspectRatio(pageAspectRatio)
    val aspect = pageAspectRatio.toDouble()
    val availableWidth = viewportWidth.toDouble() /
        if (layoutMode == PageLayoutMode.TwoPageSpread) 2.0 else 1.0
    val height = minOf(
        viewportHeight.toDouble(),
        availableWidth / aspect,
        maxTextureSize.toDouble(),
        maxTextureSize / aspect,
    )
    return PagePixelSize(
        width = (height * aspect).roundToInt().coerceAtLeast(1),
        height = height.roundToInt().coerceAtLeast(1),
    )
}

internal fun pageWorldHeight(pageWidth: Float, pageAspectRatio: Float): Float {
    require(pageWidth > 0f)
    requireSupportedPageAspectRatio(pageAspectRatio)
    return pageWidth / pageAspectRatio
}

internal fun canAdmitDragStart(
    rendererReady: Boolean,
    animatorRunning: Boolean,
    curlPhase: CurlPhase,
    bookState: PageBookState,
    layoutMode: PageLayoutMode,
    direction: PageTurnDirection,
): Boolean = rendererReady &&
    !animatorRunning &&
    curlPhase == CurlPhase.Idle &&
    bookState.canTurn(layoutMode, direction)

internal fun canOwnDragStart(
    rendererReady: Boolean,
    admissionOpen: Boolean,
    bookState: PageBookState,
    layoutMode: PageLayoutMode,
    direction: PageTurnDirection,
): Boolean = rendererReady && admissionOpen && bookState.canTurn(layoutMode, direction)

internal data class RendererStateReplacement(
    val bookState: PageBookState,
    val curlState: CurlState,
)

internal fun documentReplacement(
    currentState: CurlState,
    requestedPageIndex: Int,
    pageCount: Int,
): RendererStateReplacement = RendererStateReplacement(
    bookState = PageBookState(requestedPageIndex, pageCount),
    curlState = currentState.discardTransientCurl(),
)

internal fun glContextRecreation(
    currentState: CurlState,
    committedBookState: PageBookState,
): RendererStateReplacement = RendererStateReplacement(
    bookState = committedBookState,
    curlState = currentState.discardTransientCurl(),
)

private fun CurlState.discardTransientCurl(): CurlState =
    if (phase == CurlPhase.Idle) this else CurlState()

internal class PageCurlRenderer(
    initialSource: PageCurlBitmapSource,
    initialFocusedPageIndex: Int,
    initialLayoutMode: PageLayoutMode,
    initialDocumentGeneration: Long,
    private val onFocusedPageCommitted: (Int, Long) -> Unit,
    private val onError: (Throwable, Long) -> Unit,
) : GLSurfaceView.Renderer {
    private val geometry = CurlGeometry()
    private val animator = CurlAnimator()
    private val pageMesh = PageMesh()
    private val staticPageMesh = PageMesh(1, 1)
    private val camera = PageCamera()
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)

    private var state = CurlState()
    private var program = 0
    private var pageGpu: MeshGpu? = null
    private var staticPageGpu: MeshGpu? = null
    private var source = initialSource
    private var pageAspectRatio = validatedPageAspectRatio(initialSource)
    private var textureCache: TextureCache<Int>? = null
    private val failedPageIndices = mutableSetOf<Int>()
    private var blankTexture = 0
    private var maxTextureSize = 0
    private var pagePixelSize: PagePixelSize? = null
    private var documentGeneration = initialDocumentGeneration
    @Volatile private var bookState = PageBookState(initialFocusedPageIndex, initialSource.pageCount)
    @Volatile private var layoutMode = initialLayoutMode
    @Volatile private var dragStartAdmissionOpen = true
    @Volatile private var rendererReady = false
    private var activeDirection = bookState.preferredDirection(layoutMode)
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var mvpLocation = -1
    private var frontTextureLocation = -1
    private var backTextureLocation = -1
    private var staticPageLocation = -1
    private var pageOffsetLocation = -1
    private var blankPageLocation = -1
    private var gutterSideLocation = -1
    private var gutterWidthLocation = -1
    private var gutterOpacityLocation = -1
    private var shadowAxisPointLocation = -1
    private var shadowNormalLocation = -1
    private var shadowWidthLocation = -1
    private var shadowOpacityLocation = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val replacement = glContextRecreation(state, bookState)
        state = replacement.curlState
        bookState = replacement.bookState
        animator.cancel(state)
        activeDirection = bookState.preferredDirection(layoutMode)
        dragStartAdmissionOpen = true
        releaseGlObjects()
        val textureLimit = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, textureLimit, 0)
        check(textureLimit[0] > 0) { "Unable to query GL texture limit" }
        maxTextureSize = textureLimit[0]
        blankTexture = createBlankTexture()
        textureCache = TextureCache(maxEntries = 5, onEvict = ::deleteTexture)
        program = createProgram(ShaderSources.PAGE_VERTEX, ShaderSources.PAGE_FRAGMENT)
        mvpLocation = GLES30.glGetUniformLocation(program, "uMvp")
        frontTextureLocation = GLES30.glGetUniformLocation(program, "uFrontTexture")
        backTextureLocation = GLES30.glGetUniformLocation(program, "uBackTexture")
        staticPageLocation = GLES30.glGetUniformLocation(program, "uStaticPage")
        pageOffsetLocation = GLES30.glGetUniformLocation(program, "uPageOffsetX")
        blankPageLocation = GLES30.glGetUniformLocation(program, "uBlankPage")
        gutterSideLocation = GLES30.glGetUniformLocation(program, "uGutterSide")
        gutterWidthLocation = GLES30.glGetUniformLocation(program, "uGutterWidth")
        gutterOpacityLocation = GLES30.glGetUniformLocation(program, "uGutterOpacity")
        shadowAxisPointLocation = GLES30.glGetUniformLocation(program, "uShadowAxisPoint")
        shadowNormalLocation = GLES30.glGetUniformLocation(program, "uShadowNormal")
        shadowWidthLocation = GLES30.glGetUniformLocation(program, "uShadowWidth")
        shadowOpacityLocation = GLES30.glGetUniformLocation(program, "uShadowOpacity")
        pageGpu = MeshGpu(pageMesh, dynamic = true)
        resetStaticPageMesh()
        staticPageGpu = MeshGpu(staticPageMesh, dynamic = false)

        GLES30.glUseProgram(program)
        GLES30.glUniform1f(gutterWidthLocation, GUTTER_WIDTH)
        GLES30.glUniform1f(gutterOpacityLocation, GUTTER_OPACITY)
        GLES30.glUseProgram(0)

        GLES30.glClearColor(0.89f, 0.86f, 0.79f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        updatePagePixelSize()
        updateCamera()
        rendererReady = width > 0 &&
            height > 0 &&
            maxTextureSize > 0 &&
            program != 0 &&
            blankTexture != 0 &&
            textureCache != null &&
            pagePixelSize != null &&
            pageGpu != null &&
            staticPageGpu != null
    }

    private fun updatePagePixelSize() {
        if (viewportWidth <= 0 || viewportHeight <= 0 || maxTextureSize <= 0) return
        val requested = requestedPagePixelSize(
            viewportWidth,
            viewportHeight,
            layoutMode,
            maxTextureSize,
            pageAspectRatio,
        )
        if (requested == pagePixelSize) return
        pagePixelSize = requested
        textureCache?.clear()
        failedPageIndices.clear()
    }

    private fun updateCamera() {
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        val viewAspect = viewportWidth.toFloat() / viewportHeight
        val cameraDistance = camera.distanceFor(
            layoutMode.frameWidth(PAGE_WIDTH),
            pageHeight,
            viewAspect,
        )
        Matrix.perspectiveM(
            projection,
            0,
            camera.verticalFieldOfViewDegrees,
            viewAspect,
            camera.nearPlane,
            camera.farPlane,
        )
        Matrix.setLookAtM(
            view,
            0,
            0f,
            0f,
            cameraDistance,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
        )
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (animator.isRunning) {
            val sampled = animator.sample(System.nanoTime())
            if (sampled.phase == CurlPhase.Completed) {
                val previousFocus = bookState.focusedPageIndex
                bookState = bookState.afterSettling(layoutMode, activeDirection, sampled.phase)
                if (bookState.focusedPageIndex != previousFocus) {
                    onFocusedPageCommitted(bookState.focusedPageIndex, documentGeneration)
                }
                activeDirection = bookState.preferredDirection(layoutMode)
                state = CurlState()
                dragStartAdmissionOpen = true
            } else {
                state = sampled
                if (state.phase == CurlPhase.Idle) dragStartAdmissionOpen = true
            }
        }

        val localShadow = if (state.drawsTurningPage) {
            underPageShadowFor(
                state,
                geometry.parameters(state.dragPosition, state.grabAnchorY),
                activeDirection,
                PAGE_WIDTH,
                pageHeight,
            )
        } else {
            UnderPageShadow.Disabled
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)

        if (!state.drawsTurningPage) {
            val stable = bookState.stableSelection(layoutMode)
            drawStatic(stable.firstStatic, UnderPageShadow.Disabled)
            stable.secondStatic?.let { drawStatic(it, UnderPageShadow.Disabled) }
        } else {
            val transition = bookState.turnSelection(layoutMode, activeDirection)
            geometry.deform(
                pageMesh,
                state.dragPosition,
                PAGE_WIDTH,
                pageHeight,
                activeDirection,
                state.grabAnchorY,
            )
            pageGpu?.updateDynamic(pageMesh)
            val worldShadow = localShadow.offsetX(transition.turningSlot.offsetX)
            drawStatic(transition.firstStatic, worldShadow)
            transition.secondStatic?.let { drawStatic(it, worldShadow) }
            bindTextures(textureFor(transition.front), textureFor(transition.back))
            bindShadow(UnderPageShadow.Disabled)
            GLES30.glUniform1f(staticPageLocation, 0f)
            GLES30.glUniform1f(pageOffsetLocation, transition.turningSlot.offsetX)
            GLES30.glUniform1f(blankPageLocation, 0f)
            GLES30.glUniform1f(gutterSideLocation, 0f)
            pageGpu?.draw(pageMesh.indexCount)
        }

        GLES30.glBindVertexArray(0)
        GLES30.glUseProgram(0)
    }

    fun canTurn(direction: PageTurnDirection): Boolean = bookState.canTurn(layoutMode, direction)

    fun canStartDrag(direction: PageTurnDirection): Boolean =
        canOwnDragStart(rendererReady, dragStartAdmissionOpen, bookState, layoutMode, direction)

    fun setDocument(
        newSource: PageCurlBitmapSource,
        pageIndex: Int,
        newLayoutMode: PageLayoutMode,
        newDocumentGeneration: Long,
    ) {
        require(newSource.pageCount >= 1) { "PageCurl requires at least one page" }
        val newPageAspectRatio = validatedPageAspectRatio(newSource)
        if (newSource === source &&
            pageIndex == bookState.focusedPageIndex &&
            newLayoutMode == layoutMode &&
            newDocumentGeneration == documentGeneration
        ) return
        val replacement = documentReplacement(state, pageIndex, newSource.pageCount)
        state = replacement.curlState
        animator.cancel(state)
        val sourceChanged = newSource !== source
        val aspectChanged = newPageAspectRatio != pageAspectRatio
        if (sourceChanged) {
            source = newSource
            textureCache?.clear()
            failedPageIndices.clear()
        }
        pageAspectRatio = newPageAspectRatio
        bookState = replacement.bookState
        documentGeneration = newDocumentGeneration
        val layoutChanged = newLayoutMode != layoutMode
        layoutMode = newLayoutMode
        activeDirection = bookState.preferredDirection(layoutMode)
        if (aspectChanged) resetStaticPageMesh()
        if (layoutChanged || aspectChanged) {
            updatePagePixelSize()
            updateCamera()
        }
        dragStartAdmissionOpen = true
    }

    fun onDragStart(position: Vec2, direction: PageTurnDirection) {
        if (!canAdmitDragStart(rendererReady, animator.isRunning, state.phase, bookState, layoutMode, direction)) return
        dragStartAdmissionOpen = false
        activeDirection = direction
        bookState.turnSelection(layoutMode, direction)
            .requiredPageIndices()
            .forEach { textureFor(PageContent.page(it)) }
        state = CurlState.at(CurlPhase.Dragging, sanitize(position))
    }

    fun onDrag(position: Vec2) {
        if (state.phase == CurlPhase.Dragging) {
            state = state.withDragPosition(sanitize(position))
        }
    }

    fun onDragEnd(complete: Boolean) {
        if (state.phase != CurlPhase.Dragging) return
        val phase = if (complete) CurlPhase.SettlingToNext else CurlPhase.SettlingToOrigin
        val target = if (complete) {
            CurlState.completionTarget(state.dragPosition.y)
        } else {
            Vec2(1f, 0.5f)
        }
        val duration = if (complete) COMPLETE_DURATION_NANOS else CANCEL_DURATION_NANOS
        animator.start(
            from = state.dragPosition,
            to = target,
            startNanos = System.nanoTime(),
            durationNanos = duration,
            phase = phase,
            grabAnchorY = state.grabAnchorY,
        )
        state = CurlState.at(phase, state.dragPosition, state.grabAnchorY)
    }

    fun cancelDrag() {
        onDragEnd(complete = false)
    }

    fun reset() {
        val previousFocus = bookState.focusedPageIndex
        bookState = bookState.reset()
        state = CurlState()
        animator.cancel(state)
        activeDirection = bookState.preferredDirection(layoutMode)
        dragStartAdmissionOpen = true
        if (previousFocus != 0) {
            onFocusedPageCommitted(0, documentGeneration)
        }
    }

    fun release() {
        state = CurlState()
        animator.cancel(state)
        dragStartAdmissionOpen = false
        releaseGlObjects()
    }

    private fun sanitize(position: Vec2): Vec2 = Vec2(
        x = clamp(position.x, -0.15f, 1f),
        y = clamp(position.y),
    )

    private val pageHeight: Float get() = pageWorldHeight(PAGE_WIDTH, pageAspectRatio)

    private fun resetStaticPageMesh() {
        staticPageMesh.resetFlat(PAGE_WIDTH, pageHeight)
        for (vertex in 0 until staticPageMesh.vertexCount) {
            staticPageMesh.positions[vertex * 3 + 2] = STATIC_PAGE_DEPTH
        }
        staticPageMesh.uploadPositions()
        staticPageGpu?.updateDynamic(staticPageMesh)
    }

    private fun validatedPageAspectRatio(source: PageCurlBitmapSource): Float =
        requireSupportedPageAspectRatio(source.pageAspectRatio)

    private fun textureFor(content: PageContent): Int {
        val index = content.pageIndex ?: return blankTexture
        val cache = textureCache ?: return blankTexture
        val requestedSize = pagePixelSize ?: return blankTexture
        cache[index]?.let { return it }
        if (index in failedPageIndices) return blankTexture
        return runCatching {
            val bitmap = source.getPageBitmap(index, requestedSize.width, requestedSize.height)
            TextureLoader.upload(bitmap, "page $index", maxTextureSize)
        }.onSuccess { cache.put(index, it) }
            .getOrElse {
                failedPageIndices += index
                onError(it, documentGeneration)
                blankTexture
            }
    }

    private fun drawStatic(selection: StaticPageSelection, shadow: UnderPageShadow) {
        val texture = textureFor(selection.content)
        bindTextures(texture, texture)
        bindShadow(if (selection.receivesMovingShadow) shadow else UnderPageShadow.Disabled)
        GLES30.glUniform1f(staticPageLocation, 1f)
        GLES30.glUniform1f(pageOffsetLocation, selection.slot.offsetX)
        GLES30.glUniform1f(blankPageLocation, if (selection.content == PageContent.Blank) 1f else 0f)
        GLES30.glUniform1f(gutterSideLocation, selection.slot.gutterSide)
        staticPageGpu?.draw(staticPageMesh.indexCount)
    }

    private fun bindTextures(front: Int, back: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, front)
        GLES30.glUniform1i(frontTextureLocation, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, back)
        GLES30.glUniform1i(backTextureLocation, 1)
    }

    private fun bindShadow(shadow: UnderPageShadow) {
        GLES30.glUniform2f(shadowAxisPointLocation, shadow.axisPoint.x, shadow.axisPoint.y)
        GLES30.glUniform2f(shadowNormalLocation, shadow.normal.x, shadow.normal.y)
        GLES30.glUniform1f(shadowWidthLocation, shadow.width)
        GLES30.glUniform1f(shadowOpacityLocation, shadow.opacity)
    }

    private fun releaseGlObjects() {
        rendererReady = false
        pageGpu?.release()
        staticPageGpu?.release()
        pageGpu = null
        staticPageGpu = null
        if (program != 0) GLES30.glDeleteProgram(program)
        textureCache?.clear()
        textureCache = null
        deleteTexture(blankTexture)
        program = 0
        blankTexture = 0
    }

    private fun createBlankTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        check(ids[0] != 0) { "Unable to allocate blank page texture" }
        var ownsTexture = true
        try {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            val paper = ByteBuffer.allocateDirect(4)
                .put(byteArrayOf(227.toByte(), 219.toByte(), 201.toByte(), 255.toByte()))
                .apply { position(0) }
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA,
                1,
                1,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                paper,
            )
            validateTextureUpload("blank page", ids[0], GLES30.glGetError()) { texture ->
                ownsTexture = false
                deleteTexture(texture)
            }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            return ids[0]
        } catch (failure: Throwable) {
            if (ownsTexture) deleteTexture(ids[0])
            throw IllegalStateException("Failed to create blank page texture", failure)
        }
    }

    private fun deleteTexture(texture: Int) {
        if (texture != 0) GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
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
        private val buffers = IntArray(BUFFER_COUNT)
        private val usage = if (dynamic) GLES30.GL_DYNAMIC_DRAW else GLES30.GL_STATIC_DRAW

        init {
            GLES30.glGenVertexArrays(1, vertexArray, 0)
            GLES30.glGenBuffers(buffers.size, buffers, 0)
            GLES30.glBindVertexArray(vertexArray[0])
            uploadAttribute(0, 3, buffers[0], mesh.positionBuffer, mesh.positions.size * Float.SIZE_BYTES)
            uploadAttribute(1, 2, buffers[1], mesh.uvBuffer, mesh.uvs.size * Float.SIZE_BYTES)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[2])
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

        private companion object {
            const val BUFFER_COUNT = 3
        }
    }

    private companion object {
        const val PAGE_WIDTH = 2f
        const val STATIC_PAGE_DEPTH = -0.03f
        const val GUTTER_WIDTH = 0.08f
        const val GUTTER_OPACITY = 0.16f
        const val COMPLETE_DURATION_NANOS = 280_000_000L
        const val CANCEL_DURATION_NANOS = 220_000_000L
    }
}
