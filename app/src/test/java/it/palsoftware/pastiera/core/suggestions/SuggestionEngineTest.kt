package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SuggestionEngineTest {

    private val locale = Locale.ITALIAN
    private val fakeRepo = FakeDictionaryRepository()
    private val engine = SuggestionEngine(fakeRepo, locale = locale)

    @Test
    fun testIsReadyCheck() {
        fakeRepo.addTestEntry("hallo", 100)
        fakeRepo.isReady = false
        
        val results = engine.suggest("hallo")
        assertTrue("Returns an empty list when the repo isn't ready", results.isEmpty())
        
        fakeRepo.isReady = true
        val resultsReady = engine.suggest("hall") // Prefix search
        assertTrue("Returns results once the repo is ready", resultsReady.isNotEmpty())
    }

    @Test
    fun testKeyboardProximity_QWERTY() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("hallo", 200)
        engine.setKeyboardLayout("qwerty")

        // 'hsllo' (S is next to A -> distance 1.0)
        val resultsNear = engine.suggest("hsllo")
        val scoreNear = resultsNear.find { it.candidate == "hallo" }?.score ?: 0.0

        // 'hmllo' (M is far from A -> distance ~8.0)
        val resultsFar = engine.suggest("hmllo")
        val scoreFar = resultsFar.find { it.candidate == "hallo" }?.score ?: 0.0

        assertTrue("A nearby key ($scoreNear) should score higher than a distant one ($scoreFar)", scoreNear > scoreFar)
    }

    @Test
    fun testProximityFiltering() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("hallo", 200)
        engine.setKeyboardLayout("qwerty")

        // 'hmllo' -> 'hallo' substitutes 'm' for 'a'.
        // Distance is > 2.5, so proximity filtering should drop it.
        val results = engine.suggest("hmllo", useKeyboardProximity = true)
        assertTrue("Filters the distant 'm' -> 'a' substitution", results.none { it.candidate == "hallo" })
        
        // Without proximity filtering it should be found
        val resultsNoFilter = engine.suggest("hmllo", useKeyboardProximity = false)
        assertTrue("Found when proximity filtering is off", resultsNoFilter.any { it.candidate == "hallo" })
    }

    @Test
    fun testAccentMatching() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("perché", 200)
        
        // User types "perche" (no accent)
        val results = engine.suggest("perche", includeAccentMatching = true)
        assertEquals("Finds 'perché' as the top suggestion", "perché", results.firstOrNull()?.candidate)
    }

    @Test
    fun accentlessFrenchWordSuggestsAccentedDictionaryEntry() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("derrière", 200)

        val results = engine.suggest("derriere", includeAccentMatching = true)

        assertEquals("derrière", results.firstOrNull()?.candidate)
        assertEquals(0, results.firstOrNull()?.distance)
    }

    @Test
    fun testCaseOnlyVariantIsSuggestedForLowercaseInput() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("Problem", 200)
        fakeRepo.addTestEntry("Probleme", 250)

        val results = engine.suggest("problem")

        assertTrue("Suggests the same-length case variant 'Problem'", results.any { it.candidate == "Problem" })
    }

    @Test
    fun testUserDictionaryRanking() {
        fakeRepo.isReady = true
        // "hallo" im Hauptwörterbuch
        fakeRepo.addTestEntry("hallo", 100, SuggestionSource.MAIN)
        // "hallx" im User-Wörterbuch
        fakeRepo.addTestEntry("hallx", 100, SuggestionSource.USER)
        
        val results = engine.suggest("hall")
        
        assertEquals("The user's word should come first", "hallx", results.firstOrNull()?.candidate)
        assertEquals("The user's word should be marked as USER", SuggestionSource.USER, results.first().source)
    }

    @Test
    fun testDynamicLayoutSwitch() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("apple", 200)
        
        // QWERTY: A is (1,0), Q is (0,0) -> Distance 1.0 (Nearby)
        engine.setKeyboardLayout("qwerty")
        val resultsQwerty = engine.suggest("qpple")
        assertTrue("QWERTY: qpple should find apple (Q next to A)", resultsQwerty.any { it.candidate == "apple" })

        // AZERTY: A is (0,0), Q is (1,0) -> A and Q are adjacent here too, but swapped.
        // Let's take 'q' and 'w'.
        // QWERTY: Q(0,0), W(0,1) -> Distance 1.0
        // AZERTY: A(0,0), Z(0,1) -> Q is at (1,0), W is at (0,1) -> Distance sqrt(1^2 + 1^2) = 1.41
        
        fakeRepo.addTestEntry("queen", 200)
        
        // QWERTY: 'w' statt 'q' -> 'ween' -> 'queen'
        engine.setKeyboardLayout("qwerty")
        val scoreQwerty = engine.suggest("ween").find { it.candidate == "queen" }?.score ?: 0.0
        
        // AZERTY: 'w' (0,1) is far from 'a' (0,0), but on AZERTY 'q' sits where 'a' does.
        // In AZERTY mapping: "KEYCODE_Q" -> 'a', "KEYCODE_W" -> 'z', "KEYCODE_A" -> 'q'
        // Physical Key Q (0,0) -> 'a'
        // Physical Key W (0,1) -> 'z'
        // Physical Key A (1,0) -> 'q'
        // So in AZERTY, 'a' (0,0) and 'z' (0,1) are neighbors.
        // 'q' (1,0) and 'a' (0,0) are neighbors.
        
        engine.setKeyboardLayout("azerty")
        // 'a' instead of 'q' -> 'aueen' (on AZERTY 'a' is at (0,0), 'q' at (1,0))
        val resultsAzerty = engine.suggest("aueen")
        assertTrue("AZERTY: aueen should find queen", resultsAzerty.any { it.candidate == "queen" })
    }

    @Test
    fun testDynamicDownloadSimulation() {
        fakeRepo.isReady = true
        
        // Zuerst leer
        assertTrue(engine.suggest("hallo").isEmpty())
        
        // Dynamisch "runterladen"
        fakeRepo.addTestEntry("hallo", 200)
        
        val results = engine.suggest("hall")
        assertEquals(1, results.size)
        assertEquals("hallo", results[0].candidate)
    }

    @Test
    fun testLigatureFoldPrefersOeilOverNeil() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("œil", 116)
        fakeRepo.addTestEntry("Neil", 97)

        val results = engine.suggest("oeil")
        assertEquals("œil", results.firstOrNull()?.candidate)
    }

    @Test
    fun testApostropheLigatureRecompose() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("œil", 116)
        fakeRepo.addTestEntry("Neil", 97)

        val results = engine.suggest("l'oeil")
        assertEquals("l'œil", results.firstOrNull()?.candidate)
    }

    @Test
    fun testLongItalianElisionRecompose() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("amico", 120)

        val results = engine.suggest("dell'amivo")
        assertEquals("dell'amico", results.firstOrNull()?.candidate)
    }

    @Test
    fun testProperNameScenario_LorealVsLoral() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("L'Oréal", 120)
        fakeRepo.addTestEntry("l'oral", 220)

        val results = engine.suggest("l'oreal")
        assertEquals("l'Oréal", results.firstOrNull()?.candidate)
    }

    @Test
    fun testProperNameScenario_GenericCloserThanProperName() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("L'Oréal", 120)
        fakeRepo.addTestEntry("l'oral", 220)

        // Typo closer to generic word: "l'orak" -> "l'oral" (distance 1)
        // while "L'Oréal" requires more edits.
        val results = engine.suggest("l'orak")
        assertEquals("l'oral", results.firstOrNull()?.candidate)
    }
}
