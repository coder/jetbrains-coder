package com.coder.gateway.sdk

import spock.lang.Unroll

@Unroll
class CoderSemVerTest extends spock.lang.Specification {

    def "#semver is valid"() {
        expect:
        CoderSemVer.isValidVersion(semver)

        where:
        semver << ['0.0.4',
                   '1.2.3',
                   '10.20.30',
                   '1.1.2-prerelease+meta',
                   '1.1.2+meta',
                   '1.1.2+meta-valid',
                   '1.0.0-alpha',
                   '1.0.0-beta',
                   '1.0.0-alpha.beta',
                   '1.0.0-alpha.beta.1',
                   '1.0.0-alpha.1',
                   '1.0.0-alpha0.valid',
                   '1.0.0-alpha.0valid',
                   '1.0.0-alpha-a.b-c-somethinglong+build.1-aef.1-its-okay',
                   '1.0.0-rc.1+build.1',
                   '2.0.0-rc.1+build.123',
                   '1.2.3-beta',
                   '10.2.3-DEV-SNAPSHOT',
                   '1.2.3-SNAPSHOT-123',
                   '1.0.0',
                   '2.0.0',
                   '1.1.7',
                   '2.0.0+build.1848',
                   '2.0.1-alpha.1227',
                   '1.0.0-alpha+beta',
                   '1.2.3----RC-SNAPSHOT.12.9.1--.12+788',
                   '1.2.3----R-S.12.9.1--.12+meta',
                   '1.2.3----RC-SNAPSHOT.12.9.1--.12',
                   '1.0.0+0.build.1-rc.10000aaa-kk-0.1',
                   '2147483647.2147483647.2147483647',
                   '1.0.0-0A.is.legal']
    }

    def "#semver version is parsed and correct major, minor and patch values are extracted"() {
        expect:
        CoderSemVer.parse(semver) == expectedCoderSemVer

        where:
        semver                                                   || expectedCoderSemVer
        '0.0.4'                                                  || new CoderSemVer(0L, 0L, 4L)
        '1.2.3'                                                  || new CoderSemVer(1L, 2L, 3L)
        '10.20.30'                                               || new CoderSemVer(10L, 20L, 30L)
        '1.1.2-prerelease+meta'                                  || new CoderSemVer(1L, 1L, 2L)
        '1.1.2+meta'                                             || new CoderSemVer(1L, 1L, 2L)
        '1.1.2+meta-valid'                                       || new CoderSemVer(1L, 1L, 2L)
        '1.0.0-alpha'                                            || new CoderSemVer(1L, 0L, 0L)
        '1.0.0-beta'                                             || new CoderSemVer(1L, 0L, 0L)
        '1.0.0-alpha.beta'                                       || new CoderSemVer(1L, 0L, 0L)
        '1.0.0-alpha.beta.1'                                     || new CoderSemVer(1L, 0L, 0L)
        '1.0.0-alpha.1'                                          || new CoderSemVer(1L, 0L, 0L)
        '1.0.0-alpha0.valid'                                     || new CoderSemVer(1L, 0L, 0L)
        '1.0.0-alpha.0valid'                                     || new CoderSemVer(1L, 0L, 0L)
        '1.0.0-alpha-a.b-c-somethinglong+build.1-aef.1-its-okay' || new CoderSemVer(1L, 0L, 0L)
        '1.0.0-rc.1+build.1'                                     || new CoderSemVer(1L, 0L, 0L)
        '2.0.0-rc.1+build.123'                                   || new CoderSemVer(2L, 0L, 0L)
        '1.2.3-beta'                                             || new CoderSemVer(1L, 2L, 3L)
        '10.2.3-DEV-SNAPSHOT'                                    || new CoderSemVer(10L, 2L, 3L)
        '1.2.3-SNAPSHOT-123'                                     || new CoderSemVer(1L, 2L, 3L)
        '1.0.0'                                                  || new CoderSemVer(1L, 0L, 0L)
        '2.0.0'                                                  || new CoderSemVer(2L, 0L, 0L)
        '1.1.7'                                                  || new CoderSemVer(1L, 1L, 7L)
        '2.0.0+build.1848'                                       || new CoderSemVer(2L, 0L, 0L)
        '2.0.1-alpha.1227'                                       || new CoderSemVer(2L, 0L, 1L)
        '1.0.0-alpha+beta'                                       || new CoderSemVer(1L, 0L, 0L)
        '1.2.3----RC-SNAPSHOT.12.9.1--.12+788'                   || new CoderSemVer(1L, 2L, 3L)
        '1.2.3----R-S.12.9.1--.12+meta'                          || new CoderSemVer(1L, 2L, 3L)
        '1.2.3----RC-SNAPSHOT.12.9.1--.12'                       || new CoderSemVer(1L, 2L, 3L)
        '1.0.0+0.build.1-rc.10000aaa-kk-0.1'                     || new CoderSemVer(1L, 0L, 0L)
        '2147483647.2147483647.2147483647'                       || new CoderSemVer(2147483647L, 2147483647L, 2147483647L)
        '1.0.0-0A.is.legal'                                      || new CoderSemVer(1L, 0L, 0L)
    }

