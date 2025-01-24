package com.coder.gateway

import com.coder.gateway.services.CoderSettingsService
import com.coder.gateway.services.CoderSettingsStateService
import com.coder.gateway.settings.CODER_SSH_CONFIG_OPTIONS
import com.coder.gateway.util.canCreateDirectory
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ValidationInfoBuilder
import java.net.URL
import java.nio.file.Path

class CoderSettingsConfigurable : BoundConfigurable("Coder") {
    override fun createPanel(): DialogPanel {
        val state: CoderSettingsStateService = service()
        val settings: CoderSettingsService = service<CoderSettingsService>()
        return panel {
            row(CoderGatewayBundle.message("gateway.connector.settings.data-directory.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::dataDirectory)
                    .validationOnApply(validateDataDirectory())
                    .validationOnInput(validateDataDirectory())
                    .comment(
                        CoderGatewayBundle.message(
                            "gateway.connector.settings.data-directory.comment",
                            settings.dataDir.toString(),
                        ),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.binary-source.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::binarySource)
                    .comment(
                        CoderGatewayBundle.message(
                            "gateway.connector.settings.binary-source.comment",
                            settings.binSource(URL("http://localhost")).path,
                        ),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row {
                cell() // For alignment.
                checkBox(CoderGatewayBundle.message("gateway.connector.settings.enable-downloads.title"))
                    .bindSelected(state::enableDownloads)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.enable-downloads.comment"),
                    )
            }.layout(RowLayout.PARENT_GRID)
            // The binary directory is not validated because it could be a
            // read-only path that is pre-downloaded by admins.
            row(CoderGatewayBundle.message("gateway.connector.settings.binary-destination.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::binaryDirectory)
                    .comment(CoderGatewayBundle.message("gateway.connector.settings.binary-destination.comment"))
            }.layout(RowLayout.PARENT_GRID)
            row {
                cell() // For alignment.
                checkBox(CoderGatewayBundle.message("gateway.connector.settings.enable-binary-directory-fallback.title"))
                    .bindSelected(state::enableBinaryDirectoryFallback)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.enable-binary-directory-fallback.comment"),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.header-command.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::headerCommand)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.header-command.comment"),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.tls-cert-path.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::tlsCertPath)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.tls-cert-path.comment"),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.tls-key-path.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::tlsKeyPath)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.tls-key-path.comment"),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.tls-ca-path.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::tlsCAPath)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.tls-ca-path.comment"),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.tls-alt-name.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::tlsAlternateHostname)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.tls-alt-name.comment"),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.disable-autostart.heading")) {
                checkBox(CoderGatewayBundle.message("gateway.connector.settings.disable-autostart.title"))
                    .bindSelected(state::disableAutostart)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.disable-autostart.comment"),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.ssh-config-options.title")) {
                textArea().resizableColumn().align(AlignX.FILL)
                    .bindText(state::sshConfigOptions)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.ssh-config-options.comment", CODER_SSH_CONFIG_OPTIONS),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.setup-command.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::setupCommand)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.setup-command.comment"),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row {
                cell() // For alignment.
                checkBox(CoderGatewayBundle.message("gateway.connector.settings.ignore-setup-failure.title"))
                    .bindSelected(state::ignoreSetupFailure)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.ignore-setup-failure.comment"),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.default-url.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::defaultURL)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.default-url.comment"),
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.ssh-log-directory.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::sshLogDirectory)
                    .comment(CoderGatewayBundle.message("gateway.connector.settings.ssh-log-directory.comment"))
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.workspace-filter.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::workspaceFilter)
                    .comment(CoderGatewayBundle.message("gateway.connector.settings.workspace-filter.comment"))
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.default-ide")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::defaultIde)
                    .comment(
                        "The default IDE version to display in the IDE selection dropdown. " +
                            "Example format: CL 2023.3.6 233.15619.8",
                    )
            }
            row(CoderGatewayBundle.message("gateway.connector.settings.check-ide-updates.heading")) {
                checkBox(CoderGatewayBundle.message("gateway.connector.settings.check-ide-updates.title"))
                    .bindSelected(state::checkIDEUpdates)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.check-ide-updates.comment"),
                    )
            }.layout(RowLayout.PARENT_GRID)
        }
    }

    private fun validateDataDirectory(): ValidationInfoBuilder.(JBTextField) -> ValidationInfo? =
        {
            if (it.text.isNotBlank() && !Path.of(it.text).canCreateDirectory()) {
                error("Cannot create this directory")
            } else {
                null
            }
        }
}
