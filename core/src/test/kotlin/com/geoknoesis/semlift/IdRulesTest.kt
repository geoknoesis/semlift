package io.semlift

import io.semlift.jena.JenaSemanticLifter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IdRulesTest {
    @Test
    fun `applies id rules before context embedding`() {
        val context = """
            {
              "@context": {
                "@vocab": "https://example.com/vocab/",
                "id": "@id",
                "type": "https://example.com/type",
                "code": "https://example.com/code"
              }
            }
        """.trimIndent().toByteArray()

        val plan = LiftPlan(
            context = ContextSpec.Inline(context),
            idRules = listOf(IdRule(path = "/id", template = "https://example.com/{type}/{code}"))
        )

        val json = """
            { "type": "thing", "code": "abc" }
        """.trimIndent().toByteArray()

        val result = JenaSemanticLifter().lift(InputSource.Json(json), plan, LiftOptions())
        val rdf = result.rdf.bytes.decodeToString()

        assertThat(rdf).contains("https://example.com/thing/abc")
    }
}

