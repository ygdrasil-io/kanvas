package org.graphiks.kanvas.codec.jpegxl

import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap

/**
 * Bounded implementation scaffold for the first JPEG XL Modular profile.
 *
 * The public decoder invokes this code only after `JpegXlDocument` has
 * established transport ownership and SizeHeader resource ceilings.  It keeps
 * the remaining codestream range explicit so an ISO-BMFF box cannot bleed into
 * the entropy reader.
 */
internal fun decodeNarrowJpegXlModular(
    source: ByteArray,
    codestreamStart: Int,
    codestreamEndExclusive: Int,
    frame: JpegXlFrameInfo,
    fallbackOffset: Int,
): JpegXlDecodeResult = try {
    val reader = JxlBits(source, codestreamStart + 2, codestreamEndExclusive)
    readJxlSizeHeader(reader)
    if (reader.byteOffsetCeil == codestreamEndExclusive) {
        reader.fail("jpegxl.frame.entropy.unimplemented", Codec.Result.kUnimplemented)
    }
    val metadata = readNarrowMetadata(reader)
    readNarrowTransformData(reader)
    reader.jumpToByteBoundary()
    val header = readNarrowFrameHeader(reader, frame)
    val toc = readNarrowToc(reader, header, codestreamEndExclusive)
    val globalReader = JxlBits(source, toc.sections.first().start, toc.sections.first().endExclusive)
    val global = readJxlGlobalModular(globalReader, frame, header)
    val groupSections = if (header.groupCount == 1) null else acGroupSections(toc, header)
    val bitmap = SkBitmap(frame.width, frame.height)
    val groupColumns = ceilDiv(frame.width, header.groupDimension)
    for (groupId in 0 until header.groupCount) {
        val groupOffset = groupSections?.get(groupId)?.start ?: toc.sections.first().start
        val groupColumn = groupId % groupColumns
        val groupRow = groupId / groupColumns
        val groupX = groupColumn * header.groupDimension
        val groupY = groupRow * header.groupDimension
        val groupWidth = minOf(header.groupDimension, frame.width - groupX)
        val groupHeight = minOf(header.groupDimension, frame.height - groupY)
        if (groupWidth <= 0 || groupHeight <= 0) {
            throw JxlModularFailure(
                JpegXlDiagnostic("jpegxl.modular.group.geometry", groupOffset.toLong()),
            )
        }
        val samples = if (groupSections == null) {
            // A frame with exactly one group has one TOC section. JPEG XL
            // serializes its global Modular data and the sole AC group in the
            // same bit reader; the AC stream id is zero in that layout.
            decodeJxlModularGroup(
                reader = globalReader,
                tree = global.tree,
                code = global.code,
                header = global.header,
                streamId = 0,
                width = groupWidth,
                height = groupHeight,
                channelCount = metadata.colorChannels,
            )
        } else {
            decodeNarrowJxlAcGroup(
                source = source,
                section = groupSections[groupId],
                global = global,
                header = header,
                groupId = groupId,
                width = groupWidth,
                height = groupHeight,
                channelCount = metadata.colorChannels,
            )
        }
        for (y in 0 until groupHeight) {
            val sourceRow = y * groupWidth
            val destinationRow = (groupY + y) * frame.width + groupX
            for (x in 0 until groupWidth) {
                val sampleIndex = sourceRow + x
                val red = samples[0][sampleIndex]
                val green = if (metadata.colorChannels == 1) red else samples[1][sampleIndex]
                val blue = if (metadata.colorChannels == 1) red else samples[2][sampleIndex]
                if (red !in 0..255 || green !in 0..255 || blue !in 0..255) {
                    throw JxlModularFailure(
                        JpegXlDiagnostic("jpegxl.modular.sample.bit-depth", groupOffset.toLong()),
                    )
                }
                bitmap.pixels8888[destinationRow + x] =
                    0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
            }
        }
    }
    JpegXlDecodeResult(bitmap, null)
} catch (failure: JxlModularFailure) {
    JpegXlDecodeResult(null, failure.diagnostic)
} catch (_: JxlBitsTruncated) {
    if (fallbackOffset >= codestreamEndExclusive) {
        // A SizeHeader-only stream is already owned by the JPEG XL provider,
        // but contains no frame payload to classify as malformed. Preserve the
        // owner contract instead of misreporting that unsupported frontier as
        // corrupt input.
        JpegXlDecodeResult(
            null,
            JpegXlDiagnostic(
                "jpegxl.frame.entropy.unimplemented",
                fallbackOffset.toLong(),
                Codec.Result.kUnimplemented,
            ),
        )
    } else {
        JpegXlDecodeResult(
            null,
            JpegXlDiagnostic("jpegxl.frame.truncated", fallbackOffset.toLong()),
        )
    }
}

private data class JxlNarrowMetadata(val colorChannels: Int)

private data class JxlNarrowFrameHeader(
    val groupDimension: Int,
    val groupCount: Int,
    val dcGroupCount: Int,
)

private data class JxlSection(val start: Int, val endExclusive: Int)

private data class JxlNarrowToc(val sections: List<JxlSection>)

private data class JxlGroupHeader(
    val useGlobalTree: Boolean,
    val weighted: JxlWeightedHeader,
)

private data class JxlWeightedHeader(
    val p1c: Int,
    val p2c: Int,
    val p3ca: Int,
    val p3cb: Int,
    val p3cc: Int,
    val p3cd: Int,
    val p3ce: Int,
    val weights: IntArray,
)

private data class JxlGlobalModular(
    val tree: JxlMaTree,
    val code: JxlEntropyCode,
    val header: JxlGroupHeader,
)

private data class JxlMaTree(val nodes: List<JxlMaNode>, val leafCount: Int)

private data class JxlMaNode(
    val property: Int,
    val splitValue: Int,
    val left: Int,
    val right: Int,
    val context: Int,
    val predictor: Int,
    val offset: Int,
    val multiplier: Int,
)

private class JxlModularFailure(val diagnostic: JpegXlDiagnostic) : RuntimeException(diagnostic.code)

private fun JxlBits.fail(
    code: String,
    result: Codec.Result = Codec.Result.kErrorInInput,
): Nothing = throw JxlModularFailure(JpegXlDiagnostic(code, byteOffset.toLong(), result))

private fun readNarrowMetadata(reader: JxlBits): JxlNarrowMetadata {
    // ImageMetadata.all_default. The narrow profile must state RGB or Gray,
    // 8-bit integer samples, no extra channel, direct sRGB and non-XYB.
    if (reader.readBool()) reader.fail("jpegxl.modular.metadata.profile", Codec.Result.kUnimplemented)
    if (reader.readBool()) reader.fail("jpegxl.modular.metadata.extra", Codec.Result.kUnimplemented)
    if (reader.readBool()) reader.fail("jpegxl.modular.metadata.float", Codec.Result.kUnimplemented)
    if (reader.readU32(U32(d(8), d(10), d(12), Bits(6, 1))) != 8) {
        reader.fail("jpegxl.modular.metadata.bit-depth", Codec.Result.kUnimplemented)
    }
    if (!reader.readBool()) reader.fail("jpegxl.modular.metadata.buffer", Codec.Result.kUnimplemented)
    if (reader.readU32(U32(d(0), d(1), Bits(4, 2), Bits(12, 1))) != 0) {
        reader.fail("jpegxl.modular.metadata.extra-channel", Codec.Result.kUnimplemented)
    }
    if (reader.readBool()) reader.fail("jpegxl.modular.metadata.xyb", Codec.Result.kUnimplemented)

    // ColorEncoding.all_default is direct RGB sRGB/D65. The narrow profile
    // accepts it as well as explicit direct sRGB and Gray encodings.
    if (reader.readBool()) {
        reader.readExtensions()
        return JxlNarrowMetadata(colorChannels = 3)
    }
    if (reader.readBool()) reader.fail("jpegxl.modular.metadata.icc", Codec.Result.kUnimplemented)
    val colorSpace = reader.readEnum()
    val colorChannels = when (colorSpace) {
        0 -> 3 // RGB
        1 -> 1 // Gray
        else -> reader.fail("jpegxl.modular.metadata.colorspace", Codec.Result.kUnimplemented)
    }
    val whitePoint = reader.readEnum()
    if (whitePoint != 1) reader.fail("jpegxl.modular.metadata.white-point", Codec.Result.kUnimplemented)
    if (colorChannels == 3 && reader.readEnum() != 1) {
        reader.fail("jpegxl.modular.metadata.primaries", Codec.Result.kUnimplemented)
    }
    if (reader.readBool()) reader.fail("jpegxl.modular.metadata.gamma", Codec.Result.kUnimplemented)
    if (reader.readEnum() != 13) reader.fail("jpegxl.modular.metadata.transfer", Codec.Result.kUnimplemented)
    // The pinned cjxl fixtures use perceptual rendering intent. Rendering
    // intent leaves direct RGB and Gray sample reconstruction unchanged.
    if (reader.readEnum() != 0) reader.fail("jpegxl.modular.metadata.intent", Codec.Result.kUnimplemented)
    reader.readExtensions()

    return JxlNarrowMetadata(colorChannels)
}

