package com.coder.gateway.views

import com.coder.gateway.icons.CoderIcons
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination

class CoderGatewayConnectionComponent(lifetime: Lifetime, val url: String) : BorderLayoutPanel() {
    private val disposable = Disposer.newDisposable()
    private val mainPanel = BorderLayoutPanel().apply {
        add(JBLabel(CoderIcons.LOGO_52), "Center")
    }

    init {
        lifetime.onTermination {
            Disposer.dispose(disposable)
        }

        add(mainPanel, "Center")
    }
}