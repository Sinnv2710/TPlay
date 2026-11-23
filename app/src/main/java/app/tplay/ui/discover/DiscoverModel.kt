package app.tplay.ui.discover

import app.tplay.data.discover.MovieCard
import app.tplay.data.discover.cleanReleaseTitle
import app.tplay.domain.Title

/** Key that says two titles are the same movie: name without punctuation or case, plus year (0 when unknown). */
fun libraryKey(name: String, year: Int?): String =
    name.lowercase().filter { it.isLetterOrDigit() } + "|" + (year ?: 0)

fun libraryKeys(titles: List<Title>): Set<String> = titles.map { libraryKey(it.name, it.year) }.toSet()

/** A library title saved without a year (the torrent name had none) still counts as the same movie. */
fun MovieCard.inLibrary(keys: Set<String>): Boolean {
    val clean = cleanReleaseTitle(title)
    val names = listOf(title to year, clean.title to (year ?: clean.year))
    return names.any { (name, y) -> libraryKey(name, y) in keys || libraryKey(name, null) in keys }
}

/**
 * Hides adult cards unless the user allowed them: a genre equal to an adult option (for example "18+"), or a whole word
 * from [adultWords] in the title or genres.
 */
fun List<MovieCard>.visibleFor(adultOptions: List<String>, adultWords: List<String>, showAdult: Boolean): List<MovieCard> {
    if (showAdult || (adultOptions.isEmpty() && adultWords.isEmpty())) return this
    val words = adultWords.map { Regex("(?i)(?<![a-z0-9])" + Regex.escape(it) + "(?![a-z0-9])") }
    return filterNot { card ->
        adultOptions.any { card.genres.contains(it) } || words.any { it.containsMatchIn(card.title + " " + card.genres) }
    }
}

/** Number in a card's info line ("12 seeds" gives 12), or null when the card shows none. */
fun MovieCard.seeds(): Int? = info?.let { Regex("[0-9]+").find(it)?.value?.toIntOrNull() }

fun List<MovieCard>.withSeedsOnly(on: Boolean): List<MovieCard> = if (on) filter { (it.seeds() ?: 1) > 0 } else this
