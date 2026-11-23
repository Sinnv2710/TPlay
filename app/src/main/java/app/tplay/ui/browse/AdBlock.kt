package app.tplay.ui.browse

/** Domains that only serve ads, pop-unders or trackers. A request to any of them (or a subdomain) is dropped. */
private val AD_DOMAINS = setOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
    "google-analytics.com", "googletagmanager.com", "adnxs.com", "adsrvr.org", "adform.net",
    "popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "onclickads.net",
    "onclkds.com", "clickadu.com", "exoclick.com", "exosrv.com", "juicyads.com", "trafficjunky.net",
    "trafficstars.com", "adsterra.com", "highperformanceformat.com", "profitablegatecpm.com",
    "effectivegatecpm.com", "adcash.com", "hilltopads.net", "richpush.co", "pushame.com",
    "mgid.com", "taboola.com", "outbrain.com", "revcontent.com", "criteo.com", "pubmatic.com",
    "rubiconproject.com", "openx.net", "casalemedia.com", "amazon-adsystem.com", "adskeeper.com",
    "bidvertiser.com", "yllix.com", "a-ads.com", "coinzillatag.com", "ad-maven.com", "admaven.com",
    "popunderjs.com", "tsyndicate.com", "dolohen.com", "acscdn.com", "cdn4ads.com", "ero-advertising.com",
)

/** True when [host] is, or is a subdomain of, a known ad domain. Never blocks the page's own host. */
fun isAdHost(host: String?, pageHost: String? = null): Boolean {
    val h = host?.lowercase()?.trimEnd('.') ?: return false
    if (pageHost != null && (h == pageHost.lowercase() || h.endsWith("." + pageHost.lowercase()))) return false
    return AD_DOMAINS.any { h == it || h.endsWith(".$it") }
}

/** Hides leftover ad slots (iframes, banners, overlays) that the network filter cannot catch. */
const val AD_HIDE_JS = """
(function () {
  if (window.__tplayAds) return;
  window.__tplayAds = true;
  var css = 'iframe[src*="ads"],iframe[id*="google_ads"],ins.adsbygoogle,[id^="ad-"],[id^="ads-"],' +
    '[class*="adsbox"],[class*="ad-banner"],[class*="advert"],[id*="popunder"],[class*="popunder"],' +
    'a[href*="/ads/"],div[style*="z-index: 2147483647"]{display:none!important}';
  var s = document.createElement('style');
  s.textContent = css;
  (document.head || document.documentElement).appendChild(s);
})();
"""
