package app.kisscut.keys.shared.core.words

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * [LearnedWordsFile] as `learned_words.txt` in [dir]: on the phone `Context.noBackupFilesDir`, which Android
 * leaves out of cloud backups and phone-to-phone transfers. Writes go to a temporary file first, then replace
 * the old one.
 */
class DiskLearnedWordsFile(private val dir: File) : LearnedWordsFile {
    private val file = File(dir, NAME)
    private val temp = File(dir, "$NAME.tmp")

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (file.exists()) file.readText() else null
    }

    override suspend fun write(text: String) {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            temp.writeText(text)
            Files.move(temp.toPath(), file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
        }
    }

    override suspend fun delete() {
        withContext(Dispatchers.IO) {
            file.delete()
            temp.delete()
        }
    }

    companion object {
        const val NAME = "learned_words.txt"
    }
}
