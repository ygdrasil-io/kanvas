package org.skia.codec.jpeg

import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec

public class JpegImageIoDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkJpegCodec.Decoder)
}
