package com.g1.sketchbook.sketchbook

import com.g1.sketchbook.vector.VectorDocument
import com.g1.sketchbook.vector.editablePath
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VectorDocumentPersistenceCoordinatorTest {
    @Test fun rapidCommitsAreWrittenInSubmissionOrderAndFlushesTheLatestDocument() = runBlocking {
        val coordinator = VectorDocumentPersistenceCoordinator()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val written = mutableListOf<String>()
        val first = VectorDocument(objects = listOf(editablePath("first")))
        val second = VectorDocument(objects = listOf(editablePath("second")))

        coordinator.enqueue(first) {
            firstStarted.countDown()
            check(releaseFirst.await(2, TimeUnit.SECONDS))
            written += it.objects.single().id
        }
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
        coordinator.enqueue(second) { written += it.objects.single().id }
        releaseFirst.countDown()

        assertTrue(coordinator.flush().isSuccess)
        assertEquals(listOf("first", "second"), written)
    }

    @Test fun failedDurableWriteIsReturnedByFlushAndExposedToTheCaller() = runBlocking {
        val coordinator = VectorDocumentPersistenceCoordinator()
        val failure = IllegalStateException("disk unavailable")

        coordinator.enqueue(VectorDocument(objects = listOf(editablePath("failed")))) { throw failure }

        val result = coordinator.flush()
        assertFalse(result.isSuccess)
        assertEquals(failure, result.exceptionOrNull())
        assertEquals(failure, coordinator.lastFailure.value)

        coordinator.enqueue(VectorDocument(objects = listOf(editablePath("recovered")))) { }
        assertTrue(coordinator.flush().isSuccess)
        assertEquals(null, coordinator.lastFailure.value)
    }
}
