package app.tplay

import app.tplay.ui.add.AddViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddViewModelTest {
    @Test fun acceptsLowercaseMagnet() {
        assertTrue(AddViewModel.isValidMagnet("magnet:?xt=urn:btih:abc"))
    }

    @Test fun acceptsUppercasePrefix() {
        assertTrue(AddViewModel.isValidMagnet("MAGNET:?XT=URN:BTIH:abc"))
    }

    @Test fun acceptsAdditionalParameters() {
        assertTrue(AddViewModel.isValidMagnet("magnet:?xt=urn:btih:abc&dn=Movie"))
    }

    @Test fun acceptsWhitespaceAroundValue() {
        assertTrue(AddViewModel.isValidMagnet("  magnet:?xt=urn:btih:abc  "))
    }

    @Test fun rejectsEmptyValue() {
        assertFalse(AddViewModel.isValidMagnet(""))
    }

    @Test fun rejectsHttpUrl() {
        assertFalse(AddViewModel.isValidMagnet("https://example.test/magnet:?xt=urn:btih:abc"))
    }

    @Test fun rejectsMissingInfoHash() {
        assertFalse(AddViewModel.isValidMagnet("magnet:?dn=Movie"))
    }

    @Test fun rejectsPlainText() {
        assertFalse(AddViewModel.isValidMagnet("movie magnet link"))
    }
}
