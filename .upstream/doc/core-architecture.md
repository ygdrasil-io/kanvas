# Core Architecture

Skia is layered as a public API (`include/`) on top of a private substrate
(`include/private/`, `src/base/`, `src/core/`). The public layer presents value
types (`SkPaint`, `SkRect`, `SkMatrix`), refcounted resources (`SkData`,
`SkImage`, `SkColorSpace`, `SkSurface`), and abstract interfaces
(`SkCanvas`, `SkShader`, `SkColorFilter`, `SkPathEffect`). Almost all of those
are built on a small set of foundation primitives — reference counting,
contiguous-memory containers, byte buffers, streams, arena allocators, and
debug/assert macros — that this document covers.

```
                        public API (include/core, include/utils, include/gpu)
   SkCanvas, SkPaint, SkImage, SkSurface, SkPath, SkColorSpace, ...
                                  │
                                  │ depend on
                                  ▼
              core foundation types this file documents
   ┌──────────────┬──────────────┬───────────────┬─────────────────┐
   │ Refcounting  │ Byte/string  │ Streams       │ Arena/Block     │
   │ SkRefCnt     │ containers   │ SkStream/     │ allocators      │
   │ SkNVRefCnt   │ SkData       │ SkWStream     │ SkArenaAlloc    │
   │ SkWeakRefCnt │ SkSpan       │ SkFILEStream  │ SkBlockAllocator│
   │ sk_sp<T>     │ SkString     │ SkMemoryStream│ SkContainer-    │
   │              │ SkTArray     │ SkDynamicMem- │ Allocator       │
   │              │ SkTDArray    │ WStream       │ SkSTArenaAlloc  │
   └──────────────┴──────────────┴───────────────┴─────────────────┘
                                  │
                                  ▼
            cross-cutting platform & build glue
   ┌──────────────────────────────────────────────────────┐
   │ SkAPI / SK_SPI       (DLL visibility)                │
   │ SkAssert / SkASSERT  (asserts; release-mode aborts)  │
   │ SkDebug  / SkDebugf  (formatted debug logging)       │
   │ SkOnce               (call-once primitive)           │
   │ SkMalloc / sk_malloc (porting-layer allocator)       │
   │ SkLoadUserConfig     (per-build SkUserConfig.h)      │
   └──────────────────────────────────────────────────────┘
```

Throughout the source tree, `include/private/base/*.h` holds the smallest
cross-cutting primitives (everything that has no dependency on `SkRefCnt` or
`SkData`). `src/base/*.{h,cpp}` adds *implementation* utilities that are still
shared by everything: arena allocators, block allocators, base64 encoding,
Bézier helpers, and a handful of math kernels. `include/private/` holds
non-base private headers that depend on at least `SkRefCnt`, most importantly
[`SkWeakRefCnt`](#skweakrefcnt) and `SkPathRef`.

---

## Reference counting

### SkRefCnt / SkRefCntBase

`include/core/SkRefCnt.h` defines Skia's main intrusive refcount. `SkRefCntBase`
holds an atomic `int32_t fRefCnt` initialized to 1. The default `SkRefCnt`
publicly inherits from `SkRefCntBase` (a hook macro
`SK_REF_CNT_MIXIN_INCLUDE` lets a user config slot in a custom mixin —
Chrome uses this).

| Member | Memory ordering | Purpose |
|---|---|---|
| `ref()` | `relaxed` | Increment count. Asserts the prior value was > 0. |
| `unref()` | `acq_rel` on the decrement; `acquire` implied if it called `internal_dispose()` | Decrement; if the count reaches 0, call `internal_dispose()` (default deletes the object). |
| `unique()` | `acquire` if it returns true | Returns true if the caller is the only owner. |
| `getRefCnt()` | `relaxed`, debug-only | Diagnostic. |
| `internal_dispose()` | virtual | Override hook for subclasses (used by `SkWeakRefCnt`). |

The destructor is virtual; the destructor asserts `getRefCnt() == 1` in debug
builds and clobbers `fRefCnt` to 0 to catch use-after-free. Copy and move are
deleted: refcounted objects must be referred to by `sk_sp<T>` or raw pointer.

### SkNVRefCnt — non-virtual variant

For objects that don't otherwise need a vtable (notably `SkData`,
`SkColorSpace`, `SkVertices`), Skia provides `SkNVRefCnt<Derived>`. It has the
same `ref` / `unref` / `unique` semantics but saves the 8-or-16 byte vtable
pointer; on `unref()` to zero, it does `delete (const Derived*)this`. It also
exposes `refCntGreaterThan(int)` for thread-isolated probes used by GPU
backends.

