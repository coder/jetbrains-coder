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
        if (path.isBlank()) {
            return CoderIcons.UNKNOWN
        }

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

        return CoderIcons.UNKNOWN
    }
}