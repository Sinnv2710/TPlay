package app.tplay

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
}
