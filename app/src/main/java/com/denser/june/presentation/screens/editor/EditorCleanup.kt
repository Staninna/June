package com.denser.june.presentation.screens.editor

import androidx.annotation.StringRes
import com.denser.june.core.R
import com.denser.june.core.domain.model.JournalPerson
import com.denser.june.core.domain.model.distinctPeople
import com.denser.june.core.domain.model.mentionedPersonIds
import com.denser.june.core.localai.CleanupPerson
import com.denser.june.core.localai.EditedMeanwhile
import com.denser.june.core.localai.LocalAiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The editor's side of "Clean up dictation" (Local AI): which run is this entry's, what the sheet
 * and the sparkle button show, and the one place where a rewrite changes the entry ([accept]).
 * The run itself is [CleanupSession]'s and outlives the screen.
 *
 * It sits next to [EditorVM] rather than inside it, so upstream's view model only creates it and
 * hands it the entry through [Entry].
 */
class EditorCleanup(
    private val session: CleanupSession,
    private val prefs: LocalAiPreferences,
    private val scope: CoroutineScope,
    private val entry: Entry,
) {
    /** What a cleanup needs from the entry being edited. */
    interface Entry {
        /** The entry's journal id; null until the entry is first saved. */
        val journalId: String?
        val journalIds: Flow<String?>
        /** The body as it is edited. */
        val contents: Flow<String>
        val people: List<JournalPerson>
        val knownPeople: List<JournalPerson>
        /** Everyone in the journal, read when a run starts. */
        suspend fun everyone(): List<JournalPerson>
        /** The body as Markdown, as the editor has it now. */
        fun markdown(): String
        fun replaceBody(markdown: String)
        fun addPeople(people: List<JournalPerson>)
        fun say(@StringRes message: Int)
    }

    val isEnabled: StateFlow<Boolean> =
        prefs.settings.map { it.enabled }.stateIn(scope, SharingStarted.Eagerly, prefs.current.enabled)

    /** The review shows the texts as in the entry, or as Markdown. Remembered between cleanups. */
    val isReviewFormatted: StateFlow<Boolean> =
        prefs.settings.map { it.reviewFormatted }.stateIn(scope, SharingStarted.Eagerly, prefs.current.reviewFormatted)

    fun setReviewFormatted(formatted: Boolean) = prefs.setReviewFormatted(formatted)

    /** Until the entry has a journal id, its run is known by this key. */
    private val draftKey = "draft:" + UUID.randomUUID()
    private val key: String
        get() = entry.journalId ?: draftKey

    private val mine =
        combine(session.snapshot, entry.journalIds.distinctUntilChanged()) { snap, id ->
            snap to (snap.entryKey != null && snap.entryKey == (id ?: draftKey))
        }

    /** What the sheet shows: this entry's run, unless it is minimized. */
    val state: StateFlow<CleanupUiState> =
        mine
            .map { (snap, isMine) -> if (isMine && !snap.minimized) snap.state else CleanupUiState.Hidden }
            .stateIn(scope, SharingStarted.Eagerly, CleanupUiState.Hidden)

    /** What the sparkle button shows while the sheet is put away. */
    val indicator: StateFlow<CleanupIndicator> =
        mine
            .map { (snap, isMine) ->
                when {
                    !snap.isActive -> CleanupIndicator.Idle
                    !isMine -> if (snap.state is CleanupUiState.Working) CleanupIndicator.BusyElsewhere else CleanupIndicator.Idle
                    else ->
                        when (val st = snap.state) {
                            is CleanupUiState.Working -> CleanupIndicator.Working(st.progress)
                            is CleanupUiState.Review -> CleanupIndicator.Ready
                            is CleanupUiState.Problem -> CleanupIndicator.Problem
                            CleanupUiState.Hidden -> CleanupIndicator.Idle
                        }
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, CleanupIndicator.Idle)

    /** Emits once when this entry's rewrite arrives while the sheet is minimized. */
    private val _ready = Channel<Unit>(Channel.CONFLATED)
    val ready = _ready.receiveAsFlow()

    /** Emits once after a rewrite was applied, so the screen can offer Undo. */
    private val _applied = Channel<Unit>(Channel.CONFLATED)
    val applied = _applied.receiveAsFlow()

    /** Emits a line to show when the user writes in an entry that is being cleaned up. */
    private val _notice = Channel<Int>(Channel.CONFLATED)
    val notice = _notice.receiveAsFlow()

    private var bodyBeforeCleanup: String? = null

    init {
        session.editorShown(key)
        scope.launch {
            // an unsaved entry gets its id when it is first saved: its run moves with it
            entry.journalIds.distinctUntilChanged().collect { id ->
                if (id != null) session.rebind(draftKey, id)
                session.editorShown(key)
            }
        }
        scope.launch {
            // Writing on while a cleanup runs is fine, but the rewrite will not have that text.
            // Say once per run what happens to it, the moment it matters.
            var toldFor: Pair<String?, EditedMeanwhile>? = null
            entry.contents.distinctUntilChanged().collect {
                val snap = session.snapshot.value
                if (snap.entryKey != key || snap.state !is CleanupUiState.Working) return@collect
                val kind = when (editedMeanwhile()) {
                    is EditedMeanwhile.Added -> EditedMeanwhile.Added("", false)
                    EditedMeanwhile.Changed -> EditedMeanwhile.Changed
                    EditedMeanwhile.No -> return@collect
                }
                if (toldFor == snap.source to kind) return@collect
                // once it is known the earlier text changed, "your new text is kept" is no longer true
                if (kind is EditedMeanwhile.Added && toldFor == snap.source to EditedMeanwhile.Changed) return@collect
                toldFor = snap.source to kind
                _notice.trySend(if (kind is EditedMeanwhile.Added) R.string.cleanup_notice_added else R.string.cleanup_notice_changed)
            }
        }
        scope.launch {
            var wasWorking = false
            indicator.collect { now ->
                if (wasWorking && now == CleanupIndicator.Ready) _ready.trySend(Unit)
                wasWorking = now is CleanupIndicator.Working
            }
        }
    }

    /** The sparkle button: opens this entry's run if there is one, otherwise starts one. */
    fun start() {
        val snap = session.snapshot.value
        when {
            snap.isActive && snap.entryKey == key -> session.expand()
            snap.state is CleanupUiState.Working -> entry.say(R.string.cleanup_busy_elsewhere)
            else -> run()
        }
    }

    fun retry() = run()

    private fun run() {
        val body = entry.markdown()
        if (body.isBlank()) {
            entry.say(R.string.cleanup_empty_body)
            return
        }
        val onEntry = entry.people
        session.start(key, canOpenEntry = entry.journalId != null, body = body) {
            // the people on this entry first: a name several people share most likely means them
            (onEntry + entry.everyone()).distinctPeople().map { person ->
                CleanupPerson(
                    person.id, person.name, person.note, onEntry = onEntry.any { it.id == person.id },
                    color = person.color, shape = person.shape, contactLookupKey = person.contactLookupKey,
                )
            }
        }
    }

    /** Answers who a name several people share means in the rewrite. Null: nobody's mention. */
    fun pickPerson(key: String, personId: String?) = session.pick(key, personId)

    fun cancel() = session.cancel()

    /** Puts the sheet away. The run carries on and shows on the sparkle button. */
    fun minimize() = session.minimize()

    /** What was written in this entry since its cleanup started. */
    fun editedMeanwhile(): EditedMeanwhile {
        val snap = session.snapshot.value
        val source = snap.source?.takeIf { snap.entryKey == key } ?: return EditedMeanwhile.No
        return EditedMeanwhile.of(source, entry.markdown())
    }

    /** Replaces the body with the reviewed rewrite. The only place where Local AI changes an entry. */
    fun accept() {
        val snap = session.snapshot.value
        val review = snap.state as? CleanupUiState.Review ?: return
        if (snap.entryKey != key) return
        // Undo goes back to what is in the editor now, which may be newer than what was cleaned up;
        // text added while the cleanup ran is not in the rewrite: it stays, after it
        val edits = editedMeanwhile()
        bodyBeforeCleanup = entry.markdown()
        val rewrite = review.settled
        entry.replaceBody(if (edits is EditedMeanwhile.Added) edits.keep(rewrite) else rewrite)
        // people the rewrite names are on the entry now
        val known = entry.knownPeople
        entry.addPeople(mentionedPersonIds(rewrite).mapNotNull { id -> known.firstOrNull { it.id == id } })
        session.clear()
        _applied.trySend(Unit)
    }

    fun undo() {
        val previous = bodyBeforeCleanup ?: return
        bodyBeforeCleanup = null
        entry.replaceBody(previous)
    }

    /** The editor is closed. The run is not tied to it: it carries on, minimized. */
    fun editorGone() = session.editorGone(key)
}
