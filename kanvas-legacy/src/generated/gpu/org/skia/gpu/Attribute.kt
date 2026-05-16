package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class Attribute {
 * public:
 *     constexpr Attribute() = default;
 *     constexpr Attribute(const char* name,
 *                         VertexAttribType cpuType,
 *                         SkSLType gpuType)
 *             : fName(name), fCPUType(cpuType), fGPUType(gpuType) {
 *         SkASSERT(name && gpuType != SkSLType::kVoid);
 *     }
 *     constexpr Attribute(const Attribute&) = default;
 *
 *     Attribute& operator=(const Attribute&) = default;
 *
 *     constexpr bool isInitialized() const { return fGPUType != SkSLType::kVoid; }
 *
 *     constexpr const char*      name()    const { return fName; }
 *     constexpr VertexAttribType cpuType() const { return fCPUType; }
 *     constexpr SkSLType         gpuType() const { return fGPUType; }
 *
 *     constexpr size_t size()       const { return VertexAttribTypeSize(fCPUType); }
 *     constexpr size_t sizeAlign4() const { return SkAlign4(this->size()); }
 *
 * private:
 *     const char* fName = nullptr;
 *     VertexAttribType fCPUType = VertexAttribType::kFloat;
 *     SkSLType fGPUType = SkSLType::kVoid;
 * }
 * ```
 */
public data class Attribute public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* fName = nullptr
   * ```
   */
  private val fName: String?,
  /**
   * C++ original:
   * ```cpp
   * VertexAttribType fCPUType
   * ```
   */
  private var fCPUType: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSLType fGPUType
   * ```
   */
  private var fGPUType: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * Attribute& operator=(const Attribute&) = default
   * ```
   */
  public fun assign(param0: Attribute) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr bool isInitialized() const { return fGPUType != SkSLType::kVoid; }
   * ```
   */
  public fun isInitialized(): Boolean {
    TODO("Implement isInitialized")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr const char*      name()    const { return fName; }
   * ```
   */
  public fun name(): Char {
    TODO("Implement name")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr VertexAttribType cpuType() const { return fCPUType; }
   * ```
   */
  public fun cpuType(): Int {
    TODO("Implement cpuType")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkSLType         gpuType() const { return fGPUType; }
   * ```
   */
  public fun gpuType(): Int {
    TODO("Implement gpuType")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr size_t size()       const { return VertexAttribTypeSize(fCPUType); }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr size_t sizeAlign4() const { return SkAlign4(this->size()); }
   * ```
   */
  public fun sizeAlign4(): Int {
    TODO("Implement sizeAlign4")
  }
}
