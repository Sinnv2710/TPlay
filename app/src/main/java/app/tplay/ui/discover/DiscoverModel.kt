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

/** Hides cards tagged with an adult option (for example the genre "18+") unless the user allowed them. */
fun List<MovieCard>.visibleFor(adultOptions: List<String>, showAdult: Boolean): List<MovieCard> =
    if (showAdult || adultOptions.isEmpty()) this else filterNot { card -> adultOptions.any { card.genres.contains(it) } }
