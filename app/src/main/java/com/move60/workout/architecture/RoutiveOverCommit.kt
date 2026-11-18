package com.move60.workout.architecture

import android.content.Context
import com.move60.workout.SetRecordStore
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One lock for every writer of an over ROUTIVE_4 session (the session screen, the tracking service, the watch command handler
 * and the app's foreground sync). They all go through [RoutiveOverCommit], so two of them can never run the buffer -> commit ->
 * confirm sequence at the same moment. The commit itself is idempotent by session id, so even a late second run only finds
 * "already committed".
 */
object RoutiveCommitLock { val mutex = Mutex() }

/**
 * Saves an ended ROUTIVE_4 session through the EXISTING path: copy it to the durable [PendingSessionOutbox] first, drain the
 * outbox through the idempotent [TimerSessionSync]/[SessionCommitter] (which also refreshes challenge credit), and only a
 * confirmed outcome marks the session as committed. Nothing here depends on the session screen being open.
 */
class RoutiveOverCommit(
    private val store: RoutiveSessionStore,
    private val outbox: PendingSessionOutbox,
    private val sync: TimerSessionSync,
    private val sets: SetRecordStore?
) {
    constructor(context: Context, committer: SessionCommitter, zoneId: () -> String = { ZoneId.systemDefault().id }) :
        this(RoutiveSessionStore(context), PendingSessionOutbox(context), TimerSessionSync(committer, zoneId), SetRecordStore(context))

    /** Commits [session] (used by the session screen, which shows the outcome). Safe to repeat. */
    suspend fun commit(session: RoutiveSession): RoutiveOutcome = RoutiveCommitLock.mutex.withLock { commitLocked(session) }

    /**
     * Commits the stored session when it is over and its commit was never confirmed; also retries every other buffered session.
     * Null = there was no unconfirmed over session (the outbox is still drained).
     */
    suspend fun commitPending(): RoutiveOutcome? = RoutiveCommitLock.mutex.withLock {
        val pending = store.pendingCommit()
        if (pending == null) { sync.drain(outbox); null } else commitLocked(pending)
    }

    private suspend fun commitLocked(session: RoutiveSession): RoutiveOutcome {
        val snap = store.snapshot(session, sets)
        val id = sync.sessionId(snap)
        outbox.enqueue(snap)
        val outcomes = sync.drain(outbox)
        val outcome = when {
            outcomes.any { (it is SyncOutcome.Committed && it.sessionId == id) || (it is SyncOutcome.AlreadyCommitted && it.sessionId == id) } -> RoutiveOutcome.ResultReady(id)
            outcomes.any { it is SyncOutcome.Discarded && it.sessionId == id } -> RoutiveOutcome.NotRecorded
            outbox.pending().none { sync.sessionId(it) == id } -> RoutiveOutcome.NotRecorded
            else -> RoutiveOutcome.RetryLater
        }
        if (outcome != RoutiveOutcome.RetryLater) store.markCommitted(session.startedAtEpochMs)
        return outcome
    }
}
