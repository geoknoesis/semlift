package io.semlift

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion

data class JsonSchemaValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

class JsonSchemaValidator(
    private val factory: JsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909)
) {
    fun validate(schemaBytes: ByteArray, instanceBytes: ByteArray): JsonSchemaValidationResult {
        val schemaNode = JacksonSupport.jsonMapper.readTree(schemaBytes)
        val instanceNode = JacksonSupport.jsonMapper.readTree(instanceBytes)
        return validate(schemaNode, instanceNode)
    }

    fun validate(schemaNode: JsonNode, instanceNode: JsonNode): JsonSchemaValidationResult {
        val schema = factory.getSchema(schemaNode)
        val messages = schema.validate(instanceNode)
        val errors = messages.map { it.message }
        return JsonSchemaValidationResult(errors.isEmpty(), errors)
    }
}

