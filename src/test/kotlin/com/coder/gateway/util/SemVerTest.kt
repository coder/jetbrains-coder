package com.coder.gateway.util

import kotlin.test.Test
import kotlin.test.assertEquals

import kotlin.test.assertFailsWith

internal class SemVerTest {
    @Test
    fun testParseSemVer() {
        val tests = mapOf(
            "0.0.4"                                                  to SemVer(0L, 0L, 4L),
            "1.2.3"                                                  to SemVer(1L, 2L, 3L),
            "10.20.30"                                               to SemVer(10L, 20L, 30L),
            "1.1.2-prerelease+meta"                                  to SemVer(1L, 1L, 2L),
            "1.1.2+meta"                                             to SemVer(1L, 1L, 2L),
            "1.1.2+meta-valid"                                       to SemVer(1L, 1L, 2L),
            "1.0.0-alpha"                                            to SemVer(1L, 0L, 0L),
            "1.0.0-beta"                                             to SemVer(1L, 0L, 0L),
            "1.0.0-alpha.beta"                                       to SemVer(1L, 0L, 0L),
            "1.0.0-alpha.beta.1"                                     to SemVer(1L, 0L, 0L),
            "1.0.0-alpha.1"                                          to SemVer(1L, 0L, 0L),
            "1.0.0-alpha0.valid"                                     to SemVer(1L, 0L, 0L),
            "1.0.0-alpha.0valid"                                     to SemVer(1L, 0L, 0L),
            "1.0.0-alpha-a.b-c-somethinglong+build.1-aef.1-its-okay" to SemVer(1L, 0L, 0L),
            "1.0.0-rc.1+build.1"                                     to SemVer(1L, 0L, 0L),
            "2.0.0-rc.1+build.123"                                   to SemVer(2L, 0L, 0L),
            "1.2.3-beta"                                             to SemVer(1L, 2L, 3L),
            "10.2.3-DEV-SNAPSHOT"                                    to SemVer(10L, 2L, 3L),
            "1.2.3-SNAPSHOT-123"                                     to SemVer(1L, 2L, 3L),
            "1.0.0"                                                  to SemVer(1L, 0L, 0L),
            "2.0.0"                                                  to SemVer(2L, 0L, 0L),
            "1.1.7"                                                  to SemVer(1L, 1L, 7L),
            "2.0.0+build.1848"                                       to SemVer(2L, 0L, 0L),
            "2.0.1-alpha.1227"                                       to SemVer(2L, 0L, 1L),
            "1.0.0-alpha+beta"                                       to SemVer(1L, 0L, 0L),
            "1.2.3----RC-SNAPSHOT.12.9.1--.12+788"                   to SemVer(1L, 2L, 3L),
            "1.2.3----R-S.12.9.1--.12+meta"                          to SemVer(1L, 2L, 3L),
            "1.2.3----RC-SNAPSHOT.12.9.1--.12"                       to SemVer(1L, 2L, 3L),
            "1.0.0+0.build.1-rc.10000aaa-kk-0.1"                     to SemVer(1L, 0L, 0L),
            "2147483647.2147483647.2147483647"                       to SemVer(2147483647L, 2147483647L, 2147483647L),
            "1.0.0-0A.is.legal"                                      to SemVer(1L, 0L, 0L),
        )

        tests.forEach {
            assertEquals(it.value, SemVer.parse(it.key))
            assertEquals(it.value, SemVer.parse("v"+it.key))
        }
    }

    @Test
    fun testComparison() {
        val tests = listOf(
            // First version > second version.
            Triple(SemVer(1, 0, 0), SemVer(0, 0, 0), 1),
            Triple(SemVer(1, 0, 0), SemVer(0, 0, 1), 1),
            Triple(SemVer(1, 0, 0), SemVer(0, 1, 0), 1),
            Triple(SemVer(1, 0, 0), SemVer(0, 1, 1), 1),

            Triple(SemVer(2, 0, 0), SemVer(1, 0, 0), 1),
            Triple(SemVer(2, 0, 0), SemVer(1, 3, 0), 1),
            Triple(SemVer(2, 0, 0), SemVer(1, 0, 3), 1),
            Triple(SemVer(2, 0, 0), SemVer(1, 3, 3), 1),

            Triple(SemVer(0, 1, 0), SemVer(0, 0, 1), 1),
            Triple(SemVer(0, 2, 0), SemVer(0, 1, 0), 1),
            Triple(SemVer(0, 2, 0), SemVer(0, 1, 2), 1),

            Triple(SemVer(0, 0, 2), SemVer(0, 0, 1), 1),

            // First version == second version.
            Triple(SemVer(0, 0, 0), SemVer(0, 0, 0), 0),
            Triple(SemVer(1, 0, 0), SemVer(1, 0, 0), 0),
            Triple(SemVer(1, 1, 0), SemVer(1, 1, 0), 0),
            Triple(SemVer(1, 1, 1), SemVer(1, 1, 1), 0),
            Triple(SemVer(0, 1, 0), SemVer(0, 1, 0), 0),
            Triple(SemVer(0, 1, 1), SemVer(0, 1, 1), 0),
            Triple(SemVer(0, 0, 1), SemVer(0, 0, 1), 0),

            // First version < second version.
            Triple(SemVer(0, 0, 0), SemVer(1, 0, 0), -1),
            Triple(SemVer(0, 0, 1), SemVer(1, 0, 0), -1),
            Triple(SemVer(0, 1, 0), SemVer(1, 0, 0), -1),
            Triple(SemVer(0, 1, 1), SemVer(1, 0, 0), -1),

            Triple(SemVer(1, 0, 0), SemVer(2, 0, 0), -1),
            Triple(SemVer(1, 3, 0), SemVer(2, 0, 0), -1),
            Triple(SemVer(1, 0, 3), SemVer(2, 0, 0), -1),
            Triple(SemVer(1, 3, 3), SemVer(2, 0, 0), -1),


            Triple(SemVer(0, 0, 1), SemVer(0, 1, 0), -1),
            Triple(SemVer(0, 1, 0), SemVer(0, 2, 0), -1),
            Triple(SemVer(0, 1, 2), SemVer(0, 2, 0), -1),

            Triple(SemVer(0, 0, 1), SemVer(0, 0, 2), -1),
        )

        tests.forEach {
            assertEquals(it.third, it.first.compareTo(it.second))
        }
    }

    @Test
    fun testInvalidVersion() {
        val tests = listOf(
            "",
            "foo",
            "1.foo.2",
        )
        tests.forEach{
            assertFailsWith(
                exceptionClass = InvalidVersionException::class,
                block = { SemVer.parse(it) })
        }
    }
}
