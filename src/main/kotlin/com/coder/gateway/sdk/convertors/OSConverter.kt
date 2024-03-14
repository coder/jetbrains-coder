package com.coder.gateway.sdk.convertors

import com.coder.gateway.util.OS
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * Serializer/deserializer for converting [OS] objects.
 */
class OSConverter {
    @ToJson fun toJson(src: OS?): String = src?.toString() ?: ""
    @FromJson fun fromJson(src: String): OS? = OS.from(src)
}
