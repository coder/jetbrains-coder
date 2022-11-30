package com.coder.gateway.sdk


class CoderSemVer(private val major: Int = 0, private val minor: Int = 0) {

    init {
        require(major >= 0) { "Coder major version must be a positive number" }
        require(minor >= 0) { "Coder minor version must be a positive number" }
    }

    fun isCompatibleWith(other: CoderSemVer): Boolean {
        // in the initial development phase minor changes when there are API incompatibilities
        if (this.major == 0) {
            if (other.major > 0) return false
            return this.minor == other.minor
        }
        return this.major <= other.major
    }

    companion object {
        private val pattern = """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$""".toRegex()

        fun isValidVersion(semVer: String) = pattern.matchEntire(semVer.trimStart('v')) != null

        fun parse(semVer: String): CoderSemVer {
            val matchResult = pattern.matchEntire(semVer.trimStart('v')) ?: throw IllegalArgumentException("$semVer could not be parsed")
            return CoderSemVer(
                if (matchResult.groupValues[1].isNotEmpty()) matchResult.groupValues[1].toInt() else 0,
                if (matchResult.groupValues[2].isNotEmpty()) matchResult.groupValues[2].toInt() else 0,
            )
        }
    }
}