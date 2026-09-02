package com.g1.sketchbook.sketchbook

import com.g1.sketchbook.vector.VectorDocument
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-owned, per-book write queue. It outlives a composable and never lets two document
 * commits write the same v2 temporary file at once. */
class VectorDocumentPersistenceCoordinator(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queueLock = Any()
    private var tail: Deferred<Result<Unit>>? = null
    private val _lastFailure = MutableStateFlow<Throwable?>(null)
    val lastFailure: StateFlow<Throwable?> = _lastFailure.asStateFlow()

    fun enqueue(document: VectorDocument, writeDurably: suspend (VectorDocument) -> Unit) {
        synchronized(queueLock) {
            val previous = tail
            tail = scope.async {
                previous?.await()
                runCatching { writeDurably(document) }.also { result ->
                    _lastFailure.value = result.exceptionOrNull()
                }
            }
        }
    }

    /** Waits for the latest queued local write; callers can surface the returned failure. */
    suspend fun flush(): Result<Unit> = synchronized(queueLock) { tail }?.await() ?: Result.success(Unit)
}

/** Registry ownership is process-level rather than tied to any one `VectorCanvasScreen` instance. */
object VectorDocumentPersistenceRegistry {
    private val coordinators = ConcurrentHashMap<String, VectorDocumentPersistenceCoordinator>()
    fun forBook(bookId: String): VectorDocumentPersistenceCoordinator =
        coordinators.computeIfAbsent(bookId) { VectorDocumentPersistenceCoordinator() }
}
