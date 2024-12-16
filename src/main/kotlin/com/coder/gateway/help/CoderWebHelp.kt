package com.coder.gateway.help

import com.intellij.openapi.help.WebHelpProvider

const val ABOUT_HELP_TOPIC = "com.coder.gateway.about"

class CoderWebHelp : WebHelpProvider() {
    override fun getHelpPageUrl(helpTopicId: String): String = when (helpTopicId) {
        ABOUT_HELP_TOPIC -> "https://coder.com/docs"
        else -> "https://coder.com/docs"
    }
}
