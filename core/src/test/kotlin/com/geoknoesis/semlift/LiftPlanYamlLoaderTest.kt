package io.semlift

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class LiftPlanYamlLoaderTest {
    @Test
    fun `loads kotlin json transform by class name`() {
        val yaml = """
            context:
              ref: "classpath:/samples/context.jsonld"
            additionalSteps:
              - type: kotlin-json
                ref: "io.semlift.TestJsonTransform"
        """.trimIndent()

        val loader = LiftPlanYamlLoader()
        val plan = loader.load(yaml.toByteArray())

        assertThat(plan.pre).hasSize(1)
        val step = plan.pre.first() as PreStep.KotlinJson
        val input = JsonObject(mapOf("name" to JsonPrimitive("Ada")))
        val output = step.transform.apply(input)
        assertThat(output.jsonObject["status"]?.jsonPrimitive?.content).isEqualTo("active")
    }

    @Test
    fun `loads json schema step`() {
        val yaml = """
            context:
              ref: "classpath:/samples/context.jsonld"
            additionalSteps:
              - type: json-schema
                code: |
                  { "type": "object" }
                strict: false
        """.trimIndent()

        val loader = LiftPlanYamlLoader()
        val plan = loader.load(yaml.toByteArray())

        assertThat(plan.pre).hasSize(1)
        val step = plan.pre.first() as PreStep.JsonSchema
        assertThat(step.strict).isFalse
        assertThat(step.schema).isNotEmpty
    }

    @Test
    fun `resolves imports and metadata`() {
        val tempDir = Files.createTempDirectory("semlift-plan")
        val base = """
            context:
              json:
                "@context":
                  "@vocab": "https://example.com/"
            additionalSteps:
              - type: jq
                code: ".base = true"
        """.trimIndent()
        val child = """
            imports:
              - ref: "base.yaml"
            metadata:
              title: "Child Plan"
              version: "1.2.3"
            context:
              json:
                "@context":
                  "name": "https://example.com/name"
            additionalSteps:
              - type: json-schema
                code: '{ "type": "object" }'
        """.trimIndent()
        Files.writeString(tempDir.resolve("base.yaml"), base)
        Files.writeString(tempDir.resolve("child.yaml"), child)

        val loader = LiftPlanYamlLoader()
        val plan = loader.load(tempDir.resolve("child.yaml").toString())

        assertThat(plan.pre).hasSize(2)
        assertThat(plan.metadata?.title).isEqualTo("Child Plan")
        assertThat(plan.metadata?.version).isEqualTo("1.2.3")

        val contextBytes = (plan.context as ContextSpec.Inline).jsonLd
        val contextNode = JacksonSupport.jsonMapper.readTree(contextBytes)
        val contextArray = contextNode.get("@context")
        assertThat(contextArray.isArray).isTrue
        assertThat(contextBytes.decodeToString()).contains("https://example.com/")
        assertThat(contextBytes.decodeToString()).contains("https://example.com/name")
    }

    @Test
    fun `loads id rules from plan and context`() {
        val yaml = """
            context:
              ref: "classpath:/samples/context.jsonld"
              idRules:
                - path: "/id"
                  template: "https://example.com/{code}"
            idRules:
              - path: "/otherId"
                template: "https://example.com/{type}/{code}"
        """.trimIndent()

        val loader = LiftPlanYamlLoader()
        val plan = loader.load(yaml.toByteArray())

        assertThat(plan.idRules).hasSize(2)
        assertThat(plan.idRules[0].path).isEqualTo("/id")
        assertThat(plan.idRules[1].path).isEqualTo("/otherId")
    }

    @Test
    fun `loads id rules from ref file`() {
        val tempDir = Files.createTempDirectory("semlift-idrules")
        val rules = """
            - path: "/id"
              template: "https://example.com/{code}"
        """.trimIndent()
        Files.writeString(tempDir.resolve("id-rules.yaml"), rules)
        val yaml = """
            context:
              ref: "classpath:/samples/context.jsonld"
            idRules:
              ref: "id-rules.yaml"
        """.trimIndent()

        val loader = LiftPlanYamlLoader()
        val plan = loader.load(yaml.toByteArray(), tempDir)

        assertThat(plan.idRules).hasSize(1)
        assertThat(plan.idRules[0].template).isEqualTo("https://example.com/{code}")
    }
}

class TestJsonTransform : JsonTransform {
    override fun apply(input: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement {
        return jsonTransform { set("/status", "active") }.apply(input)
    }
}

