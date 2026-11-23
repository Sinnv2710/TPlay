package app.tplay

import app.tplay.ui.browse.blockRequest
import app.tplay.ui.browse.isAdHost
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockTest {
    @Test fun blocksKnownAdDomainsAndSubdomains() {
        assertTrue(isAdHost("doubleclick.net"))
        assertTrue(isAdHost("ads.exoclick.com"))
        assertTrue(isAdHost("POPADS.NET."))
    }

    @Test fun allowsNormalSitesAndLookalikes() {
        assertFalse(isAdHost("en.yts-official.com"))
        assertFalse(isAdHost("notdoubleclick.net"))
        assertFalse(isAdHost(null))
    }

    @Test fun neverBlocksThePageItself() {
        assertFalse(isAdHost("mgid.com", pageHost = "mgid.com"))
        assertFalse(isAdHost("cdn.mgid.com", pageHost = "mgid.com"))
    }

    @Test fun strictRuleBlocksThirdPartyCodeButNotPosters() {
        val page = "en.yts-official.com"
        assertTrue(blockRequest("cdn.unknown-ads.xyz", page, false, "*/*"))
        assertTrue(blockRequest("evil.example", page, false, "text/html,application/xhtml+xml"))
        assertFalse(blockRequest("image.tmdb.org", page, false, "*/*"))
        assertFalse(blockRequest("static.yts-official.com", page, false, "*/*"))
        assertFalse(blockRequest("x.example", page, true, "text/html"))
        assertFalse(blockRequest("pics.example", page, false, "image/avif,image/webp,*/*"))
    }
}