private fun readNarrowTransformData(reader: JxlBits) {
    // CustomTransformData is serialized after ImageMetadata, not inside it.
    // A direct RGB or Gray Modular frame has to use its canonical default.
    if (!reader.readBool()) reader.fail("jpegxl.modular.transform", Codec.Result.kUnimplemented)
}

private fun readNarrowFrameHeader(
    reader: JxlBits,
    frame: JpegXlFrameInfo,
): JxlNarrowFrameHeader {
    if (reader.readBool()) reader.fail("jpegxl.modular.frame.default", Codec.Result.kUnimplemented)
    if (reader.readEnum() != 0) reader.fail("jpegxl.modular.frame.type", Codec.Result.kUnimplemented)
    if (!reader.readBool()) reader.fail("jpegxl.modular.frame.encoding", Codec.Result.kUnimplemented)
    val flags = reader.readU64()
    // The narrow fixture excludes image features and reference/DC frames. The
    // adaptive-DC-smoothing flag is harmless and may be set by cjxl.
    if (flags and 0x7FL != 0L || flags and 0x20L != 0L) {
        reader.fail("jpegxl.modular.frame.flags", Codec.Result.kUnimplemented)
    }
    if (reader.readBool()) reader.fail("jpegxl.modular.frame.ycbcr", Codec.Result.kUnimplemented)
    if (reader.readU32(U32(d(1), d(2), d(4), d(8))) != 1) {
        reader.fail("jpegxl.modular.frame.upsampling", Codec.Result.kUnimplemented)
    }
    val groupSizeShift = reader.readBits(2)
    if (reader.readU32(U32(d(1), d(2), d(3), Bits(3, 4))) != 1) {
        reader.fail("jpegxl.modular.frame.passes", Codec.Result.kUnimplemented)
    }
    if (reader.readBool()) reader.fail("jpegxl.modular.frame.crop", Codec.Result.kUnimplemented)
    if (reader.readU32(U32(d(0), d(1), d(2), Bits(2, 3))) != 0) {
        reader.fail("jpegxl.modular.frame.blend", Codec.Result.kUnimplemented)
    }
    if (!reader.readBool()) reader.fail("jpegxl.modular.frame.last", Codec.Result.kUnimplemented)
    if (reader.readU32(U32(d(0), Bits(4, 0), Bits(5, 16), Bits(10, 48))) != 0) {
        reader.fail("jpegxl.modular.frame.name", Codec.Result.kUnimplemented)
    }
    readNarrowLoopFilter(reader)
    reader.readExtensions()

    val groupDimension = 128 shl groupSizeShift
    val groupColumns = ceilDiv(frame.width, groupDimension)
    val groupRows = ceilDiv(frame.height, groupDimension)
    val dcColumns = ceilDiv(ceilDiv(frame.width, 8), groupDimension)
    val dcRows = ceilDiv(ceilDiv(frame.height, 8), groupDimension)
    return JxlNarrowFrameHeader(
        groupDimension = groupDimension,
        groupCount = groupColumns * groupRows,
        dcGroupCount = dcColumns * dcRows,
    )
}

private fun readNarrowLoopFilter(reader: JxlBits) {
    // The fixture disables both Gaborish and EPF for its direct Modular
    // reconstruction, so LoopFilter is not all-default (whose defaults are
    // tuned for VarDCT) even though it has no effect on this path.
    if (reader.readBool()) reader.fail("jpegxl.modular.frame.filter", Codec.Result.kUnimplemented)
    if (reader.readBool()) reader.fail("jpegxl.modular.frame.gaborish", Codec.Result.kUnimplemented)
    if (reader.readBits(2) != 0) reader.fail("jpegxl.modular.frame.epf", Codec.Result.kUnimplemented)
    reader.readExtensions()
}

private fun readNarrowToc(
    reader: JxlBits,
    frame: JxlNarrowFrameHeader,
    codestreamEndExclusive: Int,
): JxlNarrowToc {
    val entryCount = if (frame.groupCount == 1) 1 else 2 + frame.dcGroupCount + frame.groupCount
    if (reader.readBool()) reader.fail("jpegxl.modular.toc.permutation", Codec.Result.kUnimplemented)
    reader.jumpToByteBoundary()
    val sizes = IntArray(entryCount) {
        reader.readU32(U32(Bits(10, 0), Bits(14, 1024), Bits(22, 17_408), Bits(30, 4_211_712)))
    }
    reader.jumpToByteBoundary()
    var cursor = reader.byteOffset
    val sections = ArrayList<JxlSection>(entryCount)
    for (size in sizes) {
        if (size < 0 || size > codestreamEndExclusive - cursor) reader.fail("jpegxl.modular.toc.truncated")
        sections += JxlSection(cursor, cursor + size)
        cursor += size
    }
    if (cursor != codestreamEndExclusive) reader.fail("jpegxl.modular.trailing-data")
    return JxlNarrowToc(sections)
}

private fun acGroupSections(toc: JxlNarrowToc, frame: JxlNarrowFrameHeader): List<JxlSection> {
    val expected = 2 + frame.dcGroupCount + frame.groupCount
    if (toc.sections.size != expected) throw JxlModularFailure(
        JpegXlDiagnostic("jpegxl.modular.toc.layout", toc.sections.firstOrNull()?.start?.toLong() ?: 0L),
    )
    // The narrow profile has no transform/DC payload. These sections must be
    // empty; each actual image group is then independently entropy-bounded.
    if (toc.sections[1].start != toc.sections[1].endExclusive ||
        toc.sections[2].start != toc.sections[2].endExclusive
    ) {
        throw JxlModularFailure(
            JpegXlDiagnostic("jpegxl.modular.toc.shared", toc.sections[1].start.toLong(), Codec.Result.kUnimplemented),
        )
    }
    return toc.sections.subList(3, expected)
}

private fun readJxlGlobalModular(
    reader: JxlBits,
    frame: JpegXlFrameInfo,
    header: JxlNarrowFrameHeader,
): JxlGlobalModular {
    // ProcessDCGlobal always carries DequantMatrices::DecodeDC before the
    // Modular payload, even though direct Modular samples do not use those
    // matrices. The narrow profile requires its canonical all-default bit.
    if (!reader.readBool()) reader.fail("jpegxl.modular.dc-matrices", Codec.Result.kUnimplemented)
    if (!reader.readBool()) reader.fail("jpegxl.modular.global.tree", Codec.Result.kUnimplemented)
    val tree = readJxlTree(
        reader,
        maxNodes = minOf(1 shl 20, 1_024 + (frame.width.toLong() * frame.height / 16L).toInt()),
    )
    val code = readJxlHistograms(reader, tree.leafCount)
    // The preliminary `has_tree` payload above is followed by the global
    // Modular GroupHeader. It must select that newly decoded global tree;
    // accepting a local tree here would silently accept a different profile.
    val globalHeader = readJxlGroupHeader(reader)
    if (!globalHeader.useGlobalTree) reader.fail("jpegxl.modular.global.header", Codec.Result.kUnimplemented)
    check(header.groupDimension > 0)
    return JxlGlobalModular(tree, code, globalHeader)
}

