package com.coder.gateway.models

import com.jetbrains.gateway.ssh.AvailableIde
import com.jetbrains.gateway.ssh.Download
import com.jetbrains.gateway.ssh.InstalledIdeUIEx
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct.GOIDE
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct.IDEA
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct.IDEA_IC
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct.PYCHARM
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct.RUBYMINE
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct.RUSTROVER
import com.jetbrains.gateway.ssh.ReleaseType
import com.jetbrains.gateway.ssh.ReleaseType.EAP
import com.jetbrains.gateway.ssh.ReleaseType.NIGHTLY
import com.jetbrains.gateway.ssh.ReleaseType.PREVIEW
import com.jetbrains.gateway.ssh.ReleaseType.RC
import com.jetbrains.gateway.ssh.ReleaseType.RELEASE
import org.junit.jupiter.api.DisplayName
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class WorkspaceProjectIDETest {
    @Test
    fun testNameFallback() {
        // Name already exists.
        assertEquals(
            "workspace-name",
            RecentWorkspaceConnection(
                name = "workspace-name",
                coderWorkspaceHostname = "coder-jetbrains--hostname--bar.coder.com",
                projectPath = "/foo/bar",
                ideProductCode = "IU",
                ideBuildNumber = "number",
                idePathOnHost = "/foo/bar",
            ).toWorkspaceProjectIDE().name,
        )

        // Pull from host name.
        assertEquals(
            "hostname",
            RecentWorkspaceConnection(
                coderWorkspaceHostname = "coder-jetbrains--hostname--baz.coder.com",
                projectPath = "/foo/bar",
                ideProductCode = "IU",
                ideBuildNumber = "number",
                idePathOnHost = "/foo/bar",
            ).toWorkspaceProjectIDE().name,
        )

        // Nothing to fall back to.
        val ex =
            assertFailsWith(
                exceptionClass = Exception::class,
                block = {
                    RecentWorkspaceConnection(
                        projectPath = "/foo/bar",
                        ideProductCode = "IU",
                        ideBuildNumber = "number",
                        idePathOnHost = "/foo/bar",
                    ).toWorkspaceProjectIDE()
                },
            )
        assertContains(ex.message.toString(), "Workspace name is missing")
    }

    @Test
    fun testURLFallback() {
        // Deployment URL already exists.
        assertEquals(
            URL("https://foo.coder.com"),
            RecentWorkspaceConnection(
                name = "workspace.agent",
                deploymentURL = "https://foo.coder.com",
                coderWorkspaceHostname = "coder-jetbrains--hostname--bar.coder.com",
                projectPath = "/foo/bar",
                ideProductCode = "IU",
                ideBuildNumber = "number",
                idePathOnHost = "/foo/bar",
            ).toWorkspaceProjectIDE().deploymentURL,
        )

        // Pull from config directory.
        assertEquals(
            URL("https://baz.coder.com"),
            RecentWorkspaceConnection(
                name = "workspace.agent",
                configDirectory = "/foo/bar/baz.coder.com/qux",
                coderWorkspaceHostname = "coder-jetbrains--hostname--bar.coder.com",
                projectPath = "/foo/bar",
                ideProductCode = "IU",
                ideBuildNumber = "number",
                idePathOnHost = "/foo/bar",
            ).toWorkspaceProjectIDE().deploymentURL,
        )

        // Pull from host name.
        assertEquals(
            URL("https://bar.coder.com"),
            RecentWorkspaceConnection(
                name = "workspace.agent",
                coderWorkspaceHostname = "coder-jetbrains--hostname--bar.coder.com",
                projectPath = "/foo/bar",
                ideProductCode = "IU",
                ideBuildNumber = "number",
                idePathOnHost = "/foo/bar",
            ).toWorkspaceProjectIDE().deploymentURL,
        )

        // Nothing to fall back to.
        val ex =
            assertFailsWith(
                exceptionClass = Exception::class,
                block = {
                    RecentWorkspaceConnection(
                        name = "workspace.agent",
                        projectPath = "/foo/bar",
                        ideProductCode = "IU",
                        ideBuildNumber = "number",
                        idePathOnHost = "/foo/bar",
                    ).toWorkspaceProjectIDE()
                },
            )
        assertContains(ex.message.toString(), "Deployment URL is missing")

        // Invalid URL.
        assertFailsWith(
            exceptionClass = Exception::class,
            block = {
                RecentWorkspaceConnection(
                    name = "workspace.agent",
                    deploymentURL = "foo.coder.com", // Missing protocol.
                    coderWorkspaceHostname = "coder-jetbrains--hostname--bar.coder.com",
                    projectPath = "/foo/bar",
                    ideProductCode = "IU",
                    ideBuildNumber = "number",
                    idePathOnHost = "/foo/bar",
                ).toWorkspaceProjectIDE()
            },
        )
    }

    @Test
    @DisplayName("test that installed IDEs filter returns an empty list when there are available IDEs but none are installed")
    fun testFilterOutWhenNoIdeIsInstalledButAvailableIsPopulated() {
        assertEquals(
            emptyList(), emptyList<InstalledIdeUIEx>().filterOutAvailableReleasedIdes(
                listOf(
                    availableIde(IDEA, "242.23726.43", EAP),
                    availableIde(IDEA_IC, "251.23726.43", RELEASE)
                )
            )
        )
    }

    @Test
    @DisplayName("test that unreleased installed IDEs are not filtered out when available list of IDEs is empty")
    fun testFilterOutAvailableReleaseIdesWhenAvailableIsEmpty() {
        // given an eap installed ide
        val installedEAPs = listOf(installedIde(IDEA, "242.23726.43", EAP))

        // expect
        assertEquals(installedEAPs, installedEAPs.filterOutAvailableReleasedIdes(emptyList()))

        // given an RC installed ide
        val installedRCs = listOf(installedIde(RUSTROVER, "243.63726.48", RC))

        // expect
        assertEquals(installedRCs, installedRCs.filterOutAvailableReleasedIdes(emptyList()))

        // given a preview installed ide
        val installedPreviews = listOf(installedIde(IDEA_IC, "244.63726.48", ReleaseType.PREVIEW))

        // expect
        assertEquals(installedPreviews, installedPreviews.filterOutAvailableReleasedIdes(emptyList()))

        // given a nightly installed ide
        val installedNightlys = listOf(installedIde(RUBYMINE, "244.63726.48", NIGHTLY))

        // expect
        assertEquals(installedNightlys, installedNightlys.filterOutAvailableReleasedIdes(emptyList()))
    }

    @Test
    @DisplayName("test that unreleased EAP ides are superseded by available RELEASED ides with the same or higher build number")
    fun testUnreleasedAndInstalledEAPIdesAreSupersededByAvailableReleasedWithSameOrHigherBuildNr() {
        // given an eap installed ide
        val installedEapIdea = installedIde(IDEA, "242.23726.43", EAP)
        val installedReleasedRustRover = installedIde(RUSTROVER, "251.55667.23", RELEASE)
        // and a released idea with same build number
        val availableReleasedIdeaWithSameBuild = availableIde(IDEA, "242.23726.43", RELEASE)

        // expect the installed eap idea to be filtered out
        assertEquals(
            listOf(installedReleasedRustRover),
            listOf(installedEapIdea, installedReleasedRustRover).filterOutAvailableReleasedIdes(
                listOf(
                    availableReleasedIdeaWithSameBuild
                )
            )
        )

        // given a released idea with higher build number
        val availableIdeaWithHigherBuild = availableIde(IDEA, "243.21726.43", RELEASE)

        // expect the installed eap idea to be filtered out
        assertEquals(
            listOf(installedReleasedRustRover),
            listOf(installedEapIdea, installedReleasedRustRover).filterOutAvailableReleasedIdes(
                listOf(
                    availableIdeaWithHigherBuild
                )
            )
        )
    }

    @Test
    @DisplayName("test that unreleased RC ides are superseded by available RELEASED ides with the same or higher build number")
    fun testUnreleasedAndInstalledRCIdesAreSupersededByAvailableReleasedWithSameOrHigherBuildNr() {
        // given an RC installed ide
        val installedRCRustRover = installedIde(RUSTROVER, "242.23726.43", RC)
        val installedReleasedGoLand = installedIde(GOIDE, "251.55667.23", RELEASE)
        // and a released idea with same build number
        val availableReleasedRustRoverWithSameBuild = availableIde(RUSTROVER, "242.23726.43", RELEASE)

        // expect the installed RC rust rover to be filtered out
        assertEquals(
            listOf(installedReleasedGoLand),
            listOf(installedRCRustRover, installedReleasedGoLand).filterOutAvailableReleasedIdes(
                listOf(
                    availableReleasedRustRoverWithSameBuild
                )
            )
        )

        // given a released rust rover with higher build number
        val availableRustRoverWithHigherBuild = availableIde(RUSTROVER, "243.21726.43", RELEASE)

        // expect the installed RC rust rover to be filtered out
        assertEquals(
            listOf(installedReleasedGoLand),
            listOf(installedRCRustRover, installedReleasedGoLand).filterOutAvailableReleasedIdes(
                listOf(
                    availableRustRoverWithHigherBuild
                )
            )
        )
    }

    @Test
    @DisplayName("test that unreleased PREVIEW ides are superseded by available RELEASED ides with the same or higher build number")
    fun testUnreleasedAndInstalledPreviewIdesAreSupersededByAvailableReleasedWithSameOrHigherBuildNr() {
        // given a PREVIEW installed ide
        val installedPreviewRubyMine = installedIde(RUBYMINE, "242.23726.43", PREVIEW)
        val installedReleasedIntelliJCommunity = installedIde(IDEA_IC, "251.55667.23", RELEASE)
        // and a released ruby mine with same build number
        val availableReleasedRubyMineWithSameBuild = availableIde(RUBYMINE, "242.23726.43", RELEASE)

        // expect the installed PREVIEW idea to be filtered out
        assertEquals(
            listOf(installedReleasedIntelliJCommunity),
            listOf(installedPreviewRubyMine, installedReleasedIntelliJCommunity).filterOutAvailableReleasedIdes(
                listOf(
                    availableReleasedRubyMineWithSameBuild
                )
            )
        )

        // given a released ruby mine with higher build number
        val availableRubyMineWithHigherBuild = availableIde(RUBYMINE, "243.21726.43", RELEASE)

        // expect the installed PREVIEW ruby mine to be filtered out
        assertEquals(
            listOf(installedReleasedIntelliJCommunity),
            listOf(installedPreviewRubyMine, installedReleasedIntelliJCommunity).filterOutAvailableReleasedIdes(
                listOf(
                    availableRubyMineWithHigherBuild
                )
            )
        )
    }

    @Test
    @DisplayName("test that unreleased NIGHTLY ides are superseded by available RELEASED ides with the same or higher build number")
    fun testUnreleasedAndInstalledNightlyIdesAreSupersededByAvailableReleasedWithSameOrHigherBuildNr() {
        // given a NIGHTLY installed ide
        val installedNightlyPyCharm = installedIde(PYCHARM, "242.23726.43", NIGHTLY)
        val installedReleasedRubyMine = installedIde(RUBYMINE, "251.55667.23", RELEASE)
        // and a released pycharm with same build number
        val availableReleasedPyCharmWithSameBuild = availableIde(PYCHARM, "242.23726.43", RELEASE)

        // expect the installed NIGHTLY pycharm to be filtered out
        assertEquals(
            listOf(installedReleasedRubyMine),
            listOf(installedNightlyPyCharm, installedReleasedRubyMine).filterOutAvailableReleasedIdes(
                listOf(
                    availableReleasedPyCharmWithSameBuild
                )
            )
        )

        // given a released pycharm with higher build number
        val availablePyCharmWithHigherBuild = availableIde(PYCHARM, "243.21726.43", RELEASE)

        // expect the installed NIGHTLY pycharm to be filtered out
        assertEquals(
            listOf(installedReleasedRubyMine),
            listOf(installedNightlyPyCharm, installedReleasedRubyMine).filterOutAvailableReleasedIdes(
                listOf(
                    availablePyCharmWithHigherBuild
                )
            )
        )
    }

    @Test
    @DisplayName("test that unreleased installed ides are NOT superseded by available unreleased IDEs with higher build numbers")
    fun testUnreleasedIdesAreNotSupersededByAvailableUnreleasedIdesWithHigherBuildNr() {
        // given installed and unreleased ides
        val installedEap = listOf(installedIde(RUSTROVER, "203.87675.5", EAP))
        val installedRC = listOf(installedIde(RUSTROVER, "203.87675.5", RC))
        val installedPreview = listOf(installedIde(RUSTROVER, "203.87675.5", PREVIEW))
        val installedNightly = listOf(installedIde(RUSTROVER, "203.87675.5", NIGHTLY))

        // and available unreleased ides
        val availableHigherAndUnreleasedIdes = listOf(
            availableIde(RUSTROVER, "204.34567.1", EAP),
            availableIde(RUSTROVER, "205.45678.2", RC),
            availableIde(RUSTROVER, "206.24667.3", PREVIEW),
            availableIde(RUSTROVER, "207.24667.4", NIGHTLY),
        )

        assertEquals(
            installedEap,
            installedEap.filterOutAvailableReleasedIdes(availableHigherAndUnreleasedIdes)
        )
        assertEquals(
            installedRC,
            installedRC.filterOutAvailableReleasedIdes(availableHigherAndUnreleasedIdes)
        )
        assertEquals(
            installedPreview,
            installedPreview.filterOutAvailableReleasedIdes(availableHigherAndUnreleasedIdes)
        )
        assertEquals(
            installedNightly,
            installedNightly.filterOutAvailableReleasedIdes(availableHigherAndUnreleasedIdes)
        )
    }

    @Test
    @DisplayName("test that unreleased installed ides are NOT superseded by available unreleased IDEs with same major number but higher minor build numbers")
    fun testUnreleasedIdesAreNotSupersededByAvailableUnreleasedIdesWithSameMajorButHigherMinorBuildNr() {
        // given installed and unreleased ides
        val installedEap = listOf(installedIde(RUSTROVER, "203.12345.5", EAP))
        val installedRC = listOf(installedIde(RUSTROVER, "203.12345.5", RC))
        val installedPreview = listOf(installedIde(RUSTROVER, "203.12345.5", PREVIEW))
        val installedNightly = listOf(installedIde(RUSTROVER, "203.12345.5", NIGHTLY))

        // and available unreleased ides
        val availableHigherAndUnreleasedIdes = listOf(
            availableIde(RUSTROVER, "203.34567.1", EAP),
            availableIde(RUSTROVER, "203.45678.2", RC),
            availableIde(RUSTROVER, "203.24667.3", PREVIEW),
            availableIde(RUSTROVER, "203.24667.4", NIGHTLY),
        )

        assertEquals(
            installedEap,
            installedEap.filterOutAvailableReleasedIdes(availableHigherAndUnreleasedIdes)
        )
        assertEquals(
            installedRC,
            installedRC.filterOutAvailableReleasedIdes(availableHigherAndUnreleasedIdes)
        )
        assertEquals(
            installedPreview,
            installedPreview.filterOutAvailableReleasedIdes(availableHigherAndUnreleasedIdes)
        )
        assertEquals(
            installedNightly,
            installedNightly.filterOutAvailableReleasedIdes(availableHigherAndUnreleasedIdes)
        )
    }

    @Test
    @DisplayName("test that unreleased installed ides are NOT superseded by available unreleased IDEs with same major and minor number but higher patch numbers")
    fun testUnreleasedIdesAreNotSupersededByAvailableUnreleasedIdesWithSameMajorAndMinorButHigherPatchNr() {
        // given installed and unreleased ides
        val installedEap = listOf(installedIde(RUSTROVER, "203.12345.1", EAP))
        val installedRC = listOf(installedIde(RUSTROVER, "203.12345.1", RC))
        val installedPreview = listOf(installedIde(RUSTROVER, "203.12345.1", PREVIEW))
        val installedNightly = listOf(installedIde(RUSTROVER, "203.12345.1", NIGHTLY))

        // and available unreleased ides
        val availableHigherAndUnreleasedIdes = listOf(
            availableIde(RUSTROVER, "203.12345.2", EAP),
            availableIde(RUSTROVER, "203.12345.3", RC),
            availableIde(RUSTROVER, "203.12345.4", PREVIEW),
            availableIde(RUSTROVER, "203.12345.5", NIGHTLY),
        )

        assertEquals(
            installedEap,
            installedEap.filterOutAvailableReleasedIdes(availableHigherAndUnreleasedIdes)
        )
        assertEquals(
            installedRC,
            installedRC.filterOutAvailableReleasedIdes(availableHigherAndUnreleasedIdes)
        )
        assertEquals(
            installedPreview,
            installedPreview.filterOutAvailableReleasedIdes(availableHigherAndUnreleasedIdes)
        )
        assertEquals(
            installedNightly,
            installedNightly.filterOutAvailableReleasedIdes(availableHigherAndUnreleasedIdes)
        )
    }

    companion object {
        private val fakeDownload = Download(
            "https://download.jetbrains.com/idea/ideaIU-2024.1.7.tar.gz",
            1328462259,
            "https://download.jetbrains.com/idea/ideaIU-2024.1.7.tar.gz.sha256"
        )

        private fun installedIde(
            product: IntelliJPlatformProduct,
            buildNumber: String,
            releaseType: ReleaseType
        ): InstalledIdeUIEx {
            return InstalledIdeUIEx(
                product,
                buildNumber,
                "/home/coder/.cache/JetBrains/",
                toPresentableVersion(buildNumber) + " " + releaseType.toString()
            )
        }

        private fun availableIde(
            product: IntelliJPlatformProduct,
            buildNumber: String,
            releaseType: ReleaseType
        ): AvailableIde {
            return AvailableIde(
                product,
                buildNumber,
                fakeDownload,
                toPresentableVersion(buildNumber) + " " + releaseType.toString(),
                null,
                releaseType
            )
        }

        private fun toPresentableVersion(buildNr: String): String {

            return "20" + buildNr.substring(0, 2) + "." + buildNr.substring(2, 3)
        }
    }
}
