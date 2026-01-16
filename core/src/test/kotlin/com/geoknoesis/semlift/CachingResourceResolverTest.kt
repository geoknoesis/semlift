package io.semlift

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Duration

class CachingResourceResolverTest {
    @Test
    fun `uses cached response within ttl`() = kotlinx.coroutines.runBlocking {
        val tempDir = Files.createTempDirectory("semlift-cache")
        val config = CacheConfig(directory = tempDir, ttl = Duration.ofHours(1))
        val fetcher = CountingFetcher(
            CacheHttpResponse(
                status = 200,
                bytes = "payload".toByteArray(),
                headers = mapOf("ETag" to "\"v1\"")
            )
        )

        val resolver = CachingResourceResolver(
            config = config,
            fetcher = fetcher
        )

        val first = resolver.resolve("https://example.org/data.json")
        val second = resolver.resolve("https://example.org/data.json")

        assertThat(first).isEqualTo("payload".toByteArray())
        assertThat(second).isEqualTo("payload".toByteArray())
        assertThat(fetcher.calls).isEqualTo(1)
    }
}

private class CountingFetcher(
    private val response: CacheHttpResponse
) : HttpFetcher {
    var calls: Int = 0
        private set

    override suspend fun fetch(url: String, etag: String?, lastModified: String?): CacheHttpResponse {
        calls += 1
        return response
    }
}

