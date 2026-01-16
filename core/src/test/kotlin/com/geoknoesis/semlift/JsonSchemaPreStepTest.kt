package io.semlift

import io.semlift.jena.JenaSemanticLifter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class JsonSchemaPreStepTest {
    private val schema = """
        {
          "${'$'}schema": "https://json-schema.org/draft/2019-09/schema",
          "type": "object",
          "required": ["id"],
          "properties": {
            "id": { "type": "string" }
          }
        }
    """.trimIndent().toByteArray()

    private val invalidJson = """{ "name": "Ada" }""".trimIndent().toByteArray()

    @Test
    fun `json schema strict validation fails lift`() = kotlinx.coroutines.runBlocking {
        val plan = liftPlan {
            contextInline("""{ "@context": {} }""".trimIndent().toByteArray())
            pre(PreStep.JsonSchema(schema = schema, strict = true))
        }

        val lifter = JenaSemanticLifter()
        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                lifter.lift(InputSource.Json(invalidJson), plan, LiftOptions())
            }
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("JSON Schema validation failed")
    }

    @Test
    fun `json schema non-strict adds warning and continues`() = kotlinx.coroutines.runBlocking {
        val plan = liftPlan {
            contextInline("""{ "@context": {} }""".trimIndent().toByteArray())
            pre(PreStep.JsonSchema(schema = schema, strict = false))
        }

        val lifter = JenaSemanticLifter()
        val result = lifter.lift(InputSource.Json(invalidJson), plan, LiftOptions())

        assertThat(result.diagnostics.warnings).isNotEmpty
        assertThat(result.diagnostics.warnings.first()).contains("JSON Schema validation failed")
    }
}