### sk_sp<T> — smart pointer

`sk_sp<T>` is Skia's analog of `std::shared_ptr` for any type that has
`ref()` / `unref()` (so it works with `SkRefCnt` *and* `SkNVRefCnt`,
`SkString::Rec`, etc). It is annotated `SK_TRIVIAL_ABI` so destroying-move is
optimizable to a memcpy.

Construction semantics (note the **adopting** raw-pointer constructor):

| Form | Behavior |
|---|---|
| `sk_sp<T>()`, `sk_sp<T>(nullptr)` | empty |
| `sk_sp<T>(T* obj)` (explicit) | **adopts** obj — does *not* call `ref()` |
| `sk_sp<T>(const sk_sp<U>&)` | shares — calls `ref()` |
| `sk_sp<T>(sk_sp<T>&&)` | transfers — neither `ref` nor `unref` |
| `sk_make_sp<T>(args...)` | `sk_sp<T>(new T(args...))` |
| `sk_ref_sp<T>(T* obj)` | wraps and **also calls `ref()`** |

`reset(T*)` adopts a new pointer (and unrefs the old one); `release()` returns
the bare pointer with ownership transferred to the caller. `operator*` /
`operator->` assert non-null in debug builds. The free helpers `SkRef`,
`SkSafeRef`, `SkSafeUnref` work directly on raw pointers.

### SkWeakRefCnt

`include/private/SkWeakRefCnt.h`. Adds an *additional* atomic weak count on top
of the strong count. The strong owners collectively hold one weak reference;
when the strong count drops to zero, `weak_dispose()` is called (subclasses
override this to free heavy resources), and that implicit weak reference is
dropped. The object is fully deleted only when the weak count itself reaches
zero.

| Member | Notes |
|---|---|
| `weak_ref()` | Increment weak count (caller must already hold a weak ref). |
| `weak_unref()` | Decrement weak count; deletes the object on transition to 0. |
| `try_ref()` | Atomic: turns a weak reference into a strong reference, returning false if the strong count was already 0. |
| `weak_expired()` | True iff strong count is 0. |
| `weak_dispose()` | Virtual hook called when strong count → 0. |

The invariant maintained internally is `fWeakCnt = #external_weak +
(fRefCnt > 0 ? 1 : 0)`. `SkWeakRefCnt` is the foundation for `SkTypeface`'s
weak-cache slot in the global font cache.

---

## Byte buffers and views

### SkData

`include/core/SkData.h` defines `SkData`, an immutable sized byte buffer.
`SkData` derives from `SkNVRefCnt<SkData>`, so it is final (the destructor is
private). The size and address of the contents never change for the lifetime
of the data object.

Construction:

| Factory | Behavior |
|---|---|
| `MakeWithCopy(ptr, len)` | Always copies. |
| `MakeUninitialized(len)` | Allocates `len` uninitialized bytes; caller writes via `writable_data()` *before* sharing. |
| `MakeZeroInitialized(len)` | As above, zero-filled. |
| `MakeWithCString(c)` | Copies the C-string including the trailing `\0`. |
| `MakeWithProc(ptr, len, proc, ctx)` | Wraps caller memory; `proc(ptr, ctx)` runs at destruction. |
| `MakeWithoutCopy(ptr, len)` | Wraps a global / static buffer (uses no-op release proc). |
| `MakeFromMalloc(ptr, len)` | Adopts a `sk_malloc`-allocated buffer; calls `sk_free` at destruction. |
| `MakeFromFileName(path)` | Memory-maps the file if possible; nullptr on failure. |
| `MakeFromFILE(FILE*)`, `MakeFromFD(int)` | Reads (does not close the descriptor). |
| `MakeFromStream(SkStream*, size)` | Reads `size` bytes from a stream. |
| `MakeEmpty()` | Returns the singleton empty `SkData`. |

