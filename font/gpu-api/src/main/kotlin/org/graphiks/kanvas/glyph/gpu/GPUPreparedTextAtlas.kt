package org.graphiks.kanvas.glyph.gpu

import java.security.MessageDigest
import java.util.Collections
import java.util.Locale

/**
 * One immutable rectangle request for the shared deterministic text-atlas packer.
 */
data class GPUTextAtlasRectItem(
    val itemKey: String,
    val width: Int,
    val height: Int,
    val guardPx: Int,
) {
    init {
        require(itemKey.isNotBlank()) { "itemKey must not be blank." }
        require(width >= 0) { "width must be non-negative." }
        require(height >= 0) { "height must be non-negative." }
        require(guardPx >= 0) { "guardPx must be non-negative." }
    }
}

/**
 * Final placement of one text artifact inside one A8 atlas page.
 */
data class GPUTextA8AtlasPlacement(
    val itemKey: String,
    val pageIndex: Int,
    val allocationRect: GPUTextIntRect,
    val contentRect: GPUTextIntRect,
) {
    init {
        require(itemKey.isNotBlank()) { "itemKey must not be blank." }
        require(pageIndex >= 0) { "pageIndex must be non-negative." }
        require(contentRect.left >= allocationRect.left) {
            "contentRect must be contained by allocationRect."
        }
        require(contentRect.top >= allocationRect.top) {
            "contentRect must be contained by allocationRect."
        }
        require(contentRect.right <= allocationRect.right) {
            "contentRect must be contained by allocationRect."
        }
        require(contentRect.bottom <= allocationRect.bottom) {
            "contentRect must be contained by allocationRect."
        }
    }
}

enum class GPUTextAtlasPackingRefusal {
    ITEM_TOO_LARGE,
    PAGE_LIMIT,
}

sealed interface GPUTextAtlasPackingResult {
    class Ready(
        val pageCount: Int,
        sourcePlacements: List<GPUTextA8AtlasPlacement>,
    ) : GPUTextAtlasPackingResult {
        val placements: List<GPUTextA8AtlasPlacement> =
            Collections.unmodifiableList(ArrayList(sourcePlacements))

        init {
            require(pageCount >= 0) { "pageCount must be non-negative." }
            require(placements.all { placement -> placement.pageIndex < pageCount }) {
                "Every placement pageIndex must be lower than pageCount."
            }
        }
    }

    data class Refused(
        val reason: GPUTextAtlasPackingRefusal,
        val itemKey: String,
    ) : GPUTextAtlasPackingResult {
        val placements: List<GPUTextA8AtlasPlacement> = emptyList()

        init {
            require(itemKey.isNotBlank()) { "itemKey must not be blank." }
        }
    }
}

/**
 * Mutable cursor implementing the one shared shelf-packing authority.
 *
 * The cursor publishes no state change when [place] cannot fit its item. It is exposed so
 * historical atlas wrappers can preserve their incremental API while delegating the placement
 * decision to the same authority as prepared text.
 */
