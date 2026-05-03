package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSpan
import org.skia.tests.Src
import undefined.Args
import undefined.Map

/**
 * C++ original:
 * ```cpp
 * class SubRunAllocator {
 * public:
 *     struct Destroyer {
 *         template <typename T>
 *         void operator()(T* ptr) { ptr->~T(); }
 *     };
 *
 *     struct ArrayDestroyer {
 *         int n;
 *         template <typename T>
 *         void operator()(T* ptr) {
 *             for (int i = 0; i < n; i++) { ptr[i].~T(); }
 *         }
 *     };
 *
 *     template<class T>
 *     inline static constexpr bool HasNoDestructor = std::is_trivially_destructible<T>::value;
 *
 *     SubRunAllocator(char* block, int blockSize, int firstHeapAllocation);
 *     explicit SubRunAllocator(int firstHeapAllocation = 0);
 *     SubRunAllocator(const SubRunAllocator&) = delete;
 *     SubRunAllocator& operator=(const SubRunAllocator&) = delete;
 *     SubRunAllocator(SubRunAllocator&&) = default;
 *     SubRunAllocator& operator=(SubRunAllocator&&) = default;
 *
 *     template <typename T>
 *     static std::tuple<SubRunInitializer<T>, int, SubRunAllocator>
 *     AllocateClassMemoryAndArena(int allocSizeHint) {
 *         SkASSERT_RELEASE(allocSizeHint >= 0);
 *         // Round the size after the object the optimal amount.
 *         int extraSize = BagOfBytes::PlatformMinimumSizeWithOverhead(allocSizeHint, alignof(T));
 *
 *         // Don't overflow or die.
 *         SkASSERT_RELEASE(INT_MAX - SkTo<int>(sizeof(T)) > extraSize);
 *         int totalMemorySize = sizeof(T) + extraSize;
 *
 *         void* memory = ::operator new (totalMemorySize);
 *         SubRunAllocator alloc{SkTAddOffset<char>(memory, sizeof(T)), extraSize, extraSize/2};
 *         return {memory, totalMemorySize, std::move(alloc)};
 *     }
 *
 *     template <typename T, typename... Args> T* makePOD(Args&&... args) {
 *         static_assert(HasNoDestructor<T>, "This is not POD. Use makeUnique.");
 *         char* bytes = fAlloc.template allocateBytesFor<T>();
 *         return new (bytes) T(std::forward<Args>(args)...);
 *     }
 *
 *     template <typename T, typename... Args>
 *     std::unique_ptr<T, Destroyer> makeUnique(Args&&... args) {
 *         static_assert(!HasNoDestructor<T>, "This is POD. Use makePOD.");
 *         char* bytes = fAlloc.template allocateBytesFor<T>();
 *         return std::unique_ptr<T, Destroyer>{new (bytes) T(std::forward<Args>(args)...)};
 *     }
 *
 *     template<typename T> T* makePODArray(int n) {
 *         static_assert(HasNoDestructor<T>, "This is not POD. Use makeUniqueArray.");
 *         return reinterpret_cast<T*>(fAlloc.template allocateBytesFor<T>(n));
 *     }
 *
 *     template<typename T>
 *     SkSpan<T> makePODSpan(SkSpan<const T> s) {
 *         static_assert(HasNoDestructor<T>, "This is not POD. Use makeUniqueArray.");
 *         if (s.empty()) {
 *             return SkSpan<T>{};
 *         }
 *
 *         T* result = this->makePODArray<T>(SkTo<int>(s.size()));
 *         memcpy(result, s.data(), s.size_bytes());
 *         return {result, s.size()};
 *     }
 *
 *     template<typename T, typename Src, typename Map>
 *     SkSpan<T> makePODArray(const Src& src, Map map) {
 *         static_assert(HasNoDestructor<T>, "This is not POD. Use makeUniqueArray.");
 *         int size = SkTo<int>(src.size());
 *         T* result = this->template makePODArray<T>(size);
 *         for (int i = 0; i < size; i++) {
 *             new (&result[i]) T(map(src[i]));
 *         }
 *         return {result, src.size()};
 *     }
 *
 *     template<typename T>
 *     std::unique_ptr<T[], ArrayDestroyer> makeUniqueArray(int n) {
 *         static_assert(!HasNoDestructor<T>, "This is POD. Use makePODArray.");
 *         T* array = reinterpret_cast<T*>(fAlloc.template allocateBytesFor<T>(n));
 *         for (int i = 0; i < n; i++) {
 *             new (&array[i]) T{};
 *         }
 *         return std::unique_ptr<T[], ArrayDestroyer>{array, ArrayDestroyer{n}};
 *     }
 *
 *     template<typename T, typename I>
 *     std::unique_ptr<T[], ArrayDestroyer> makeUniqueArray(int n, I initializer) {
 *         static_assert(!HasNoDestructor<T>, "This is POD. Use makePODArray.");
 *         T* array = reinterpret_cast<T*>(fAlloc.template allocateBytesFor<T>(n));
 *         for (int i = 0; i < n; i++) {
 *             new (&array[i]) T(initializer(i));
 *         }
 *         return std::unique_ptr<T[], ArrayDestroyer>{array, ArrayDestroyer{n}};
 *     }
 *
 *     template<typename T, typename U, typename Map>
 *     std::unique_ptr<T[], ArrayDestroyer> makeUniqueArray(SkSpan<const U> src, Map map) {
 *         static_assert(!HasNoDestructor<T>, "This is POD. Use makePODArray.");
 *         int count = SkCount(src);
 *         T* array = reinterpret_cast<T*>(fAlloc.template allocateBytesFor<T>(src.size()));
 *         for (int i = 0; i < count; ++i) {
 *             new (&array[i]) T(map(src[i]));
 *         }
 *         return std::unique_ptr<T[], ArrayDestroyer>{array, ArrayDestroyer{count}};
 *     }
 *
 *     void* alignedBytes(int size, int alignment);
 *
 * private:
 *     BagOfBytes fAlloc;
 * }
 * ```
 */
