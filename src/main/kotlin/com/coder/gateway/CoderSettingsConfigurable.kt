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
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ValidationInfoBuilder
import java.net.URL
import java.nio.file.Path

class CoderSettingsConfigurable : BoundConfigurable("Coder") {
    override fun createPanel(): DialogPanel {
        val state: CoderSettingsState = service()
        return panel {
            row(CoderGatewayBundle.message("gateway.connector.settings.binary-source.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::binarySource)
                    .comment(
                        CoderGatewayBundle.message(
                            "gateway.connector.settings.binary-source.comment",
                            CoderCLIManager(URL("http://localhost")).remoteBinaryURL.path,
                        )
                    )
            }
            row(CoderGatewayBundle.message("gateway.connector.settings.binary-destination.title")) {
                textField().resizableColumn().align(AlignX.FILL)
                    .bindText(state::binaryDestination)
                    .validationOnApply(validateBinaryDestination())
                    .validationOnInput(validateBinaryDestination())
                    .comment(
                        CoderGatewayBundle.message(
                            "gateway.connector.settings.binary-destination.comment",
                            CoderCLIManager.getDataDir(),
                        )
                    )
            }
        }
    }

    private fun validateBinaryDestination(): ValidationInfoBuilder.(JBTextField) -> ValidationInfo? = {
        if (it.text.isNotBlank() && !Path.of(it.text).canCreateDirectory()) {
            error("Cannot create this directory")
        } else {
            null
        }
    }
}
