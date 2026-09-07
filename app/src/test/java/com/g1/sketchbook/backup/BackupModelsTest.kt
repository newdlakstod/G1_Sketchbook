package com.g1.sketchbook.backup

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupModelsTest {
    @Test fun bothMissingIsNoop() {
        assertEquals(SyncAction.NOOP, decideSyncAction(null, null))
    }

    @Test fun onlyRemoteExistsPulls() {
        assertEquals(SyncAction.PULL, decideSyncAction(null, 100L))
    }

    @Test fun onlyLocalExistsPushes() {
        assertEquals(SyncAction.PUSH, decideSyncAction(100L, null))
    }

    @Test fun remoteNewerPulls() {
        assertEquals(SyncAction.PULL, decideSyncAction(100L, 200L))
    }

    @Test fun localNewerPushes() {
        assertEquals(SyncAction.PUSH, decideSyncAction(200L, 100L))
    }

    @Test fun tieMeansAlreadySynced() {
        assertEquals(SyncAction.NOOP, decideSyncAction(100L, 100L))
    }

    @Test fun remoteTombstoneWithLocalCopyDeletesLocal() {
        assertEquals(SyncAction.DELETE_LOCAL, decideSyncAction(100L, 999L, remoteDeleted = true))
    }

    @Test fun remoteTombstoneWithNoLocalCopyIsNoop() {
        assertEquals(SyncAction.NOOP, decideSyncAction(null, 999L, remoteDeleted = true))
    }

    @Test fun unsafeRecoveryRollbackRestoresAnOlderRemoteCopyForALockedPastDiary() {
        assertEquals(
            SyncAction.PULL,
            decideDiarySyncAction(
                localUpdatedAt = 200L,
                remoteUpdatedAt = 100L,
                rollbackUnsafeRecovery = true,
                isLockedPastDate = true,
            ),
        )
    }

    @Test fun unsafeRecoveryRollbackNeverDiscardsALocalDiaryWhenNoRemoteCopyExists() {
        assertEquals(
            SyncAction.PUSH,
            decideDiarySyncAction(
                localUpdatedAt = 200L,
                remoteUpdatedAt = null,
                rollbackUnsafeRecovery = true,
                isLockedPastDate = true,
            ),
        )
    }

    @Test fun unsafeRecoveryRollbackDoesNotOverrideTodaysNewerDrawing() {
        assertEquals(
            SyncAction.PUSH,
            decideDiarySyncAction(
                localUpdatedAt = 200L,
                remoteUpdatedAt = 100L,
                rollbackUnsafeRecovery = true,
                isLockedPastDate = false,
            ),
        )
    }

    @Test fun normalDiarySyncResumesAfterTheOneTimeRollback() {
        assertEquals(
            SyncAction.PUSH,
            decideDiarySyncAction(
                localUpdatedAt = 200L,
                remoteUpdatedAt = 100L,
                rollbackUnsafeRecovery = false,
                isLockedPastDate = true,
            ),
        )
    }
}
