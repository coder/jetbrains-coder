package com.coder.gateway

import com.coder.gateway.sdk.CoderCLIManager
import com.coder.gateway.sdk.canCreateDirectory
import com.coder.gateway.services.CoderSettingsState
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
        val state: CoderSettingsState = service()
        return panel {
            row(CoderGatewayBundle.message("gateway.connector.settings.data-directory.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::dataDirectory)
                    .validationOnApply(validateDataDirectory())
                    .validationOnInput(validateDataDirectory())
                    .comment(
                        CoderGatewayBundle.message(
                            "gateway.connector.settings.data-directory.comment",
                            CoderCLIManager.getDataDir(),
                        )
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.binary-source.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::binarySource)
                    .comment(
                        CoderGatewayBundle.message(
                            "gateway.connector.settings.binary-source.comment",
                            CoderCLIManager(URL("http://localhost"), CoderCLIManager.getDataDir()).remoteBinaryURL.path,
                        )
                    )
            }.layout(RowLayout.PARENT_GRID)
            row {
                cell() // For alignment.
                checkBox(CoderGatewayBundle.message("gateway.connector.settings.enable-downloads.title"))
                    .bindSelected(state::enableDownloads)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.enable-downloads.comment")
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
                        CoderGatewayBundle.message("gateway.connector.settings.enable-binary-directory-fallback.comment")
                    )
            }.layout(RowLayout.PARENT_GRID)
            row(CoderGatewayBundle.message("gateway.connector.settings.header-command.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::headerCommand)
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.header-command.comment")
                    )
            }.layout(RowLayout.PARENT_GRID)
        }
    }

    private fun validateDataDirectory(): ValidationInfoBuilder.(JBTextField) -> ValidationInfo? = {
        if (it.text.isNotBlank() && !Path.of(it.text).canCreateDirectory()) {
            error("Cannot create this directory")
        } else {
            null
        }
    }
}
