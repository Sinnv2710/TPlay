package app.tplay.data.discover

/** What the user chose: a search word plus one value per filter parameter of the source. Blank means "any". */
data class DiscoverFilters(
    val keyword: String = "",
    val values: Map<String, String> = emptyMap(),
) {
    val activeCount: Int get() = values.values.count { it.isNotBlank() }

    fun with(param: String, value: String): DiscoverFilters =
        copy(values = if (value.isBlank()) values - param else values + (param to value))

    fun cleared(): DiscoverFilters = copy(values = emptyMap())
}

data class MovieCard(
    val title: String,
    val year: Int?,
    val genres: String,
    val posterUrl: String?,
    val fallbackPosterUrl: String? = null,
    val rating: String?,
    val qualities: List<String>,
    val pageUrl: String,
)

/** One downloadable release on a movie page. [magnet] is the full magnet link. */
data class Release(val name: String, val quality: String, val size: String, val seeds: Int?, val magnet: String)
