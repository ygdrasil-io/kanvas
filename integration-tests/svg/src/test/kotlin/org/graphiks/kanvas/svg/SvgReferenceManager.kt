package org.graphiks.kanvas.svg

import org.graphiks.kanvas.test.ReferenceManager

object SvgReferenceManager {
    fun getReferencePngPath(svgPath: String): String {
        require(svgPath.endsWith(".svg")) { "svgPath must end with .svg" }
        val pngPath = svgPath.removeSuffix(".svg") + ".png"
        return pngPath.replaceFirst("by-render-family", "generated-references/by-render-family")
    }

    fun hasReferencePng(svgPath: String): Boolean {
        return ReferenceManager.hasReference(getReferencePngPath(svgPath))
    }

    fun loadReferencePng(svgPath: String): ByteArray {
        return ReferenceManager.loadReference(getReferencePngPath(svgPath))
    }
}
