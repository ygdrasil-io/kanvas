package org.graphiks.kanvas.codec

public class ExtendedCodecDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<Codec.Decoder> = listOf(
        AvifDecoder.RegistryEntry,
        RawDecoder.RegistryEntry,
    )
}
