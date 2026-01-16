package io.semlift

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.semlift.jena.JenaBackend

class ShaclGeneratorTest {
    @Test
    fun `generates shacl from schema`() {
        val schema = """
            {
              "title": "Person",
              "type": "object",
              "required": ["id", "age"],
              "properties": {
                "id": { "type": "string" },
                "age": { "type": "integer", "minimum": 0 },
                "status": { "enum": ["active", "inactive"] }
              }
            }
        """.trimIndent().toByteArray()

        val shacl = JsonSchemaToShacl().generate(schema, backend = JenaBackend())

        assertThat(shacl).contains("sh:NodeShape")
        assertThat(shacl).contains("sh:minCount")
        assertThat(shacl).contains("sh:datatype")
        assertThat(shacl).contains("xsd:integer")
        assertThat(shacl).contains("sh:in")
    }
}

