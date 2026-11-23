package app.tplay.ui.discover

import app.tplay.data.discover.MovieCard
import app.tplay.domain.Title

/** Filter choices offered in the sheet: label to the value the site expects. */
object DiscoverOptions {
    const val ADULT_GENRE = "18+"

    val genres = listOf(
        "Action", "Adventure", "Animation", "Biography", "Comedy", "Crime", "Documentary", "Drama", "Family",
        "Fantasy", "Film-Noir", "History", "Horror", "Music", "Musical", "Mystery", "Romance", "Sci-Fi",
        "Sport", "Thriller", "War", "Western",
    )
    val years = listOf(
        "2020-2029" to "2020s", "2010-2019" to "2010s", "2000-2009" to "2000s", "1990-1999" to "1990s",
        "1980-1989" to "1980s", "2027" to "2027", "2026" to "2026", "2025" to "2025", "2024" to "2024",
        "2023" to "2023", "2022" to "2022", "2021" to "2021", "2020" to "2020",
    )
    val qualities = listOf("720p", "1080p", "2160p", "3D")
    val ratings = listOf("5" to "5+", "6" to "6+", "7" to "7+", "8" to "8+", "9" to "9+")
    val sorts = listOf(
        "latest" to "Latest", "released" to "Released", "rating" to "Rating", "downloads" to "Downloads",
        "year" to "Year", "alphabetical" to "A to Z", "oldest" to "Oldest",
    )

    /** Genres to show; the adult one only when the user switched it on in Settings. */
    fun genresFor(showAdult: Boolean): List<String> = if (showAdult) listOf(ADULT_GENRE) + genres else genres
}

/** Key that says two titles are the same movie: name without punctuation or case, plus year (0 when unknown). */
fun libraryKey(name: String, year: Int?): String =
    name.lowercase().filter { it.isLetterOrDigit() } + "|" + (year ?: 0)

fun libraryKeys(titles: List<Title>): Set<String> = titles.map { libraryKey(it.name, it.year) }.toSet()

/** A library title saved without a year (the torrent name had none) still counts as the same movie. */
fun MovieCard.inLibrary(keys: Set<String>): Boolean =
    libraryKey(title, year) in keys || libraryKey(title, null) in keys

/** Hides adult cards unless the user allowed them. */
fun List<MovieCard>.visibleFor(showAdult: Boolean): List<MovieCard> =
    if (showAdult) this else filterNot { it.genres.contains(DiscoverOptions.ADULT_GENRE) }
