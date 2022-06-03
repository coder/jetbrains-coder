package com.coder.gateway.views

import com.intellij.openapi.util.Disposer
import com.intellij.ui.IconManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination

class CoderGatewayConnectionComponent(val lifetime: Lifetime, val url: String, val workspaceId: String) : BorderLayoutPanel() {
    private val disposable = Disposer.newDisposable()
    private val mainPanel = BorderLayoutPanel().apply {
        add(JBLabel(IconManager.getInstance().getIcon("coder_logo_52.svg", CoderGatewayConnectionComponent::class.java)), "Center")
    }

    init {
        lifetime.onTermination {
            Disposer.dispose(disposable)
        }

        add(mainPanel, "Center")
    }
}