package app.tplay.data.source

import kotlinx.serialization.Serializable

/**
 * A movie site described as data, never as code. Selectors are CSS selectors; a field written as
 * `selector@attr` reads that attribute, otherwise it reads the element's text.
 */
@Serializable
data class SourceDef(
    val id: String,
    val name: String,
    val version: Int = 1,
    val site: String,
    val language: String = "en",
    val list: ListDef,
    val filters: List<FilterDef> = emptyList(),
    val detail: DetailDef,
    /** Filter option values (for example a genre) that stay hidden until the user allows them. */
    val adultOptions: List<String> = emptyList(),
)

@Serializable
data class ListDef(
    /** Path and query with placeholders: {keyword}, {page} and one per filter param. */
    val path: String,
    val card: String,
    val fields: Map<String, String>,
    val next: String? = null,
)

@Serializable
data class FilterDef(
    val name: String,
    val param: String,
    val options: List<String>,
    val labels: Map<String, String> = emptyMap(),
) {
    fun labelOf(value: String): String = labels[value] ?: value
}

@Serializable
data class DetailDef(
    val release: String,
    val fields: Map<String, String>,
    val synopsis: String? = null,
    val synopsisAfter: String? = null,
)

/** One entry of a repository index. */
@Serializable
data class RepoSource(val id: String, val name: String, val version: Int, val url: String)

@Serializable
data class RepoIndex(val name: String, val sources: List<RepoSource>)