Views: `data()`, `bytes()`, `byteSpan()` (returns `SkSpan<const uint8_t>`),
`size()`, `empty()`. `writable_data()` exists but is marked "USE WITH CAUTION"
— mutation is only safe as long as no other thread can observe the data.

Subset operations: `copySubset(offset, len)` always allocates and copies;
`shareSubset(offset, len)` returns a new `SkData` whose underlying release proc
holds the original alive without copying. `copyRange(offset, len, buffer)`
clamps and copies into a caller-provided buffer.

`SkData::ReleaseProc` has signature `void(const void* ptr, void* context)`.

### SkSpan<T>

`include/core/SkSpan.h` is a thin re-export of
`include/private/base/SkSpan_impl.h`. While `SK_USE_LEGACY_SKSPAN` is defined,
`SkSpan` is a bespoke `(T* ptr, size_t size)` pair that mirrors `std::span`;
when that macro is unset, `SkSpan` becomes `std::span`. The legacy implementation
adds bounds-checked `operator[]`, `front()`, `back()`, `first(n)`, `last(n)`,
`subspan(offset[, count])`, plus a converting deduction guide so passing a
`std::vector` or a C array deduces the right element type.

`SkSpan` is the standard parameter type for any "sized array" input across
modern Skia APIs. When passing an `initializer_list` literal, the lifetime is
limited to the call expression, so `routine({1, 2, 3, 4})` is safe but
binding the list to a local variable first is undefined behavior — see the
`SK_CHECK_IL_LIFETIME` annotation.

### SkString

`include/core/SkString.h` is a UTF-8 string with copy-on-write reference
counting. Storage is a private `Rec` POD that stores its own atomic refcount
followed by the inline character buffer; multiple `SkString` instances can
share one `Rec`. The empty string is a static `gEmptyRec` singleton, so
default-constructed `SkString`s never allocate.

The interesting parts of the API:

- Construction from `const char*`, `(const char*, size_t)`, `std::string`,
  `std::string_view`, plus a `SkString(size_t len)` that pre-allocates.
- `equals`, `startsWith`, `endsWith`, `contains`, `find`, `findLastOf`.
- `c_str()`, `data()`, `size()`, `isEmpty()`, range-for via `begin()/end()`.
- Mutators that COW: `set`, `insert(...)`, `append(...)`, `prepend(...)`,
  `remove(offset, length)`, `resize(len)`, plus typed `appendU32`,
  `appendS64`, `appendHex`, `appendScalar`, `appendUnichar`.
- `printf`, `printVAList`, `appendf`, `appendVAList`, `prependf`,
  `prependVAList` (all `SK_PRINTF_LIKE`-checked).
- `swap` (noexcept), assignment from C-string, `operator+=` with `char`,
  `const char*`, or another `SkString`.
- `SkStringPrintf("%s %d", ...)` is the printf-into-fresh-string helper.
- `sk_is_trivially_relocatable` is true so SkStrings can move with `memcpy` in
  Skia's container types.

A small family of free helpers in the same header — `SkStrAppendU32`,
`SkStrAppendS32`, `SkStrAppendU64`, `SkStrAppendS64`, `SkStrAppendScalar` —
write decimal numbers into caller-supplied buffers, returning a pointer past
the last character; constants like `kSkStrAppendU32_MaxSize` (10) and
`kSkStrAppendScalar_MaxSize` (15) name the worst-case lengths.

---

## Streams

`include/core/SkStream.h` declares an abstract reader/writer hierarchy used by
codecs, font scanners, deserialization, the SVG canvas, and the PDF backend.

### Reader hierarchy

```
        SkStream  (read, peek, isAtEnd)
            │
            ▼
        SkStreamRewindable  (rewind, duplicate)
            │
            ▼
        SkStreamSeekable    (hasPosition, getPosition, seek, move, fork)
            │
            ▼
        SkStreamAsset       (hasLength, getLength)
            │
            ▼
        SkStreamMemory      (getMemoryBase)
```

Each subclass narrows the contract: `SkStreamRewindable` guarantees
`rewind()` / `duplicate()`, `SkStreamSeekable` adds positional seeking and
`fork()` (a duplicate at the same position), `SkStreamAsset` knows its total
length, `SkStreamMemory` exposes a base pointer for in-place reads. A reader
in higher tiers narrows what subclasses it can return — `SkStreamSeekable`
overrides `onDuplicate()` to return a `SkStreamSeekable*` so the wrapping
`duplicate()` can return `unique_ptr<SkStreamSeekable>`.

