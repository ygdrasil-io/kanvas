package org.graphiks.kanvas.paint

data class MeshChildren(
    val entries: List<Entry> = emptyList(),
) {
    data class Entry(val name: String, val child: MeshChild)

    companion object {
        val EMPTY = MeshChildren()
        fun of(vararg pairs: Pair<String, MeshChild>) =
            MeshChildren(pairs.map { Entry(it.first, it.second) })
    }

    fun getShader(name: String): Shader? =
        entries.firstOrNull { it.name == name }?.child?.let { (it as? ShaderChild)?.shader }

    fun getColorFilter(name: String): ColorFilter? =
        entries.firstOrNull { it.name == name }?.child?.let { (it as? ColorFilterChild)?.filter }

    fun getBlender(name: String): Blender? =
        entries.firstOrNull { it.name == name }?.child?.let { (it as? BlenderChild)?.blender }
}
