package brobata.physiboard.app.settings

import brobata.physiboard.core.dict.PersonalWord
import brobata.physiboard.core.dict.UserWordFileCodec
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * settings-catalog.md SS7.2 step 6: a restore writes the two word files the keyboard edits under
 * the same lock the keyboard takes, through a temporary file renamed over the old one.
 */
class BackupWordFileRestoreTest {

    private val dir: File = Files.createTempDirectory("restore").toFile()
    private val personalFile = File(dir, UserWordFileCodec.PERSONAL_WORDS_FILE_NAME)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun encoded(vararg words: String) = UserWordFileCodec.encodePersonalWords(words.map { PersonalWord(it, 1, 0L) })

    @Test
    fun `a restored personal word file waits for a keyboard save in progress`() {
        personalFile.writeText(encoded("titan"))
        val restore: Thread
        synchronized(UserWordFileCodec.PersonalDictionaryFileLock) {
            restore = Thread { BackupArchive.restoreSideFile(dir, UserWordFileCodec.PERSONAL_WORDS_FILE_NAME, encoded("brobata").toByteArray()) }
            restore.start()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (restore.state != Thread.State.BLOCKED) {
                check(System.nanoTime() < deadline) { "the restore never reached the lock" }
                check(restore.isAlive) { "the restore wrote without taking the lock" }
                Thread.yield()
            }
            assertEquals(encoded("titan"), personalFile.readText(), "nothing written while the keyboard holds the lock")
        }
        restore.join(5_000)
        assertEquals(encoded("brobata"), personalFile.readText())
        assertFalse(File(dir, "${personalFile.name}.tmp").exists(), "written through a temporary file renamed over the old one")
    }

    @Test
    fun `the default word file is restored under the lock too, and other side files are written plainly`() {
        BackupArchive.restoreSideFile(dir, UserWordFileCodec.DEFAULT_WORDS_FILE_NAME, "[]".toByteArray())
        assertEquals("[]", File(dir, UserWordFileCodec.DEFAULT_WORDS_FILE_NAME).readText())
        BackupArchive.restoreSideFile(dir, "keyboard_layouts/custom.json", "{}".toByteArray())
        assertTrue(File(dir, "keyboard_layouts/custom.json").isFile)
    }

    @Test
    fun `a path that resolves outside the files directory is refused`() {
        assertFailsWith<IllegalArgumentException> { BackupArchive.restoreSideFile(dir, "../escape.json", "{}".toByteArray()) }
        assertFalse(File(dir.parentFile, "escape.json").exists())
    }
}
