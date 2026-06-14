package it.palsoftware.pastiera.core.suggestions

import android.os.Looper
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import java.util.Locale
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SuggestionControllerNextWordTest {

    private lateinit var store: UserNGramStore
    private lateinit var fakeRepository: FakeDictionaryRepository
    private val snapshots = mutableListOf<List<SuggestionResult>>()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        store = UserNGramStore(context).apply { clearAll() }
        fakeRepository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("bar", 200)
        }
        snapshots.clear()
    }

    @After
    fun tearDown() {
        store.clearAll()
        store.close()
    }

    @Test
    fun learnsBigramAndPredictsAfterSpace() {
        val controller = newController()

        typeWord(controller, "ich")
        pressSpace(controller)
        typeWord(controller, "bin")
        pressSpace(controller)
        typeWord(controller, "ich")
        pressSpace(controller)

        val latest = snapshots.last()
        assertEquals(listOf("bin", "bar"), latest.map { it.candidate })
        assertEquals(SuggestionKind.NEXT_WORD, latest.first().kind)
        assertEquals(SuggestionKind.STARTER_WORD, latest[1].kind)
    }

    @Test
    fun semicolonKeepsContextForLearning() {
        val controller = newController()

        typeWord(controller, "teste")
        pressSemicolon(controller)
        pressSpace(controller)
        typeWord(controller, "das")
        pressSpace(controller)

        val predictions = store.predict("de-DE", "teste", limit = 3)
        assertEquals(listOf("das"), predictions.map { it.word })
    }

    @Test
    fun periodLearnsCurrentPairThenResetsContext() {
        val controller = newController()

        typeWord(controller, "ich")
        pressSpace(controller)
        typeWord(controller, "bin")
        pressPeriod(controller)
        typeWord(controller, "morgen")
        pressSpace(controller)

        assertEquals(listOf("bin"), store.predict("de-DE", "ich", limit = 3).map { it.word })
        assertTrue(store.predict("de-DE", "bin", limit = 3).isEmpty())
        assertEquals(SuggestionKind.STARTER_WORD, snapshots.last().first().kind)
    }

    @Test
    fun typingLetterOverridesNextWordPredictions() {
        val controller = newController()

        typeWord(controller, "ich")
        pressSpace(controller)
        typeWord(controller, "bin")
        pressSpace(controller)
        typeWord(controller, "ich")
        pressSpace(controller)
        typeWord(controller, "b")

        val latest = waitForSuggestionCandidates("bar")
        assertEquals(listOf("bar"), latest.map { it.candidate })
        assertEquals(SuggestionKind.CURRENT_WORD, latest.first().kind)
    }

    @Test
    fun currentWordSuggestionsIncludeAdditionalLocales() {
        val deRepository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("deutsch", 200)
        }
        val enRepository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("english", 200)
        }
        val context = RuntimeEnvironment.getApplication()
        val controller = SuggestionController(
            context = context,
            assets = context.assets,
            settingsProvider = { SuggestionSettings(suggestionsEnabled = true, maxSuggestions = 3) },
            onSuggestionsUpdated = { snapshots.add(it) },
            currentLocale = Locale.GERMANY,
            dictionaryRepositoryFactory = { _, _, _, locale, _ ->
                if (locale.language == "en") enRepository else deRepository
            },
            nextWordPredictorOverride = NextWordPredictor(store),
            activeSuggestionLocalesProvider = { listOf(Locale.ENGLISH) }
        )

        typeWord(controller, "eng")

        assertEquals(listOf("english"), waitForSuggestionCandidates("english").map { it.candidate })
    }

    @Test
    fun emptyInitialContextShowsStarterSuggestions() {
        fakeRepository.addTestEntry("Pastiera", 255, SuggestionSource.DEFAULT_USER)
        fakeRepository.addTestEntry("BlackBerry", 255, SuggestionSource.DEFAULT_USER)
        fakeRepository.addTestEntry("Parenzo", 255, SuggestionSource.DEFAULT_USER)
        fakeRepository.addTestEntry("ich", 220)
        fakeRepository.addTestEntry("dann", 210)
        val controller = newController()

        controller.readInitialContext(emptyInputConnection())

        val latest = snapshots.last()
        assertEquals(listOf("ich", "dann", "bar"), latest.map { it.candidate })
        assertEquals(SuggestionKind.STARTER_WORD, latest.first().kind)
    }

    @Test
    fun typingLetterOverridesStarterSuggestions() {
        fakeRepository.addTestEntry("ich", 220)
        fakeRepository.addTestEntry("dann", 210)
        val controller = newController()

        controller.readInitialContext(emptyInputConnection())
        typeWord(controller, "b")

        val latest = waitForSuggestionCandidates("bar")
        assertEquals(listOf("bar"), latest.map { it.candidate })
        assertEquals(SuggestionKind.CURRENT_WORD, latest.first().kind)
    }

    @Test
    fun softBoundaryFallsBackToStarterSuggestionsWhenNoBigramExists() {
        fakeRepository.addTestEntry("ich", 220)
        fakeRepository.addTestEntry("dann", 210)
        val controller = newController()

        typeWord(controller, "neu")
        pressSpace(controller)

        val latest = snapshots.last()
        assertEquals(listOf("ich", "dann", "bar"), latest.map { it.candidate })
        assertEquals(SuggestionKind.STARTER_WORD, latest.first().kind)
    }

    @Test
    fun learnedBigramOverridesSoftBoundaryStarterFallback() {
        fakeRepository.addTestEntry("ich", 220)
        fakeRepository.addTestEntry("dann", 210)
        val controller = newController()

        typeWord(controller, "ich")
        pressSpace(controller)
        typeWord(controller, "bin")
        pressSpace(controller)
        typeWord(controller, "ich")
        pressSpace(controller)

        val latest = snapshots.last()
        assertEquals(listOf("bin", "ich", "dann"), latest.map { it.candidate })
        assertEquals(SuggestionKind.NEXT_WORD, latest.first().kind)
        assertEquals(SuggestionKind.STARTER_WORD, latest[1].kind)
    }

    @Test
    fun dismissSuggestionForgetsBigramAndRefillsVisibleSuggestions() {
        fakeRepository.addTestEntry("ich", 220)
        fakeRepository.addTestEntry("dann", 210)
        val controller = newController()

        typeWord(controller, "ich")
        pressSpace(controller)
        typeWord(controller, "bin")
        pressSpace(controller)
        typeWord(controller, "ich")
        pressSpace(controller)

        controller.dismissSuggestion("bin")

        val latest = snapshots.last()
        assertEquals(listOf("ich", "dann", "bar"), latest.map { it.candidate })
        assertEquals(SuggestionKind.STARTER_WORD, latest.first().kind)
        assertTrue(store.predict("de-DE", "ich", limit = 3).isEmpty())
    }

    @Test
    fun learnsSentenceStartAndPredictsItBeforeStarterFallback() {
        fakeRepository.addTestEntry("ich", 220)
        fakeRepository.addTestEntry("dann", 210)
        val controller = newController()

        typeWord(controller, "Hallo")
        pressPeriod(controller)
        typeWord(controller, "Morgen")
        pressSpace(controller)
        controller.onContextReset()
        controller.readInitialContext(emptyInputConnection())

        val latest = snapshots.last()
        assertEquals(listOf("Hallo", "Morgen", "ich"), latest.map { it.candidate })
        assertEquals(SuggestionKind.NEXT_WORD, latest.first().kind)
        assertEquals(SuggestionKind.STARTER_WORD, latest[2].kind)
    }

    private fun newController(): SuggestionController {
        val context = RuntimeEnvironment.getApplication()
        return SuggestionController(
            context = context,
            assets = context.assets,
            settingsProvider = { SuggestionSettings(suggestionsEnabled = true, maxSuggestions = 3) },
            onSuggestionsUpdated = { snapshots.add(it) },
            currentLocale = Locale.GERMANY,
            dictionaryRepositoryFactory = { _, _, _, _, _ -> fakeRepository },
            nextWordPredictorOverride = NextWordPredictor(store)
        )
    }

    private fun typeWord(controller: SuggestionController, word: String) {
        word.forEach { ch ->
            controller.onCharacterCommitted(ch.toString(), null)
        }
    }

    private fun waitForSuggestionCandidates(vararg candidates: String): List<SuggestionResult> {
        val expected = candidates.toList()
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            snapshots.lastOrNull()?.let { latest ->
                if (latest.map { it.candidate } == expected) {
                    return latest
                }
            }
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return snapshots.last()
    }

    private fun pressSpace(controller: SuggestionController) {
        controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, null)
    }

    private fun pressSemicolon(controller: SuggestionController) {
        controller.onBoundaryKey(KeyEvent.KEYCODE_SEMICOLON, keyEvent(KeyEvent.KEYCODE_SEMICOLON), null)
    }

    private fun pressPeriod(controller: SuggestionController) {
        controller.onBoundaryKey(KeyEvent.KEYCODE_PERIOD, keyEvent(KeyEvent.KEYCODE_PERIOD), null)
    }

    private fun keyEvent(keyCode: Int): KeyEvent {
        val character = when (keyCode) {
            KeyEvent.KEYCODE_SEMICOLON -> ';'
            KeyEvent.KEYCODE_PERIOD -> '.'
            else -> null
        }
        val event = character?.let {
            KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                .getEvents(charArrayOf(it))
                ?.firstOrNull { generated -> generated.action == KeyEvent.ACTION_DOWN }
        }
        return event ?: KeyEvent(
            0L,
            0L,
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
            0,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0
        )
    }

    private fun emptyInputConnection(): InputConnection {
        return Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getTextBeforeCursor", "getTextAfterCursor" -> ""
                else -> null
            }
        } as InputConnection
    }
}
