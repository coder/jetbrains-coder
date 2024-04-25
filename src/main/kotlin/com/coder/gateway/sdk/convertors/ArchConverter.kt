package com.coder.gateway.sdk.convertors

import com.coder.gateway.util.Arch
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * Serializer/deserializer for converting [Arch] objects.
 */
class ArchConverter {
    @ToJson fun toJson(src: Arch?): String = src?.toString() ?: ""

    @FromJson fun fromJson(src: String): Arch? = Arch.from(src)
}
