package com.coder.gateway.sdk.convertors

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.util.UUID

/**
 * Serializer/deserializer for converting [UUID] objects.
 */
class UUIDConverter {
    @ToJson fun toJson(src: UUID): String = src.toString()

    @FromJson fun fromJson(src: String): UUID = UUID.fromString(src)
}
