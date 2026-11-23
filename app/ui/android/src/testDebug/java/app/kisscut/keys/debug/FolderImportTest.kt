package app.kisscut.keys.debug

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

class FolderImportTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun touch(path: String) = tmp.root.resolve(path).apply { parentFile.mkdirs(); writeText("x") }

    @Test
    fun subFoldersBecomeNestedCollectionsAndOtherFilesAreSkipped() {
        touch("Pokemon/Meowth/welcome.gif")
        touch("Pokemon/Meowth/excited.GIF")
        touch("Pokemon/Kanto/squirtle.gif")
        touch("Ryza/Party/Lent.jpg")
        touch("loose.png")
        touch("notes.txt")
        touch("Pokemon/.hidden.png")

        val plan = FolderImport.plan(tmp.root).map { it.path.joinToString("/") to it.file.name }

        assertEquals(
            listOf(
                "" to "loose.png",
                "Pokemon/Kanto" to "squirtle.gif",
                "Pokemon/Meowth" to "excited.GIF",
                "Pokemon/Meowth" to "welcome.gif",
                "Ryza/Party" to "Lent.jpg",
            ),
            plan,
        )
    }
}