    def "#semver is considered valid even when it starts with `v`"() {
        expect:
        CoderSemVer.isValidVersion(semver)

        where:
        semver << ['v0.0.4',
                   'v1.2.3',
                   'v10.20.30',
                   'v1.1.2-prerelease+meta',
                   'v1.1.2+meta',
                   'v1.1.2+meta-valid',
                   'v1.0.0-alpha',
                   'v1.0.0-beta',
                   'v1.0.0-alpha.beta',
                   'v1.0.0-alpha.beta.1',
                   'v1.0.0-alpha.1',
                   'v1.0.0-alpha0.valid',
                   'v1.0.0-alpha.0valid',
                   'v1.0.0-alpha-a.b-c-somethinglong+build.1-aef.1-its-okay',
                   'v1.0.0-rc.1+build.1',
                   'v2.0.0-rc.1+build.123',
                   'v1.2.3-beta',
                   'v10.2.3-DEV-SNAPSHOT',
                   'v1.2.3-SNAPSHOT-123',
                   'v1.0.0',
                   'v2.0.0',
                   'v1.1.7',
                   'v2.0.0+build.1848',
                   'v2.0.1-alpha.1227',
                   'v1.0.0-alpha+beta',
                   'v1.2.3----RC-SNAPSHOT.12.9.1--.12+788',
                   'v1.2.3----R-S.12.9.1--.12+meta',
                   'v1.2.3----RC-SNAPSHOT.12.9.1--.12',
                   'v1.0.0+0.build.1-rc.10000aaa-kk-0.1',
                   'v2147483647.2147483647.2147483647',
                   'v1.0.0-0A.is.legal']
    }

