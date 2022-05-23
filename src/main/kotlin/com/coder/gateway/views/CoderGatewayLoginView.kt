package com.coder.gateway.views

import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.models.LoginModel
import com.coder.gateway.views.steps.CoderAuthStepView
import com.coder.gateway.views.steps.CoderWorkspacesStepView
import com.coder.gateway.views.steps.CoderWorkspacesWizardStep
import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.gateway.api.GatewayUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import javax.swing.JButton

class CoderGatewayLoginView : BorderLayoutPanel(), Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)
    private var steps = arrayListOf<CoderWorkspacesWizardStep>()
    private var currentStep = 0
    private val model = CoderWorkspacesWizardModel(LoginModel(), emptyList())

    private lateinit var previousButton: JButton
    private lateinit var nextButton: JButton

    init {
        setupWizard()
    }

    private fun setupWizard() {
        background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()

        registerStep(CoderAuthStepView())
        registerStep(CoderWorkspacesStepView())

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
        nextButton.isVisible = true
        if (currentStep == 0) {
            GatewayUI.Companion.getInstance().reset()
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
        }
    }

    private fun next() {
        cs.launch {
            if (currentStep + 1 < steps.size) {
                withContext(Dispatchers.Main) { doNextCallback() }
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

                nextButton.isVisible = currentStep != steps.size - 1
            }
        }
    }


    private suspend fun doNextCallback() {
        steps[currentStep].apply {
            component.apply()
            onNext(model)
        }
    }

    private fun createBackComponent(): Component {
        previousButton = JButton()
        nextButton = JButton()
        return panel {
            separator(background = WelcomeScreenUIManager.getSeparatorColor())
            indent {
                row {

                    label("").resizableColumn().horizontalAlign(HorizontalAlign.FILL).gap(RightGap.SMALL)
                    previousButton = button("") { previous() }.horizontalAlign(HorizontalAlign.RIGHT).gap(RightGap.SMALL).applyToComponent { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }.component
                    nextButton = button("") { next() }.horizontalAlign(HorizontalAlign.RIGHT).gap(RightGap.SMALL).applyToComponent { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }.component
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

