package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class SK_API ShaderErrorHandler {
 * public:
 *     virtual ~ShaderErrorHandler() = default;
 *
 *     /**
 *      * compileError(shader, errors) is kept for backward compatibility with older clients.
 *      */
 *     virtual void compileError([[maybe_unused]] const char* shader,
 *                               [[maybe_unused]] const char* errors) {}
 *     virtual void compileError(const char* shader,
 *                               const char* errors,
 *                               [[maybe_unused]] bool shaderWasCached) {
 *         // Default implementation. Ignore shaderWasCached.
 *         this->compileError(shader, errors);
 *     }
 *
 * protected:
 *     ShaderErrorHandler() = default;
 *     ShaderErrorHandler(const ShaderErrorHandler&) = delete;
 *     ShaderErrorHandler& operator=(const ShaderErrorHandler&) = delete;
 * }
 * ```
 */
public open class ShaderErrorHandler public constructor() {
  /**
   * C++ original:
   * ```cpp
   * ShaderErrorHandler() = default
   * ```
   */
  public constructor(param0: ShaderErrorHandler) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void compileError([[maybe_unused]] const char* shader,
   *                               [[maybe_unused]] const char* errors) {}
   * ```
   */
  public open fun compileError(shader: Any?, errors: Any?) {
    TODO("Implement compileError")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void compileError(const char* shader,
   *                               const char* errors,
   *                               [[maybe_unused]] bool shaderWasCached) {
   *         // Default implementation. Ignore shaderWasCached.
   *         this->compileError(shader, errors);
   *     }
   * ```
   */
  public open fun compileError(
    shader: String?,
    errors: String?,
    shaderWasCached: Boolean,
  ) {
    TODO("Implement compileError")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderErrorHandler& operator=(const ShaderErrorHandler&) = delete
   * ```
   */
  protected fun assign(param0: ShaderErrorHandler) {
    TODO("Implement assign")
  }
}
