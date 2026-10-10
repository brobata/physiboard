package brobata.physiboard.app.settings

import brobata.physiboard.app.settings.ui.DictionaryUndo
import brobata.physiboard.core.dict.PersonalWord
import brobata.physiboard.core.dict.UserWordFileCodec
import brobata.physiboard.core.dict.WordFrequency
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * app-shell.md SS22.4, dictionaries-languages.md SS7: the Personal Dictionary screen and the
 * keyboard write one `personal_dictionary.json`. Undo of a deleted word reads the file, puts the
 * word back and writes it; a word the keyboard saves in between must not be lost.
 */
class PersonalWordFileUpdateTest {

    private val dir: File = Files.createTempDirectory("personal-words").toFile()
    private val personalFile = File(dir, UserWordFileCodec.PERSONAL_WORDS_FILE_NAME)
    private val defaultFile = File(dir, UserWordFileCodec.DEFAULT_WORDS_FILE_NAME)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun write(vararg words: PersonalWord) = personalFile.writeText(UserWordFileCodec.encodePersonalWords(words.toList()))

    private fun stored(): Set<String> = UserWordFileCodec.decodePersonalWords(personalFile.readText()).map { it.word }.toSet()

    @Test
    fun `undo puts the word back with its count and last use`() {
        val removed = PersonalWord("brobata", 7, 1_000)
        write(PersonalWord("titan", 2, 500))
        val result = updatePersonalFile(personalFile, defaultFile) { DictionaryUndo.restorePersonal(it, removed) }
        assertNotNull(result)
        assertEquals(setOf(PersonalWord("titan", 2, 500), removed), UserWordFileCodec.decodePersonalWords(personalFile.readText()).toSet())
    }

    @Test
    fun `the screen's delete, add and rename keep a word the keyboard saved after the screen opened`() {
        write(PersonalWord("titan", 2, 500), PersonalWord("teh", 1, 100))
        // The screen read the file when it opened; then the keyboard saved "physiboard".
        val screenCopy = UserWordFileCodec.decodePersonalWords(personalFile.readText())
        write(*(screenCopy + PersonalWord("physiboard", 1, 2_000)).toTypedArray())

        updatePersonalFile(personalFile, defaultFile) { it.withPersonalWordRemoved("teh") }
        assertEquals(setOf("titan", "physiboard"), stored())
        updatePersonalFile(personalFile, defaultFile) { it.withPersonalWordAdded("brobata", 3_000) }
        assertEquals(setOf("titan", "physiboard", "brobata"), stored())
        val result = updatePersonalFile(personalFile, defaultFile) { it.withPersonalWordRenamed("titan", "Titan") }
        assertEquals(setOf("Titan", "physiboard", "brobata"), stored())
        assertEquals(setOf("Titan", "physiboard", "brobata"), result?.personalWords()?.map { it.word }?.toSet(), "the screen's list comes from the file")
    }

    @Test
    fun `undoing a deleted default word keeps a default word edited meanwhile`() {
        val words = listOf(WordFrequency("haha", 1), WordFrequency("lol", 1), WordFrequency("brb", 1))
        defaultFile.writeText(UserWordFileCodec.encodeDefaultWords(words))
        fun update(transform: (List<WordFrequency>) -> List<WordFrequency>) =
            UserWordFileCodec.updateDefaults({ defaultFile.readText() }, { defaultFile.writeText(it); true }, transform)

        update { list -> list.filterNot { it.word == "lol" } }
        // Renamed by hand before Undo was tapped.
        update { list -> list.map { if (it.word == "brb") it.copy(word = "BRB") else it } }
        update { DictionaryUndo.restoreDefault(it, WordFrequency("lol", 1), 1) }

        assertEquals(listOf("haha", "lol", "BRB"), UserWordFileCodec.decodeDefaultWords(defaultFile.readText()).map { it.word })
    }

    @Test
    fun `a word the keyboard saves while undo runs is kept`() {
        val kept = PersonalWord("titan", 2, 500)
        val removed = PersonalWord("brobata", 7, 1_000)
        val learned = PersonalWord("physiboard", 1, 2_000)
        write(kept)

        val undo: Thread
        // The keyboard's writer (UserWordFileLoader.savePersonalAsync) holds the lock while it
        // writes. Undo starts during that write and must wait for it before it reads the file.
        synchronized(UserWordFileCodec.PersonalDictionaryFileLock) {
            undo = Thread { updatePersonalFile(personalFile, defaultFile) { DictionaryUndo.restorePersonal(it, removed) } }
            undo.start()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (undo.state != Thread.State.BLOCKED) {
                check(System.nanoTime() < deadline) { "undo never reached the lock" }
                Thread.yield()
            }
            write(kept, learned)
        }
        undo.join(5_000)

        assertEquals(setOf("titan", "brobata", "physiboard"), stored())
    }
}
