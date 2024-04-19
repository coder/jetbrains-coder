package com.coder.gateway.models

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class WorkspaceProjectIDETest {
    @Test
    fun testNameFallback() {
        // Name already exists.
        assertEquals("workspace-name", RecentWorkspaceConnection(
            name = "workspace-name",
            coderWorkspaceHostname = "coder-jetbrains--hostname--bar.coder.com",
            projectPath = "/foo/bar",
            ideProductCode = "IU",
            ideBuildNumber = "number",
            idePathOnHost = "/foo/bar",
        ).toWorkspaceProjectIDE().name)

        // Pull from host name.
        assertEquals("hostname", RecentWorkspaceConnection(
            coderWorkspaceHostname = "coder-jetbrains--hostname--baz.coder.com",
            projectPath = "/foo/bar",
            ideProductCode = "IU",
            ideBuildNumber = "number",
            idePathOnHost = "/foo/bar",
        ).toWorkspaceProjectIDE().name)

        // Nothing to fall back to.
        val ex = assertFailsWith(
            exceptionClass = Exception::class,
            block = { RecentWorkspaceConnection(
                projectPath = "/foo/bar",
                ideProductCode = "IU",
                ideBuildNumber = "number",
                idePathOnHost = "/foo/bar",
            ).toWorkspaceProjectIDE() })
        assertContains(ex.message.toString(), "Workspace name is missing")
    }

    @Test
    fun testURLFallback() {
        // Deployment URL already exists.
        assertEquals("https://foo.coder.com", RecentWorkspaceConnection(
            name = "workspace.agent",
            deploymentURL = "https://foo.coder.com",
            coderWorkspaceHostname = "coder-jetbrains--hostname--bar.coder.com",
            projectPath = "/foo/bar",
            ideProductCode = "IU",
            ideBuildNumber = "number",
            idePathOnHost = "/foo/bar",
        ).toWorkspaceProjectIDE().deploymentURL)

        // Pull from config directory.
        assertEquals("https://baz.coder.com", RecentWorkspaceConnection(
            name = "workspace.agent",
            configDirectory = "/foo/bar/baz.coder.com/qux",
            coderWorkspaceHostname = "coder-jetbrains--hostname--bar.coder.com",
            projectPath = "/foo/bar",
            ideProductCode = "IU",
            ideBuildNumber = "number",
            idePathOnHost = "/foo/bar",
        ).toWorkspaceProjectIDE().deploymentURL)

        // Pull from host name.
        assertEquals("https://bar.coder.com", RecentWorkspaceConnection(
            name = "workspace.agent",
            coderWorkspaceHostname = "coder-jetbrains--hostname--bar.coder.com",
            projectPath = "/foo/bar",
            ideProductCode = "IU",
            ideBuildNumber = "number",
            idePathOnHost = "/foo/bar",
        ).toWorkspaceProjectIDE().deploymentURL)

        // Nothing to fall back to.
        val ex = assertFailsWith(
            exceptionClass = Exception::class,
            block = { RecentWorkspaceConnection(
                name = "workspace.agent",
                projectPath = "/foo/bar",
                ideProductCode = "IU",
                ideBuildNumber = "number",
                idePathOnHost = "/foo/bar",
            ).toWorkspaceProjectIDE() })
        assertContains(ex.message.toString(), "Deployment URL is missing")

        // Accepts invalid URL.
        assertEquals("foo.coder.com", RecentWorkspaceConnection(
            name = "workspace.agent",
            deploymentURL = "foo.coder.com", // Missing protocol.
            coderWorkspaceHostname = "coder-jetbrains--hostname--bar.coder.com",
            projectPath = "/foo/bar",
            ideProductCode = "IU",
            ideBuildNumber = "number",
            idePathOnHost = "/foo/bar",
        ).toWorkspaceProjectIDE().deploymentURL)
    }
}
