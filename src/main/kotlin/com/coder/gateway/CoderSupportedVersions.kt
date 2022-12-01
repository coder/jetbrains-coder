package com.coder.gateway

import com.coder.gateway.sdk.CoderSemVer
import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "version.CoderSupportedVersions"

object CoderSupportedVersions : DynamicBundle(BUNDLE) {
    val lastTestedVersion = CoderSemVer.parse(message("lastTestedCoderVersion"))

    @Suppress("SpreadOperator")
    @JvmStatic
    private fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) = getMessage(key, *params)
}