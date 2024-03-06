package com.coder.gateway.views.steps

import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.gateway.api.GatewayUI
import javax.swing.JButton

/**
 * Wrapper around all wizard steps.  This view takes you from configuring a URL
 * to connecting to a workspace.
 */
class CoderWizardView(
    private val onFinish: (data: Map<String, String>) -> Unit,
) : BorderLayoutPanel(), Disposable {
    private lateinit var previousButton: JButton
    private lateinit var nextButton: JButton

    // These are not in a list because the types of one step lead into the types
    // of the next and it would not be possible to do that with a generic type
    // on a list.  It could possibly be refactored to have steps point to their
    // own next step which might be cleaner, but this works.
    private val step1 = CoderWorkspacesStepView { nextButton.isEnabled = it }
    private val step2 = CoderWorkspaceStepView { nextButton.isEnabled = it }
    private var current: CoderWizardStep? = null

    private val buttons = panel {
        separator(background = WelcomeScreenUIManager.getSeparatorColor())
        row {
            label("").resizableColumn().align(AlignX.FILL).gap(RightGap.SMALL)
            previousButton = button("") { previous() }
                .align(AlignX.RIGHT).gap(RightGap.SMALL)
                .applyToComponent { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }.component
            nextButton = button("") { next() }
                .align(AlignX.RIGHT)
                .applyToComponent { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }.component
        }
    }.apply {
        background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
        border = JBUI.Borders.empty(0, 16)
    }

    init {
        background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
        addToBottom(buttons)
        setStep(step1)
        step1.init()
    }

    /**
     * Replace the current step with the new one.
     */
    private fun setStep(step: CoderWizardStep) {
        current?.apply {
            remove(component)
            stop()
        }
        current = step
        step.apply {
            addToCenter(component.apply {
                background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
                border = JBUI.Borders.empty(0, 16)
            })
            nextButton.text = nextActionText
            previousButton.text = previousActionText
            nextButton.isEnabled = false
            updateUI()
        }
    }

    private fun previous() {
        when(current) {
            is CoderWorkspacesStepView -> {
                GatewayUI.getInstance().reset()
                dispose()
            }
            is CoderWorkspaceStepView -> {
                setStep(step1)
                step1.init()
            }
            null -> throw Error("Unexpected null step")
        }
    }

    private fun next() {
        when(current) {
            is CoderWorkspacesStepView -> {
                setStep(step2)
                step2.init(step1.data())
            }
            is CoderWorkspaceStepView -> {
                onFinish(step2.data())
                GatewayUI.getInstance().reset()
                dispose()
            }
            null -> throw Error("Unexpected null step")
        }
    }

    override fun dispose() {
        step1.dispose()
        step2.dispose()
    }
}
