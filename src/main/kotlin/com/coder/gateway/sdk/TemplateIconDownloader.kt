package com.coder.gateway.sdk

import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.util.toURL
import com.coder.gateway.util.withPath
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ImageLoader
import com.intellij.util.ui.ImageUtil
import org.imgscalr.Scalr
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.net.URL
import javax.swing.Icon

fun alignToInt(g: Graphics) {
    if (g !is Graphics2D) {
        return
    }

    val rm = PaintUtil.RoundingMode.ROUND_FLOOR_BIAS
    PaintUtil.alignTxToInt(g, null, true, true, rm)
    PaintUtil.alignClipToInt(g, true, true, rm, rm)
}

@Service(Service.Level.APP)
class TemplateIconDownloader {
    private val clientService: CoderRestClientService = service()
    private val cache = mutableMapOf<Pair<String, String>, Icon>()

    fun load(path: String, workspaceName: String): Icon {
        var url: URL? = null
        if (path.startsWith("http")) {
            url = path.toURL()
        } else if (!path.contains(":") && !path.contains("//")) {
            url = clientService.client.url.withPath(path)
        }

        if (url != null) {
            val cachedIcon = cache[Pair(workspaceName, path)]
            if (cachedIcon != null) {
                return cachedIcon
            }
            var img = ImageLoader.loadFromUrl(url)
            if (img != null) {
                val icon = toRetinaAwareIcon(Scalr.resize(ImageUtil.toBufferedImage(img), Scalr.Method.ULTRA_QUALITY, 32))
                cache[Pair(workspaceName, path)] = icon
                return icon
            }
        }

        return iconForChar(workspaceName.lowercase().first())
    }

    // We could replace this with com.intellij.ui.icons.toRetinaAwareIcon at
    // some point if we want to break support for Gateway < 232.
    private fun toRetinaAwareIcon(image: BufferedImage): Icon {
        val sysScale = JBUIScale.sysScale()
        return object : Icon {
            override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
                if (isJreHiDPI) {
                    val newG = g.create(x, y, image.width, image.height) as Graphics2D
                    alignToInt(newG)
                    newG.scale(1.0 / sysScale, 1.0 / sysScale)
                    newG.drawImage(image, 0, 0, null)
                    newG.dispose()
                } else {
                    g.drawImage(image, x, y, null)
                }
            }

            override fun getIconWidth(): Int = if (isJreHiDPI) (image.width / sysScale).toInt() else image.width

            override fun getIconHeight(): Int = if (isJreHiDPI) (image.height / sysScale).toInt() else image.height

            private val isJreHiDPI: Boolean
                get() = JreHiDpiUtil.isJreHiDPI(sysScale)

            override fun toString(): String {
                return "TemplateIconDownloader.toRetinaAwareIcon for $image"
            }
        }
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
