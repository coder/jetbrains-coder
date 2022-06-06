package com.coder.gateway.views.steps

import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.intellij.openapi.ui.DialogPanel

sealed interface CoderWorkspacesWizardStep {
    val component: DialogPanel

    val nextActionText: String
    val previousActionText: String

    fun onInit(wizardModel: CoderWorkspacesWizardModel)
    fun onNext(wizardModel: CoderWorkspacesWizardModel): Boolean
}