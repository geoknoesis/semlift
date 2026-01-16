package io.semlift

import io.semlift.jena.JenaSemanticLifter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SemanticLifterE2ETest {
    @Test
    fun `lifts json and validates shacl`() = kotlinx.coroutines.runBlocking {
        val json = resourceBytes("/samples/sample.json")
        val loader = LiftPlanYamlLoader()
        val plan = loader.load(resourceBytes("/samples/semantic-uplift.yaml"))

        val lifter = JenaSemanticLifter()
        val result = lifter.lift(InputSource.Json(json), plan, LiftOptions())

        assertThat(result.report).isNotNull
        assertThat(result.report?.conforms).isTrue
        assertThat(result.rdf.bytes).isNotEmpty
    }

    private fun resourceBytes(path: String): ByteArray {
        return requireNotNull(javaClass.getResourceAsStream(path)) {
            "Missing test resource: $path"
        }.use { it.readBytes() }
    }
}

