package app.tplay.data.source

import app.tplay.data.discover.DiscoverClient
import app.tplay.data.discover.DiscoverFilters
import app.tplay.data.discover.PageResult
import app.tplay.data.discover.ReleasesResult

data class SelfTestResult(val level: HealthLevel, val message: String)

/** Loads page 1 and one movie of a source and says what could be read, so a changed site shows up at once. */
suspend fun selfTest(client: DiscoverClient, def: SourceDef): SelfTestResult {
    val cards = when (val page = client.page(def, DiscoverFilters(), 1)) {
        is PageResult.Failure -> return SelfTestResult(HealthLevel.FAIL, page.message)
        PageResult.Unsupported -> return SelfTestResult(HealthLevel.WARN, "No movie cards found. The site may have changed.")
        is PageResult.Ok -> page.cards
    }
    if (cards.isEmpty()) return SelfTestResult(HealthLevel.WARN, "No movie cards found. The site may have changed.")
    val posters = cards.count { it.posterUrl != null }
    val head = "${cards.size} cards read, $posters with a poster"
    return when (val detail = client.releases(def, cards.first().pageUrl)) {
        is ReleasesResult.Failure -> SelfTestResult(HealthLevel.WARN, "$head, but the first movie gave no magnet links")
        is ReleasesResult.Ok -> SelfTestResult(HealthLevel.OK, "$head, first movie has ${detail.releases.size} magnet links")
    }
}
