package io.semlift

import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

class DefaultResourceResolver(
    private val classLoader: ClassLoader = DefaultResourceResolver::class.java.classLoader
) : ResourceResolver {
    override suspend fun resolve(uri: String): ByteArray {
        return when {
            uri.startsWith("classpath:") -> {
                val path = uri.removePrefix("classpath:")
                val normalized = path.removePrefix("/")
                classLoader.getResourceAsStream(normalized)
                    ?.readBytes()
                    ?: error("Classpath resource not found: $uri")
            }
            uri.startsWith("http://") || uri.startsWith("https://") -> {
                URL(uri).openStream().use { it.readBytes() }
            }
            uri.startsWith("file:") -> {
                Files.readAllBytes(Paths.get(URI.create(uri)))
            }
            else -> {
                Files.readAllBytes(Paths.get(uri))
            }
        }
    }
}