public open class SubRunAllocator public constructor(
  block: String?,
  blockSize: Int,
  firstHeapAllocation: Int,
) {
  /**
   * C++ original:
   * ```cpp
   *     template<class T>
   *     inline static constexpr bool HasNoDestructor
   * ```
   */
  private var fAlloc: BagOfBytes = TODO("Initialize fAlloc")

  /**
   * C++ original:
   * ```cpp
   * SubRunAllocator::SubRunAllocator(char* bytes, int size, int firstHeapAllocation)
   *         : fAlloc{bytes, SkTo<size_t>(size), SkTo<size_t>(firstHeapAllocation)} {
   *     SkASSERT_RELEASE(SkTFitsIn<size_t>(size));
   *     SkASSERT_RELEASE(SkTFitsIn<size_t>(firstHeapAllocation));
   * }
   * ```
   */
  public constructor(firstHeapAllocation: Int = TODO()) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SubRunAllocator::SubRunAllocator(int firstHeapAllocation)
   *         : SubRunAllocator(nullptr, 0, firstHeapAllocation) { }
   * ```
   */
  public constructor(param0: SubRunAllocator) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SubRunAllocator& operator=(const SubRunAllocator&) = delete
   * ```
   */
  public fun assign(param0: SubRunAllocator) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SubRunAllocator& operator=(SubRunAllocator&&) = default
   * ```
   */
  public fun makePOD(args: Args): T {
    TODO("Implement makePOD")
  }

  /**
   * C++ original:
   * ```cpp
   * T* makePOD(Args&&... args) {
   *         static_assert(HasNoDestructor<T>, "This is not POD. Use makeUnique.");
   *         char* bytes = fAlloc.template allocateBytesFor<T>();
   *         return new (bytes) T(std::forward<Args>(args)...);
   *     }
   * ```
   */
  public fun <T, Args> makeUnique(args: Args): Int {
    TODO("Implement makeUnique")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T, typename... Args>
   *     std::unique_ptr<T, Destroyer> makeUnique(Args&&... args) {
   *         static_assert(!HasNoDestructor<T>, "This is POD. Use makePOD.");
   *         char* bytes = fAlloc.template allocateBytesFor<T>();
   *         return std::unique_ptr<T, Destroyer>{new (bytes) T(std::forward<Args>(args)...)};
   *     }
   * ```
   */
  public fun makePODArray(n: Int): T {
    TODO("Implement makePODArray")
  }

  /**
   * C++ original:
   * ```cpp
   * T* makePODArray(int n) {
   *         static_assert(HasNoDestructor<T>, "This is not POD. Use makeUniqueArray.");
   *         return reinterpret_cast<T*>(fAlloc.template allocateBytesFor<T>(n));
   *     }
   * ```
   */
  public fun <T> makePODSpan(s: SkSpan<T>): Int {
    TODO("Implement makePODSpan")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename T>
   *     SkSpan<T> makePODSpan(SkSpan<const T> s) {
   *         static_assert(HasNoDestructor<T>, "This is not POD. Use makeUniqueArray.");
   *         if (s.empty()) {
   *             return SkSpan<T>{};
   *         }
   *
   *         T* result = this->makePODArray<T>(SkTo<int>(s.size()));
   *         memcpy(result, s.data(), s.size_bytes());
   *         return {result, s.size()};
   *     }
   * ```
   */
  public fun <T, Src, Map> makePODArray(src: Src, map: Map): Int {
    TODO("Implement makePODArray")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename T, typename Src, typename Map>
   *     SkSpan<T> makePODArray(const Src& src, Map map) {
   *         static_assert(HasNoDestructor<T>, "This is not POD. Use makeUniqueArray.");
   *         int size = SkTo<int>(src.size());
   *         T* result = this->template makePODArray<T>(size);
   *         for (int i = 0; i < size; i++) {
   *             new (&result[i]) T(map(src[i]));
   *         }
   *         return {result, src.size()};
   *     }
   * ```
   */
  public fun <T> makeUniqueArray(n: Int): Int {
    TODO("Implement makeUniqueArray")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename T>
   *     std::unique_ptr<T[], ArrayDestroyer> makeUniqueArray(int n) {
   *         static_assert(!HasNoDestructor<T>, "This is POD. Use makePODArray.");
   *         T* array = reinterpret_cast<T*>(fAlloc.template allocateBytesFor<T>(n));
   *         for (int i = 0; i < n; i++) {
   *             new (&array[i]) T{};
   *         }
   *         return std::unique_ptr<T[], ArrayDestroyer>{array, ArrayDestroyer{n}};
   *     }
   * ```
   */
  public fun <T, I> makeUniqueArray(n: Int, initializer: I): Int {
    TODO("Implement makeUniqueArray")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename T, typename I>
   *     std::unique_ptr<T[], ArrayDestroyer> makeUniqueArray(int n, I initializer) {
   *         static_assert(!HasNoDestructor<T>, "This is POD. Use makePODArray.");
   *         T* array = reinterpret_cast<T*>(fAlloc.template allocateBytesFor<T>(n));
   *         for (int i = 0; i < n; i++) {
   *             new (&array[i]) T(initializer(i));
   *         }
   *         return std::unique_ptr<T[], ArrayDestroyer>{array, ArrayDestroyer{n}};
   *     }
   * ```
   */
  public fun <T, U, Map> makeUniqueArray(src: SkSpan<U>, map: Map): Int {
    TODO("Implement makeUniqueArray")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename T, typename U, typename Map>
   *     std::unique_ptr<T[], ArrayDestroyer> makeUniqueArray(SkSpan<const U> src, Map map) {
   *         static_assert(!HasNoDestructor<T>, "This is POD. Use makePODArray.");
   *         int count = SkCount(src);
   *         T* array = reinterpret_cast<T*>(fAlloc.template allocateBytesFor<T>(src.size()));
   *         for (int i = 0; i < count; ++i) {
   *             new (&array[i]) T(map(src[i]));
   *         }
   *         return std::unique_ptr<T[], ArrayDestroyer>{array, ArrayDestroyer{count}};
   *     }
   * ```
   */
  public fun alignedBytes(size: Int, alignment: Int) {
    TODO("Implement alignedBytes")
  }

  public open class Destroyer {
    public operator fun <T> invoke(ptr: T?) {
      TODO("Implement invoke")
    }
  }

  public data class ArrayDestroyer public constructor(
    public var n: Int,
  ) {
    public operator fun <T> invoke(ptr: T?) {
      TODO("Implement invoke")
    }
  }

  public companion object {
    public val hasNoDestructor: Boolean = TODO("Initialize hasNoDestructor")

    /**
     * C++ original:
     * ```cpp
     *     template <typename T>
     *     static std::tuple<SubRunInitializer<T>, int, SubRunAllocator>
     *     AllocateClassMemoryAndArena(int allocSizeHint) {
     *         SkASSERT_RELEASE(allocSizeHint >= 0);
     *         // Round the size after the object the optimal amount.
     *         int extraSize = BagOfBytes::PlatformMinimumSizeWithOverhead(allocSizeHint, alignof(T));
     *
     *         // Don't overflow or die.
     *         SkASSERT_RELEASE(INT_MAX - SkTo<int>(sizeof(T)) > extraSize);
     *         int totalMemorySize = sizeof(T) + extraSize;
     *
     *         void* memory = ::operator new (totalMemorySize);
     *         SubRunAllocator alloc{SkTAddOffset<char>(memory, sizeof(T)), extraSize, extraSize/2};
     *         return {memory, totalMemorySize, std::move(alloc)};
     *     }
     * ```
     */
    public fun <T> allocateClassMemoryAndArena(allocSizeHint: Int): Int {
      TODO("Implement allocateClassMemoryAndArena")
    }
  }
}