class GPUTextAtlasPageCursor(
    private val pageWidth: Int,
    private val pageHeight: Int,
    private val outerPaddingPx: Int = 0,
    private val interItemPaddingPx: Int = 0,
) {
    private var shelfX: Int = outerPaddingPx
    private var shelfY: Int = outerPaddingPx
    private var shelfHeight: Int = 0

    init {
        require(pageWidth > 0) { "pageWidth must be positive." }
        require(pageHeight > 0) { "pageHeight must be positive." }
        require(outerPaddingPx >= 0) { "outerPaddingPx must be non-negative." }
        require(interItemPaddingPx >= 0) { "interItemPaddingPx must be non-negative." }
    }

    fun place(
        item: GPUTextAtlasRectItem,
        pageIndex: Int,
    ): GPUTextA8AtlasPlacement? {
        require(pageIndex >= 0) { "pageIndex must be non-negative." }
        val allocationWidth = checkedAllocationExtent(item.width, item.guardPx)
        val allocationHeight = checkedAllocationExtent(item.height, item.guardPx)
        if (!fitsEmptyPage(allocationWidth, allocationHeight)) return null

        var candidateX = shelfX
        var candidateY = shelfY
        var candidateShelfHeight = shelfHeight
        if (
            candidateX.toLong() + allocationWidth.toLong() + outerPaddingPx.toLong() >
            pageWidth.toLong()
        ) {
            candidateY = checkedAdd(
                checkedAdd(candidateY, candidateShelfHeight, "atlas shelf y"),
                interItemPaddingPx,
                "atlas shelf padding",
            )
            candidateX = outerPaddingPx
            candidateShelfHeight = 0
        }
        if (
            candidateY.toLong() + allocationHeight.toLong() + outerPaddingPx.toLong() >
            pageHeight.toLong()
        ) {
            return null
        }

        val allocationRight = checkedAdd(candidateX, allocationWidth, "allocation right")
        val allocationBottom = checkedAdd(candidateY, allocationHeight, "allocation bottom")
        val contentLeft = checkedAdd(candidateX, item.guardPx, "content left")
        val contentTop = checkedAdd(candidateY, item.guardPx, "content top")
        val contentRight = checkedAdd(contentLeft, item.width, "content right")
        val contentBottom = checkedAdd(contentTop, item.height, "content bottom")

        shelfX = checkedAdd(allocationRight, interItemPaddingPx, "atlas shelf x")
        shelfY = candidateY
        shelfHeight = maxOf(candidateShelfHeight, allocationHeight)
        return GPUTextA8AtlasPlacement(
            itemKey = item.itemKey,
            pageIndex = pageIndex,
            allocationRect = GPUTextIntRect(
                left = candidateX,
                top = candidateY,
                right = allocationRight,
                bottom = allocationBottom,
            ),
            contentRect = GPUTextIntRect(
                left = contentLeft,
                top = contentTop,
                right = contentRight,
                bottom = contentBottom,
            ),
        )
    }

    internal fun fitsEmptyPage(
        allocationWidth: Int,
        allocationHeight: Int,
    ): Boolean =
        allocationWidth.toLong() + outerPaddingPx.toLong() * 2L <= pageWidth.toLong() &&
            allocationHeight.toLong() + outerPaddingPx.toLong() * 2L <= pageHeight.toLong()
}

object GPUTextAtlasRectPacker {
    fun pack(
        items: List<GPUTextAtlasRectItem>,
        pageWidth: Int,
        pageHeight: Int,
        maxPages: Int,
        outerPaddingPx: Int = 0,
        interItemPaddingPx: Int = 0,
    ): GPUTextAtlasPackingResult {
        require(pageWidth > 0) { "pageWidth must be positive." }
        require(pageHeight > 0) { "pageHeight must be positive." }
        require(maxPages >= 0) { "maxPages must be non-negative." }
        require(outerPaddingPx >= 0) { "outerPaddingPx must be non-negative." }
        require(interItemPaddingPx >= 0) { "interItemPaddingPx must be non-negative." }
        require(items.map { item -> item.itemKey }.toSet().size == items.size) {
            "itemKey values must be unique."
        }
        if (items.isEmpty()) {
            return GPUTextAtlasPackingResult.Ready(
                pageCount = 0,
                sourcePlacements = emptyList(),
            )
        }

        val placements = ArrayList<GPUTextA8AtlasPlacement>(items.size)
        val pages = mutableListOf<GPUTextAtlasPageCursor>()
        for (item in items) {
            val allocationWidth = checkedAllocationExtent(item.width, item.guardPx)
            val allocationHeight = checkedAllocationExtent(item.height, item.guardPx)
            val emptyPage = GPUTextAtlasPageCursor(
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                outerPaddingPx = outerPaddingPx,
                interItemPaddingPx = interItemPaddingPx,
            )
            if (!emptyPage.fitsEmptyPage(allocationWidth, allocationHeight)) {
                return GPUTextAtlasPackingResult.Refused(
                    reason = GPUTextAtlasPackingRefusal.ITEM_TOO_LARGE,
                    itemKey = item.itemKey,
                )
            }

            var placement: GPUTextA8AtlasPlacement? = pages.lastOrNull()?.place(
                item = item,
                pageIndex = pages.lastIndex,
            )
            if (placement == null) {
                if (pages.size >= maxPages) {
                    return GPUTextAtlasPackingResult.Refused(
                        reason = GPUTextAtlasPackingRefusal.PAGE_LIMIT,
                        itemKey = item.itemKey,
                    )
                }
                val pageIndex = pages.size
                val page = emptyPage
                pages += page
                placement = checkNotNull(page.place(item, pageIndex)) {
                    "An item validated against an empty page must fit that page."
                }
            }
            placements += placement
        }

        return GPUTextAtlasPackingResult.Ready(
            pageCount = pages.size,
            sourcePlacements = placements,
        )
    }
}

