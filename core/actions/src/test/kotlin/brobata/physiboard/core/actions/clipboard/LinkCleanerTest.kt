package brobata.physiboard.core.actions.clipboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * spec: expansion-clipboard-pickers-launcher.md SS3.7 and its test cases T57 to T76 and T78 to T80. The links are
 * real share links of the shape each site hands out (ids shortened or replaced).
 */
class LinkCleanerTest {

    private fun clean(url: String) = LinkCleaner.cleanUrl(url)

    // --- tracking parameters -----------------------------------------------------------------

    @Test
    fun `T57 utm parameters go and the rest keep their order and encoding`() {
        assertEquals(
            "https://www.example.com/article?id=42&lang=en%2DGB#comments",
            clean("https://www.example.com/article?utm_source=newsletter&id=42&utm_medium=email&lang=en%2DGB&utm_campaign=fall#comments"),
        )
    }

    @Test
    fun `T58 the question mark goes when every parameter was tracking`() {
        assertEquals("https://example.com/page", clean("https://example.com/page?utm_source=x&fbclid=IwAR0abc"))
        assertEquals("https://example.com/page#top", clean("https://example.com/page?gclid=Cj0KCQ#top"))
    }

    @Test
    fun `T59 a link with nothing to remove is returned as the same string`() {
        val url = "https://en.wikipedia.org/wiki/Hydraulic_cylinder?action=history#top"
        assertSame(url, clean(url))
    }

