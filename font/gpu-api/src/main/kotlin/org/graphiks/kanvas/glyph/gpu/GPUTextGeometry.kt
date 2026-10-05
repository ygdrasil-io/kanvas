package org.graphiks.kanvas.glyph.gpu

data class GPUTextIntRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left) { "right must be >= left." }
        require(bottom >= top) { "bottom must be >= top." }
    }
}

data class GPUTextFloatRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(right >= left) { "right must be >= left." }
        require(bottom >= top) { "bottom must be >= top." }
    }
}
