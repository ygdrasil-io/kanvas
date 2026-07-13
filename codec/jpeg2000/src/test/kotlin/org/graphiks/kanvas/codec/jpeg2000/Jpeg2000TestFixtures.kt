package org.graphiks.kanvas.codec.jpeg2000

import java.util.Base64

internal object Jpeg2000TestFixtures {
    private const val OPENJPEG_FIXTURE =
        "/jpeg2000-openjpeg/openjpeg-2.5.4-lossless-5x3.j2k.base64"
    private const val OPENJPEG_TWO_CODEBLOCKS_FIXTURE =
        "/jpeg2000-openjpeg/openjpeg-2.5.4-lossless-two-codeblocks-96x17.j2k.base64"
    private const val OPENJPEG_NDECOMP_ONE_FIXTURE =
        "/jpeg2000-openjpeg/openjpeg-2.5.4-lossless-ndecomp1-96x17.j2k.base64"
    private const val OPENJPEG_NDECOMP_ONE_ODD_FIXTURE =
        "/jpeg2000-openjpeg/openjpeg-2.5.4-lossless-ndecomp1-5x3.j2k.base64"
    private const val OPENJPEG_NDECOMP_ONE_ODD_RANDOM_FIXTURE =
        "/jpeg2000-openjpeg/openjpeg-2.5.4-lossless-ndecomp1-5x5-random.j2k.base64"
    private const val OPENJPEG_NDECOMP_TWO_FIXTURE =
        "/jpeg2000-openjpeg/openjpeg-2.5.4-lossless-ndecomp2-8x8.j2k.base64"
    private const val OPENJPEG_NDECOMP_TWO_ODD_FIXTURE =
        "/jpeg2000-openjpeg/openjpeg-2.5.4-lossless-ndecomp2-5x5-random.j2k.base64"
    private const val OPENJPEG_NDECOMP_ZERO_JP2_FIXTURE =
        "/jpeg2000-openjpeg/openjpeg-2.5.4-lossless-ndecomp0-5x5.jp2.base64"

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

    fun openJpegLosslessNdecomp1_96x17(): ByteArray = Base64.getDecoder().decode(
        requireNotNull(Jpeg2000TestFixtures::class.java.getResourceAsStream(OPENJPEG_NDECOMP_ONE_FIXTURE)) {
            "missing $OPENJPEG_NDECOMP_ONE_FIXTURE"
        }.use { input -> input.readBytes().decodeToString().trim() },
    )

    fun openJpegLosslessNdecomp1_5x3(): ByteArray = Base64.getDecoder().decode(
        requireNotNull(Jpeg2000TestFixtures::class.java.getResourceAsStream(OPENJPEG_NDECOMP_ONE_ODD_FIXTURE)) {
            "missing $OPENJPEG_NDECOMP_ONE_ODD_FIXTURE"
        }.use { input -> input.readBytes().decodeToString().trim() },
    )

    fun openJpegLosslessNdecomp1_5x5(): ByteArray = Base64.getDecoder().decode(
        requireNotNull(Jpeg2000TestFixtures::class.java.getResourceAsStream(OPENJPEG_NDECOMP_ONE_ODD_RANDOM_FIXTURE)) {
            "missing $OPENJPEG_NDECOMP_ONE_ODD_RANDOM_FIXTURE"
        }.use { input -> input.readBytes().decodeToString().trim() },
    )

    fun openJpegLosslessNdecomp2_8x8(): ByteArray = Base64.getDecoder().decode(
        requireNotNull(Jpeg2000TestFixtures::class.java.getResourceAsStream(OPENJPEG_NDECOMP_TWO_FIXTURE)) {
            "missing $OPENJPEG_NDECOMP_TWO_FIXTURE"
        }.use { input -> input.readBytes().decodeToString().trim() },
    )

    fun openJpegLosslessNdecomp2_5x5(): ByteArray = Base64.getDecoder().decode(
        requireNotNull(Jpeg2000TestFixtures::class.java.getResourceAsStream(OPENJPEG_NDECOMP_TWO_ODD_FIXTURE)) {
            "missing $OPENJPEG_NDECOMP_TWO_ODD_FIXTURE"
        }.use { input -> input.readBytes().decodeToString().trim() },
    )

    fun openJpegLosslessNdecomp0_5x5Jp2(): ByteArray = Base64.getDecoder().decode(
        requireNotNull(Jpeg2000TestFixtures::class.java.getResourceAsStream(OPENJPEG_NDECOMP_ZERO_JP2_FIXTURE)) {
            "missing $OPENJPEG_NDECOMP_ZERO_JP2_FIXTURE"
        }.use { input -> input.readBytes().decodeToString().trim() },
    )
}