    @Test
    fun `T60 YouTube share link loses si but keeps the start time`() {
        assertEquals("https://youtu.be/dQw4w9WgXcQ?t=42", clean("https://youtu.be/dQw4w9WgXcQ?si=Ab3dEfGhIjKlMnOp&t=42"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", clean("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=shared&pp=ygUEcmljaw%3D%3D"))
    }

    @Test
    fun `T61 Spotify share link loses si`() {
        assertEquals("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", clean("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=1a2b3c4d5e6f"))
    }

    @Test
    fun `T62 si and t survive on sites where they are not tracking`() {
        val url = "https://example.com/search?si=1&t=20&s=abc"
        assertSame(url, clean(url))
    }

    @Test
    fun `T63 X share link loses s and t`() {
        assertEquals("https://x.com/nasa/status/1234567890", clean("https://x.com/nasa/status/1234567890?s=20&t=AbCdEfGh"))
        assertEquals("https://twitter.com/nasa/status/1", clean("https://twitter.com/nasa/status/1?ref_src=twsrc%5Etfw"))
    }

    @Test
    fun `T64 Instagram, Facebook, TikTok, Reddit and Amazon share links`() {
        assertEquals("https://www.instagram.com/p/C1a2b3c4d5e/", clean("https://www.instagram.com/p/C1a2b3c4d5e/?igsh=MWZ4dGdwY2Rh"))
        assertEquals("https://www.facebook.com/story.php?story_fbid=1&id=2", clean("https://www.facebook.com/story.php?story_fbid=1&id=2&mibextid=Nif5oz"))
        assertEquals("https://www.tiktok.com/@user/video/7300000000000000000", clean("https://www.tiktok.com/@user/video/7300000000000000000?is_from_webapp=1&sender_device=pc&_r=1&_t=8hK"))
        assertEquals("https://www.reddit.com/r/Austin/comments/abc/title/", clean("https://www.reddit.com/r/Austin/comments/abc/title/?share_id=xYz&utm_content=1&utm_medium=android_app"))
        assertEquals("https://www.amazon.com/dp/B0C1234567?th=1", clean("https://www.amazon.com/dp/B0C1234567?pd_rd_w=abc&th=1&pf_rd_p=def&ref_=pd_gw&content-id=amzn1.sym"))
    }

    @Test
    fun `T65 parameter names match case-insensitively and in their encoded form`() {
        assertEquals("https://example.com/?a=1", clean("https://example.com/?UTM_Source=x&a=1&FBCLID=y"))
        assertEquals("https://example.com/?a=1", clean("https://example.com/?utm%5Fsource=x&a=1"))
    }

    @Test
    fun `T66 Google search keeps the query and drops its session parameters`() {
        assertEquals(
            "https://www.google.com/search?q=hydraulic+seal+kit&client=firefox",
            clean("https://www.google.com/search?q=hydraulic+seal+kit&client=firefox&sca_esv=123&ei=abc&ved=0ahUK&oq=hydraulic&gs_lp=Egxnd3"),
        )
    }

    @Test
    fun `T67 an empty parameter and a parameter without a value are kept as they were when nothing goes`() {
        val url = "https://example.com/?a&&b="
        assertSame(url, clean(url))
        assertEquals("https://example.com/?a&b=", clean("https://example.com/?a&&utm_source=x&b="))
    }

    // --- redirect wrappers -------------------------------------------------------------------

    @Test
    fun `T68 a Google redirect is unwrapped and the target cleaned`() {
        assertEquals(
            "https://txbayservice.com/services?page=2",
            clean("https://www.google.com/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&url=https%3A%2F%2Ftxbayservice.com%2Fservices%3Fpage%3D2%26utm_source%3Dgoogle&usg=AOvVaw0"),
        )
        assertEquals("https://example.com/a", clean("https://google.co.uk/url?q=https://example.com/a&sa=D&ust=1&usg=AOv"))
    }

    @Test
    fun `T69 Facebook, Messenger and Instagram link shims are unwrapped`() {
        assertEquals("https://example.com/menu?x=1", clean("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fmenu%3Fx%3D1%26fbclid%3DIwAR2&h=AT3abc&s=1"))
        assertEquals("https://example.com/", clean("https://l.messenger.com/l.php?u=https%3A%2F%2Fexample.com%2F&h=AT0"))
        assertEquals("https://example.com/shop", clean("https://l.instagram.com/?u=https%3A%2F%2Fexample.com%2Fshop%3Figshid%3Dabc&e=AT1"))
    }

    @Test
    fun `T70 YouTube, Reddit, Slack, Outlook, DuckDuckGo and LinkedIn wrappers are unwrapped`() {
        assertEquals("https://example.com/x", clean("https://www.youtube.com/redirect?event=video_description&redir_token=QUFF&q=https%3A%2F%2Fexample.com%2Fx&v=abc"))
        assertEquals("https://example.com/y", clean("https://out.reddit.com/t3_abc?url=https%3A%2F%2Fexample.com%2Fy&token=AQAA&app_name=web"))
        assertEquals("https://example.com/z", clean("https://slack-redir.net/link?url=https%3A%2F%2Fexample.com%2Fz"))
        assertEquals("https://example.com/w", clean("https://nam12.safelinks.protection.outlook.com/?url=https%3A%2F%2Fexample.com%2Fw&data=05%7C01&sdata=abc&reserved=0"))
        assertEquals("https://example.com/d", clean("https://duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fd&rut=abc"))
        assertEquals("https://example.com/l", clean("https://www.linkedin.com/redir/redirect?url=https%3A%2F%2Fexample.com%2Fl&urlhash=abc&trk=public_post"))
    }

    @Test
    fun `T71 a wrapper inside a wrapper is unwrapped all the way`() {
        val google = "https://www.google.com/url?q=https%3A%2F%2Fexample.com%2Fdeep%3Futm_source%3Dx"
        val outlook = "https://eur01.safelinks.protection.outlook.com/?url=" + java.net.URLEncoder.encode(google, "UTF-8") + "&data=1"
        assertEquals("https://example.com/deep", clean(outlook))
    }

    @Test
    fun `T72 a wrapper whose target is missing, malformed or not a web address is left alone`() {
        for (url in listOf(
            "https://www.google.com/url?sa=t&usg=AOv",
            "https://www.google.com/url?q=hydraulic+seals",
            "https://www.google.com/url?q=javascript%3Aalert(1)",
            "https://l.facebook.com/l.php?u=https%3A%2F%2Fexa%ZZmple.com",
            "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fa%20b",
            "https://l.facebook.com/l.php?u=ftp%3A%2F%2Fexample.com%2F",
        )) {
            assertSame(url, clean(url), url)
        }
    }

    @Test
    fun `T78 a wrapper whose target was written without encoding is left alone, never shortened`() {
        for (url in listOf(
            "https://steamcommunity.com/linkfilter/?url=https://shop.example.com/item?id=1&color=red",
            "https://steamcommunity.com/linkfilter/?url=https://docs.example.com/page#install",
            "https://www.google.com/url?q=https://a.com/x?y=1&sa=D",
        )) {
            assertSame(url, clean(url), url)
        }
        // With nothing of its own to lose, an unencoded target is still unwrapped.
        assertEquals("https://example.com/a", clean("https://www.google.com/url?q=https://example.com/a&sa=D&usg=AOv"))
    }

    @Test
    fun `T79 ref_url is a working return address off X and stays there`() {
        val url = "https://a.com/login?ref_url=https%3A%2F%2Fb.com"
        assertSame(url, clean(url))
    }

    @Test
    fun `T80 a query separated by semicolons is not touched`() {
        val url = "https://example.com/?utm_source=x;id=1"
        assertSame(url, clean(url))
    }

    @Test
    fun `T73 the fragment of the wrapped link is kept and the wrapper's own is dropped`() {
        assertEquals("https://example.com/doc#section-2", clean("https://www.google.com/url?q=https%3A%2F%2Fexample.com%2Fdoc%23section-2#wrapperfrag"))
    }

    // --- things that are not links -------------------------------------------------------------

    @Test
    fun `T74 anything that is not an http or https URL is returned untouched`() {
        for (s in listOf(
            "utm_source=x&fbclid=y",
            "mailto:someone@example.com?utm_source=x",
            "ftp://example.com/file?utm_source=x",
            "example.com/page?utm_source=x",
            "https://",
            "https:///path?utm_source=x",
            "",
        )) {
            assertSame(s, clean(s), s)
        }
    }

    @Test
    fun `T75 links inside text are cleaned and everything around them is kept exactly`() {
        val text = "Menu: https://example.com/menu?utm_source=ig&day=fri.\nAlso (see https://en.wikipedia.org/wiki/Brisket_(food)?fbclid=1) and https://x.com/a/status/1?s=20!"
        assertEquals(
            "Menu: https://example.com/menu?day=fri.\nAlso (see https://en.wikipedia.org/wiki/Brisket_(food)) and https://x.com/a/status/1!",
            LinkCleaner.cleanText(text),
        )
    }

    @Test
    fun `T76 text with no link to change is returned as the same string, and very long text is not scanned`() {
        val plain = "Brisket for 40, pickup at 4pm. https://example.com/order?id=7"
        assertSame(plain, LinkCleaner.cleanText(plain))
        val long = "https://example.com/?utm_source=x " + "a".repeat(LinkCleaner.MAX_TEXT_LENGTH)
        assertSame(long, LinkCleaner.cleanText(long))
    }

    @Test
    fun `ports, user info and IPv6 hosts parse without being changed`() {
        assertEquals("https://user@example.com:8443/p?a=1", clean("https://user@example.com:8443/p?a=1&utm_term=z"))
        assertEquals("http://[::1]:8080/?a=1", clean("http://[::1]:8080/?utm_id=1&a=1"))
        assertEquals("HTTPS://Example.COM/A?b=2", clean("HTTPS://Example.COM/A?b=2&utm_source=x"))
    }

    @Test
    fun `a question mark inside the fragment is not a query`() {
        val url = "https://example.com/app#/route?utm_source=x"
        assertSame(url, clean(url))
    }

    @Test
    fun `cleaning is idempotent`() {
        val once = clean("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2F%3Fa%3D1%26utm_source%3Dfb&h=x")
        assertSame(once, clean(once))
    }

    @Test
    fun `percent decoding refuses bad escapes and bad UTF-8`() {
        assertEquals("a b", LinkCleaner.decodeComponent("a+b", plusIsSpace = true))
        assertEquals("a+b", LinkCleaner.decodeComponent("a+b"))
        assertEquals("é", LinkCleaner.decodeComponent("%C3%A9"))
        assertNull(LinkCleaner.decodeComponent("%E9"))
        assertNull(LinkCleaner.decodeComponent("%4"))
        assertNull(LinkCleaner.decodeComponent("%GG"))
    }

    @Test
    fun `trailing punctuation splits off, a balanced bracket stays`() {
        assertEquals("https://a.com/x" to ".", LinkCleaner.splitTrailingPunctuation("https://a.com/x."))
        assertEquals("https://a.com/x_(y)" to "", LinkCleaner.splitTrailingPunctuation("https://a.com/x_(y)"))
        assertEquals("https://a.com/x" to ")", LinkCleaner.splitTrailingPunctuation("https://a.com/x)"))
        assertEquals("https://a.com/x" to "\",", LinkCleaner.splitTrailingPunctuation("https://a.com/x\","))
    }
}
