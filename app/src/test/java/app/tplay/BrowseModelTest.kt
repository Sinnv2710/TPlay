package app.tplay

import app.tplay.ui.browse.BLANK_PAGE
import app.tplay.ui.browse.startUrl
import app.tplay.ui.browse.isMagnet
import app.tplay.ui.browse.isWebUrl
import app.tplay.ui.browse.normalizeAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseModelTest {
    @Test fun magnetLinksAreDetectedCaseInsensitively() {
        assertTrue(isMagnet("magnet:?xt=urn:btih:abc"))
        assertTrue(isMagnet("  MAGNET:?xt=urn:btih:abc"))
        assertFalse(isMagnet("https://x/magnet:?xt=1"))
    }

    @Test fun onlyHttpAndHttpsStayInTheBrowser() {
        assertTrue(isWebUrl("https://example.com/a"))
        assertTrue(isWebUrl("http://example.com"))
        assertFalse(isWebUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isWebUrl("market://details?id=x"))
        assertFalse(isWebUrl("javascript:alert(1)"))
        assertFalse(isWebUrl("file:///sdcard/x"))
    }

    @Test fun addressesGetAScheme() {
        assertEquals("https://yts.mx", normalizeAddress("yts.mx"))
        assertEquals("http://a.b", normalizeAddress("http://a.b"))
        assertEquals(BLANK_PAGE, normalizeAddress("   "))
    }
}

class MagnetHookTest {
    @Test fun hookRunsInCapturePhaseAndCallsTheBridge() {
        val js = app.tplay.ui.browse.MAGNET_HOOK_JS
        assertTrue(js.contains("}, true);"))
        assertTrue(js.contains("stopImmediatePropagation"))
        assertTrue(js.contains("${app.tplay.ui.browse.JS_BRIDGE_NAME}.magnet("))
        assertTrue(js.contains("a[href^=\"magnet:\"]"))
    }

    @Test fun hookIsIdempotent() {
        assertTrue(app.tplay.ui.browse.MAGNET_HOOK_JS.contains("if (window.__tplayHook) return;"))
    }
}

class StartUrlTest {
    @Test fun savedWebAddressIsUsed() {
        assertEquals("https://example.org/home", startUrl("  https://example.org/home "))
    }

    @Test fun emptyOrUnsafeSavedValueFallsBackToTheHintPage() {
        assertEquals(BLANK_PAGE, startUrl(""))
        assertEquals(BLANK_PAGE, startUrl("javascript:alert(1)"))
        assertEquals(BLANK_PAGE, startUrl("file:///sdcard/x"))
    }
}