The base `SkStream` exposes typed primitive readers — `readS8`, `readS16`,
`readS32`, `readS64`, `readU8`, `readU16`, `readU32`, `readU64`,
`readBool`, `readScalar`, `readPackedUInt` — all `[[nodiscard]]`.

Concrete subclasses:

| Class | Backing |
|---|---|
| `SkFILEStream` | C `FILE*` — opened from a path or wrapped from a caller-provided handle. Implements full `SkStreamAsset`. |
| `SkMemoryStream` | `sk_sp<const SkData>` — implements full `SkStreamMemory`. Static factories: `MakeCopy`, `MakeDirect`, `Make(sk_sp<const SkData>)`. |

`SkStream::MakeFromFile(path)` is a free factory that returns a
`unique_ptr<SkStreamAsset>`.

### Writer hierarchy

`SkWStream` is the abstract base; required overrides are `write(buf, size)`,
`bytesWritten()`, with optional `flush()`. Helpers include `write8`, `write16`,
`write32`, `write64`, `writeText`, `newline`, `writeDecAsText`,
`writeBigDecAsText`, `writeHexAsText`, `writeScalarAsText`, `writeBool`,
`writeScalar`, `writePackedUInt`, and `writeStream`.

| Class | Behavior |
|---|---|
| `SkNullWStream` | Discards all writes; only counts bytes. |
| `SkFILEWStream` | Writes to `FILE*` opened from a path; `fsync()` is exposed. |
| `SkDynamicMemoryWStream` | Block-linked accumulator. `detachAsData()` / `detachAsStream()` / `detachAsVector()` consume the buffer; `copyTo` / `writeToStream` / `writeToAndReset` move bytes elsewhere. |

`SkDynamicMemoryWStream` is the standard "build up a buffer in pieces" type
used by serializers (notably the PDF and SKP writers). When the destination
of `writeToAndReset` is another `SkDynamicMemoryWStream`, the implementation
is constant-time — it splices its block list into the destination's tail.

---

## Arena and block allocators

For the per-draw, per-frame, per-scope allocations that dominate Skia's hot
paths, individual `new`/`delete` calls would be both fragmenting and slow.
Two custom allocators in `src/base/` solve that.

### SkArenaAlloc

`src/base/SkArenaAlloc.h`. Allocates objects in monotonically-growing blocks
and runs all destructors at arena destruction time.

Construction options:

```c++
char block[kMostCases];
SkArenaAlloc arena(block, kMostCases, kFirstHeapAllocation);

// or, for a large-but-uncommon allocation:
SkArenaAlloc arena(nullptr, 0, kAlmostAllCases);

// or, with inline storage in a class:
template <size_t N> class SkSTArenaAlloc;
template <size_t N> class SkSTArenaAllocWithReset;
```

The arena starts using a caller-provided `block` of `blockSize` bytes (often
on the stack via `SkSTArenaAlloc<N>`). When that block is exhausted, the
arena allocates from the heap, starting with `firstHeapAllocation` bytes and
growing on a Fibonacci progression (`SkFibBlockSizes` / `SkFibonacci47`).
Fibonacci growth bounds at most ~48 allocations to reach 4 GiB and ~71 to
reach 256 TiB.

The hot allocation API:

| Call | Notes |
|---|---|
| `arena.make<T>(args...)` | Forwards `args` to a placement-new `T(args...)`. |
| `arena.make<T>()` | If `T` is POD, allocates aligned bytes only (no constructor); otherwise placement-news. |
| `arena.makeArray<T>(count)` | Value-init array (POD elements zero-initialized). |
| `arena.makeArrayDefault<T>(count)` | Default-init array (POD elements left uninitialized). |
| `arena.makeInitializedArray<T>(count, init)` | Calls `T(init(i))` per element. |
| `arena.makeArrayCopy<T>(SkSpan<const T>)` | Copies via memcpy if trivially copyable, otherwise per-element. |
| `arena.makeBytesAlignedTo(size, align)` | Raw aligned bytes (use only when no typed variant fits). |

