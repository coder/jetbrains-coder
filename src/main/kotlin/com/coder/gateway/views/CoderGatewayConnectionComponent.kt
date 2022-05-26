package com.coder.gateway.views

import com.intellij.openapi.util.Disposer
import com.intellij.ui.IconManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import java.awt.BorderLayout

class CoderGatewayConnectionComponent(val lifetime: Lifetime, val url: String, val workspaceId: String) : BorderLayoutPanel() {
    private val disposable = Disposer.newDisposable()
    private val contentPanel = BorderLayoutPanel().apply {
        add(JBLabel(IconManager.getInstance().getIcon("coder_logo_52.svg", CoderGatewayConnectionComponent::class.java)), "Center")
    }
    val loadingPanel = JBLoadingPanel(BorderLayout(), disposable).apply {
        startLoading()
        add(contentPanel, "Center")
    }

    init {

        lifetime.onTermination {
            Disposer.dispose(disposable)
        }
    }

    var isLoading: Boolean
        get() = loadingPanel.isLoading
        set(value) = if (value) {
            loadingPanel.startLoading()
        } else {
            loadingPanel.stopLoading()
        }
}