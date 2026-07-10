package org.graphiks.kanvas.codec.jpeg

/** A JPEG marker segment as it appeared in the encoded document. */
public data class JpegSegment(
    val marker: Int,
    val payload: ByteArray,
    val offset: Long,
)
