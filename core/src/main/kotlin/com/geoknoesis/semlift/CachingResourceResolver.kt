package io.semlift

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64

data class CacheConfig(
    val directory: Path = Paths.get(System.getProperty("user.home"), ".semlift", "cache"),
    val ttl: Duration = Duration.ofHours(24),
    val staleIfError: Boolean = true,
    val connectTimeoutMs: Int = 10_000,
    val readTimeoutMs: Int = 20_000
)

class CachingResourceResolver(
    private val delegate: ResourceResolver = DefaultResourceResolver(),
    private val config: CacheConfig = CacheConfig(),
    private val fetcher: HttpFetcher = DefaultHttpFetcher(config)
) : ResourceResolver {
    override suspend fun resolve(uri: String): ByteArray {
        if (!isHttpUri(uri)) {
            return delegate.resolve(uri)
        }

        Files.createDirectories(config.directory)
        val key = cacheKey(uri)
        val dataPath = config.directory.resolve("$key.data")
        val metaPath = config.directory.resolve("$key.meta.json")

        val cached = readCache(metaPath, dataPath)
        if (cached != null && !isExpired(cached)) {
            return cached.bytes
        }

        val response = runCatching {
            fetcher.fetch(uri, cached?.etag, cached?.lastModified)
        }.getOrElse { err ->
            if (cached != null && config.staleIfError) {
                return cached.bytes
            }
            throw err
        }

        return when (response.status) {
            200 -> {
                writeCache(metaPath, dataPath, uri, response)
                response.bytes
            }
            304 -> {
                if (cached == null) {
                    error("Cache miss for 304 response: $uri")
                }
                touchCache(metaPath, cached)
                cached.bytes
            }
            else -> {
                if (cached != null && config.staleIfError) {
                    cached.bytes
                } else {
                    error("HTTP ${response.status} from $uri")
                }
            }
        }
    }

    private fun writeCache(
        metaPath: Path,
        dataPath: Path,
        uri: String,
        response: CacheHttpResponse
    ) {
        Files.write(dataPath, response.bytes)
        val meta = CacheMetadata(
            uri = uri,
            etag = response.headers["ETag"],
            lastModified = response.headers["Last-Modified"],
            fetchedAt = Instant.now().toEpochMilli()
        )
        Files.write(metaPath, JacksonSupport.jsonMapper.writeValueAsBytes(meta))
    }

    private fun touchCache(metaPath: Path, cached: CachedEntry) {
        val meta = CacheMetadata(
            uri = cached.uri,
            etag = cached.etag,
            lastModified = cached.lastModified,
            fetchedAt = Instant.now().toEpochMilli()
        )
        Files.write(metaPath, JacksonSupport.jsonMapper.writeValueAsBytes(meta))
    }

    private fun readCache(metaPath: Path, dataPath: Path): CachedEntry? {
        if (!Files.exists(metaPath) || !Files.exists(dataPath)) {
            return null
        }
        val meta = JacksonSupport.jsonMapper.readValue(
            Files.readAllBytes(metaPath),
            CacheMetadata::class.java
        )
        return CachedEntry(
            uri = meta.uri,
            etag = meta.etag,
            lastModified = meta.lastModified,
            fetchedAt = meta.fetchedAt,
            bytes = Files.readAllBytes(dataPath)
        )
    }

    private fun isExpired(entry: CachedEntry): Boolean {
        val fetched = Instant.ofEpochMilli(entry.fetchedAt)
        return Duration.between(fetched, Instant.now()) > config.ttl
    }

    private fun cacheKey(uri: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun isHttpUri(uri: String): Boolean {
        return uri.startsWith("http://") || uri.startsWith("https://")
    }
}

data class CacheHttpResponse(
    val status: Int,
    val bytes: ByteArray,
    val headers: Map<String, String>
)

interface HttpFetcher {
    suspend fun fetch(url: String, etag: String?, lastModified: String?): CacheHttpResponse
}

class DefaultHttpFetcher(
    private val config: CacheConfig
) : HttpFetcher {
    override suspend fun fetch(url: String, etag: String?, lastModified: String?): CacheHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.connectTimeout = config.connectTimeoutMs
        connection.readTimeout = config.readTimeoutMs
        if (!etag.isNullOrBlank()) {
            connection.setRequestProperty("If-None-Match", etag)
        }
        if (!lastModified.isNullOrBlank()) {
            connection.setRequestProperty("If-Modified-Since", lastModified)
        }
        connection.connect()

        val status = connection.responseCode
        val stream = if (status in 200..299 || status == 304) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val bytes = stream?.readBytes() ?: ByteArray(0)
        val headers = connection.headerFields
            .filterKeys { it != null }
            .mapValues { it.value?.firstOrNull().orEmpty() }
        return CacheHttpResponse(status = status, bytes = bytes, headers = headers)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CacheMetadata(
    val uri: String,
    val etag: String?,
    val lastModified: String?,
    val fetchedAt: Long
)

data class CachedEntry(
    val uri: String,
    val etag: String?,
    val lastModified: String?,
    val fetchedAt: Long,
    val bytes: ByteArray
)

