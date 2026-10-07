package brobata.physiboard.core.actions.gif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** spec: layers-sym-alt.md SS4.5 and SS14 cases 54-62: the KLIPY client, the local lists, the request decision. */
class GifTest {

    /** Trimmed from KLIPY's documented search response: two GIFs, then one with no usable file. */
    private val canned = """
        {"result":true,"data":{"data":[
          {"id":1001,"slug":"cat-wave-abc","title":"Cat waving","file":{
             "hd":{"gif":{"url":"https://static.klipy.com/ii/hd/cat.gif","width":498,"height":280,"size":2100000}},
             "md":{"gif":{"url":"https://static.klipy.com/ii/md/cat.gif","width":220,"height":124,"size":402000},
                   "webp":{"url":"https://static.klipy.com/ii/md/cat.webp","width":220,"height":124,"size":90000}},
             "sm":{"gif":{"url":"https://static.klipy.com/ii/sm/cat.gif","width":150,"height":84,"size":200000}},
             "xs":{"gif":{"url":"https://static.klipy.com/ii/xs/cat.gif","width":90,"height":50,"size":60000},
                   "webp":{"url":"https://static.klipy.com/ii/xs/cat.webp?x=1&sig=AbC","width":90,"height":50,"size":12000}}},
           "tags":["cat"],"type":"gif","blur_preview":"data:image/jpeg;base64,xx"},
          {"id":1002,"slug":"dog-yes","title":"Dog says yes","file":{
             "sm":{"gif":{"url":"https://static.klipy.com/ii/sm/dog.gif","width":"150","height":"100"}}},"type":"gif"},
          {"id":1003,"slug":"broken","file":{"xs":{"jpg":{"url":"https://static.klipy.com/ii/xs/b.jpg"}}}}
        ],"current_page":1,"per_page":24,"has_next":true}}
    """.trimIndent()

    private fun gif(slug: String) = GifItem(slug, slug, "https://p/$slug.webp", 90, 50, "https://s/$slug.gif", 220, 124)

    // The client --------------------------------------------------------------------------------

    @Test
    fun `case 54 - search and trending URLs carry the key in the path and only the documented parameters`() {
        assertEquals(
            "https://api.klipy.com/api/v1/KEY123/gifs/search?page=1&per_page=24&q=thumbs%20up%20%26%20more&locale=us&content_filter=medium&format_filter=gif%2Cwebp",
            Klipy.searchUrl("KEY123", " thumbs up & more ", page = 1, country = "US"),
        )
        assertEquals(
            "https://api.klipy.com/api/v1/KEY123/gifs/trending?page=2&per_page=24&content_filter=medium&format_filter=gif%2Cwebp",
            Klipy.trendingUrl("KEY123", page = 2, country = null),
        )
        assertFalse("customer_id" in Klipy.searchUrl("k", "x"), "no user identifier is ever sent")
        assertFalse("locale" in Klipy.trendingUrl("k", country = "419"), "a country that is not two letters is left out")
    }

    @Test
    fun `case 55 - a response parses in the provider's order with the smallest preview and an md GIF to send`() {
        val page = Klipy.parse(canned)!!
        assertEquals(listOf("cat-wave-abc", "dog-yes"), page.items.map { it.slug }, "the item with no GIF or WebP is skipped; the rest keep their order")
        val cat = page.items[0]
        assertEquals("https://static.klipy.com/ii/xs/cat.webp?x=1&sig=AbC", cat.previewUrl, "URLs are kept exactly as returned")
        assertEquals(90, cat.previewWidth)
        assertEquals("https://static.klipy.com/ii/md/cat.gif", cat.sendUrl)
        assertEquals("Cat waving", cat.title)
        val dog = page.items[1]
        assertEquals("https://static.klipy.com/ii/sm/dog.gif", dog.previewUrl)
        assertEquals("https://static.klipy.com/ii/sm/dog.gif", dog.sendUrl)
        assertEquals(100, dog.sendHeight, "numbers sent as strings still read")
        assertTrue(page.hasNext)
        assertEquals(1, page.page)
    }

    @Test
    fun `case 56 - an error result or a body of another shape does not parse`() {
        assertNull(Klipy.parse("""{"result":false,"errors":{"message":["Invalid app key"]}}"""))
        assertNull(Klipy.parse("<html>502</html>"))
        assertNull(Klipy.parse("""{"result":true,"data":[]}"""))
        val empty = Klipy.parse("""{"result":true,"data":{"data":[],"current_page":1,"has_next":false}}""")!!
        assertTrue(empty.items.isEmpty())
        val noPageField = Klipy.parse("""{"result":true,"data":{"data":[],"has_next":true}}""", requestedPage = 3)!!
        assertEquals(3, noPageField.page, "a body without current_page counts as the page asked for")
    }

    // The request decision and the gate ---------------------------------------------------------

