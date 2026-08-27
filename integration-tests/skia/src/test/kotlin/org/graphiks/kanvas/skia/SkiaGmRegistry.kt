package org.graphiks.kanvas.skia

import java.io.BufferedReader
import java.io.InputStreamReader

object SkiaGmRegistry {
    data class Entry(val provider: String, val gm: SkiaGm?, val diagnostic: String?)

    fun entries(): List<Entry> {
        val resourceName = "META-INF/services/${SkiaGm::class.qualifiedName}"
        val classLoader = SkiaGm::class.java.classLoader
        val stream = classLoader.getResourceAsStream(resourceName)
            ?: throw IllegalStateException("No $resourceName found")
        return BufferedReader(InputStreamReader(stream)).use { reader -> entries(reader.lineSequence(), classLoader) }
    }

    internal fun entries(providerLines: Sequence<String>, classLoader: ClassLoader): List<Entry> =
        providerLines.map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { className ->
                try {
                    val clazz = classLoader.loadClass(className)
                    require(SkiaGm::class.java.isAssignableFrom(clazz)) {
                        "provider does not implement SkiaGm"
                    }
                    Entry(className, clazz.getDeclaredConstructor().newInstance() as SkiaGm, null)
                } catch (failure: Throwable) {
                    Entry(className, null, "${failure::class.simpleName}: ${failure.message.orEmpty()}")
                }
            }.toList()

    fun all(): List<SkiaGm> {
        val gms = entries().mapNotNull { it.gm }
        require(gms.isNotEmpty()) { "No SkiaGms registered." }
        return gms
    }
}
