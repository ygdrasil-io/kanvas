package org.skia.codec.bmp

import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec

public class BmpImageIoDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkBmpCodec.Decoder)
}
