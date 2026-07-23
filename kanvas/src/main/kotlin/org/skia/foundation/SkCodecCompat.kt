package org.skia.foundation

import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.SkcmsICCProfile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

public enum class SkColorType(public val bytesPerPixel: Int) {
    kUnknown(0),
    kAlpha_8(1),
    kRGB_565(2),
    kARGB_4444(2),
    kRGBA_8888(4),
    kRGB_888x(4),
    kBGRA_8888(4),
    kRGBA_1010102(4),
    kBGRA_1010102(4),
    kRGB_101010x(4),
    kBGR_101010x(4),
    kBGR_101010x_XR(4),
    kBGRA_10101010_XR(8),
    kRGBA_10x6(8),
    kGray_8(1),
    kRGBA_F16Norm(8),
    kRGBA_F16(8),
    kRGB_F16F16F16x(8),
    kRGBA_F32(16),
    kR8G8_unorm(2),
    kA16_float(2),
    kR16G16_float(4),
    kA16_unorm(2),
    kR16_unorm(2),
    kR16G16_unorm(4),
    kR16G16B16A16_unorm(8),
    kSRGBA_8888(4),
    kR8_unorm(1),
    ;

    public fun isValid(): Boolean = this != kUnknown
}

public enum class SkAlphaType {
    kUnknown,
    kOpaque,
    kPremul,
    kUnpremul,
    ;

    public fun isOpaque(): Boolean = this == kOpaque
    public fun isValid(): Boolean = this != kUnknown
}

public enum class SkEncodedImageFormat {
    kBMP,
    kGIF,
    kICO,
    kJPEG,
    kJPEG2000,
    kPNG,
    kWBMP,
    kWEBP,
    kPKM,
    kKTX,
    kASTC,
    kDNG,
    kHEIF,
    kAVIF,
    kJPEGXL,
}

public enum class SkEncodedOrigin(public val exifValue: Int) {
    kTopLeft(1),
    kTopRight(2),
    kBottomRight(3),
    kBottomLeft(4),
    kLeftTop(5),
    kRightTop(6),
    kRightBottom(7),
    kLeftBottom(8),
    ;

    public fun swapsWidthHeight(): Boolean = ordinal >= kLeftTop.ordinal