/**
 * Immutable finalized A8 page bytes and their exact packed placement snapshot.
 */
class GPUTextA8AtlasPageArtifact private constructor(
    val artifactKey: GPUTextArtifactKey,
    val pageIndex: Int,
    val width: Int,
    val height: Int,
    val rowBytes: Int,
    sourceBytes: List<Int>,
    val contentSha256: String,
    sourcePlacements: List<GPUTextA8AtlasPlacement>,
) {
    val bytes: List<Int> = Collections.unmodifiableList(ArrayList(sourceBytes))
    val placements: List<GPUTextA8AtlasPlacement> =
        Collections.unmodifiableList(ArrayList(sourcePlacements))

    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative." }
        require(width > 0) { "width must be positive." }
        require(height > 0) { "height must be positive." }
        require(rowBytes >= width) { "rowBytes must be >= width." }
        require(rowBytes.toLong() * height.toLong() == bytes.size.toLong()) {
            "bytes must contain exactly rowBytes * height samples."
        }
        require(bytes.all { sample -> sample in 0..255 }) {
            "A8 samples must be in 0..255."
        }
        require(contentSha256 == sha256(bytes)) {
            "contentSha256 must match finalized page bytes."
        }
        require(
            artifactKey.contentFingerprint == contentFingerprint(
                width = width,
                height = height,
                rowBytes = rowBytes,
                contentSha256 = contentSha256,
                placements = placements,
            ),
        ) {
            "artifactKey.contentFingerprint must match the exact finalized page layout."
        }
        require(placements.all { placement -> placement.pageIndex == pageIndex }) {
            "Every placement must belong to pageIndex."
        }
        require(placements.map { placement -> placement.itemKey }.toSet().size == placements.size) {
            "Every page placement itemKey must be unique."
        }
        placements.forEach { placement ->
            require(
                placement.allocationRect.left >= 0 &&
                    placement.allocationRect.top >= 0 &&
                    placement.allocationRect.right <= width &&
                    placement.allocationRect.bottom <= height,
            ) {
                "Every allocationRect must be contained by the page."
            }
        }
        placements.indices.forEach { leftIndex ->
            ((leftIndex + 1) until placements.size).forEach { rightIndex ->
                require(
                    !placements[leftIndex].allocationRect.overlaps(
                        placements[rightIndex].allocationRect,
                    ),
                ) {
                    "Page allocationRect values must not overlap."
                }
            }
        }
    }

    fun uniqueMaskCount(): Int = placements.size

    companion object {
        fun create(
            artifactKey: GPUTextArtifactKey,
            pageIndex: Int,
            width: Int,
            height: Int,
            rowBytes: Int,
            bytes: List<Int>,
            contentSha256: String,
            placements: List<GPUTextA8AtlasPlacement>,
        ): GPUTextA8AtlasPageArtifact = GPUTextA8AtlasPageArtifact(
            artifactKey = artifactKey,
            pageIndex = pageIndex,
            width = width,
            height = height,
            rowBytes = rowBytes,
            sourceBytes = bytes,
            contentSha256 = contentSha256,
            sourcePlacements = placements,
        )

        fun sha256(bytes: List<Int>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            bytes.forEach { sample ->
                require(sample in 0..255) { "A8 samples must be in 0..255." }
                digest.update(sample.toByte())
            }
            return digest.digest().joinToString(separator = "") { byte ->
                String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
            }
        }

        fun contentFingerprint(
            width: Int,
            height: Int,
            rowBytes: Int,
            contentSha256: String,
            placements: List<GPUTextA8AtlasPlacement>,
        ): String {
            require(width > 0) { "width must be positive." }
            require(height > 0) { "height must be positive." }
            require(rowBytes >= width) { "rowBytes must be >= width." }
            require(contentSha256.matches(Regex("[0-9a-f]{64}"))) {
                "contentSha256 must be a lowercase SHA-256 value."
            }
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(
                buildString {
                    append("prepared-text-a8-page:v1")
                    append("|width=").append(width)
                    append("|height=").append(height)
                    append("|rowBytes=").append(rowBytes)
                    append("|bytes=").append(contentSha256)
                    placements.forEach { placement ->
                        append("|placement=").append(placement.itemKey)
                        append(':').append(placement.pageIndex)
                        append(':').append(placement.allocationRect.left)
                        append(':').append(placement.allocationRect.top)
                        append(':').append(placement.allocationRect.right)
                        append(':').append(placement.allocationRect.bottom)
                        append(':').append(placement.contentRect.left)
                        append(':').append(placement.contentRect.top)
                        append(':').append(placement.contentRect.right)
                        append(':').append(placement.contentRect.bottom)
                    }
                }.toByteArray(Charsets.UTF_8),
            )
            return digest.digest().joinToString(separator = "") { byte ->
                String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
            }
        }
    }
}

