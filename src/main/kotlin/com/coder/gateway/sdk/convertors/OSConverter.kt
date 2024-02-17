package com.coder.gateway.sdk.convertors

import com.coder.gateway.util.OS
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * GSON serialiser/deserialiser for converting [OS] objects.
 */
class OSConverter : JsonSerializer<OS?>, JsonDeserializer<OS?> {
    override fun serialize(src: OS?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src?.toString() ?: "")
    }

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): OS? {
        return OS.from(json.asString)
    }
}
