package app.kisscut.keys.shared.core.words

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one file that holds the learned words. On the phone it lives in `noBackupFilesDir`, which Android never
 * backs up or copies to a new phone (docs/KEYBOARD.md, Learned words). Not a database (CLAUDE.md rule 3).
 */
interface LearnedWordsFile {
    /** The stored text, or null if there is none yet. */
    suspend fun read(): String?

    /** Replaces the stored text in one step, so a crash leaves the old or the new list, never half of one. */
    suspend fun write(text: String)

    suspend fun delete()
}

/**
 * The user's learned words, shared by the keyboard and the app (same process). Loads [file] once, then applies
 * changes in order and saves after each batch. A file that can't be read starts an empty list; a failed save keeps
 * the words in memory. Words never leave the phone: nothing here is in backups or `.stickerpack` files.
 */
class LearnedWordsRepository(
    private val file: LearnedWordsFile,
    private val clock: () -> Long,
    scope: CoroutineScope,
) {
    private sealed interface Change {
        data class Typed(val word: String) : Change
        data class Undid(val word: String) : Change
        data object Clear : Change
    }

    private val state = MutableStateFlow(PersonalWords.EMPTY)
    private val changes = Channel<Change>(Channel.UNLIMITED)

    /** The current list; empty until the file has loaded. */
    val words: StateFlow<PersonalWords> = state.asStateFlow()

    init {
        scope.launch {
            state.value = load()
            for (first in changes) {
                var next = apply(state.value, first)
                while (true) next = apply(next, changes.tryReceive().getOrNull() ?: break)
                state.value = next
                save(next)
            }
        }
    }

    /** [PersonalWords.typed]: a word the keyboard committed as typed, in a field where learning is allowed. */
    fun typed(word: String) {
        changes.trySend(Change.Typed(word))
    }

    /** [PersonalWords.undidAutocorrect]. */
    fun undidAutocorrect(word: String) {
        changes.trySend(Change.Undid(word))
    }

    /** "Clear learned words": forgets every word and deletes the file. */
    fun clear() {
        changes.trySend(Change.Clear)
    }

    private fun apply(words: PersonalWords, change: Change): PersonalWords = when (change) {
        is Change.Typed -> words.typed(change.word, clock())
        is Change.Undid -> words.undidAutocorrect(change.word, clock())
        Change.Clear -> PersonalWords.EMPTY
    }

    private suspend fun load(): PersonalWords = try {
        file.read()?.let { PersonalWords.of(LearnedWordsCodec.decode(it), clock()) } ?: PersonalWords.EMPTY
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        PersonalWords.EMPTY
    }

    private suspend fun save(words: PersonalWords) {
        try {
            if (words.entries.isEmpty()) file.delete() else file.write(LearnedWordsCodec.encode(words.entries))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }
}

/** The file format: a header line, then `word<TAB>count<TAB>lastSeen` lines. Bad lines are skipped on reading. */
object LearnedWordsCodec {
    private const val HEADER = "# Kisscut Keys learned words, v1"

    fun encode(words: Collection<LearnedWord>): String = buildString {
        append(HEADER).append('\n')
        for (w in words) append(w.text).append('\t').append(w.count).append('\t').append(w.lastSeen).append('\n')
    }

    fun decode(text: String): List<LearnedWord> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.trimEnd('\r').split('\t')
            val count = parts.getOrNull(1)?.toIntOrNull()
            val lastSeen = parts.getOrNull(2)?.toLongOrNull()
            if (parts.size == 3 && count != null && count > 0 && lastSeen != null) {
                LearnedWord(parts[0], count, lastSeen)
            } else {
                null
            }
        }
        .toList()
}
