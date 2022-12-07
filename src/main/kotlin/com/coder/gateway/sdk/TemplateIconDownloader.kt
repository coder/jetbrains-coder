package com.coder.gateway.sdk

import com.coder.gateway.icons.CoderIcons
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ImageLoader
import com.intellij.util.ui.ImageUtil
import java.net.URL
import javax.swing.Icon
import javax.swing.ImageIcon

@Service(Service.Level.APP)
class TemplateIconDownloader {
    private val coderClient: CoderRestClientService = service()

    fun load(path: String, templateName: String): Icon {
        var url: URL? = null
        if (path.startsWith("http")) {
            url = path.toURL()
        } else if (path.contains(coderClient.coderURL.host)) {
            url = coderClient.coderURL.withPath(path)
        }

        if (url != null) {
            var img = ImageLoader.loadFromUrl(url)
            if (img != null) {
                if (ImageUtil.getRealHeight(img) > 32 && ImageUtil.getRealWidth(img) > 32) {
                    img = ImageUtil.resize(img, 32, ScaleContext.create())
                }
                return ImageIcon(img)
            }
        }

        return iconForChar(templateName.lowercase().first())
    }

    private fun iconForChar(c: Char) = when (c) {
        '0' -> CoderIcons.ZERO
        '1' -> CoderIcons.ONE
        '2' -> CoderIcons.TWO
        '3' -> CoderIcons.THREE
        '4' -> CoderIcons.FOUR
        '5' -> CoderIcons.FIVE
        '6' -> CoderIcons.SIX
        '7' -> CoderIcons.SEVEN
        '8' -> CoderIcons.EIGHT
        '9' -> CoderIcons.NINE

        'a' -> CoderIcons.A
        'b' -> CoderIcons.B
        'c' -> CoderIcons.C
        'd' -> CoderIcons.D
        'e' -> CoderIcons.E
        'f' -> CoderIcons.F
        'g' -> CoderIcons.G
        'h' -> CoderIcons.H
        'i' -> CoderIcons.I
        'j' -> CoderIcons.J
        'k' -> CoderIcons.K
        'l' -> CoderIcons.L
        'm' -> CoderIcons.M
        'n' -> CoderIcons.N
        'o' -> CoderIcons.O
        'p' -> CoderIcons.P
        'q' -> CoderIcons.Q
        'r' -> CoderIcons.R
        's' -> CoderIcons.S
        't' -> CoderIcons.T
        'u' -> CoderIcons.U
        'v' -> CoderIcons.V
        'w' -> CoderIcons.W
        'x' -> CoderIcons.X
        'y' -> CoderIcons.Y
        'z' -> CoderIcons.Z

        else -> CoderIcons.UNKNOWN
    }

}