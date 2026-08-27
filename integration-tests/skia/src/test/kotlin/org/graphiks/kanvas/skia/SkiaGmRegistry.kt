package org.graphiks.kanvas.skia

import java.io.BufferedReader
import java.io.InputStreamReader

object SkiaGmRegistry {
    data class Entry(val provider: String, val gm: SkiaGm?, val diagnostic: String?)

    fun entries(): List<Entry> {
        val result = mutableListOf<Entry>()
        val resourceName = "META-INF/services/${SkiaGm::class.qualifiedName}"
        val classLoader = SkiaGm::class.java.classLoader
        val stream = classLoader.getResourceAsStream(resourceName)
            ?: throw IllegalStateException("No $resourceName found")
        BufferedReader(InputStreamReader(stream)).use { reader ->
            reader.lineSequence().map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { className ->
                    try {
                        val clazz = classLoader.loadClass(className)
                        require(SkiaGm::class.java.isAssignableFrom(clazz)) {
                            "provider does not implement SkiaGm"
                        }
                        result += Entry(className, clazz.getDeclaredConstructor().newInstance() as SkiaGm, null)
                    } catch (failure: Throwable) {
                        result += Entry(className, null, "${failure::class.simpleName}: ${failure.message.orEmpty()}")
                    }
                }
        }
        return result
    }

    fun all(): List<SkiaGm> {
        val gms = entries().mapNotNull { it.gm }
        require(gms.isNotEmpty()) { "No SkiaGms registered." }
        return gms
    }
}
