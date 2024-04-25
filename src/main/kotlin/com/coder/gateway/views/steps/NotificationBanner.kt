package com.coder.gateway.views.steps

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JEditorPane
import javax.swing.JLabel

class NotificationBanner {
    var component: DialogPanel
    private lateinit var icon: JLabel
    private lateinit var txt: JEditorPane

    init {
        component =
            panel {
                row {
                    icon =
                        icon(AllIcons.General.Warning).applyToComponent {
                            border = JBUI.Borders.empty(0, 5)
                        }.component
                    txt =
                        text("").resizableColumn().align(AlignX.FILL).applyToComponent {
                            foreground = JBUI.CurrentTheme.NotificationWarning.foregroundColor()
                        }.component
                }
            }.apply {
                background = JBUI.CurrentTheme.NotificationWarning.backgroundColor()
            }
    }

    fun showWarning(warning: String) {
        icon.icon = AllIcons.General.Warning
        txt.apply {
            text = warning
            foreground = JBUI.CurrentTheme.NotificationWarning.foregroundColor()
        }

        component.background = JBUI.CurrentTheme.NotificationWarning.backgroundColor()
    }

    fun showInfo(info: String) {
        icon.icon = AllIcons.General.Information
        txt.apply {
            text = info
            foreground = JBUI.CurrentTheme.NotificationInfo.foregroundColor()
        }

        component.background = JBUI.CurrentTheme.NotificationInfo.backgroundColor()
    }
}