For non-trivially-destructible types, the arena installs a small **footer**
after the object that records a destruction callback (`FooterAction*`).
On `~SkArenaAlloc`, the arena walks each block's footer chain to invoke
destructors in reverse-allocation order.

`SkArenaAllocWithReset` adds `reset()` (destroys everything, frees heap
blocks, returns to the static block) and `isEmpty()`. `SkSTArenaAlloc<N>`
inherits from `std::array<char, N>` first so the inline storage outlives the
`SkArenaAlloc` member during destruction (important under MSAN).

### SkBlockAllocator

`src/base/SkBlockAllocator.h`. A lower-level companion to `SkArenaAlloc` for
clients that need to release / resize / re-use allocations within blocks
themselves. It does not call destructors — the wrapping container is
responsible.

- Per-block byte limit: `kMaxAllocationSize = 1 << 29` (512 MB), chosen so all
  internal arithmetic fits in `int`.
- Growth policies: `kFixed`, `kLinear`, `kFibonacci`, `kExponential`.
- `Block::release(start, end)` and `Block::resize(start, end, deltaBytes)`
  allow reclaiming bytes when the released range is at the tail of the block.
- Each `Block` carries a 32-bit `metadata()` integer for higher-level
  bookkeeping (used by some pool types).
- `SkSBlockAllocator<N>` adds `N` bytes of inline storage.

`SkTBlockList<T, N>` (in the same header tree) is the typical typed wrapper,
giving an append-only list of `T` items backed by a `SkBlockAllocator`.

### Bulk-byte allocator

`include/private/base/SkContainers.h` defines `SkContainerAllocator` and the
free functions `sk_allocate_throw(size)` / `sk_allocate_canfail(size)`. These
back Skia's resizable container types (`SkTArray`, `SkTDArray`). The allocator
rounds capacity up to multiples of `kCapacityMultiple = 8` bytes (matching
ASAN shadow granularity) and bounds growth by `fMaxCapacity`.

---

## Asserts, debug logging, and runtime checks

### SkAssert / SkASSERT

`include/private/base/SkAssert.h`. The hierarchy is, from cheapest to most
forceful:

| Macro | Debug | Release |
|---|---|---|
| `SkASSERT(cond)` | aborts via `SK_ABORT("check(%s)", #cond)` | no-op |
| `SkASSERTF(cond, fmt, ...)` | aborts with formatted message | no-op |
| `SkAssertResult(cond)` | `SkASSERT(cond)` | evaluates `cond` exactly once |
| `SkASSERT_RELEASE(cond)` | aborts | aborts (same as debug) |
| `SkASSERTF_RELEASE` | aborts with formatted message | aborts |
| `SkDEBUGFAIL(msg)`, `SkDEBUGFAILF(...)` | aborts | no-op |

`SK_ABORT(message, ...)` prints `__FILE__:__LINE__: fatal error: "..."` via
`SkDebugf`, optionally prints a Google3 stack via
`SK_DUMP_GOOGLE3_STACK`, then calls `[[noreturn]] sk_abort_no_print()`.

Branch-prediction and unreachability helpers in the same file:

- `SK_LIKELY` / `SK_UNLIKELY` map to `[[likely]]` / `[[unlikely]]` if
  available.
- `SK_ASSUME(cond)` maps to `__builtin_assume` (clang),
  `__attribute__((assume(cond)))` (GCC ≥13), or `__assume(cond)` (MSVC).
- `SkUNREACHABLE` maps to `__builtin_trap()` or, on MSVC, `__fastfail(5)`.

Bounds-check helpers `sk_collection_check_bounds(i, size)` and
`sk_collection_check_length(i, size)` abort with a printable message in debug
and `SkUNREACHABLE` in release; `SkSpan::operator[]` calls
`sk_collection_check_bounds` so out-of-bounds access is caught when
`SK_DEBUG` is defined.

### SkDebug / SkDebugf

`include/private/base/SkDebug.h`. Declares the global, printf-style
`SkDebugf(fmt, ...)` (porting layer; `src/base/SkDebug.cpp` provides defaults
that route to `stderr`/`OutputDebugString`/`__android_log_print`/etc.) and
two debug-only wrappers:

- `SkDEBUGCODE(...)` expands to its arguments in debug, nothing in release.
- `SkDEBUGF(...)` calls `SkDebugf` only in debug builds.

