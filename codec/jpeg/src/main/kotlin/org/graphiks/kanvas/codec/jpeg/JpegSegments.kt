package org.graphiks.kanvas.codec.jpeg

/**
 * A JPEG marker segment as it appeared in the encoded document.
 *
 * [range] is the inclusive byte range of this segment's payload in its owning
 * [JpegDocument]. Obtain a defensive copy through [JpegDocument.copyPayload].
 */
public class JpegSegment internal constructor(
    val marker: Int,
    val offset: Long,
    val range: IntRange,
)
