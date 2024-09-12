package com.coder.gateway.views

import com.coder.gateway.settings.Source
import com.jetbrains.toolbox.gateway.ui.LabelField
import com.jetbrains.toolbox.gateway.ui.RunnableActionDescription
import com.jetbrains.toolbox.gateway.ui.TextField
import com.jetbrains.toolbox.gateway.ui.TextType
import com.jetbrains.toolbox.gateway.ui.UiField
import java.net.URL

/**
 * A page with a field for providing the Coder deployment URL.
 *
 * Populates with the provided URL, at which point the user can accept or
 * enter their own.
 */
class SignInPage(
    private val deploymentURL: Pair<String, Source>?,
    private val onSignIn: (deploymentURL: URL) -> Unit,
) : CoderPage() {
    private val urlField = TextField("Deployment URL", deploymentURL?.first ?: "", TextType.General)

    override fun getTitle(): String = "Sign In to Coder"

    /**
     * Fields for this page, displayed in order.
     *
     * TODO@JB: Fields are reset when you navigate back.
     *          Ideally they remember what the user entered.
     */
    override fun getFields(): MutableList<UiField> = listOfNotNull(
        urlField,
        deploymentURL?.let { LabelField(deploymentURL.second.description("URL")) },
        errorField,
    ).toMutableList()

    /**
     * Buttons displayed at the bottom of the page.
     */
    override fun getActionButtons(): MutableList<RunnableActionDescription> = mutableListOf(
        Action("Sign In", closesPage = false) { submit() },
    )

    /**
     * Call onSignIn with the URL, or error if blank.
     */
    private fun submit() {
        val urlRaw = get(urlField) as String
        // Ensure the URL can be parsed.
        try {
            if (urlRaw.isBlank()) {
                throw Exception("URL is required")
            }
            // Prefix the protocol if the user left it out.
            // URL() will throw if the URL is invalid.
            onSignIn(
                URL(
                    if (!urlRaw.startsWith("http://") && !urlRaw.startsWith("https://")) {
                        "https://$urlRaw"
                    } else {
                        urlRaw
                    },
                ),
            )
        } catch (ex: Exception) {
            // TODO@JB: Works on the other page, but not this one.
            updateError(ex.message)
        }
    }
}