### SkLog

`include/private/base/SkLog.h` and `src/base/SkLog.cpp` provide a leveled
logger (`SK_LOG_PRIORITY_*`) that defaults to printing through `SkDebugf`
on most platforms but maps to `__android_log_print` on Android. Log levels
are filtered against `SK_DEFAULT_LOG_PRIORITY`.

---

## Visibility, attributes, and per-build config

### SkAPI / SK_SPI

`include/private/base/SkAPI.h`. The entire public API is annotated with
`SK_API` so that when Skia is built as a DLL (`SKIA_DLL` defined), public
symbols are exported with `__declspec(dllexport)` (MSVC) or
`__attribute__((visibility("default")))` (clang/gcc) and clients get
`__declspec(dllimport)` automatically. `SK_SPI` is the "less stable" twin
used for `src/`-only entrypoints. `SK_API_AVAILABLE` is an Apple-flavored
availability hook.

### SkAttributes

`include/private/base/SkAttributes.h` defines portable wrappers around
`SK_ALWAYS_INLINE`, `SK_PRINTF_LIKE(fmt_idx, va_idx)`, `SK_TRIVIAL_ABI`,
`SK_NO_SANITIZE`, `SK_INTEL_INTRINSICS_TARGET`, and a few similar
compiler-attribute mappings.

### SkLoadUserConfig

`include/private/base/SkLoadUserConfig.h` is the include-once shim that pulls
in `SkUserConfig.h`. Embedders can override almost any compile-time switch
(SK_DEBUG, default backends, optional features, custom mixins) by providing
their own `SkUserConfig.h` on the include path.

### SkMacros

`include/private/base/SkMacros.h` provides project-wide macro idioms:
`SK_BEGIN_REQUIRE_DENSE` (asserts struct packing), `SK_INIT_TO_AVOID_WARNING`,
`SkBitmaskEnum` traits for bit-mask enums, and similar small helpers.

### SkFeatures

`include/private/base/SkFeatures.h` is the single header where Skia decides,
based on compiler/platform macros, what is buildable: `SK_BUILD_FOR_ANDROID`,
`SK_BUILD_FOR_MAC`, `SK_CPU_LENDIAN`, `SK_CPU_SSE_LEVEL`, etc. The rest of
the codebase guards on the `SK_*` family these introduce.

---

## Threading & synchronization primitives

`include/private/base/`:

- **`SkThreadAnnotations.h`** — clang `[[guarded_by]]` /
  `SK_POTENTIALLY_BLOCKING_REGION_BEGIN`-style macros.
- **`SkOnce.h`** — call-once primitive. Lock-free three-state
  (`NotStarted`/`Claimed`/`Done`) with relaxed/release/acquire ordering.
  Used by lazily-initialized singletons (e.g. `SkColorSpace::computeLazyDstFields`).
- **`SkMutex.h`** — wraps `std::mutex` plus thread-annotation macros.
- **`SkSemaphore.h`** — counting semaphore.
- **`SkThreadID.h`** — opaque thread identifier.

`src/base/` adds heavier implementations:

- **`SkSharedMutex`** — read/write lock used by font caches and resource
  caches.
- **`SkSpinlock`** — short-section spinlock; used in performance-critical
  paths where a `SkMutex` would be too heavy.

---

## Math and bit utilities

Public/private split parallels the rest of `base/`:

- `include/core/SkScalar.h` — `SkScalar` is `float`. Defines `SK_ScalarPI`,
  `SK_ScalarHalf`, conversions, `SkScalarSin`/`SkScalarCos`, etc.
- `include/private/base/SkMath.h`, `SkFloatingPoint.h`, `SkFixed.h`,
  `SkSafe32.h`, `SkAlign.h`, `SkTo.h`, `SkTPin.h`, `SkTFitsIn.h`,
  `SkTypeTraits.h`, `SkTLogic.h`.
- `src/base/SkMathPriv.h` — non-public extras.
- `src/base/SkHalf.h` — IEEE half-float conversions used by `kRGBA_F16`.
- `src/base/SkFloatBits.h` — bit-tricks on float bit-patterns.
- `src/base/SkSafeMath.h` — overflow-checking integer arithmetic used by
  size calculations.
