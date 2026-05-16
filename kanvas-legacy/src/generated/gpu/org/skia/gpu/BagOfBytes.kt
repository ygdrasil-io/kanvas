package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class BagOfBytes {
 * public:
 *     BagOfBytes(char* block, size_t blockSize, size_t firstHeapAllocation);
 *     explicit BagOfBytes(size_t firstHeapAllocation = 0);
 *     BagOfBytes(const BagOfBytes&) = delete;
 *     BagOfBytes& operator=(const BagOfBytes&) = delete;
 *     BagOfBytes(BagOfBytes&& that)
 *             : fEndByte{std::exchange(that.fEndByte, nullptr)}
 *             , fCapacity{that.fCapacity}
 *             , fFibProgression{that.fFibProgression} {}
 *     BagOfBytes& operator=(BagOfBytes&& that) {
 *         this->~BagOfBytes();
 *         new (this) BagOfBytes{std::move(that)};
 *         return *this;
 *     }
 *
 *     ~BagOfBytes();
 *
 *     // Given a requestedSize round up to the smallest size that accounts for all the per block
 *     // overhead and alignment. It crashes if requestedSize is negative or too big.
 *     static constexpr int PlatformMinimumSizeWithOverhead(int requestedSize, int assumedAlignment) {
 *         return MinimumSizeWithOverhead(
 *                 requestedSize, assumedAlignment, sizeof(Block), kMaxAlignment);
 *     }
 *
 *     static constexpr int MinimumSizeWithOverhead(
 *             int requestedSize, int assumedAlignment, int blockSize, int maxAlignment) {
 *         SkASSERT_RELEASE(0 <= requestedSize && requestedSize < kMaxByteSize);
 *         SkASSERT_RELEASE(SkIsPow2(assumedAlignment) && SkIsPow2(maxAlignment));
 *
 *         const int minAlignment = std::min(maxAlignment, assumedAlignment);
 *         // There are two cases, one easy and one subtle. The easy case is when minAlignment ==
 *         // maxAlignment. When that happens, the term maxAlignment - minAlignment is zero, and the
 *         // block will be placed at the proper alignment because alignUp is properly
 *         // aligned.
 *         // The subtle case is where minAlignment < maxAlignment. Because
 *         // minAlignment < maxAlignment, alignUp(requestedSize, minAlignment) + blockSize does not
 *         // guarantee that block can be placed at a maxAlignment address. Block can be placed at
 *         // maxAlignment/minAlignment different address to achieve alignment, so we need
 *         // to add memory to allow the block to be placed on a maxAlignment address.
 *         // For example, if assumedAlignment = 4 and maxAlignment = 16 then block can be placed at
 *         // the following address offsets at the end of minimumSize bytes.
 *         //   0 * minAlignment =  0
 *         //   1 * minAlignment =  4
 *         //   2 * minAlignment =  8
 *         //   3 * minAlignment = 12
 *         // Following this logic, the equation for the additional bytes is
 *         //   (maxAlignment/minAlignment - 1) * minAlignment
 *         //     = maxAlignment - minAlignment.
 *         int minimumSize = SkToInt(AlignUp(requestedSize, minAlignment))
 *                           + blockSize
 *                           + maxAlignment - minAlignment;
 *
 *         // If minimumSize is > 32k then round to a 4K boundary unless it is too close to the
 *         // maximum int. The > 32K heuristic is from the JEMalloc behavior.
 *         constexpr int k32K = (1 << 15);
 *         if (minimumSize >= k32K && minimumSize < std::numeric_limits<int>::max() - k4K) {
 *             minimumSize = SkToInt(AlignUp(minimumSize, k4K));
 *         }
 *
 *         return minimumSize;
 *     }
 *
 *     template <int size>
 *     using Storage = std::array<char, PlatformMinimumSizeWithOverhead(size, 1)>;
 *
 *     // Returns true if n * sizeof(T) will fit in an allocation block.
 *     template <typename T>
 *     static bool WillCountFit(int n) {
 *         constexpr int kMaxN = kMaxByteSize / sizeof(T);
 *         return 0 <= n && n < kMaxN;
 *     }
 *
 *     // Returns a pointer to memory suitable for holding n Ts.
 *     template <typename T> char* allocateBytesFor(int n = 1) {
 *         static_assert(alignof(T) <= kMaxAlignment, "Alignment is too big for arena");
 *         static_assert(sizeof(T) < kMaxByteSize, "Size is too big for arena");
 *         SkASSERT_RELEASE(WillCountFit<T>(n));
 *
 *         int size = n ? n * sizeof(T) : 1;
 *         return this->allocateBytes(size, alignof(T));
 *     }
 *
 *     void* alignedBytes(int unsafeSize, int unsafeAlignment);
 *
 * private:
 *     // The maximum alignment supported by GrBagOfBytes. 16 seems to be a good number for alignment.
 *     // If a use case for larger alignments is found, we can turn this into a template parameter.
 *     inline static constexpr int kMaxAlignment = std::max(16, (int)alignof(std::max_align_t));
 *     // The largest size that can be allocated. In larger sizes, the block is rounded up to 4K
 *     // chunks. Leave a 4K of slop.
 *     inline static constexpr int k4K = (1 << 12);
 *     // This should never overflow with the calculations done on the code.
 *     inline static constexpr int kMaxByteSize = std::numeric_limits<int>::max() - k4K;
 *     // The assumed alignment of new char[] given the platform.
 *     // There is a bug in Emscripten's allocator that make alignment different than max_align_t.
 *     // kAllocationAlignment accounts for this difference. For more information see:
 *     // https://github.com/emscripten-core/emscripten/issues/10072
 *     #if !defined(SK_FORCE_8_BYTE_ALIGNMENT)
 *         static constexpr int kAllocationAlignment = alignof(std::max_align_t);
 *     #else
 *         static constexpr int kAllocationAlignment = 8;
 *     #endif
 *
 *     static constexpr size_t AlignUp(int size, int alignment) {
 *         return (size + (alignment - 1)) & -alignment;
 *     }
 *
 *     // The Block starts at the location pointed to by fEndByte.
 *     // Beware. Order is important here. The destructor for fPrevious must be called first because
 *     // the Block is embedded in fBlockStart. Destructors are run in reverse order.
 *     struct Block {
 *         Block(char* previous, char* startOfBlock);
 *         // The start of the originally allocated bytes. This is the thing that must be deleted.
 *         char* const fBlockStart;
 *         Block* const fPrevious;
 *     };
 *
 *     // Note: fCapacity is the number of bytes remaining, and is subtracted from fEndByte to
 *     // generate the location of the object.
 *     char* allocateBytes(int size, int alignment) {
 *         fCapacity = fCapacity & -alignment;
 *         if (fCapacity < size) {
 *             this->needMoreBytes(size, alignment);
 *         }
 *         char* const ptr = fEndByte - fCapacity;
 *         SkASSERT(((intptr_t)ptr & (alignment - 1)) == 0);
 *         SkASSERT(fCapacity >= size);
 *         fCapacity -= size;
 *         return ptr;
 *     }
 *
 *     // Adjust fEndByte and fCapacity give a new block starting at bytes with size.
 *     void setupBytesAndCapacity(char* bytes, int size);
 *
 *     // Adjust fEndByte and fCapacity to satisfy the size and alignment request.
 *     void needMoreBytes(int size, int alignment);
 *
 *     // This points to the highest kMaxAlignment address in the allocated block. The address of
 *     // the current end of allocated data is given by fEndByte - fCapacity. While the negative side
 *     // of this pointer are the bytes to be allocated. The positive side points to the Block for
 *     // this memory. In other words, the pointer to the Block structure for these bytes is
 *     // reinterpret_cast<Block*>(fEndByte).
 *     char* fEndByte{nullptr};
 *
 *     // The number of bytes remaining in this block.
 *     int fCapacity{0};
 *
 *     SkFibBlockSizes<kMaxByteSize> fFibProgression;
 * }
 * ```
 */
public data class BagOfBytes public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kMaxAlignment
   * ```
   */
  private var fEndByte: String?,
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int k4K = (1 << 12)
   * ```
   */
  private var fCapacity: Int,
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kMaxByteSize
   * ```
   */
  private var fFibProgression: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * BagOfBytes& operator=(const BagOfBytes&) = delete
   * ```
   */
  public fun assign(param0: BagOfBytes) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * BagOfBytes& operator=(BagOfBytes&& that) {
   *         this->~BagOfBytes();
   *         new (this) BagOfBytes{std::move(that)};
   *         return *this;
   *     }
   * ```
   */
  public fun allocateBytesFor(n: Int = TODO()): Char {
    TODO("Implement allocateBytesFor")
  }

  /**
   * C++ original:
   * ```cpp
   * char* allocateBytesFor(int n = 1) {
   *         static_assert(alignof(T) <= kMaxAlignment, "Alignment is too big for arena");
   *         static_assert(sizeof(T) < kMaxByteSize, "Size is too big for arena");
   *         SkASSERT_RELEASE(WillCountFit<T>(n));
   *
   *         int size = n ? n * sizeof(T) : 1;
   *         return this->allocateBytes(size, alignof(T));
   *     }
   * ```
   */
  public fun alignedBytes(unsafeSize: Int, unsafeAlignment: Int) {
    TODO("Implement alignedBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * void* BagOfBytes::alignedBytes(int size, int alignment) {
   *     SkASSERT_RELEASE(0 < size && size < kMaxByteSize);
   *     SkASSERT_RELEASE(0 < alignment && alignment <= kMaxAlignment);
   *     SkASSERT_RELEASE(SkIsPow2(alignment));
   *
   *     return this->allocateBytes(size, alignment);
   * }
   * ```
   */
  private fun allocateBytes(size: Int, alignment: Int): Char {
    TODO("Implement allocateBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * char* allocateBytes(int size, int alignment) {
   *         fCapacity = fCapacity & -alignment;
   *         if (fCapacity < size) {
   *             this->needMoreBytes(size, alignment);
   *         }
   *         char* const ptr = fEndByte - fCapacity;
   *         SkASSERT(((intptr_t)ptr & (alignment - 1)) == 0);
   *         SkASSERT(fCapacity >= size);
   *         fCapacity -= size;
   *         return ptr;
   *     }
   * ```
   */
  private fun setupBytesAndCapacity(bytes: String?, size: Int) {
    TODO("Implement setupBytesAndCapacity")
  }

  /**
   * C++ original:
   * ```cpp
   * void BagOfBytes::setupBytesAndCapacity(char* bytes, int size) {
   *     // endByte must be aligned to the maximum alignment to allow tracking alignment using capacity;
   *     // capacity and endByte are both aligned to max alignment.
   *     intptr_t endByte = reinterpret_cast<intptr_t>(bytes + size - sizeof(Block)) & -kMaxAlignment;
   *     fEndByte  = reinterpret_cast<char*>(endByte);
   *     fCapacity = fEndByte - bytes;
   * }
   * ```
   */
  private fun needMoreBytes(size: Int, alignment: Int) {
    TODO("Implement needMoreBytes")
  }

  public open class Block public constructor(
    public val fBlockStart: String?,
    public val fPrevious: org.skia.modules.Block?,
  ) {
    public constructor(previous: String?, startOfBlock: String?) : this() {
      TODO("Implement constructor")
    }
  }

  public companion object {
    private val kMaxAlignment: Int = TODO("Initialize kMaxAlignment")

    private val k4K: Int = TODO("Initialize k4K")

    private val kMaxByteSize: Int = TODO("Initialize kMaxByteSize")

    private val kAllocationAlignment: Int = TODO("Initialize kAllocationAlignment")

    /**
     * C++ original:
     * ```cpp
     * static constexpr int PlatformMinimumSizeWithOverhead(int requestedSize, int assumedAlignment) {
     *         return MinimumSizeWithOverhead(
     *                 requestedSize, assumedAlignment, sizeof(Block), kMaxAlignment);
     *     }
     * ```
     */
    public fun platformMinimumSizeWithOverhead(requestedSize: Int, assumedAlignment: Int): Int {
      TODO("Implement platformMinimumSizeWithOverhead")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr int MinimumSizeWithOverhead(
     *             int requestedSize, int assumedAlignment, int blockSize, int maxAlignment) {
     *         SkASSERT_RELEASE(0 <= requestedSize && requestedSize < kMaxByteSize);
     *         SkASSERT_RELEASE(SkIsPow2(assumedAlignment) && SkIsPow2(maxAlignment));
     *
     *         const int minAlignment = std::min(maxAlignment, assumedAlignment);
     *         // There are two cases, one easy and one subtle. The easy case is when minAlignment ==
     *         // maxAlignment. When that happens, the term maxAlignment - minAlignment is zero, and the
     *         // block will be placed at the proper alignment because alignUp is properly
     *         // aligned.
     *         // The subtle case is where minAlignment < maxAlignment. Because
     *         // minAlignment < maxAlignment, alignUp(requestedSize, minAlignment) + blockSize does not
     *         // guarantee that block can be placed at a maxAlignment address. Block can be placed at
     *         // maxAlignment/minAlignment different address to achieve alignment, so we need
     *         // to add memory to allow the block to be placed on a maxAlignment address.
     *         // For example, if assumedAlignment = 4 and maxAlignment = 16 then block can be placed at
     *         // the following address offsets at the end of minimumSize bytes.
     *         //   0 * minAlignment =  0
     *         //   1 * minAlignment =  4
     *         //   2 * minAlignment =  8
     *         //   3 * minAlignment = 12
     *         // Following this logic, the equation for the additional bytes is
     *         //   (maxAlignment/minAlignment - 1) * minAlignment
     *         //     = maxAlignment - minAlignment.
     *         int minimumSize = SkToInt(AlignUp(requestedSize, minAlignment))
     *                           + blockSize
     *                           + maxAlignment - minAlignment;
     *
     *         // If minimumSize is > 32k then round to a 4K boundary unless it is too close to the
     *         // maximum int. The > 32K heuristic is from the JEMalloc behavior.
     *         constexpr int k32K = (1 << 15);
     *         if (minimumSize >= k32K && minimumSize < std::numeric_limits<int>::max() - k4K) {
     *             minimumSize = SkToInt(AlignUp(minimumSize, k4K));
     *         }
     *
     *         return minimumSize;
     *     }
     * ```
     */
    public fun minimumSizeWithOverhead(
      requestedSize: Int,
      assumedAlignment: Int,
      blockSize: Int,
      maxAlignment: Int,
    ): Int {
      TODO("Implement minimumSizeWithOverhead")
    }

    /**
     * C++ original:
     * ```cpp
     *     template <typename T>
     *     static bool WillCountFit(int n) {
     *         constexpr int kMaxN = kMaxByteSize / sizeof(T);
     *         return 0 <= n && n < kMaxN;
     *     }
     * ```
     */
    public fun <T> willCountFit(n: Int): Boolean {
      TODO("Implement willCountFit")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr size_t AlignUp(int size, int alignment) {
     *         return (size + (alignment - 1)) & -alignment;
     *     }
     * ```
     */
    private fun alignUp(size: Int, alignment: Int): Int {
      TODO("Implement alignUp")
    }
  }
}