private fun decodeNarrowJxlAcGroup(
    source: ByteArray,
    section: JxlSection,
    global: JxlGlobalModular,
    header: JxlNarrowFrameHeader,
    groupId: Int,
    width: Int,
    height: Int,
    channelCount: Int,
): Array<IntArray> {
    val reader = JxlBits(source, section.start, section.endExclusive)
    val groupHeader = readJxlGroupHeader(reader)
    val (tree, code) = if (groupHeader.useGlobalTree) {
        global.tree to global.code
    } else {
        val localTree = readJxlTree(
            reader,
            maxNodes = 1_024 + header.groupDimension * header.groupDimension,
        )
        localTree to readJxlHistograms(reader, localTree.leafCount)
    }
    return decodeJxlModularGroup(
        reader = reader,
        tree = tree,
        code = code,
        header = groupHeader,
        streamId = modularAcStreamId(groupId, header),
        width = width,
        height = height,
        channelCount = channelCount,
    )
}

/** `ModularStreamId::ModularAC(groupId, pass = 0).ID(frame_dim)` in libjxl. */
private fun modularAcStreamId(groupId: Int, header: JxlNarrowFrameHeader): Int {
    require(groupId in 0 until header.groupCount)
    return 1 + 3 * header.dcGroupCount + JXL_NUM_QUANT_TABLES + groupId
}

private fun readJxlGroupHeader(reader: JxlBits): JxlGroupHeader {
    val useGlobalTree = reader.readBool()
    val weighted = readJxlWeightedHeader(reader)
    if (reader.readU32(U32(d(0), d(1), Bits(4, 2), Bits(8, 18))) != 0) {
        reader.fail("jpegxl.modular.transform", Codec.Result.kUnimplemented)
    }
    return JxlGroupHeader(useGlobalTree, weighted)
}

private fun readJxlWeightedHeader(reader: JxlBits): JxlWeightedHeader {
    if (reader.readBool()) {
        return JxlWeightedHeader(
            p1c = 16,
            p2c = 10,
            p3ca = 7,
            p3cb = 7,
            p3cc = 7,
            p3cd = 0,
            p3ce = 0,
            weights = intArrayOf(0xD, 0xC, 0xC, 0xC),
        )
    }
    return JxlWeightedHeader(
        p1c = reader.readBits(5),
        p2c = reader.readBits(5),
        p3ca = reader.readBits(5),
        p3cb = reader.readBits(5),
        p3cc = reader.readBits(5),
        p3cd = reader.readBits(5),
        p3ce = reader.readBits(5),
        weights = IntArray(4) { reader.readBits(4) },
    )
}

/**
 * Reconstructs one direct-Modular Gray or RGB group from its MA residuals.
 *
 * The narrow profile has no transforms. Its MA tree may select direct
 * non-weighted JPEG XL predictors. Weighted prediction and its error property
 * deliberately remain outside the validated profile.
 */
private fun decodeJxlModularGroup(
    reader: JxlBits,
    tree: JxlMaTree,
    code: JxlEntropyCode,
    header: JxlGroupHeader,
    streamId: Int,
    width: Int,
    height: Int,
    channelCount: Int,
): Array<IntArray> {
    if (width !in 1..256 || height !in 1..256) reader.fail("jpegxl.modular.group.dimensions")
    if (channelCount !in 1..3) reader.fail("jpegxl.modular.channels", Codec.Result.kUnimplemented)
    val entropy = JxlEntropyReader(reader, code, distanceMultiplier = width)
    val channels = Array(channelCount) { IntArray(width * height) }
    val properties = IntArray(maxOf(16, tree.nodes.maxOf { node -> node.property } + 1))
    // MA property 1 is the full ModularStreamId, rather than the TOC's raw
    // AC-group index. This distinguishes AC groups from the earlier DC and
    // quant-table streams when a tree branches on a static stream property.
    properties[1] = streamId
    val usesWeighted = tree.nodes.any { node -> node.property == 15 || node.predictor == 6 }
    if (usesWeighted) reader.fail("jpegxl.modular.predictor.weighted", Codec.Result.kUnimplemented)
    for (channel in channels.indices) {
        val pixels = channels[channel]
        properties[0] = channel
        for (y in 0 until height) {
            properties[2] = y
            properties[9] = 0
            for (x in 0 until width) {
                val left = if (x != 0) pixels[y * width + x - 1] else if (y != 0) pixels[(y - 1) * width] else 0
                val top = if (y != 0) pixels[(y - 1) * width + x] else left
                val topLeft = if (x != 0 && y != 0) pixels[(y - 1) * width + x - 1] else left
                val topRight = if (y != 0 && x + 1 < width) pixels[(y - 1) * width + x + 1] else top
                val leftLeft = if (x > 1) pixels[y * width + x - 2] else left
                val topTop = if (y > 1) pixels[(y - 2) * width + x] else top
                val topRightRight = if (y != 0 && x + 2 < width) pixels[(y - 1) * width + x + 2] else topRight
                populateJxlProperties(properties, x, left, top, topLeft, topRight, leftLeft, topTop)
                val leaf = tree.lookup(properties, reader)
                val prediction = predictJxlSample(
                    predictor = leaf.predictor,
                    left = left,
                    top = top,
                    topLeft = topLeft,
                    topRight = topRight,
                    leftLeft = leftLeft,
                    topTop = topTop,
                    topRightRight = topRightRight,
                    weightedPrediction = 0,
                )
                val residual = unpackJxlSigned(entropy.readHybrid(leaf.context))
                val sample = residual.toLong() * leaf.multiplier + leaf.offset + prediction
                if (sample !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) reader.fail("jpegxl.modular.sample.range")
                pixels[y * width + x] = sample.toInt()
            }
        }
    }
    entropy.checkFinal()
    return channels
}

private fun JxlMaTree.lookup(properties: IntArray, reader: JxlBits): JxlMaNode {
    var index = 0
    repeat(nodes.size) {
        val node = nodes.getOrNull(index) ?: reader.fail("jpegxl.modular.tree.lookup")
        if (node.property < 0) return node
        if (node.property !in properties.indices) reader.fail("jpegxl.modular.tree.property")
        // JPEG XL serializes the greater-than branch as lchild and the
        // less-than-or-equal branch as rchild (see libjxl FilterTree).
        index = if (properties[node.property] > node.splitValue) node.left else node.right
    }
    reader.fail("jpegxl.modular.tree.lookup")
}

private fun populateJxlProperties(
    properties: IntArray,
    x: Int,
    left: Int,
    top: Int,
    topLeft: Int,
    topRight: Int,
    leftLeft: Int,
    topTop: Int,
) {
    properties[3] = x
    properties[4] = kotlin.math.abs(top)
    properties[5] = kotlin.math.abs(left)
    properties[6] = top
    properties[7] = left
    properties[8] = left - properties[9]
    properties[9] = left + top - topLeft
    properties[10] = left - topLeft
    properties[11] = topLeft - top
    properties[12] = top - topRight
    properties[13] = top - topTop
    properties[14] = left - leftLeft
}

private fun predictJxlSample(
    predictor: Int,
    left: Int,
    top: Int,
    topLeft: Int,
    topRight: Int,
    leftLeft: Int,
    topTop: Int,
    topRightRight: Int,
    weightedPrediction: Int,
): Int = when (predictor) {
    0 -> 0
    1 -> left
    2 -> top
    3 -> (left + top) / 2
    4 -> selectJxl(left, top, topLeft)
    5 -> clampedJxlGradient(left, top, topLeft)
    6 -> weightedPrediction
    7 -> topRight
    8 -> topLeft
    9 -> leftLeft
    10 -> (left + topLeft) / 2
    11 -> (topLeft + top) / 2
    12 -> (top + topRight) / 2
    13 -> ((6L * top - 2L * topTop + 7L * left + leftLeft + topRightRight + 3L * topRight + 8L) / 16L).toInt()
    else -> error("validated MA tree predictor out of range")
}

private fun selectJxl(left: Int, top: Int, topLeft: Int): Int {
    val estimate = left.toLong() + top - topLeft
    return if (kotlin.math.abs(estimate - left) < kotlin.math.abs(estimate - top)) left else top
}

private fun clampedJxlGradient(left: Int, top: Int, topLeft: Int): Int {
    val minimum = minOf(left, top)
    val maximum = maxOf(left, top)
    return (left.toLong() + top - topLeft).coerceIn(minimum.toLong(), maximum.toLong()).toInt()
}

