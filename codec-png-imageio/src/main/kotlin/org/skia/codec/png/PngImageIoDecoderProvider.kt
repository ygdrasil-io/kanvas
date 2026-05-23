package org.skia.codec.png

import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec

public class PngImageIoDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkPngCodec.Decoder)
}
