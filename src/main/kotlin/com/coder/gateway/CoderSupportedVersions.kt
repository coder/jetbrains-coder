package com.coder.gateway

import com.coder.gateway.sdk.CoderSemVer
import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "version.CoderSupportedVersions"

object CoderSupportedVersions : DynamicBundle(BUNDLE) {
    val minCompatibleCoderVersion = CoderSemVer.parse(message("minCompatibleCoderVersion"))
    val maxCompatibleCoderVersion = CoderSemVer.parse(message("maxCompatibleCoderVersion"))

    @JvmStatic
    @Suppress("SpreadOperator")
    private fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) = getMessage(key, *params)
}