/** Stateful JPEG XL weighted predictor, including its single tree property. */
private class JxlWeightedPredictor(
    private val header: JxlWeightedHeader,
    width: Int,
) {
    private val prediction = LongArray(4)
    private val errors = Array(4) { LongArray((width + 2) * 2) }
    private val localError = LongArray((width + 2) * 2)
    private val width = width

    fun predict(
        left: Int,
        top: Int,
        topLeft: Int,
        topRight: Int,
        topTop: Int,
        x: Int,
        y: Int,
        properties: IntArray,
    ): Int {
        val currentRow = if ((y and 1) != 0) 0 else width + 2
        val previousRow = if ((y and 1) != 0) width + 2 else 0
        val north = previousRow + x
        val northEast = if (x + 1 < width) north + 1 else north
        val northWest = if (x > 0) north - 1 else north
        val weights = IntArray(4) { index ->
            weightedJxlErrorWeight(errors[index][north] + errors[index][northEast] + errors[index][northWest], header.weights[index])
        }
        val left8 = left.toLong() shl 3
        val top8 = top.toLong() shl 3
        val topRight8 = topRight.toLong() shl 3
        val topLeft8 = topLeft.toLong() shl 3
        val topTop8 = topTop.toLong() shl 3
        val errorLeft = if (x == 0) 0L else localError[currentRow + x - 1]
        val errorTop = localError[north]
        val errorTopLeft = localError[northWest]
        val errorTopRight = localError[northEast]
        val sum = errorLeft + errorTop
        var maximum = errorLeft
        if (kotlin.math.abs(errorTop) > kotlin.math.abs(maximum)) maximum = errorTop
        if (kotlin.math.abs(errorTopLeft) > kotlin.math.abs(maximum)) maximum = errorTopLeft
        if (kotlin.math.abs(errorTopRight) > kotlin.math.abs(maximum)) maximum = errorTopRight
        if (properties.size > 15) properties[15] = maximum.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        prediction[0] = left8 + topRight8 - top8
        prediction[1] = top8 - ((sum + errorTopRight) * header.p1c shr 5)
        prediction[2] = left8 - ((sum + errorTopLeft) * header.p2c shr 5)
        prediction[3] = top8 - ((
            errorTopLeft * header.p3ca + errorTop * header.p3cb + errorTopRight * header.p3cc +
                (topTop8 - top8) * header.p3cd + (topLeft8 - left8) * header.p3ce
            ) shr 5)
        var weightSum = 0
        val normalized = IntArray(4)
        val logWeight = floorLog2Jxl(weights.sum().coerceAtLeast(16))
        for (index in normalized.indices) {
            normalized[index] = weights[index] shr (logWeight - 4)
            weightSum += normalized[index]
        }
        var sumPrediction = (weightSum shr 1).toLong() - 1
        for (index in normalized.indices) sumPrediction += prediction[index] * normalized[index]
        val estimate8 = sumPrediction * JXL_WEIGHTED_DIV_LOOKUP[weightSum - 1] shr 24
        val sameSign = ((errorTop xor errorLeft) or (errorTop xor errorTopLeft)) <= 0
        val clamped = if (sameSign) estimate8 else estimate8.coerceIn(minOf(left8, top8, topRight8), maxOf(left8, top8, topRight8))
        lastWeighted = clamped
        return ((clamped + 3) shr 3).toInt()
    }

    fun update(value: Long, x: Int, y: Int) {
        val currentRow = if ((y and 1) != 0) 0 else width + 2
        val previousRow = if ((y and 1) != 0) width + 2 else 0
        val value8 = value shl 3
        localError[currentRow + x] = lastWeighted - value8
        for (index in errors.indices) {
            val error = (kotlin.math.abs(prediction[index] - value8) + 3) shr 3
            errors[index][currentRow + x] = error
            errors[index][previousRow + x + 1] += error
        }
    }

    private var lastWeighted = 0L

}

private fun floorLog2Jxl(value: Int): Int = 31 - Integer.numberOfLeadingZeros(value)

