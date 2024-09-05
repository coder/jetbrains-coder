package com.coder.gateway.views

import com.coder.gateway.services.CoderSettingsService
import com.jetbrains.toolbox.gateway.ui.CheckboxField
import com.jetbrains.toolbox.gateway.ui.RunnableActionDescription
import com.jetbrains.toolbox.gateway.ui.TextField
import com.jetbrains.toolbox.gateway.ui.TextType
import com.jetbrains.toolbox.gateway.ui.UiField

/**
 * A page for modifying Coder settings.
 *
 * TODO@JB: Even without an icon there is an unnecessary gap at the top.
 * TODO@JB: There is no scroll, and our settings do not fit.  As a consequence,
 *          I have not been able to test this page.
 */
class CoderSettingsPage(private val settings: CoderSettingsService) : CoderPage(false) {
    // TODO: Copy over the descriptions, holding until I can test this page.
    private val binarySourceField = TextField("Binary source", settings.binarySource, TextType.General)
    private val binaryDirectoryField = TextField("Binary directory", settings.binaryDirectory, TextType.General)
    private val dataDirectoryField = TextField("Data directory", settings.dataDirectory, TextType.General)
    private val enableDownloadsField = CheckboxField(settings.enableDownloads, "Enable downloads")
    private val enableBinaryDirectoryFallbackField =
        CheckboxField(settings.enableBinaryDirectoryFallback, "Enable binary directory fallback")
    private val headerCommandField = TextField("Header command", settings.headerCommand, TextType.General)
    private val tlsCertPathField = TextField("TLS cert path", settings.tlsCertPath, TextType.General)
    private val tlsKeyPathField = TextField("TLS key path", settings.tlsKeyPath, TextType.General)
    private val tlsCAPathField = TextField("TLS CA path", settings.tlsCAPath, TextType.General)
    private val tlsAlternateHostnameField =
        TextField("TLS alternate hostname", settings.tlsAlternateHostname, TextType.General)
    private val disableAutostartField = CheckboxField(settings.disableAutostart, "Disable autostart")

    override fun getFields(): MutableList<UiField> = mutableListOf(
        binarySourceField,
        enableDownloadsField,
        binaryDirectoryField,
        enableBinaryDirectoryFallbackField,
        dataDirectoryField,
        headerCommandField,
        tlsCertPathField,
        tlsKeyPathField,
        tlsCAPathField,
        tlsAlternateHostnameField,
        disableAutostartField,
    )

    override fun getTitle(): String = "Coder Settings"

    override fun getActionButtons(): MutableList<RunnableActionDescription> = mutableListOf(
        Action("Save", true) {
            settings.binarySource = get(binarySourceField) as String
            settings.binaryDirectory = get(binaryDirectoryField) as String
            settings.dataDirectory = get(dataDirectoryField) as String
            settings.enableDownloads = get(enableDownloadsField) as Boolean
            settings.enableBinaryDirectoryFallback = get(enableBinaryDirectoryFallbackField) as Boolean
            settings.headerCommand = get(headerCommandField) as String
            settings.tlsCertPath = get(tlsCertPathField) as String
            settings.tlsKeyPath = get(tlsKeyPathField) as String
            settings.tlsCAPath = get(tlsCAPathField) as String
            settings.tlsAlternateHostname = get(tlsAlternateHostnameField) as String
            settings.disableAutostart = get(disableAutostartField) as Boolean
        },
    )
}