/**
 * Immutable per-draw A8 instance record consumed by a later upload/execution task.
 */
@JvmInline
value class GPUTextSourceGlyphIndex(val value: Int) {
    init {
        require(value >= 0) { "GPUTextSourceGlyphIndex must be non-negative." }
    }
}

class GPUTextA8Instance private constructor(
    val glyphId: Int,
    val sourceGlyphIndex: GPUTextSourceGlyphIndex,
    sourceDeviceQuad: List<Float>,
    val uvRect: GPUTextFloatRect,
    val pageIndex: Int,
    val colorLayerIndex: Int?,
) {
    val deviceQuad: List<Float> = Collections.unmodifiableList(ArrayList(sourceDeviceQuad))

    init {
        require(glyphId >= 0) { "glyphId must be non-negative." }
        require(deviceQuad.size == 8) { "deviceQuad must contain four x/y pairs." }
        require(deviceQuad.all(Float::isFinite)) { "deviceQuad values must be finite." }
        require(
            listOf(uvRect.left, uvRect.top, uvRect.right, uvRect.bottom).all(Float::isFinite),
        ) {
            "uvRect values must be finite."
        }
        require(pageIndex >= 0) { "pageIndex must be non-negative." }
        require(colorLayerIndex == null || colorLayerIndex >= 0) {
            "colorLayerIndex must be null or non-negative."
        }
    }

    companion object {
        const val ENCODED_BYTE_SIZE: Int = 64

        fun create(
            glyphId: Int,
            sourceGlyphIndex: GPUTextSourceGlyphIndex = GPUTextSourceGlyphIndex(0),
            deviceQuad: List<Float>,
            uvRect: GPUTextFloatRect,
            pageIndex: Int,
            colorLayerIndex: Int? = null,
        ): GPUTextA8Instance = GPUTextA8Instance(
            glyphId = glyphId,
            sourceGlyphIndex = sourceGlyphIndex,
            sourceDeviceQuad = deviceQuad,
            uvRect = uvRect,
            pageIndex = pageIndex,
            colorLayerIndex = colorLayerIndex,
        )
    }
}

private fun checkedAllocationExtent(
    contentExtent: Int,
    guardPx: Int,
): Int {
    val extent = contentExtent.toLong() + guardPx.toLong() * 2L
    require(extent in 0..Int.MAX_VALUE.toLong()) {
        "content extent plus guard exceeds Int range."
    }
    return extent.toInt()
}

private fun GPUTextIntRect.overlaps(other: GPUTextIntRect): Boolean =
    left < other.right && other.left < right &&
        top < other.bottom && other.top < bottom

private fun checkedAdd(
    first: Int,
    second: Int,
    label: String,
): Int {
    val result = first.toLong() + second.toLong()
    require(result in 0..Int.MAX_VALUE.toLong()) { "$label exceeds Int range." }
    return result.toInt()
}
