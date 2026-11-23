package app.tplay.ui.browse

import java.net.URI

/** Shown when no start page is set. The user chooses a site in Settings, none is built in. */
const val BLANK_PAGE = "data:text/html;charset=utf-8," +
    "<meta name=viewport content='width=device-width,initial-scale=1'>" +
    "<body style='background:%230f1117;color:%23c8cce0;font-family:sans-serif;padding:32px'>" +
    "<h2>Browse</h2><p>Set a start page in Settings, or type an address you trust. " +
    "Tap a magnet link on any page and TPlay picks it up.</p>"

/** True for a magnet link the user tapped inside the page. */
fun isMagnet(url: String): Boolean = url.trim().startsWith("magnet:", ignoreCase = true)

/** Only plain web pages may open inside the browser tab; everything else (intent:, market:, tel:) is dropped. */
fun isWebUrl(url: String): Boolean {
    val scheme = runCatching { URI(url).scheme }.getOrNull()?.lowercase()
    return scheme == "https" || scheme == "http"
}

/** Repairs what people type into the address bar: adds https:// when missing. */
fun normalizeAddress(input: String): String {
    val text = input.trim()
    if (text.isEmpty()) return BLANK_PAGE
    if (isWebUrl(text)) return text
    return "https://$text"
}

/**
 * Runs inside every page. Sites often cancel the click on a magnet link (instant-navigation scripts,
 * custom handlers), so the browser never sees it. A capture-phase listener runs first, hands the link
 * to the app and stops the page's own handlers.
 */
const val MAGNET_HOOK_JS = """
(function () {
  if (window.__tplayHook) return;
  window.__tplayHook = true;
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[href^="magnet:"]') : null;
    if (!a) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    TPlay.magnet(a.href);
  }, true);
})();
"""

const val JS_BRIDGE_NAME = "TPlay"

/** The page to open: the saved start page when it is a web address, otherwise the blank hint page. */
fun startUrl(saved: String): String = if (isWebUrl(saved.trim())) saved.trim() else BLANK_PAGE

