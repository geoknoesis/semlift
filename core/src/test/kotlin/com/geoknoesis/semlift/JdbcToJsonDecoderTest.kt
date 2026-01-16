package io.semlift

import io.semlift.jsonld.KotlinxJsonCodec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.DriverManager

class JdbcToJsonDecoderTest {
    @Test
    fun `decodes jdbc rows to json array`() = kotlinx.coroutines.runBlocking {
        val url = "jdbc:sqlite:file:memdb1?mode=memory&cache=shared"
        val connection = DriverManager.getConnection(url)
        connection.createStatement().use { statement ->
            statement.execute("CREATE TABLE users (id INTEGER, name TEXT)")
            statement.execute("INSERT INTO users (id, name) VALUES (1, 'Ada'), (2, 'Linus')")
        }

        val decoder = JdbcToJsonDecoder(fetchSize = 2)
        val document = decoder.decode(InputSource.Jdbc(jdbcUrl = url, table = "users"))
        val codec = KotlinxJsonCodec()
        val actual = codec.parse(document)

        val expected = JsonArray(
            listOf(
                JsonObject(mapOf("id" to JsonPrimitive(1), "name" to JsonPrimitive("Ada"))),
                JsonObject(mapOf("id" to JsonPrimitive(2), "name" to JsonPrimitive("Linus")))
            )
        )
        assertThat(actual).isEqualTo(expected)

        connection.close()
    }
}

