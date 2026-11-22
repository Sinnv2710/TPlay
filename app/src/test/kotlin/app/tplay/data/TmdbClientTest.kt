package app.tplay.data

import app.tplay.data.tmdb.TmdbClient
import app.tplay.domain.ServiceIds
import app.tplay.domain.ServiceToken
import app.tplay.domain.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class KeyVault(private val key: String?) : VaultRepository {
    override fun services(): Flow<List<ServiceToken>> = flowOf(emptyList())
    override suspend fun put(serviceId: String, label: String, value: String) = Unit
    override suspend fun get(serviceId: String): String? = if (serviceId == ServiceIds.TMDB) key else null
    override suspend fun remove(serviceId: String) = Unit
}

class TmdbClientTest {
    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().apply { start() }
    }

    @After fun stop() {
        server.shutdown()
    }

    private fun client(key: String? = "K") = TmdbClient(
        KeyVault(key),
        baseUrl = server.url("/3").toString().trimEnd('/'),
        imageBase = "https://img/w342",
    )

    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    @Test fun noKeyMeansNoRequest() = run {
        assertNull(client(null).lookup("Movie", 2020))
        assertEquals(0, server.requestCount)
    }

    @Test fun picksMostPopularResultWithAPoster() = run {
        server.enqueue(
            MockResponse().setBody(
                """{"results":[{"poster_path":null,"popularity":99},
                |{"poster_path":"/a.jpg","popularity":1,"release_date":"2020-01-01"},
                |{"poster_path":"/b.jpg","popularity":5,"vote_average":7.5}]}""".trimMargin(),
            ),
        )
        val info = client().lookup("Movie", 2020)!!
        assertEquals("https://img/w342/b.jpg", info.posterUrl)
        assertEquals(7.5, info.rating!!, 0.0)
        val path = server.takeRequest().path!!
        assertEquals(true, path.contains("/3/search/movie") && path.contains("year=2020") && path.contains("api_key=K"))
    }

    @Test fun fallsBackToTvWhenNoMovieMatches() = run {
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))
        server.enqueue(MockResponse().setBody("""{"results":[{"poster_path":"/t.jpg","first_air_date":"2021-05-05"}]}"""))
        val info = client().lookup("Show", null)!!
        assertEquals("https://img/w342/t.jpg", info.posterUrl)
        assertEquals("2021-05-05", info.releaseDate)
    }

    @Test fun serverErrorMeansNull() = run {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        assertNull(client().lookup("X", null))
    }
}
