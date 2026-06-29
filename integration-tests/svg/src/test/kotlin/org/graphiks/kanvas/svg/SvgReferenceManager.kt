package org.graphiks.kanvas.svg

/**
 * Gère le chargement des PNG de référence checkés dans le repo.
 * Les PNG sont stockés dans src/test/resources/generated-references/
 * avec la même structure que les SVG dans src/main/resources/by-render-family/
 */
object SvgReferenceManager {
    /**
     * Résout le chemin d'un PNG de référence à partir d'un chemin SVG.
     * Ex: "/by-render-family/geometric/geometric-1.svg"
     *     → "/generated-references/by-render-family/geometric/geometric-1.png"
     */
    fun getReferencePngPath(svgPath: String): String {
        require(svgPath.endsWith(".svg")) { "svgPath must end with .svg" }
        return svgPath
            .replace(".svg", ".png")
            .replaceFirst("by-render-family", "generated-references/by-render-family")
    }

    /**
     * Charge un PNG de référence depuis les ressources du classpath.
     * @param svgPath Chemin vers le SVG (ex: "/by-render-family/geometric/geometric-1.svg")
     * @return ByteArray du PNG (format RGBA, 4 bytes par pixel)
     */
    fun loadReferencePng(svgPath: String): ByteArray {
        val pngPath = getReferencePngPath(svgPath)
        val resource = object {}.javaClass.getResource(pngPath)
            ?: error("Reference PNG not found: $pngPath (for SVG: $svgPath)")
        return resource.readBytes()
    }

    /**
     * Vérifie qu'un PNG de référence existe pour un SVG donné.
     */
    fun hasReferencePng(svgPath: String): Boolean {
        val pngPath = getReferencePngPath(svgPath)
        return object {}.javaClass.getResource(pngPath) != null
    }

    /**
     * Liste tous les SVG dans by-render-family/.
     */
    fun listAllSvgPaths(): List<String> {
        val loader = object {}.javaClass.classLoader
        val resources = loader.resources("by-render-family")
        return resources.toList().flatMap { url ->
            val path = url.path.removePrefix("/")
            if (path.endsWith(".svg")) {
                listOf("/$path")
            } else {
                loader.resources(path).toList().mapNotNull { subUrl ->
                    val subPath = subUrl.path.removePrefix("/")
                    if (subPath.endsWith(".svg")) "/$subPath" else null
                }
            }
        }
    }
}
