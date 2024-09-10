package com.coder.gateway.util

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.cli.CoderCLIManager
import com.coder.gateway.models.WorkspaceProjectIDE
import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.settings.CoderSettings
import com.coder.gateway.settings.Source
import com.coder.gateway.views.steps.CoderWorkspaceProjectIDEStepView
import com.coder.gateway.views.steps.CoderWorkspacesStepSelection
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.AppIcon
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.applyIf
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.net.URL
import javax.swing.JComponent
import javax.swing.border.Border

/**
 * A dialog wrapper around CoderWorkspaceStepView.
 */
private class CoderWorkspaceStepDialog(
    name: String,
    private val state: CoderWorkspacesStepSelection,
) : DialogWrapper(true) {
    private val view = CoderWorkspaceProjectIDEStepView(showTitle = false)

    init {
        init()
        title = CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.choose.text", name)
    }

    override fun show() {
        view.init(state)
        view.onPrevious = { close(1) }
        view.onNext = { close(0) }
        super.show()
        view.dispose()
    }

    fun showAndGetData(): WorkspaceProjectIDE? {
        if (showAndGet()) {
            return view.data()
        }
        return null
    }

    override fun createContentPaneBorder(): Border = JBUI.Borders.empty()

    override fun createCenterPanel(): JComponent = view

    override fun createSouthPanel(): JComponent {
        // The plugin provides its own buttons.
        // TODO: Is it more idiomatic to handle buttons out here?
        return panel {}.apply {
            border = JBUI.Borders.empty()
        }
    }
}

fun askIDE(
    name: String,
    agent: WorkspaceAgent,
    workspace: Workspace,
    cli: CoderCLIManager,
    client: CoderRestClient,
    workspaces: List<Workspace>,
): WorkspaceProjectIDE? {
    var data: WorkspaceProjectIDE? = null
    ApplicationManager.getApplication().invokeAndWait {
        val dialog =
            CoderWorkspaceStepDialog(
                name,
                CoderWorkspacesStepSelection(agent, workspace, cli, client, workspaces),
            )
        data = dialog.showAndGetData()
    }
    return data
}

/**
 * Dialog implementation for standalone Gateway.
 *
 * This is meant to mimic ToolboxUi.
 */
class DialogUi(
    private val settings: CoderSettings,
) {
    fun confirm(title: String, description: String): Boolean {
        var inputFromUser = false
        ApplicationManager.getApplication().invokeAndWait({
            AppIcon.getInstance().requestAttention(null, true)
            if (!dialog(
                    title = title,
                    panel = panel {
                        row {
                            label(description)
                        }
                    },
                ).showAndGet()
            ) {
                return@invokeAndWait
            }
            inputFromUser = true
        }, ModalityState.defaultModalityState())
        return inputFromUser
    }

    fun ask(
        title: String,
        description: String,
        placeholder: String? = null,
        isError: Boolean = false,
        link: Pair<String, String>? = null,
    ): String? {
        var inputFromUser: String? = null
        ApplicationManager.getApplication().invokeAndWait({
            lateinit var inputTextField: JBTextField
            AppIcon.getInstance().requestAttention(null, true)
            if (!dialog(
                    title = title,
                    panel = panel {
                        row {
                            if (link != null) browserLink(link.first, link.second)
                            inputTextField =
                                textField()
                                    .applyToComponent {
                                        this.text = placeholder
                                        minimumSize = Dimension(520, -1)
                                    }.component
                        }.layout(RowLayout.PARENT_GRID)
                        row {
                            cell() // To align with the text box.
                            cell(
                                ComponentPanelBuilder.createCommentComponent(description, false, -1, true)
                                    .applyIf(isError) {
                                        apply {
                                            foreground = UIUtil.getErrorForeground()
                                        }
                                    },
                            )
                        }.layout(RowLayout.PARENT_GRID)
                    },
                    focusedComponent = inputTextField,
                ).showAndGet()
            ) {
                return@invokeAndWait
            }
            inputFromUser = inputTextField.text
        }, ModalityState.any())
        return inputFromUser
    }

    private fun openUrl(url: URL) {
        BrowserUtil.browse(url)
    }

    /**
     * Open a dialog for providing the token.  Show any existing token so
     * the user can validate it if a previous connection failed.
     *
     * If we have not already tried once (no error) and the user has not checked
     * the existing token box then also open a browser to the auth page.
     *
     * If the user has checked the existing token box then return the token
     * on disk immediately and skip the dialog (this will overwrite any
     * other existing token) unless this is a retry to avoid clobbering the
     * token that just failed.
     */
    fun askToken(
        url: URL,
        token: Pair<String, Source>?,
        useExisting: Boolean,
        error: String?,
    ): Pair<String, Source>? {
        val getTokenUrl = url.withPath("/login?redirect=%2Fcli-auth")

        // On the first run (no error) either open a browser to generate a new
        // token or, if using an existing token, use the token on disk if it
        // exists otherwise assume the user already copied an existing token and
        // they will paste in.
        if (error == null) {
            if (!useExisting) {
                openUrl(getTokenUrl)
            } else {
                // Look on disk in case we already have a token, either in
                // the deployment's config or the global config.
                val tryToken = settings.token(url)
                if (tryToken != null && tryToken.first != token?.first) {
                    return tryToken
                }
            }
        }

        // On subsequent tries or if not using an existing token, ask the user
        // for the token.
        val tokenFromUser =
            ask(
                title = "Session Token",
                description = error
                    ?: token?.second?.description("token")
                    ?: "No existing token for ${url.host} found.",
                placeholder = token?.first,
                link = Pair("Session Token:", getTokenUrl.toString()),
                isError = error != null,
            )
        if (tokenFromUser.isNullOrBlank()) {
            return null
        }
        // If the user submitted the same token, keep the same source too.
        val source = if (tokenFromUser == token?.first) token.second else Source.USER
        return Pair(tokenFromUser, source)
    }
}
