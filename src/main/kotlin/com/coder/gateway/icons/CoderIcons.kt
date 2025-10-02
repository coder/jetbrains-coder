package com.coder.gateway.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.swing.Icon

object CoderIcons {
    val LOGO = IconLoader.getIcon("logo/coder_logo.svg", javaClass)
    val LOGO_16 = IconLoader.getIcon("logo/coder_logo_16.svg", javaClass)

    val OPEN_TERMINAL = IconLoader.getIcon("icons/open_terminal.svg", javaClass)

    val HOME = IconLoader.getIcon("icons/homeFolder.svg", javaClass)
    val CREATE = IconLoader.getIcon("icons/create.svg", javaClass)
    val RUN = IconLoader.getIcon("icons/run.svg", javaClass)
    val STOP = IconLoader.getIcon("icons/stop.svg", javaClass)
    val UPDATE = IconLoader.getIcon("icons/update.svg", javaClass)
    val DELETE = IconLoader.getIcon("icons/delete.svg", javaClass)

    val UNKNOWN = IconLoader.getIcon("icons/unknown.svg", javaClass)

    private val ZERO = IconLoader.getIcon("symbols/0.svg", javaClass)
    private val ONE = IconLoader.getIcon("symbols/1.svg", javaClass)
    private val TWO = IconLoader.getIcon("symbols/2.svg", javaClass)
    private val THREE = IconLoader.getIcon("symbols/3.svg", javaClass)
    private val FOUR = IconLoader.getIcon("symbols/4.svg", javaClass)
    private val FIVE = IconLoader.getIcon("symbols/5.svg", javaClass)
    private val SIX = IconLoader.getIcon("symbols/6.svg", javaClass)
    private val SEVEN = IconLoader.getIcon("symbols/7.svg", javaClass)
    private val EIGHT = IconLoader.getIcon("symbols/8.svg", javaClass)
    private val NINE = IconLoader.getIcon("symbols/9.svg", javaClass)

    private val A = IconLoader.getIcon("symbols/a.svg", javaClass)
    private val B = IconLoader.getIcon("symbols/b.svg", javaClass)
    private val C = IconLoader.getIcon("symbols/c.svg", javaClass)
    private val D = IconLoader.getIcon("symbols/d.svg", javaClass)
    private val E = IconLoader.getIcon("symbols/e.svg", javaClass)
    private val F = IconLoader.getIcon("symbols/f.svg", javaClass)
    private val G = IconLoader.getIcon("symbols/g.svg", javaClass)
    private val H = IconLoader.getIcon("symbols/h.svg", javaClass)
    private val I = IconLoader.getIcon("symbols/i.svg", javaClass)
    private val J = IconLoader.getIcon("symbols/j.svg", javaClass)
    private val K = IconLoader.getIcon("symbols/k.svg", javaClass)
    private val L = IconLoader.getIcon("symbols/l.svg", javaClass)
    private val M = IconLoader.getIcon("symbols/m.svg", javaClass)
    private val N = IconLoader.getIcon("symbols/n.svg", javaClass)
    private val O = IconLoader.getIcon("symbols/o.svg", javaClass)
    private val P = IconLoader.getIcon("symbols/p.svg", javaClass)
    private val Q = IconLoader.getIcon("symbols/q.svg", javaClass)
    private val R = IconLoader.getIcon("symbols/r.svg", javaClass)
    private val S = IconLoader.getIcon("symbols/s.svg", javaClass)
    private val T = IconLoader.getIcon("symbols/t.svg", javaClass)
    private val U = IconLoader.getIcon("symbols/u.svg", javaClass)
    private val V = IconLoader.getIcon("symbols/v.svg", javaClass)
    private val W = IconLoader.getIcon("symbols/w.svg", javaClass)
    private val X = IconLoader.getIcon("symbols/x.svg", javaClass)
    private val Y = IconLoader.getIcon("symbols/y.svg", javaClass)
    private val Z = IconLoader.getIcon("symbols/z.svg", javaClass)

    fun fromChar(c: Char) = when (c) {
        '0' -> ZERO
        '1' -> ONE
        '2' -> TWO
        '3' -> THREE
        '4' -> FOUR
        '5' -> FIVE
        '6' -> SIX
        '7' -> SEVEN
        '8' -> EIGHT
        '9' -> NINE

        'a' -> A
        'b' -> B
        'c' -> C
        'd' -> D
        'e' -> E
        'f' -> F
        'g' -> G
        'h' -> H
        'i' -> I
        'j' -> J
        'k' -> K
        'l' -> L
        'm' -> M
        'n' -> N
        'o' -> O
        'p' -> P
        'q' -> Q
        'r' -> R
        's' -> S
        't' -> T
        'u' -> U
        'v' -> V
        'w' -> W
        'x' -> X
        'y' -> Y
        'z' -> Z

        else -> UNKNOWN
    }
}

fun alignToInt(g: Graphics) {
    if (g !is Graphics2D) {
        return
    }

    val rm = PaintUtil.RoundingMode.ROUND_FLOOR_BIAS
    PaintUtil.alignTxToInt(g, null, true, true, rm)
    PaintUtil.alignClipToInt(g, true, true, rm, rm)
}

// We could replace this with com.intellij.ui.icons.toRetinaAwareIcon at
// some point if we want to break support for Gateway < 232.
fun toRetinaAwareIcon(image: BufferedImage): Icon {
    val sysScale = JBUIScale.sysScale()
    return object : Icon {
        override fun paintIcon(
            c: Component?,
            g: Graphics,
            x: Int,
            y: Int,
        ) {
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

        override fun toString(): String = "TemplateIconDownloader.toRetinaAwareIcon for $image"
    }
}