private fun weightedJxlErrorWeight(error: Long, maximumWeight: Int): Int {
    var shift = floorLog2Jxl((error + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) - 5
    if (shift < 0) shift = 0
    return 4 + ((maximumWeight.toLong() * JXL_WEIGHTED_DIV_LOOKUP[(error shr shift).coerceIn(0, 63).toInt()]) shr shift).toInt()
}

private val JXL_WEIGHTED_DIV_LOOKUP = longArrayOf(
    16777216, 8388608, 5592405, 4194304, 3355443, 2796202, 2396745, 2097152,
    1864135, 1677721, 1525201, 1398101, 1290555, 1198372, 1118481, 1048576,
    986895, 932067, 883011, 838860, 798915, 762600, 729444, 699050,
    671088, 645277, 621378, 599186, 578524, 559240, 541200, 524288,
    508400, 493447, 479349, 466033, 453438, 441505, 430185, 419430,
    409200, 399457, 390167, 381300, 372827, 364722, 356962, 349525,
    342392, 335544, 328965, 322638, 316551, 310689, 305040, 299593,
    294337, 289262, 284359, 279620, 275036, 270600, 266305, 262144,
)

private fun readJxlSizeHeader(reader: JxlBits) {
    val small = reader.readBool()
    if (small) reader.readBits(5) else reader.readU32(U32(Bits(9, 1), Bits(13, 1), Bits(18, 1), Bits(30, 1)))
    val ratio = reader.readBits(3)
    if (ratio == 0) {
        if (small) reader.readBits(5) else reader.readU32(U32(Bits(9, 1), Bits(13, 1), Bits(18, 1), Bits(30, 1)))
    }
}

private data class U32(
    val selector0: Distribution,
    val selector1: Distribution,
    val selector2: Distribution,
    val selector3: Distribution,
) {
    fun at(index: Int): Distribution = when (index) {
        0 -> selector0
        1 -> selector1
        2 -> selector2
        else -> selector3
    }
}

private sealed interface Distribution
private data class Direct(val value: Int) : Distribution
private data class Bits(val count: Int, val offset: Int) : Distribution
private fun d(value: Int): Distribution = Direct(value)

private class JxlBits(
    private val data: ByteArray,
    private val start: Int,
    private val endExclusive: Int,
) {
    private var position: Int = start * 8

    val byteOffset: Int get() = position ushr 3
    val byteOffsetCeil: Int get() = (position + 7) ushr 3

    fun readBool(): Boolean = readBits(1) != 0

    fun readBits(count: Int): Int {
        require(count in 0..30)
        if (position > endExclusive * 8 - count) throw JxlBitsTruncated()
        var result = 0
        repeat(count) { bit ->
            result = result or (((data[position ushr 3].toInt() ushr (position and 7)) and 1) shl bit)
            position++
        }
        return result
    }

    fun peekBits(count: Int): Int {
        require(count in 0..30)
        if (position > endExclusive * 8 - count) throw JxlBitsTruncated()
        var result = 0
        repeat(count) { bit ->
            val sourceBit = position + bit
            result = result or (((data[sourceBit ushr 3].toInt() ushr (sourceBit and 7)) and 1) shl bit)
        }
        return result
    }

    fun skipBits(count: Int) {
        require(count >= 0)
        if (position > endExclusive * 8 - count) throw JxlBitsTruncated()
        position += count
    }

    fun readU32(distribution: U32): Int = when (val selected = distribution.at(readBits(2))) {
        is Direct -> selected.value
        is Bits -> readBits(selected.count) + selected.offset
    }

    fun readEnum(): Int = readU32(U32(d(0), d(1), Bits(4, 2), Bits(6, 18)))

    fun readU64(): Long = when (readBits(2)) {
        0 -> 0L
        1 -> (1 + readBits(4)).toLong()
        2 -> (17 + readBits(8)).toLong()
        else -> {
            var result = readBits(12).toLong()
            var shift = 12
            while (readBool()) {
                if (shift == 60) {
                    result = result or (readBits(4).toLong() shl shift)
                    break
                }
                result = result or (readBits(8).toLong() shl shift)
                shift += 8
            }
            result
        }
    }

    fun readExtensions() {
        val extensions = readU64()
        var remaining = extensions
        var bits = 0L
        while (remaining != 0L) {
            bits += readU64()
            if (bits > Int.MAX_VALUE) fail("jpegxl.modular.extension.limit")
            remaining = remaining and (remaining - 1)
        }
        if (bits != 0L) {
            if (position > endExclusive * 8 - bits.toInt()) throw JxlBitsTruncated()
            position += bits.toInt()
        }
    }

    fun jumpToByteBoundary() {
        val padding = (8 - (position and 7)) and 7
        if (padding != 0 && readBits(padding) != 0) fail("jpegxl.modular.padding")
    }
}

private class JxlBitsTruncated : RuntimeException()

private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor

private data class JxlHybridConfig(
    val splitExponent: Int,
    val msbInToken: Int,
    val lsbInToken: Int,
) {
    val splitToken: Int = 1 shl splitExponent

    fun decode(token: Int, reader: JxlBits): Int {
        if (token < splitToken) return token
        val retained = msbInToken + lsbInToken
        val extraBits = splitExponent - retained + ((token - splitToken) ushr retained)
        if (extraBits !in 0..29) reader.fail("jpegxl.modular.hybrid.range")
        val low = token and ((1 shl lsbInToken) - 1)
        val high = (token ushr lsbInToken) and ((1 shl msbInToken) - 1)
        return ((((1 shl msbInToken) or high) shl extraBits or reader.readBits(extraBits)) shl lsbInToken) or low
    }
}

private data class JxlAliasEntry(
    val cutoff: Int,
    val rightValue: Int,
    val frequency0: Int,
    val offsets1: Int,
    val frequencyXor: Int,
)

private data class JxlEntropyCode(
    val contextMap: IntArray,
    val configs: List<JxlHybridConfig>,
    val logAlphaSize: Int,
    val tables: List<List<JxlAliasEntry>>,
    val huffman: List<JxlHuffmanCode>?,
    val lz77: JxlLz77?,
)

private data class JxlHuffmanCode(
    val values: Map<Int, IntArray>,
    val singleton: Int? = null,
) {
    fun read(reader: JxlBits): Int {
        singleton?.let { return it }
        var bits = 0
        for (length in 1..15) {
            bits = bits or (reader.readBits(1) shl (length - 1))
            values[length]?.let { table ->
                val value = table.getOrElse(bits) { -1 }
                if (value >= 0) return value
            }
        }
        reader.fail("jpegxl.modular.huffman.code")
    }
}

private data class JxlLz77(
    val threshold: Int,
    val minLength: Int,
    val lengthConfig: JxlHybridConfig,
    val distanceContext: Int,
)

private class JxlEntropyReader(
    private val reader: JxlBits,
    private val code: JxlEntropyCode,
    distanceMultiplier: Int = 0,
) {
    // rANS state is an unsigned 32-bit value. Keeping it in Long avoids
    // accidentally normalizing values whose top bit is set.
    private var state: Long = if (code.huffman == null) {
        reader.readBits(16).toLong() or (reader.readBits(16).toLong() shl 16)
    } else {
        0x13_0000L
    }
    private val lzWindow: IntArray? = code.lz77?.let { IntArray(JXL_LZ77_WINDOW_SIZE) }
    private val specialDistances: IntArray = if (distanceMultiplier == 0) {
        IntArray(0)
    } else {
        IntArray(JXL_SPECIAL_DISTANCES.size) { index ->
            val (a, b) = JXL_SPECIAL_DISTANCES[index]
            maxOf(1, a + distanceMultiplier * b)
        }
    }
    private var copiesRemaining = 0
    private var copyPosition = 0L
    private var decoded = 0L

    fun readHybrid(context: Int): Int {
        if (context !in code.contextMap.indices) reader.fail("jpegxl.modular.context.range")
        val lz = code.lz77
        val window = lzWindow
        if (copiesRemaining > 0) {
            check(window != null)
            val value = window[(copyPosition++ and JXL_LZ77_WINDOW_MASK.toLong()).toInt()]
            copiesRemaining--
            window[(decoded++ and JXL_LZ77_WINDOW_MASK.toLong()).toInt()] = value
            return value
        }
        val cluster = code.contextMap[context]
        val token = readSymbol(cluster)
        if (lz != null && token >= lz.threshold) {
            val length = lz.lengthConfig.decode(token - lz.threshold, reader)
            if (length > Int.MAX_VALUE - lz.minLength) reader.fail("jpegxl.modular.lz77.length")
            copiesRemaining = length + lz.minLength
            val distanceToken = readSymbol(lz.distanceContext)
            var distance = code.configs[lz.distanceContext].decode(distanceToken, reader)
            distance = if (distance < specialDistances.size) {
                specialDistances[distance]
            } else {
                distance + 1 - specialDistances.size
            }
            if (distance < 0) reader.fail("jpegxl.modular.lz77.distance")
            val bounded = minOf(distance.toLong(), decoded, JXL_LZ77_WINDOW_SIZE.toLong())
            copyPosition = decoded - bounded
            if (bounded == 0L) {
                check(window != null)
                java.util.Arrays.fill(window, 0, minOf(copiesRemaining, window.size), 0)
            }
            return readHybrid(context)
        }
        val value = code.configs[cluster].decode(token, reader)
        if (window != null) window[(decoded++ and JXL_LZ77_WINDOW_MASK.toLong()).toInt()] = value
        return value
    }

    fun checkFinal() {
        if (code.huffman == null && state != 0x13_0000L) reader.fail("jpegxl.modular.ans.final")
    }

    private fun readSymbol(cluster: Int): Int {
        code.huffman?.let { codes ->
            if (cluster !in codes.indices) reader.fail("jpegxl.modular.cluster.range")
            return codes[cluster].read(reader)
        }
        if (cluster !in code.tables.indices) reader.fail("jpegxl.modular.cluster.range")
        val table = code.tables[cluster]
        val residue = (state and 0xFFF).toInt()
        val logEntrySize = 12 - code.logAlphaSize
        val entrySizeMask = (1 shl logEntrySize) - 1
        val entry = table[residue ushr logEntrySize]
        val position = residue and entrySizeMask
        val greater = position >= entry.cutoff
        val symbol = if (greater) entry.rightValue else residue ushr logEntrySize
        val offset = if (greater) entry.offsets1 + position else position
        val frequency = if (greater) entry.frequency0 xor entry.frequencyXor else entry.frequency0
        if (frequency <= 0) reader.fail("jpegxl.modular.ans.frequency")
        state = frequency.toLong() * (state ushr 12) + offset
        if (state < (1L shl 16)) state = (state shl 16) or reader.readBits(16).toLong()
        return symbol
    }
}

private fun readJxlTree(reader: JxlBits, maxNodes: Int): JxlMaTree {
    if (maxNodes !in 1..1_048_576) reader.fail("jpegxl.modular.tree.limit")
    val treeCode = readJxlHistograms(reader, contexts = 6)
    val entropy = JxlEntropyReader(reader, treeCode)
    val nodes = ArrayList<JxlMaNode>()
    var pending = 1
    var leafCount = 0
    while (pending != 0) {
        pending--
        if (nodes.size >= maxNodes) reader.fail("jpegxl.modular.tree.limit")
        val property = entropy.readHybrid(1) - 1
        if (property !in -1..255) reader.fail("jpegxl.modular.tree.property")
        if (property > 15) reader.fail("jpegxl.modular.tree.reference", Codec.Result.kUnimplemented)
        if (property == -1) {
            val predictor = entropy.readHybrid(2)
            if (predictor !in 0..13) reader.fail("jpegxl.modular.tree.predictor")
            val offset = unpackJxlSigned(entropy.readHybrid(3))
            val multiplierLog = entropy.readHybrid(4)
            if (multiplierLog !in 0..30) reader.fail("jpegxl.modular.tree.multiplier")
            val multiplierBits = entropy.readHybrid(5)
            val maximum = (1L shl (31 - multiplierLog)) - 1L
            if (multiplierBits.toLong() >= maximum) reader.fail("jpegxl.modular.tree.multiplier")
            val multiplier = ((multiplierBits + 1).toLong() shl multiplierLog)
            if (multiplier > Int.MAX_VALUE) reader.fail("jpegxl.modular.tree.multiplier")
            nodes += JxlMaNode(-1, 0, -1, -1, leafCount, predictor, offset, multiplier.toInt())
            leafCount++
        } else {
            val splitValue = unpackJxlSigned(entropy.readHybrid(0))
            // Depth-first tree serialization stores children immediately after
            // the as-yet pending left subtree.
            val left = nodes.size + pending + 1
            val right = left + 1
            nodes += JxlMaNode(property, splitValue, left, right, -1, 0, 0, 1)
            pending += 2
        }
    }
    entropy.checkFinal()
    if (leafCount == 0) reader.fail("jpegxl.modular.tree.empty")
    validateJxlTree(nodes, reader)
    return JxlMaTree(nodes, leafCount)
}

private fun unpackJxlSigned(value: Int): Int = (value ushr 1) xor -(value and 1)

private fun validateJxlTree(nodes: List<JxlMaNode>, reader: JxlBits) {
    if (nodes.isEmpty()) reader.fail("jpegxl.modular.tree.empty")
    val ranges = HashMap<Int, IntRange>()
    fun visit(index: Int, depth: Int) {
        if (depth > 2_048 || index !in nodes.indices) reader.fail("jpegxl.modular.tree.shape")
        val node = nodes[index]
        if (node.property < 0) return
        val old = ranges[node.property] ?: Int.MIN_VALUE..Int.MAX_VALUE
        if (node.splitValue !in old || node.splitValue == Int.MAX_VALUE) reader.fail("jpegxl.modular.tree.split")
        ranges[node.property] = (node.splitValue + 1)..old.last
        visit(node.left, depth + 1)
        ranges[node.property] = old.first..node.splitValue
        visit(node.right, depth + 1)
        ranges[node.property] = old
    }
    visit(0, 0)
}

private fun readJxlHistograms(
    reader: JxlBits,
    contexts: Int,
    allowLz77: Boolean = true,
): JxlEntropyCode {
    if (contexts !in 1..65_536) reader.fail("jpegxl.modular.context.count")
    val lzEnabled = reader.readBool()
    // Context-map entropy is itself recursive. JPEG XL forbids LZ77 in its
    // small (one or two context) inner stream, otherwise a malformed stream
    // can recurse through another context map without a structural bound.
    if (lzEnabled && !allowLz77) reader.fail("jpegxl.modular.context-map.lz77")
    val lzLengthConfig: JxlHybridConfig?
    val lzThreshold: Int
    val lzMinLength: Int
    if (lzEnabled) {
        lzThreshold = reader.readU32(U32(d(224), d(512), d(4_096), Bits(15, 8)))
        lzMinLength = reader.readU32(U32(d(3), d(4), Bits(2, 5), Bits(8, 9)))
        lzLengthConfig = readJxlHybridConfig(reader, logAlphaSize = 8)
    } else {
        lzThreshold = 0
        lzMinLength = 0
        lzLengthConfig = null
    }
    val totalContexts = contexts + if (lzEnabled) 1 else 0
    val contextMap = if (totalContexts == 1) intArrayOf(0) else readJxlContextMap(reader, totalContexts)
    val clusters = contextMap.maxOrNull()!! + 1
    val useHuffman = reader.readBool()
    val logAlphaSize = if (useHuffman) 15 else reader.readBits(2) + 5
    val configs = List(clusters) { readJxlHybridConfig(reader, logAlphaSize) }
    val huffman = if (useHuffman) {
        // Prefix-code alphabet sizes are serialized as a complete array
        // before any of the corresponding code trees.  An alphabet of one
        // has a zero-bit, implicit symbol 0 and therefore has *no* tree in
        // the bitstream.  Reading a tree for it desynchronizes every
        // following histogram.
        val alphabetSizes = IntArray(clusters) { readJxlVarLenUint16(reader) + 1 }
        List(clusters) { cluster ->
            val alphabetSize = alphabetSizes[cluster]
            if (alphabetSize == 1) JxlHuffmanCode(emptyMap(), singleton = 0)
            else readJxlHuffmanCode(reader, alphabetSize)
        }
    } else {
        null
    }
    val tables = if (useHuffman) {
        emptyList()
    } else {
        List(clusters) {
            val counts = readJxlHistogram(reader)
            if (counts.size > (1 shl logAlphaSize)) reader.fail("jpegxl.modular.histogram.alphabet")
            buildJxlAliasTable(counts, logAlphaSize, reader)
        }
    }
    val lz77 = if (lzEnabled) {
        JxlLz77(
            threshold = lzThreshold,
            minLength = lzMinLength,
            lengthConfig = checkNotNull(lzLengthConfig),
            distanceContext = contextMap.last(),
        )
    } else {
        null
    }
    return JxlEntropyCode(contextMap, configs, logAlphaSize, tables, huffman, lz77)
}

private fun readJxlContextMap(reader: JxlBits, contexts: Int): IntArray {
    val map = IntArray(contexts)
    if (reader.readBool()) {
        val bits = reader.readBits(2)
        if (bits != 0) for (index in map.indices) map[index] = reader.readBits(bits)
    } else {
        val useMoveToFront = reader.readBool()
        // The recursive context-map stream itself has one context.
        val recursive = readJxlHistograms(reader, contexts = 1, allowLz77 = false)
        val entropy = JxlEntropyReader(reader, recursive)
        for (index in map.indices) map[index] = entropy.readHybrid(0)
        entropy.checkFinal()
        if (useMoveToFront) inverseMoveToFront(map)
    }
    val clusters = map.maxOrNull()!! + 1
    if (clusters > 256 || map.any { it !in 0 until clusters } || (0 until clusters).any { expected -> expected !in map }) {
        reader.fail("jpegxl.modular.context-map")
    }
    return map
}

private fun inverseMoveToFront(values: IntArray) {
    val entries = IntArray(256) { it }
    for (index in values.indices) {
        val position = values[index]
        if (position !in entries.indices) return
        val value = entries[position]
        for (cursor in position downTo 1) entries[cursor] = entries[cursor - 1]
        entries[0] = value
        values[index] = value
    }
}

private fun readJxlHybridConfig(reader: JxlBits, logAlphaSize: Int): JxlHybridConfig {
    val splitExponentBits = ceilLog2(logAlphaSize + 1)
    val splitExponent = reader.readBits(splitExponentBits)
    var msb = 0
    var lsb = 0
    if (splitExponent != logAlphaSize) {
        msb = reader.readBits(ceilLog2(splitExponent + 1))
        if (msb > splitExponent) reader.fail("jpegxl.modular.hybrid.config")
        lsb = reader.readBits(ceilLog2(splitExponent - msb + 1))
    }
    if (msb + lsb > splitExponent) reader.fail("jpegxl.modular.hybrid.config")
    return JxlHybridConfig(splitExponent, msb, lsb)
}

private fun readJxlHistogram(reader: JxlBits): IntArray {
    val range = 4096
    if (reader.readBool()) {
        val symbolCount = reader.readBits(1) + 1
        val symbols = IntArray(symbolCount) { readJxlVarLenUint8(reader) }
        val maxSymbol = symbols.maxOrNull()!!
        val counts = IntArray(maxSymbol + 1)
        if (symbolCount == 1) {
            counts[symbols.single()] = range
        } else {
            if (symbols[0] == symbols[1]) reader.fail("jpegxl.modular.histogram.symbol")
            counts[symbols[0]] = reader.readBits(12)
            counts[symbols[1]] = range - counts[symbols[0]]
        }
        return counts
    }
    if (reader.readBool()) {
        val alphabet = readJxlVarLenUint8(reader) + 1
        if (alphabet > range) reader.fail("jpegxl.modular.histogram.flat")
        return IntArray(alphabet) { (range / alphabet) + if (it < range % alphabet) 1 else 0 }
    }
    var log = 0
    while (log < 12 && reader.readBits(1) != 0) log++
    val shift = (reader.readBits(log) or (1 shl log)) - 1
    if (shift > 13) reader.fail("jpegxl.modular.histogram.shift")
    val length = readJxlVarLenUint8(reader) + 3
    val logCounts = IntArray(length)
    val same = IntArray(length)
    var omitLog = -1
    var omitPosition = -1
    var index = 0
    while (index < length) {
        val packed = JXL_LOG_COUNT_LUT[reader.peekBits(7)]
        reader.skipBits(packed ushr 8)
        val countLog = (packed and 0xFF) - 1
        logCounts[index] = countLog
        if (countLog == 12) {
            val run = readJxlVarLenUint8(reader) + 5
            same[index] = run
            // The RLE marker itself plus `run - 2` further positions are
            // consumed before the loop increment, matching libjxl's
            // `i += rle_length + 3` for `run = rle_length + 5`.
            index += run - 2
        } else if (countLog > omitLog) {
            omitLog = countLog
            omitPosition = index
        }
        index++
    }
    if (omitPosition < 0 || (omitPosition + 1 < length && logCounts[omitPosition + 1] == 12)) {
        reader.fail("jpegxl.modular.histogram.log")
    }
    val counts = IntArray(length)
    var total = 0
    var previous = 0
    var copies = 0
    for (position in counts.indices) {
        if (same[position] != 0) {
            copies = same[position] - 1
            previous = if (position > 0) counts[position - 1] else 0
        }
        if (copies > 0) {
            counts[position] = previous
            copies--
        } else {
            val code = logCounts[position]
            if (position != omitPosition && code >= 0) {
                counts[position] = if (shift == 0 || code == 0) {
                    1 shl code
                } else {
                    val bitCount = minOf(code, shift - ((12 - code) ushr 1)).coerceAtLeast(0)
                    (1 shl code) + (reader.readBits(bitCount) shl (code - bitCount))
                }
            }
        }
        total += counts[position]
    }
    counts[omitPosition] = range - total
    if (counts[omitPosition] <= 0) reader.fail("jpegxl.modular.histogram.count")
    return counts
}

private fun readJxlVarLenUint8(reader: JxlBits): Int {
    if (!reader.readBool()) return 0
    val bits = reader.readBits(3)
    return if (bits == 0) 1 else reader.readBits(bits) + (1 shl bits)
}

private fun readJxlVarLenUint16(reader: JxlBits): Int {
    if (!reader.readBool()) return 0
    val bits = reader.readBits(4)
    return if (bits == 0) 1 else reader.readBits(bits) + (1 shl bits)
}

private fun readJxlHuffmanCode(reader: JxlBits, alphabetSize: Int): JxlHuffmanCode {
    if (alphabetSize !in 1..(1 shl 15)) reader.fail("jpegxl.modular.huffman.alphabet")
    val simpleOrSkip = reader.readBits(2)
    if (simpleOrSkip == 1) return readJxlSimpleHuffman(reader, alphabetSize)

    val order = intArrayOf(1, 2, 3, 4, 0, 5, 17, 6, 16, 7, 8, 9, 10, 11, 12, 13, 14, 15)
    val staticLengths = intArrayOf(2, 4, 3, 2, 2, 4)
    val codeLengthLengths = IntArray(18)
    var space = 32
    var nonZero = 0
    for (index in simpleOrSkip until 18) {
        if (space <= 0) break
        val symbol = readJxlCanonicalHuffman(reader, buildJxlHuffman(staticLengths))
        codeLengthLengths[order[index]] = symbol
        if (symbol != 0) {
            if (symbol > 5) reader.fail("jpegxl.modular.huffman.length")
            space -= 32 ushr symbol
            nonZero++
        }
    }
    if (nonZero != 1 && space != 0) reader.fail("jpegxl.modular.huffman.length")
    val lengths = readJxlHuffmanLengths(reader, codeLengthLengths, alphabetSize)
    return buildJxlHuffman(lengths)
}

private fun readJxlSimpleHuffman(reader: JxlBits, alphabetSize: Int): JxlHuffmanCode {
    val maxBits = ceilLog2(alphabetSize)
    val symbols = IntArray(reader.readBits(2) + 1) { reader.readBits(maxBits) }
    if (symbols.any { it !in 0 until alphabetSize } || symbols.toSet().size != symbols.size) {
        reader.fail("jpegxl.modular.huffman.simple")
    }
    var count = symbols.size
    if (count == 4 && reader.readBool()) count++
    if (count == 1) return JxlHuffmanCode(emptyMap(), singleton = symbols[0])
    val lengths = IntArray(alphabetSize)
    when (count) {
        2 -> symbols.sorted().forEach { lengths[it] = 1 }
        3 -> {
            lengths[symbols[0]] = 1
            symbols.copyOfRange(1, 3).sorted().forEach { lengths[it] = 2 }
        }
        4 -> symbols.sorted().forEach { lengths[it] = 2 }
        5 -> {
            lengths[symbols[0]] = 1
            lengths[symbols[1]] = 2
            symbols.copyOfRange(2, 4).sorted().forEach { lengths[it] = 3 }
        }
        else -> reader.fail("jpegxl.modular.huffman.simple")
    }
    return buildJxlHuffman(lengths)
}

private fun readJxlHuffmanLengths(reader: JxlBits, codeLengthLengths: IntArray, alphabetSize: Int): IntArray {
    val codeLengths = IntArray(alphabetSize)
    val codeLengthCode = buildJxlHuffman(codeLengthLengths)
    var symbol = 0
    var previous = 8
    var repeat = 0
    var repeatLength = 0
    var space = 32_768
    while (symbol < alphabetSize && space > 0) {
        val length = readJxlCanonicalHuffman(reader, codeLengthCode)
        if (length < 16) {
            repeat = 0
            codeLengths[symbol++] = length
            if (length != 0) {
                previous = length
                space -= 32_768 ushr length
            }
        } else {
            if (length !in 16..17) reader.fail("jpegxl.modular.huffman.repeat")
            val extraBits = length - 14
            val newLength = if (length == 16) previous else 0
            if (repeatLength != newLength) {
                repeat = 0
                repeatLength = newLength
            }
            val oldRepeat = repeat
            if (repeat > 0) repeat = (repeat - 2) shl extraBits
            repeat += reader.readBits(extraBits) + 3
            val delta = repeat - oldRepeat
            if (delta < 0 || symbol + delta > alphabetSize) reader.fail("jpegxl.modular.huffman.repeat")
            repeat(delta) { codeLengths[symbol++] = repeatLength }
            if (repeatLength != 0) space -= delta shl (15 - repeatLength)
        }
    }
    if (space != 0) reader.fail("jpegxl.modular.huffman.tree")
    return codeLengths
}

private fun buildJxlHuffman(lengths: IntArray): JxlHuffmanCode {
    if (lengths.any { it !in 0..15 }) throw IllegalArgumentException("invalid Huffman length")
    val counts = IntArray(16)
    lengths.forEach { if (it != 0) counts[it]++ }
    val symbols = lengths.count { it != 0 }
    if (symbols == 0) throw IllegalArgumentException("empty Huffman tree")
    if (symbols == 1) {
        val symbol = lengths.indexOfFirst { it != 0 }
        return JxlHuffmanCode(mapOf(1 to intArrayOf(symbol, symbol)))
    }
    var code = 0
    val next = IntArray(16)
    for (length in 1..15) {
        code = (code + counts[length - 1]) shl 1
        next[length] = code
    }
    val byLength = HashMap<Int, IntArray>()
    for (length in 1..15) if (counts[length] != 0) byLength[length] = IntArray(1 shl length) { -1 }
    for (symbol in lengths.indices) {
        val length = lengths[symbol]
        if (length == 0) continue
        val canonical = next[length]++
        val reversed = Integer.reverse(canonical) ushr (32 - length)
        byLength.getValue(length)[reversed] = symbol
    }
    return JxlHuffmanCode(byLength)
}

private fun readJxlCanonicalHuffman(reader: JxlBits, code: JxlHuffmanCode): Int = code.read(reader)

private fun buildJxlAliasTable(countsSource: IntArray, logAlphaSize: Int, reader: JxlBits): List<JxlAliasEntry> {
    val tableSize = 1 shl logAlphaSize
    val counts = countsSource.copyOf()
    var last = counts.size
    while (last > 0 && counts[last - 1] == 0) last--
    val distribution = if (last == 0) intArrayOf(4096) else counts.copyOf(last)
    if (distribution.size > tableSize || distribution.sum() != 4096) reader.fail("jpegxl.modular.alias.distribution")
    val entrySize = 4096 ushr logAlphaSize
    val cutoffs = IntArray(tableSize)
    val right = IntArray(tableSize)
    val offsets = IntArray(tableSize)
    val underfull = ArrayDeque<Int>()
    val overfull = ArrayDeque<Int>()
    var single = -1
    for (symbol in distribution.indices) {
        cutoffs[symbol] = distribution[symbol]
        if (distribution[symbol] == 4096) single = symbol
        when {
            cutoffs[symbol] > entrySize -> overfull.addLast(symbol)
            cutoffs[symbol] < entrySize -> underfull.addLast(symbol)
            else -> right[symbol] = symbol
        }
    }
    for (symbol in distribution.size until tableSize) underfull.addLast(symbol)
    if (single >= 0) return List(tableSize) { JxlAliasEntry(0, single, 0, it * entrySize, 4096) }
    while (overfull.isNotEmpty()) {
        if (underfull.isEmpty()) reader.fail("jpegxl.modular.alias.balance")
        val over = overfull.removeLast()
        val under = underfull.removeLast()
        val missing = entrySize - cutoffs[under]
        cutoffs[over] -= missing
        right[under] = over
        // Keep the overfull cutoff here. The final table conversion below
        // subtracts the underfull cutoff exactly once, as required by rANS.
        offsets[under] = cutoffs[over]
        when {
            cutoffs[over] < entrySize -> underfull.addLast(over)
            cutoffs[over] > entrySize -> overfull.addLast(over)
            else -> right[over] = over
        }
    }
    if (underfull.isNotEmpty()) reader.fail("jpegxl.modular.alias.balance")
    return List(tableSize) { index ->
        if (cutoffs[index] == entrySize) {
            JxlAliasEntry(0, index, distribution.getOrElse(index) { 0 }, 0, 0)
        } else {
            val frequency0 = distribution.getOrElse(index) { 0 }
            val frequency1 = distribution.getOrElse(right[index]) { 0 }
            JxlAliasEntry(cutoffs[index], right[index], frequency0, offsets[index] - cutoffs[index], frequency0 xor frequency1)
        }
    }
}

private fun ceilLog2(value: Int): Int = when {
    value <= 1 -> 0
    else -> 32 - Integer.numberOfLeadingZeros(value - 1)
}

private const val JXL_LZ77_WINDOW_SIZE = 1 shl 20
private const val JXL_LZ77_WINDOW_MASK = JXL_LZ77_WINDOW_SIZE - 1
private const val JXL_NUM_QUANT_TABLES = 17

private val JXL_SPECIAL_DISTANCES: Array<IntArray> = arrayOf(
    intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(-1, 1),
    intArrayOf(0, 2), intArrayOf(2, 0), intArrayOf(1, 2), intArrayOf(-1, 2),
    intArrayOf(2, 1), intArrayOf(-2, 1), intArrayOf(2, 2), intArrayOf(-2, 2),
    intArrayOf(0, 3), intArrayOf(3, 0), intArrayOf(1, 3), intArrayOf(-1, 3),
    intArrayOf(3, 1), intArrayOf(-3, 1), intArrayOf(2, 3), intArrayOf(-2, 3),
    intArrayOf(3, 2), intArrayOf(-3, 2), intArrayOf(0, 4), intArrayOf(4, 0),
    intArrayOf(1, 4), intArrayOf(-1, 4), intArrayOf(4, 1), intArrayOf(-4, 1),
    intArrayOf(3, 3), intArrayOf(-3, 3), intArrayOf(2, 4), intArrayOf(-2, 4),
    intArrayOf(4, 2), intArrayOf(-4, 2), intArrayOf(0, 5), intArrayOf(3, 4),
    intArrayOf(-3, 4), intArrayOf(4, 3), intArrayOf(-4, 3), intArrayOf(5, 0),
    intArrayOf(1, 5), intArrayOf(-1, 5), intArrayOf(5, 1), intArrayOf(-5, 1),
    intArrayOf(2, 5), intArrayOf(-2, 5), intArrayOf(5, 2), intArrayOf(-5, 2),
    intArrayOf(4, 4), intArrayOf(-4, 4), intArrayOf(3, 5), intArrayOf(-3, 5),
    intArrayOf(5, 3), intArrayOf(-5, 3), intArrayOf(0, 6), intArrayOf(6, 0),
    intArrayOf(1, 6), intArrayOf(-1, 6), intArrayOf(6, 1), intArrayOf(-6, 1),
    intArrayOf(2, 6), intArrayOf(-2, 6), intArrayOf(6, 2), intArrayOf(-6, 2),
    intArrayOf(4, 5), intArrayOf(-4, 5), intArrayOf(5, 4), intArrayOf(-5, 4),
    intArrayOf(3, 6), intArrayOf(-3, 6), intArrayOf(6, 3), intArrayOf(-6, 3),
    intArrayOf(0, 7), intArrayOf(7, 0), intArrayOf(1, 7), intArrayOf(-1, 7),
    intArrayOf(5, 5), intArrayOf(-5, 5), intArrayOf(7, 1), intArrayOf(-7, 1),
    intArrayOf(4, 6), intArrayOf(-4, 6), intArrayOf(6, 4), intArrayOf(-6, 4),
    intArrayOf(2, 7), intArrayOf(-2, 7), intArrayOf(7, 2), intArrayOf(-7, 2),
    intArrayOf(3, 7), intArrayOf(-3, 7), intArrayOf(7, 3), intArrayOf(-7, 3),
    intArrayOf(5, 6), intArrayOf(-5, 6), intArrayOf(6, 5), intArrayOf(-6, 5),
    intArrayOf(8, 0), intArrayOf(4, 7), intArrayOf(-4, 7), intArrayOf(7, 4),
    intArrayOf(-7, 4), intArrayOf(8, 1), intArrayOf(8, 2), intArrayOf(6, 6),
    intArrayOf(-6, 6), intArrayOf(8, 3), intArrayOf(5, 7), intArrayOf(-5, 7),
    intArrayOf(7, 5), intArrayOf(-7, 5), intArrayOf(8, 4), intArrayOf(6, 7),
    intArrayOf(-6, 7), intArrayOf(7, 6), intArrayOf(-7, 6), intArrayOf(8, 5),
    intArrayOf(7, 7), intArrayOf(-7, 7), intArrayOf(8, 6), intArrayOf(8, 7),
)

private val JXL_LOG_COUNT_LUT: IntArray = intArrayOf(
    0x030A, 0x070C, 0x0307, 0x0403, 0x0306, 0x0308, 0x0309, 0x0405,
    0x030A, 0x0404, 0x0307, 0x0401, 0x0306, 0x0308, 0x0309, 0x0402,
    0x030A, 0x0500, 0x0307, 0x0403, 0x0306, 0x0308, 0x0309, 0x0405,
    0x030A, 0x0404, 0x0307, 0x0401, 0x0306, 0x0308, 0x0309, 0x0402,
    0x030A, 0x060B, 0x0307, 0x0403, 0x0306, 0x0308, 0x0309, 0x0405,
    0x030A, 0x0404, 0x0307, 0x0401, 0x0306, 0x0308, 0x0309, 0x0402,
    0x030A, 0x0500, 0x0307, 0x0403, 0x0306, 0x0308, 0x0309, 0x0405,
    0x030A, 0x0404, 0x0307, 0x0401, 0x0306, 0x0308, 0x0309, 0x0402,
    0x030A, 0x070D, 0x0307, 0x0403, 0x0306, 0x0308, 0x0309, 0x0405,
    0x030A, 0x0404, 0x0307, 0x0401, 0x0306, 0x0308, 0x0309, 0x0402,
    0x030A, 0x0500, 0x0307, 0x0403, 0x0306, 0x0308, 0x0309, 0x0405,
    0x030A, 0x0404, 0x0307, 0x0401, 0x0306, 0x0308, 0x0309, 0x0402,
    0x030A, 0x060B, 0x0307, 0x0403, 0x0306, 0x0308, 0x0309, 0x0405,
    0x030A, 0x0404, 0x0307, 0x0401, 0x0306, 0x0308, 0x0309, 0x0402,
    0x030A, 0x0500, 0x0307, 0x0403, 0x0306, 0x0308, 0x0309, 0x0405,
    0x030A, 0x0404, 0x0307, 0x0401, 0x0306, 0x0308, 0x0309, 0x0402,
)