- `src/base/SkVx.h` — `skvx::Vec<N, T>` SIMD wrapper used throughout
  `src/core/` (e.g. `SkRasterPipeline`, blitters).

---

## Containers

Beyond `SkSpan`, Skia ships a handful of typed containers in
`include/private/base/`:

- **`SkTArray<T[, MEM_MOVE]>`** — vector-like, optional small-object inline
  storage (`SkSTArray<N, T>`). The `MEM_MOVE` template arg switches between
  per-element move and `memcpy` move based on `sk_is_trivially_relocatable`.
- **`SkTDArray<T>`** — vector for trivially-copyable T; lighter than
  `SkTArray`.
- **`SkSpan_impl.h`** — see above.
- **`SkAlignedStorage<N, T>`** — `std::aligned_storage` replacement that
  avoids pulling in `<memory>`.
- **`SkAnySubclass<Base, Bytes>`** — type-erased "any subclass of `Base` that
  fits in `Bytes`" used by `SkPath`-related allocations.
- **`SkContainers.h`** — see [Bulk-byte allocator](#bulk-byte-allocator).
- **`SkDeque.h`** — block-linked deque used by `SkCanvas`'s save stack.

`src/base/`:

- **`SkTBlockList<T, N>`** — append-only list backed by `SkBlockAllocator`.
- **`SkTLazy<T>`** — single-slot optional with placement-new.
- **`SkTSearch.h`**, **`SkTSort.h`** — typed binary search and sort helpers.
- **`SkTInternalLList.h`** — intrusive doubly-linked list.
- **`SkTDPQueue.h`** — typed indexed priority queue.
- **`SkRectMemcpy.h`** — strided 2-D memcpy used by pixel transfers.
- **`SkZip.h`** — zip-iterator helper.

---

## Memory porting layer

`include/private/base/SkMalloc.h`. Everything inside Skia that allocates raw
bytes goes through `sk_malloc_*` / `sk_free`, so embedders can intercept all
allocations by replacing the implementation in `src/base/SkMalloc.cpp` (or a
platform-specific override). The flags are:

| Flag | Effect |
|---|---|
| `SK_MALLOC_ZERO_INITIALIZE` | Zero-fill on success. |
| `SK_MALLOC_THROW` | On failure, do not return — call `sk_out_of_memory()`. |

Public helpers wrap the flag combinations: `sk_malloc_throw(size)`,
`sk_calloc_throw(size)`, `sk_malloc_canfail(size)`, `sk_calloc_canfail(size)`,
plus the `(count, elemSize)` overloads that internally do
overflow-checked multiplication. `sk_realloc_throw` keeps the
"never returns null" contract; `sk_realloc_throw(buf, 0)` calls `sk_free`.

`sk_bzero`, `sk_careful_memcpy`, `sk_careful_memmove`, and `sk_careful_memcmp`
are zero-length-safe wrappers around the C library equivalents (mempcy with a
null pointer is undefined even when `len == 0`).

---

## Where this fits in the rest of Skia

Almost every other class in Skia builds on these foundations:

- [`SkPaint`, `SkColor4f`, `SkColorFilter`, `SkBlender`](paint-color-and-blending.md)
  use `sk_sp` for the effect pointers and `SkRefCnt`-derived bases.
- [`SkPath`, `SkMatrix`, `SkRect`, `SkRRect`](geometry-and-math.md) are pure
  value types but use `SkSpan` and `SkTo<T>` extensively for their public API.
- [`SkCanvas`, `SkPicture`, `SkPictureRecorder`](canvas-and-recording.md) use
  `SkArenaAlloc` for per-draw scratch and `SkDeque` for the save stack.
- [`SkColorSpace`](color-management.md) is `SkNVRefCnt`-final and uses
  `SkOnce` for lazy inverse-matrix computation.
- [`SkSurface`](surface-and-output.md) is `SkRefCnt`-derived and exposes
  `SkPixmap` views into its backing memory.
- [`SkBitmap` / `SkPixmap` / `SkImage`](bitmap-pixmap-image.md) are built on
  `SkData` for storage and `SkRefCnt`/`SkNVRefCnt` for sharing.
- [`SkTypeface`, `SkFontMgr`, `SkTextBlob`](text-and-fonts.md) use
  `SkWeakRefCnt` and `SkStream` for font asset loading.
