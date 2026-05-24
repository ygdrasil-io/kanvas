package org.skia.codec

public class ExtendedCodecDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(
        SkAvifDecoder.RegistryEntry,
        SkJpegxlDecoder.RegistryEntry,
        SkRawDecoder.RegistryEntry,
    )
}