    @Test
    fun `case 57 - with no key the page says it is not set up and nothing is requested`() {
        assertEquals(GifPage.Request.Refused(GifPage.NOT_SET_UP), GifPage.request("", "cat", 1, "us", privateMode = false, blockedReason = "x"))
        assertEquals(GifPage.Request.Refused(GifPage.NOT_SET_UP), GifPage.request("  ", "", 1, null, privateMode = true, blockedReason = "x"))
    }

    @Test
    fun `case 58 - in private mode nothing is requested and the gate's sentence is shown`() {
        val reason = "Private mode is on, so PhysiBoard makes no network requests."
        assertEquals(GifPage.Request.Refused(reason), GifPage.request("KEY", "cat", 1, null, privateMode = true, blockedReason = reason))
    }

    @Test
    fun `case 59 - an empty query asks for trending, a query asks for search`() {
        val trending = assertIs<GifPage.Request.Fetch>(GifPage.request("KEY", "  ", 1, null, privateMode = false, blockedReason = ""))
        assertTrue(trending.trending)
        assertTrue("/gifs/trending?" in trending.url)
        val search = assertIs<GifPage.Request.Fetch>(GifPage.request("KEY", "Wow", 1, null, privateMode = false, blockedReason = ""))
        assertFalse(search.trending)
        assertTrue("/gifs/search?" in search.url && "q=Wow" in search.url)
    }

    @Test
    fun `case 60 - a refusal from the gate at request time is shown as the gate's reason, a failure as a plain message`() {
        assertEquals(GifPage.Outcome.Message("Private mode is on, so PhysiBoard makes no network requests."), GifPage.outcome(null, "Private mode is on, so PhysiBoard makes no network requests."))
        assertEquals(GifPage.Outcome.Message(GifPage.FAILED), GifPage.outcome(null, null))
        assertEquals(GifPage.Outcome.Message(GifPage.FAILED), GifPage.outcome("not json", null))
        assertEquals(GifPage.Outcome.Message(GifPage.NO_RESULTS), GifPage.outcome("""{"result":true,"data":{"data":[],"current_page":1}}""", null))
        assertIs<GifPage.Outcome.Results>(GifPage.outcome(canned, null))
    }

    @Test
    fun `case 61 - the field takes the GIF itself only when it declares a matching image type`() {
        assertTrue(GifPage.fieldAcceptsGif(listOf("image/gif")))
        assertTrue(GifPage.fieldAcceptsGif(listOf("image/png", "IMAGE/*")))
        assertTrue(GifPage.fieldAcceptsGif(listOf("*/*")))
        assertFalse(GifPage.fieldAcceptsGif(emptyList()))
        assertFalse(GifPage.fieldAcceptsGif(listOf("image/png", "video/*", "text/plain")))
    }

    // Favourites and recents --------------------------------------------------------------------

    @Test
    fun `case 62 - a sent GIF moves to the front of recents once, capped, and not while learning is off`() {
        val start = listOf(gif("a"), gif("b"))
        assertEquals(listOf("b", "a"), GifShelf.afterSend(start, gif("b"), learningAllowed = true).map { it.slug })
        assertEquals(listOf("c", "a", "b"), GifShelf.afterSend(start, gif("c"), learningAllowed = true).map { it.slug })
        assertSame(start, GifShelf.afterSend(start, gif("c"), learningAllowed = false))
        val full = (1..GifShelf.MAX_RECENTS).map { gif("r$it") }
        val after = GifShelf.afterSend(full, gif("new"), learningAllowed = true)
        assertEquals(GifShelf.MAX_RECENTS, after.size)
        assertEquals("new", after.first().slug)
    }

    @Test
    fun `a star adds a favourite first, a second star removes it, and private mode only refuses adding`() {
        val added = assertIs<GifShelf.FavouriteChange.Added>(GifShelf.toggleFavourite(listOf(gif("a")), gif("b"), learningAllowed = true))
        assertEquals(listOf("b", "a"), added.list.map { it.slug })
        val removed = assertIs<GifShelf.FavouriteChange.Removed>(GifShelf.toggleFavourite(added.list, gif("b"), learningAllowed = false))
        assertEquals(listOf("a"), removed.list.map { it.slug })
        assertEquals(GifShelf.FavouriteChange.RefusedPrivate, GifShelf.toggleFavourite(listOf(gif("a")), gif("z"), learningAllowed = false))
    }

    @Test
    fun `the stored lists round-trip and read tolerantly`() {
        val items = listOf(gif("a"), gif("b"))
        assertEquals(items, GifShelf.decode(GifShelf.encode(items)))
        assertEquals(emptyList(), GifShelf.decode(null))
        assertEquals(emptyList(), GifShelf.decode("{nope"))
        val messy = """[{"slug":"a","preview":"https://p","send":"https://s"},{"slug":"x","preview":"http://insecure","send":"https://s"},{"title":"no slug"},{"slug":"a","preview":"https://p2","send":"https://s2"},7]"""
        val read = GifShelf.decode(messy)
        assertEquals(listOf("a"), read.map { it.slug })
        assertEquals("https://p", read.single().previewUrl)
    }
}
