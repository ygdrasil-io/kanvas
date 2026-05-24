package org.skia.codec.wbmp

import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec

public class WbmpImageIoDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkWbmpCodec.Decoder)
}