    def "#semver is parsed and correct major, minor and patch values are extracted even though the version starts with a `v`"() {
        expect:
        CoderSemVer.parse(semver) == expectedCoderSemVer

        where:
        semver                                                    || expectedCoderSemVer
        'v0.0.4'                                                  || new CoderSemVer(0L, 0L, 4L)
        'v1.2.3'                                                  || new CoderSemVer(1L, 2L, 3L)
        'v10.20.30'                                               || new CoderSemVer(10L, 20L, 30L)
        'v1.1.2-prerelease+meta'                                  || new CoderSemVer(1L, 1L, 2L)
        'v1.1.2+meta'                                             || new CoderSemVer(1L, 1L, 2L)
        'v1.1.2+meta-valid'                                       || new CoderSemVer(1L, 1L, 2L)
        'v1.0.0-alpha'                                            || new CoderSemVer(1L, 0L, 0L)
        'v1.0.0-beta'                                             || new CoderSemVer(1L, 0L, 0L)
        'v1.0.0-alpha.beta'                                       || new CoderSemVer(1L, 0L, 0L)
        'v1.0.0-alpha.beta.1'                                     || new CoderSemVer(1L, 0L, 0L)
        'v1.0.0-alpha.1'                                          || new CoderSemVer(1L, 0L, 0L)
        'v1.0.0-alpha0.valid'                                     || new CoderSemVer(1L, 0L, 0L)
        'v1.0.0-alpha.0valid'                                     || new CoderSemVer(1L, 0L, 0L)
        'v1.0.0-alpha-a.b-c-somethinglong+build.1-aef.1-its-okay' || new CoderSemVer(1L, 0L, 0L)
        'v1.0.0-rc.1+build.1'                                     || new CoderSemVer(1L, 0L, 0L)
        'v2.0.0-rc.1+build.123'                                   || new CoderSemVer(2L, 0L, 0L)
        'v1.2.3-beta'                                             || new CoderSemVer(1L, 2L, 3L)
        'v10.2.3-DEV-SNAPSHOT'                                    || new CoderSemVer(10L, 2L, 3L)
        'v1.2.3-SNAPSHOT-123'                                     || new CoderSemVer(1L, 2L, 3L)
        'v1.0.0'                                                  || new CoderSemVer(1L, 0L, 0L)
        'v2.0.0'                                                  || new CoderSemVer(2L, 0L, 0L)
        'v1.1.7'                                                  || new CoderSemVer(1L, 1L, 7L)
        'v2.0.0+build.1848'                                       || new CoderSemVer(2L, 0L, 0L)
        'v2.0.1-alpha.1227'                                       || new CoderSemVer(2L, 0L, 1L)
        'v1.0.0-alpha+beta'                                       || new CoderSemVer(1L, 0L, 0L)
        'v1.2.3----RC-SNAPSHOT.12.9.1--.12+788'                   || new CoderSemVer(1L, 2L, 3L)
        'v1.2.3----R-S.12.9.1--.12+meta'                          || new CoderSemVer(1L, 2L, 3L)
        'v1.2.3----RC-SNAPSHOT.12.9.1--.12'                       || new CoderSemVer(1L, 2L, 3L)
        'v1.0.0+0.build.1-rc.10000aaa-kk-0.1'                     || new CoderSemVer(1L, 0L, 0L)
        'v2147483647.2147483647.2147483647'                       || new CoderSemVer(2147483647L, 2147483647L, 2147483647L)
        'v1.0.0-0A.is.legal'                                      || new CoderSemVer(1L, 0L, 0L)
    }

    def "#firstVersion is > than #secondVersion"() {
        expect:
        firstVersion <=> secondVersion == 1

        where:
        firstVersion             | secondVersion
        new CoderSemVer(1, 0, 0) | new CoderSemVer(0, 0, 0)
        new CoderSemVer(1, 0, 0) | new CoderSemVer(0, 0, 1)
        new CoderSemVer(1, 0, 0) | new CoderSemVer(0, 1, 0)
        new CoderSemVer(1, 0, 0) | new CoderSemVer(0, 1, 1)

        new CoderSemVer(2, 0, 0) | new CoderSemVer(1, 0, 0)
        new CoderSemVer(2, 0, 0) | new CoderSemVer(1, 3, 0)
        new CoderSemVer(2, 0, 0) | new CoderSemVer(1, 0, 3)
        new CoderSemVer(2, 0, 0) | new CoderSemVer(1, 3, 3)


        new CoderSemVer(0, 1, 0) | new CoderSemVer(0, 0, 1)
        new CoderSemVer(0, 2, 0) | new CoderSemVer(0, 1, 0)
        new CoderSemVer(0, 2, 0) | new CoderSemVer(0, 1, 2)

        new CoderSemVer(0, 0, 2) | new CoderSemVer(0, 0, 1)
    }

