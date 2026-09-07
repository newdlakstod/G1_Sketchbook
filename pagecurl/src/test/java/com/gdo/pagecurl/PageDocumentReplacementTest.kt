package com.gdo.pagecurl

import com.gdo.pagecurl.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageDocumentReplacementTest {
    @Test
    fun externalPageChangeReplacesCommittedStateAndCancelsDrag() {
        val result = documentReplacement(
            currentState = CurlState.at(CurlPhase.Dragging, Vec2(0.4f, 0.2f)),
            requestedPageIndex = 3,
            pageCount = 5,
        )

        assertEquals(3, result.bookState.focusedPageIndex)
        assertEquals(CurlPhase.Idle, result.curlState.phase)
    }

    @Test(expected = IllegalArgumentException::class)
    fun externalPageMustExistInSource() {
        documentReplacement(CurlState(), requestedPageIndex = 5, pageCount = 5)
    }

    @Test
    fun glContextRecreationDiscardsTransientCurlAndPreservesCommittedPage() {
        val committedBook = PageBookState(focusedPageIndex = 2, pageCount = 5)

        val result = glContextRecreation(
            currentState = CurlState.at(CurlPhase.SettlingToNext, Vec2(0.2f, 0.8f)),
            committedBookState = committedBook,
        )

        assertEquals(committedBook, result.bookState)
        assertEquals(CurlPhase.Idle, result.curlState.phase)
    }

    @Test
    fun queuedOldSettleCommitIsRejectedAfterDocumentReplacement() {
        val coordinator = DocumentGenerationCoordinator()
        val oldSettleGeneration = coordinator.currentGeneration
        val replacementGeneration = coordinator.advance()
        val deliveredPages = mutableListOf<Int>()

        assertFalse(
            coordinator.deliverIfCurrent(oldSettleGeneration) {
                deliveredPages += 1
            },
        )
        assertTrue(
            coordinator.deliverIfCurrent(replacementGeneration) {
                deliveredPages += 3
            },
        )
        assertEquals(listOf(3), deliveredPages)
    }

    @Test
    fun queuedCommitIsRejectedAfterSurfaceRelease() {
        val coordinator = DocumentGenerationCoordinator()
        val queuedGeneration = coordinator.currentGeneration
        val deliveredPages = mutableListOf<Int>()

        coordinator.release()

        assertFalse(
            coordinator.deliverIfCurrent(queuedGeneration) {
                deliveredPages += 1
            },
        )
        assertTrue(deliveredPages.isEmpty())
    }

    @Test
    fun sourceAErrorIsRejectedAfterSourceBReplacement() {
        val coordinator = DocumentGenerationCoordinator()
        val sourceAGeneration = coordinator.currentGeneration
        coordinator.advance()
        val deliveredErrors = mutableListOf<Throwable>()
        val sourceAFailure = IllegalStateException("source A failed")

        assertFalse(
            coordinator.deliverErrorIfCurrent(sourceAGeneration, sourceAFailure) {
                deliveredErrors += it
            },
        )
        assertTrue(deliveredErrors.isEmpty())
    }
}
