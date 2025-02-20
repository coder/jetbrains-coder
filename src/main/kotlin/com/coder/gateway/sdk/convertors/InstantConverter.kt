package com.coder.gateway.sdk.convertors

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

/**
 * Serializer/deserializer for converting [Instant] objects.
 */
class InstantConverter {
    @ToJson fun toJson(src: Instant?): String = FORMATTER.format(src)

    @FromJson fun fromJson(src: String): Instant? = FORMATTER.parse(src) { temporal: TemporalAccessor? ->
        Instant.from(temporal)
    }

    companion object {
        private val FORMATTER = DateTimeFormatter.ISO_INSTANT
    }
}
