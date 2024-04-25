package com.coder.gateway.views.steps

import com.coder.gateway.util.withoutNull
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JButton

sealed class CoderWizardStep<T>(
    nextActionText: String,
) : BorderLayoutPanel(), Disposable {
    var onPrevious: (() -> Unit)? = null
    var onNext: ((data: T) -> Unit)? = null

    private lateinit var previousButton: JButton
    protected lateinit var nextButton: JButton

    private val buttons =
        panel {
            separator(background = WelcomeScreenUIManager.getSeparatorColor())
            row {
                label("").resizableColumn().align(AlignX.FILL).gap(RightGap.SMALL)
                previousButton =
                    button(IdeBundle.message("button.back")) { previous() }
                        .align(AlignX.RIGHT).gap(RightGap.SMALL)
                        .applyToComponent { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }.component
                nextButton =
                    button(nextActionText) { next() }
                        .align(AlignX.RIGHT)
                        .applyToComponent { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }.component
            }.bottomGap(BottomGap.SMALL)
        }.apply {
            background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
            border = JBUI.Borders.empty(0, 16)
        }

    init {
        nextButton.isEnabled = false
        addToBottom(buttons)
    }

    private fun previous() {
        withoutNull(onPrevious) {
            it()
        }
    }

    private fun next() {
        withoutNull(onNext) {
            it(data())
        }
    }

    /**
     * Return data gathered by this step.
     */
    abstract fun data(): T

    /**
     * Stop any background processes.  Data will still be available.
     */
    abstract fun stop()
}