    def "#firstVersion is == #secondVersion"() {
        expect:
        firstVersion <=> secondVersion == 0

        where:
        firstVersion             | secondVersion
        new CoderSemVer(0, 0, 0) | new CoderSemVer(0, 0, 0)
        new CoderSemVer(1, 0, 0) | new CoderSemVer(1, 0, 0)
        new CoderSemVer(1, 1, 0) | new CoderSemVer(1, 1, 0)
        new CoderSemVer(1, 1, 1) | new CoderSemVer(1, 1, 1)
        new CoderSemVer(0, 1, 0) | new CoderSemVer(0, 1, 0)
        new CoderSemVer(0, 1, 1) | new CoderSemVer(0, 1, 1)
        new CoderSemVer(0, 0, 1) | new CoderSemVer(0, 0, 1)

    }

    def "#firstVersion is < than #secondVersion"() {
        expect:
        firstVersion <=> secondVersion == -1

        where:
        firstVersion             | secondVersion
        new CoderSemVer(0, 0, 0) | new CoderSemVer(1, 0, 0)
        new CoderSemVer(0, 0, 1) | new CoderSemVer(1, 0, 0)
        new CoderSemVer(0, 1, 0) | new CoderSemVer(1, 0, 0)
        new CoderSemVer(0, 1, 1) | new CoderSemVer(1, 0, 0)

        new CoderSemVer(1, 0, 0) | new CoderSemVer(2, 0, 0)
        new CoderSemVer(1, 3, 0) | new CoderSemVer(2, 0, 0)
        new CoderSemVer(1, 0, 3) | new CoderSemVer(2, 0, 0)
        new CoderSemVer(1, 3, 3) | new CoderSemVer(2, 0, 0)


        new CoderSemVer(0, 0, 1) | new CoderSemVer(0, 1, 0)
        new CoderSemVer(0, 1, 0) | new CoderSemVer(0, 2, 0)
        new CoderSemVer(0, 1, 2) | new CoderSemVer(0, 2, 0)

        new CoderSemVer(0, 0, 1) | new CoderSemVer(0, 0, 2)
    }

    def 'in closed range comparison returns true when the version is equal to the left side of the range'() {
        expect:
        new CoderSemVer(1, 2, 3).isInClosedRange(new CoderSemVer(1, 2, 3), new CoderSemVer(7, 8, 9))
    }

    def 'in closed range comparison returns true when the version is equal to the right side of the range'() {
        expect:
        new CoderSemVer(7, 8, 9).isInClosedRange(new CoderSemVer(1, 2, 3), new CoderSemVer(7, 8, 9))
    }

    def "in closed range comparison returns false when #buildVersion is lower than the left side of the range"() {
        expect:
        buildVersion.isInClosedRange(new CoderSemVer(1, 2, 3), new CoderSemVer(7, 8, 9)) == false

        where:
        buildVersion << [
                new CoderSemVer(0, 0, 0),
                new CoderSemVer(0, 0, 1),
                new CoderSemVer(0, 1, 0),
                new CoderSemVer(1, 0, 0),
                new CoderSemVer(0, 1, 1),
                new CoderSemVer(1, 1, 1),
                new CoderSemVer(1, 2, 1),
                new CoderSemVer(0, 2, 3),
        ]
    }

    def "in closed range comparison returns false when #buildVersion is higher than the right side of the range"() {
        expect:
        buildVersion.isInClosedRange(new CoderSemVer(1, 2, 3), new CoderSemVer(7, 8, 9)) == false

        where:
        buildVersion << [
                new CoderSemVer(7, 8, 10),
                new CoderSemVer(7, 9, 0),
                new CoderSemVer(8, 0, 0),
                new CoderSemVer(8, 8, 9),
        ]
    }

    def "in closed range comparison returns true when #buildVersion is higher than the left side of the range but lower then the right side"() {
        expect:
        buildVersion.isInClosedRange(new CoderSemVer(1, 2, 3), new CoderSemVer(7, 8, 9)) == true

        where:
        buildVersion << [
                new CoderSemVer(1, 2, 4),
                new CoderSemVer(1, 3, 0),
                new CoderSemVer(2, 0, 0),
                new CoderSemVer(7, 8, 8),
                new CoderSemVer(7, 7, 10),
                new CoderSemVer(6, 9, 10),

        ]
    }
}
