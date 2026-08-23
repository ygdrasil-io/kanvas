package org.graphiks.kanvas.codec.png

import org.graphiks.kanvas.image.Bitmap

internal operator fun Bitmap.get(index: Int): Int = getArgb(index % width, index / width)

internal operator fun Bitmap.set(index: Int, argb: Int) {
    setArgb(index % width, index / width, argb)
}
