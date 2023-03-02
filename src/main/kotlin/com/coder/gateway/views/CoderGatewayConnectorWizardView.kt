package com.coder.gateway.views

import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.views.steps.CoderLocateRemoteProjectStepView
import com.coder.gateway.views.steps.CoderWorkspacesStepView
import com.coder.gateway.views.steps.CoderWorkspacesWizardStep
import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.gateway.api.GatewayUI
import java.awt.Component
import javax.swing.JButton

class CoderGatewayConnectorWizardView : BorderLayoutPanel(), Disposable {
    private var steps = arrayListOf<CoderWorkspacesWizardStep>()
    private var currentStep = 0
    private val model = CoderWorkspacesWizardModel()

    private lateinit var previousButton: JButton
    private lateinit var nextButton: JButton

    init {
        setupWizard()
    }

    private fun setupWizard() {
        background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()

        registerStep(CoderWorkspacesStepView { nextButton.isEnabled = it })
        registerStep(CoderLocateRemoteProjectStepView {
            nextButton.isVisible = false
        })

        addToBottom(createBackComponent())

        steps[0].apply {
            onInit(model)
            addToCenter(component)
            updateUI()
            nextButton.text = nextActionText
            previousButton.text = previousActionText
        }

    }

    private fun registerStep(step: CoderWorkspacesWizardStep) {
        steps.add(step)
    }

    private fun previous() {
        steps[currentStep].onPrevious()

        if (currentStep == 0) {
            GatewayUI.getInstance().reset()
        } else {
            remove(steps[currentStep].component)
            updateUI()

            currentStep--
            steps[currentStep].apply {
                onInit(model)
                addToCenter(component)
                nextButton.text = nextActionText
                previousButton.text = previousActionText
            }
            showNavigationButtons()
        }
    }

    private fun showNavigationButtons() {
        nextButton.isVisible = true
        previousButton.isVisible = true
    }

    private fun next() {
        if (!doNextCallback()) return
        if (currentStep + 1 < steps.size) {
            remove(steps[currentStep].component)
            updateUI()
            currentStep++
            steps[currentStep].apply {
                addToCenter(component)
                onInit(model)
                updateUI()

                nextButton.text = nextActionText
                previousButton.text = previousActionText
            }
            showNavigationButtons()
        }
    }


    private fun doNextCallback(): Boolean {
        steps[currentStep].apply {
            component.apply()
            return onNext(model)
        }
    }

    private fun createBackComponent(): Component {
        previousButton = JButton()
        nextButton = JButton()
        return panel {
            separator(background = WelcomeScreenUIManager.getSeparatorColor())
            indent {
                row {

                    label("").resizableColumn().align(AlignX.FILL).gap(RightGap.SMALL)
                    previousButton = button("") { previous() }.align(AlignX.RIGHT).gap(RightGap.SMALL).applyToComponent { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }.component
                    nextButton = button("") { next() }.align(AlignX.RIGHT).gap(RightGap.SMALL).applyToComponent { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }.component
                }
            }.apply {
                background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
            }

        }.apply {
            background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
        }
    }

    override fun dispose() {
        steps.clear()
    }
}

