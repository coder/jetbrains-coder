package com.coder.gateway.sdk.convertors

import com.coder.gateway.util.Arch
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * GSON serialiser/deserialiser for converting [Arch] objects.
 */
class ArchConverter : JsonSerializer<Arch?>, JsonDeserializer<Arch?> {
    override fun serialize(src: Arch?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src?.toString() ?: "")
    }

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Arch? {
        return Arch.from(json.asString)
    }
}
