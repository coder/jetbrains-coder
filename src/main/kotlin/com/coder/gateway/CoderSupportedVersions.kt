package com.coder.gateway

import com.coder.gateway.util.SemVer
import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "version.CoderSupportedVersions"

object CoderSupportedVersions : DynamicBundle(BUNDLE) {
    val minCompatibleCoderVersion = SemVer.parse(message("minCompatibleCoderVersion"))
    val maxCompatibleCoderVersion = SemVer.parse(message("maxCompatibleCoderVersion"))

    @JvmStatic
    @Suppress("SpreadOperator")
    private fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) = getMessage(key, *params)
}
