package org.graphiks.kanvas.codec.jpeg2000

import java.util.Base64

internal object Jpeg2000TestFixtures {
    private const val OPENJPEG_FIXTURE =
        "/jpeg2000-openjpeg/openjpeg-2.5.4-lossless-5x3.j2k.base64"
    private const val OPENJPEG_TWO_CODEBLOCKS_FIXTURE =
        "/jpeg2000-openjpeg/openjpeg-2.5.4-lossless-two-codeblocks-96x17.j2k.base64"

    fun openJpegLossless5x3(): ByteArray = Base64.getDecoder().decode(
        requireNotNull(Jpeg2000TestFixtures::class.java.getResourceAsStream(OPENJPEG_FIXTURE)) {
            "missing $OPENJPEG_FIXTURE"
        }.use { input -> input.readBytes().decodeToString().trim() },
    )

    fun openJpegLosslessTwoCodeblocks96x17(): ByteArray = Base64.getDecoder().decode(
        requireNotNull(Jpeg2000TestFixtures::class.java.getResourceAsStream(OPENJPEG_TWO_CODEBLOCKS_FIXTURE)) {
            "missing $OPENJPEG_TWO_CODEBLOCKS_FIXTURE"
        }.use { input -> input.readBytes().decodeToString().trim() },
    )
}
