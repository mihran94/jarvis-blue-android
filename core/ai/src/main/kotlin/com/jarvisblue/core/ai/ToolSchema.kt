package com.jarvisblue.core.ai

import com.jarvisblue.core.model.ParamType
import com.jarvisblue.core.model.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Converts the declarative [ToolSpec] into the JSON-Schema shapes the different
 * providers expect (OpenAI function schema, Gemini functionDeclarations).
 */
object ToolSchema {

    fun toJsonSchema(spec: ToolSpec): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            spec.parameters.forEach { param ->
                put(param.name, parameterSchema(param))
            }
        })
        val required = spec.parameters.filter { it.required }.map { it.name }
        if (required.isNotEmpty()) {
            put("required", JsonArray(required.map { JsonPrimitive(it) }))
        }
        put("additionalProperties", false)
    }

    fun toOpenAiTool(spec: ToolSpec): JsonObject = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject {
            put("name", spec.name)
            put("description", spec.description)
            put("parameters", toJsonSchema(spec))
        })
    }

    fun toGeminiDeclaration(spec: ToolSpec): JsonObject = buildJsonObject {
        put("name", spec.name)
        put("description", spec.description)
        put("parameters", toGeminiParameters(toJsonSchema(spec)))
    }

    /**
     * Gemini `functionDeclarations[].parameters` is the limited Google Schema
     * message, NOT arbitrary JSON Schema. In particular `additionalProperties`
     * triggers HTTP 400 at that path (even though OpenAI accepts it).
     *
     * Keep the internal/OpenAI schema strict and sanitize ONLY at the Gemini
     * HTTP boundary. Recursion protects nested objects and array items too.
     */
    fun toGeminiParameters(openAiSchema: JsonObject): JsonObject = buildJsonObject {
        openAiSchema.forEach { (field, value) ->
            when (field) {
                "type" -> put("type", value.jsonPrimitive.content.uppercase(Locale.ROOT))
                "properties" -> if (value is JsonObject) {
                    put("properties", buildJsonObject {
                        value.forEach { (name, child) ->
                            if (child is JsonObject) put(name, toGeminiParameters(child))
                        }
                    })
                }
                "items" -> if (value is JsonObject) {
                    put("items", toGeminiParameters(value))
                }
                // Google Schema currently does not accept these from JSON Schema.
                "additionalProperties", "default" -> Unit
                // maxLength is an int64 string in the Google Schema protocol.
                "maxLength" -> put("maxLength", value.jsonPrimitive.content)
                "description", "enum", "required", "format", "nullable",
                "minimum", "maximum", "minItems", "maxItems" -> put(field, value)
                // Unknown fields are deliberately not forwarded to Gemini.
            }
        }
    }

    private fun parameterSchema(param: com.jarvisblue.core.model.ToolParameter): JsonObject =
        buildJsonObject {
            when (param.type) {
                ParamType.STRING -> put("type", "string")
                ParamType.INTEGER -> put("type", "integer")
                ParamType.NUMBER -> put("type", "number")
                ParamType.BOOLEAN -> put("type", "boolean")
                ParamType.OBJECT -> put("type", "object")
                ParamType.ARRAY -> put("type", "array")
            }
            put("description", param.description)
            if (param.enumValues.isNotEmpty()) {
                put("enum", buildJsonArray { param.enumValues.forEach { add(JsonPrimitive(it)) } })
            }
            param.minimum?.let { put("minimum", it) }
            param.maximum?.let { put("maximum", it) }
            param.maxLength?.let { put("maxLength", it) }
            param.default?.let { put("default", JsonPrimitive(it)) }
        }
}