    public fun toMatrix(w: Int, h: Int): SkMatrix {
        val fw = w.toFloat()
        val fh = h.toFloat()
        return when (this) {
            kTopLeft -> SkMatrix.I()
            kTopRight -> SkMatrix.MakeAll(-1f, 0f, fw, 0f, 1f, 0f, 0f, 0f, 1f)
            kBottomRight -> SkMatrix.MakeAll(-1f, 0f, fw, 0f, -1f, fh, 0f, 0f, 1f)
            kBottomLeft -> SkMatrix.MakeAll(1f, 0f, 0f, 0f, -1f, fh, 0f, 0f, 1f)
            kLeftTop -> SkMatrix.MakeAll(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            kRightTop -> SkMatrix.MakeAll(0f, -1f, fw, 1f, 0f, 0f, 0f, 0f, 1f)
            kRightBottom -> SkMatrix.MakeAll(0f, -1f, fw, -1f, 0f, fh, 0f, 0f, 1f)
            kLeftBottom -> SkMatrix.MakeAll(0f, 1f, 0f, -1f, 0f, fh, 0f, 0f, 1f)
        }
    }

    public companion object {
        public val kDefault: SkEncodedOrigin = kTopLeft
        public val kLast: SkEncodedOrigin = kLeftBottom

        public fun fromExifValue(v: Int): SkEncodedOrigin =
            entries.firstOrNull { it.exifValue == v } ?: kDefault
    }
}

public enum class SkColorSpaceProfileStatus {
    kSupported,
    kUnsupported,
}

public class SkColorSpace private constructor(
    public val transferFn: SkcmsTransferFunction,
    toXYZD50: SkcmsMatrix3x3,
    public val colorProfile: ColorProfile,
    originalIccBytes: ByteArray?,
    private val srgb: Boolean,
    private val srgbTransfer: Boolean,
    private val linear: Boolean,
    public val profileStatus: SkColorSpaceProfileStatus,
    public val profileRefusalCode: String?,
) {
    private val originalIccBytes: ByteArray? = originalIccBytes?.copyOf()
    private val toXYZD50Snapshot: SkcmsMatrix3x3 = toXYZD50.copy()

    public val iccProfileBytes: ByteArray? get() = originalIccBytes?.copyOf()
    public val toXYZD50: SkcmsMatrix3x3 get() = toXYZD50Snapshot.copy()
    public fun isSRGB(): Boolean = srgb
    public fun gammaIsLinear(): Boolean = linear
    public fun gammaCloseToSRGB(): Boolean = srgbTransfer
    public fun isProfileSupported(): Boolean = profileStatus == SkColorSpaceProfileStatus.kSupported

    override fun toString(): String =
        when {
            srgb -> "SkColorSpace(sRGB)"
            profileStatus == SkColorSpaceProfileStatus.kUnsupported ->
                "SkColorSpace(unsupported=${profileRefusalCode ?: "unknown"})"
            else -> "SkColorSpace(RGB)"
        }

    public companion object {
        private val SRGB = SkColorSpace(
            transferFn = SkNamedTransferFn.kSRGB,
            toXYZD50 = SkNamedGamut.kSRGB,
            colorProfile = ColorProfiles.sRGB(),
            originalIccBytes = null,
            srgb = true,
            srgbTransfer = true,
            linear = false,
            profileStatus = SkColorSpaceProfileStatus.kSupported,
            profileRefusalCode = null,
        )
        private val LINEAR_SRGB = SkColorSpace(
            transferFn = SkNamedTransferFn.kLinear,
            toXYZD50 = SkNamedGamut.kSRGB,
            colorProfile = ColorProfile(ColorModel.RGB, SkNamedGamut.kSRGB, SkNamedTransferFn.kLinear),
            originalIccBytes = null,
            srgb = false,
            srgbTransfer = false,
            linear = true,
            profileStatus = SkColorSpaceProfileStatus.kSupported,
            profileRefusalCode = null,
        )

        public fun makeSRGB(): SkColorSpace = SRGB
        public fun MakeSRGB(): SkColorSpace = SRGB
        public fun makeSRGBLinear(): SkColorSpace = LINEAR_SRGB
        public fun MakeSRGBLinear(): SkColorSpace = LINEAR_SRGB

        public fun make(profile: SkcmsICCProfile): SkColorSpace? {
            val colorProfile = profile.colorProfile
            if (colorProfile.colorModel != ColorModel.RGB ||
                colorProfile.unsupportedCode != null ||
                colorProfile.isHdr ||
                !colorProfile.hasMatrixTrc
            ) {
                return null
            }
            val transferFunction = colorProfile.transferFunction ?: return null
            val matrix = colorProfile.toXyzD50 ?: return null
            val originalBytes = profile.bytes.takeIf { it.isNotEmpty() }
            return makeMatrixTrc(colorProfile, transferFunction, matrix, originalBytes)
        }

        /** Retains parsed profile metadata and an explicit refusal when [make] cannot map it. */
        public fun makeProfileAware(profile: SkcmsICCProfile): SkColorSpace =
            make(profile) ?: makeUnsupportedProfile(profile)

        public fun makeRGB(
            transferFn: SkcmsTransferFunction,
            toXYZD50: SkcmsMatrix3x3,
        ): SkColorSpace? = makeMatrixTrc(
            colorProfile = ColorProfile(ColorModel.RGB, toXYZD50, transferFn),
            transferFn = transferFn,
            toXYZD50 = toXYZD50,
            originalIccBytes = null,
        )

        private fun makeMatrixTrc(
            colorProfile: ColorProfile,
            transferFn: SkcmsTransferFunction,
            toXYZD50: SkcmsMatrix3x3,
            originalIccBytes: ByteArray?,
        ): SkColorSpace {
            val isSrgbGamut = isSrgbMatrix(toXYZD50)
            val isSrgbTransfer = transferFunctionsNear(transferFn, SkNamedTransferFn.kSRGB)
            return SkColorSpace(
                transferFn = transferFn,
                toXYZD50 = toXYZD50,
                colorProfile = colorProfile,
                originalIccBytes = originalIccBytes,
                srgb = isSrgbGamut && isSrgbTransfer,
                srgbTransfer = isSrgbTransfer,
                linear = transferFunctionsNear(transferFn, SkNamedTransferFn.kLinear),
                profileStatus = SkColorSpaceProfileStatus.kSupported,
                profileRefusalCode = null,
            )
        }

        private fun makeUnsupportedProfile(profile: SkcmsICCProfile): SkColorSpace {
            val colorProfile = profile.colorProfile
            val refusalCode = when {
                colorProfile.unsupportedCode != null -> colorProfile.unsupportedCode
                colorProfile.colorModel == ColorModel.GRAY -> "icc.gray.unsupported"
                colorProfile.isHdr -> "color.hdr.unsupported"
                else -> "icc.profile.shape.unsupported"
            }
            return SkColorSpace(
                transferFn = profile.transferFn,
                toXYZD50 = profile.toXYZD50,
                colorProfile = colorProfile,
                originalIccBytes = profile.bytes.takeIf { it.isNotEmpty() },
                srgb = false,
                srgbTransfer = false,
                linear = false,
                profileStatus = SkColorSpaceProfileStatus.kUnsupported,
                profileRefusalCode = refusalCode,
            )
        }

        // Encoding identity is deliberately narrower than the writer's D50 normalization allowance.
        private fun isSrgbMatrix(matrix: SkcmsMatrix3x3): Boolean =
            matricesNear(matrix, SkNamedGamut.kSRGB) || matricesNear(matrix, SERIALIZED_SRGB_GAMUT)

        private fun matricesNear(left: SkcmsMatrix3x3, right: SkcmsMatrix3x3): Boolean {
            for (row in 0 until 3) for (column in 0 until 3) {
                if (abs(left[row, column] - right[row, column]) > SRGB_MATRIX_IDENTITY_TOLERANCE) return false
            }
            return true
        }

        private fun transferFunctionsNear(
            left: SkcmsTransferFunction,
            right: SkcmsTransferFunction,
        ): Boolean = listOf(
            left.g to right.g,
            left.a to right.a,
            left.b to right.b,
            left.c to right.c,
            left.d to right.d,
            left.e to right.e,
            left.f to right.f,
        ).all { (leftValue, rightValue) -> abs(leftValue - rightValue) <= ICC_TRANSFER_TOLERANCE }

        private val SERIALIZED_SRGB_GAMUT: SkcmsMatrix3x3 by lazy {
            checkNotNull(SkcmsICCProfile.fromColorProfile(ColorProfiles.sRGB()).colorProfile.toXyzD50)
        }

        private const val SRGB_MATRIX_IDENTITY_TOLERANCE: Float = 2f / 65_536f
        private const val ICC_TRANSFER_TOLERANCE: Float = 2f / 65_536f
    }
}

public class SkImageInfo private constructor(
    public val width: Int,
    public val height: Int,
    public val colorType: SkColorType,
    public val alphaType: SkAlphaType,
    public val colorSpace: SkColorSpace,
) {
    init {
        require(width >= 0 && height >= 0) { "negative dimensions: ${width}x$height" }
    }

    public fun dimensions(): SkISize = SkISize.Make(width, height)
    public fun bounds(): SkIRect = SkIRect.MakeWH(width, height)
    public fun isEmpty(): Boolean = width <= 0 || height <= 0
    public fun isOpaque(): Boolean = alphaType == SkAlphaType.kOpaque
    public fun bytesPerPixel(): Int = colorType.bytesPerPixel
    public fun minRowBytes(): Int = width * bytesPerPixel()
    public fun makeWH(newW: Int, newH: Int): SkImageInfo =
        SkImageInfo(newW, newH, colorType, alphaType, colorSpace)
    public fun makeColorType(ct: SkColorType): SkImageInfo =
        SkImageInfo(width, height, ct, alphaType, colorSpace)
    public fun makeAlphaType(at: SkAlphaType): SkImageInfo =
        SkImageInfo(width, height, colorType, at, colorSpace)
    public fun makeColorSpace(cs: SkColorSpace): SkImageInfo =
        SkImageInfo(width, height, colorType, alphaType, cs)

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is SkImageInfo &&
                width == other.width &&
                height == other.height &&
                colorType == other.colorType &&
                alphaType == other.alphaType &&
                colorSpace == other.colorSpace
            )

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + colorType.hashCode()
        result = 31 * result + alphaType.hashCode()
        result = 31 * result + colorSpace.hashCode()
        return result
    }

    public companion object {
        public fun Make(
            width: Int,
            height: Int,
            colorType: SkColorType = SkColorType.kRGBA_8888,
            alphaType: SkAlphaType = defaultAlphaTypeFor(colorType),
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = SkImageInfo(width, height, colorType, alphaType, colorSpace)

        public fun MakeN32(
            width: Int,
            height: Int,
            alphaType: SkAlphaType = SkAlphaType.kUnpremul,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = Make(width, height, SkColorType.kRGBA_8888, alphaType, colorSpace)

        public fun MakeN32Premul(
            width: Int,
            height: Int,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = Make(width, height, SkColorType.kRGBA_8888, SkAlphaType.kPremul, colorSpace)

        public fun MakeA8(
            width: Int,
            height: Int,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = Make(width, height, SkColorType.kAlpha_8, SkAlphaType.kPremul, colorSpace)

        public fun Make4444(
            width: Int,
            height: Int,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = Make(width, height, SkColorType.kARGB_4444, SkAlphaType.kPremul, colorSpace)

        public fun MakeRGB565(
            width: Int,
            height: Int,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = Make(width, height, SkColorType.kRGB_565, SkAlphaType.kOpaque, colorSpace)

        public fun MakeGray8(
            width: Int,
            height: Int,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = Make(width, height, SkColorType.kGray_8, SkAlphaType.kOpaque, colorSpace)

        private fun defaultAlphaTypeFor(ct: SkColorType): SkAlphaType = when (ct) {
            SkColorType.kRGB_565,
            SkColorType.kGray_8,
                -> SkAlphaType.kOpaque
            SkColorType.kRGBA_F16,
            SkColorType.kRGBA_F16Norm,
            SkColorType.kAlpha_8,
            SkColorType.kARGB_4444,
                -> SkAlphaType.kPremul
            SkColorType.kUnknown -> SkAlphaType.kUnknown
            else -> SkAlphaType.kUnpremul
        }
    }
}

public class SkBitmap(
    public val width: Int,
    public val height: Int,
    public val colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
    public val colorType: SkColorType = SkColorType.kRGBA_8888,
) {
    public val pixels8888: IntArray = IntArray(width * height)
    public val pixelsF16: FloatArray =
        if (colorType == SkColorType.kRGBA_F16 || colorType == SkColorType.kRGBA_F16Norm) {
            FloatArray(width * height * 4)
        } else {
            FloatArray(0)
        }
    public val pixels: IntArray get() = pixels8888

    public fun eraseColor(c: SkColor) {
        pixels8888.fill(c)
        if (pixelsF16.isNotEmpty()) {
            val a = SkColorGetA(c) / 255f
            val r = SkColorGetR(c) / 255f
            val g = SkColorGetG(c) / 255f
            val b = SkColorGetB(c) / 255f
            var i = 0
            while (i < pixelsF16.size) {
                pixelsF16[i] = r * a
                pixelsF16[i + 1] = g * a
                pixelsF16[i + 2] = b * a
                pixelsF16[i + 3] = a
                i += 4
            }
        }
    }

    public fun getPixel(x: Int, y: Int): SkColor {
        if (x !in 0 until width || y !in 0 until height) return 0
        return pixels8888[y * width + x]
    }

    public fun getPixelAsSrgb(x: Int, y: Int): SkColor = getPixel(x, y)

    public fun setPixel(x: Int, y: Int, c: SkColor) {
        if (x !in 0 until width || y !in 0 until height) return
        pixels8888[y * width + x] = convertForColorType(c)
        if (pixelsF16.isNotEmpty()) {
            val a = SkColorGetA(c) / 255f
            setPixelF16(
                x = x,
                y = y,
                r = (SkColorGetR(c) / 255f) * a,
                g = (SkColorGetG(c) / 255f) * a,
                b = (SkColorGetB(c) / 255f) * a,
                a = a,
            )
        }
    }

    public fun setPixelF16(x: Int, y: Int, r: Float, g: Float, b: Float, a: Float) {
        if (x !in 0 until width || y !in 0 until height) return
        if (pixelsF16.isNotEmpty()) {
            val offset = (y * width + x) * 4
            pixelsF16[offset] = r
            pixelsF16[offset + 1] = g
            pixelsF16[offset + 2] = b
            pixelsF16[offset + 3] = a
        }
        val ia = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
        val invA = if (a > 0f) 1f / a else 0f
        val ir = (r * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
        val ig = (g * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
        val ib = (b * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
        pixels8888[y * width + x] = convertForColorType(SkColorSetARGB(ia, ir, ig, ib))
    }

    public fun getPixelF16(x: Int, y: Int, out: FloatArray): Boolean {
        require(out.size >= 4) { "out must hold at least four components" }
        if (x !in 0 until width || y !in 0 until height || pixelsF16.isEmpty()) return false
        val offset = (y * width + x) * 4
        out[0] = pixelsF16[offset]
        out[1] = pixelsF16[offset + 1]
        out[2] = pixelsF16[offset + 2]
        out[3] = pixelsF16[offset + 3]
        return true
    }

    private fun convertForColorType(c: SkColor): SkColor {
        val a = SkColorGetA(c)
        val r = SkColorGetR(c)
        val g = SkColorGetG(c)
        val b = SkColorGetB(c)
        return when (colorType) {
            SkColorType.kAlpha_8 -> SkColorSetARGB(a, 0, 0, 0)
            SkColorType.kGray_8 -> {
                val l = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
                SkColorSetARGB(0xFF, l, l, l)
            }
            SkColorType.kRGB_565 -> {
                val r5 = (r * 31 + 127) / 255
                val g6 = (g * 63 + 127) / 255
                val b5 = (b * 31 + 127) / 255
                SkColorSetARGB(
                    0xFF,
                    (r5 * 255 + 15) / 31,
                    (g6 * 255 + 31) / 63,
                    (b5 * 255 + 15) / 31,
                )
            }
            SkColorType.kARGB_4444 -> {
                val a4 = (a * 15 + 127) / 255
                val r4 = (r * 15 + 127) / 255
                val g4 = (g * 15 + 127) / 255
                val b4 = (b * 15 + 127) / 255
                SkColorSetARGB(
                    (a4 * 255 + 7) / 15,
                    (r4 * 255 + 7) / 15,
                    (g4 * 255 + 7) / 15,
                    (b4 * 255 + 7) / 15,
                )
            }
            else -> c
        }
    }
}

public class SkData private constructor(private val bytes: ByteArray) {
    public val size: Int get() = bytes.size
    public fun byteAt(index: Int): Byte = bytes[index]
    public fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean =
        this === other || (other is SkData && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    public companion object {
        public val EMPTY: SkData = SkData(ByteArray(0))
        public fun MakeWithCopy(src: ByteArray): SkData =
            if (src.isEmpty()) EMPTY else SkData(src.copyOf())
        public fun MakeUninitialized(size: Int): SkData {
            require(size >= 0) { "SkData size must be non-negative" }
            return if (size == 0) EMPTY else SkData(ByteArray(size))
        }
    }
}

public class SkPixmap {
    private var info: SkImageInfo = SkImageInfo.Make(0, 0, SkColorType.kUnknown, SkAlphaType.kUnknown)
    private var buffer: ByteBuffer = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN)
    private var rowBytes: Int = 0

    public constructor()
    public constructor(info: SkImageInfo, addr: ByteBuffer, rowBytes: Int) {
        reset(info, addr, rowBytes)
    }

    public fun reset() {
        info = SkImageInfo.Make(0, 0, SkColorType.kUnknown, SkAlphaType.kUnknown)
        buffer = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN)
        rowBytes = 0
    }

    public fun reset(info: SkImageInfo, addr: ByteBuffer, rowBytes: Int) {
        require(info.isEmpty() || rowBytes >= info.minRowBytes()) {
            "rowBytes=$rowBytes < minRowBytes=${info.minRowBytes()}"
        }
        this.info = info
        this.buffer = addr.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        this.rowBytes = rowBytes
    }

    public fun info(): SkImageInfo = info
    public fun rowBytes(): Int = rowBytes
    public fun addr(): ByteBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    public fun width(): Int = info.width
    public fun height(): Int = info.height
    public fun colorType(): SkColorType = info.colorType
    public fun alphaType(): SkAlphaType = info.alphaType
    public fun colorSpace(): SkColorSpace? = info.colorSpace
    public fun bounds(): SkIRect = SkIRect.MakeWH(width(), height())

    public fun computeByteSize(): Long =
        if (height() == 0 || width() == 0) 0L
        else (height() - 1).toLong() * rowBytes + width().toLong() * info.bytesPerPixel()

    public fun getColor(x: Int, y: Int): SkColor {
        if (x !in 0 until width() || y !in 0 until height()) return 0
        val offset = y * rowBytes + x * info.bytesPerPixel()
        return when (info.colorType) {
            SkColorType.kRGBA_8888,
            SkColorType.kBGRA_8888,
                -> buffer.getInt(offset)
            SkColorType.kAlpha_8 -> SkColorSetARGB(buffer.get(offset).toInt() and 0xFF, 0, 0, 0)
            SkColorType.kGray_8 -> {
                val l = buffer.get(offset).toInt() and 0xFF
                SkColorSetARGB(0xFF, l, l, l)
            }
            else -> 0
        }
    }
}

public class SkImage(
    public val width: Int,
    public val height: Int,
    public val pixels: IntArray = IntArray(width * height),
    public val colorType: SkColorType = SkColorType.kRGBA_8888,
) {
    public fun peekPixel(x: Int, y: Int): SkColor =
        if (x in 0 until width && y in 0 until height) pixels[y * width + x] else 0
}
