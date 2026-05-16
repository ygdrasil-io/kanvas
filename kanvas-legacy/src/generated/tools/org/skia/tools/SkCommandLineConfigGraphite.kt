package org.skia.tools

import kotlin.Boolean
import kotlin.String
import org.skia.core.TArray
import org.skia.foundation.SkColorType
import org.skia.gpu.ganesh.SkAlphaType

/**
 * C++ original:
 * ```cpp
 * class SkCommandLineConfigGraphite : public SkCommandLineConfig {
 * public:
 *     using ContextType = skgpu::ContextType;
 *
 *     SkCommandLineConfigGraphite(const SkString& tag,
 *                                 const skia_private::TArray<SkString>& viaParts,
 *                                 ContextType contextType,
 *                                 SkColorType colorType,
 *                                 SkAlphaType alphaType,
 *                                 bool testPersistentStorage,
 *                                 bool testPrecompileGraphite,
 *                                 bool testPipelineTracking)
 *             : SkCommandLineConfig(tag, SkString("graphite"), viaParts)
 *             , fContextType(contextType)
 *             , fColorType(colorType)
 *             , fAlphaType(alphaType)
 *             , fTestPersistentStorage(testPersistentStorage)
 *             , fTestPrecompileGraphite(testPrecompileGraphite)
 *             , fTestPipelineTracking(testPipelineTracking) {
 *     }
 *
 *     const SkCommandLineConfigGraphite* asConfigGraphite() const override { return this; }
 *
 *     ContextType getContextType() const { return fContextType; }
 *     SkColorType getColorType() const { return fColorType; }
 *     SkAlphaType getAlphaType() const { return fAlphaType; }
 *     bool        getTestPersistentStorage() const { return fTestPersistentStorage; }
 *     bool        getTestPrecompileGraphite() const { return fTestPrecompileGraphite; }
 *     bool        getTestPipelineTracking() const { return fTestPipelineTracking; }
 *
 * private:
 *     ContextType                     fContextType;
 *     SkColorType                     fColorType;
 *     SkAlphaType                     fAlphaType;
 *     bool                            fTestPersistentStorage;
 *     bool                            fTestPrecompileGraphite;
 *     bool                            fTestPipelineTracking;
 * }
 * ```
 */
public open class SkCommandLineConfigGraphite public constructor(
  tag: String,
  viaParts: TArray<String>,
  contextType: SkCommandLineConfigGraphiteContextType,
  colorType: SkColorType,
  alphaType: SkAlphaType,
  testPersistentStorage: Boolean,
  testPrecompileGraphite: Boolean,
  testPipelineTracking: Boolean,
) : SkCommandLineConfig(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * ContextType                     fContextType
   * ```
   */
  private var fContextType: SkCommandLineConfigGraphiteContextType = TODO("Initialize fContextType")

  /**
   * C++ original:
   * ```cpp
   * SkColorType                     fColorType
   * ```
   */
  private var fColorType: SkColorType = TODO("Initialize fColorType")

  /**
   * C++ original:
   * ```cpp
   * SkAlphaType                     fAlphaType
   * ```
   */
  private var fAlphaType: SkAlphaType = TODO("Initialize fAlphaType")

  /**
   * C++ original:
   * ```cpp
   * bool                            fTestPersistentStorage
   * ```
   */
  private var fTestPersistentStorage: Boolean = TODO("Initialize fTestPersistentStorage")

  /**
   * C++ original:
   * ```cpp
   * bool                            fTestPrecompileGraphite
   * ```
   */
  private var fTestPrecompileGraphite: Boolean = TODO("Initialize fTestPrecompileGraphite")

  /**
   * C++ original:
   * ```cpp
   * bool                            fTestPipelineTracking
   * ```
   */
  private var fTestPipelineTracking: Boolean = TODO("Initialize fTestPipelineTracking")

  /**
   * C++ original:
   * ```cpp
   * const SkCommandLineConfigGraphite* asConfigGraphite() const override { return this; }
   * ```
   */
  public override fun asConfigGraphite(): SkCommandLineConfigGraphite {
    TODO("Implement asConfigGraphite")
  }

  /**
   * C++ original:
   * ```cpp
   * ContextType getContextType() const { return fContextType; }
   * ```
   */
  public fun getContextType(): SkCommandLineConfigGraphiteContextType {
    TODO("Implement getContextType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorType getColorType() const { return fColorType; }
   * ```
   */
  public fun getColorType(): SkColorType {
    TODO("Implement getColorType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAlphaType getAlphaType() const { return fAlphaType; }
   * ```
   */
  public fun getAlphaType(): SkAlphaType {
    TODO("Implement getAlphaType")
  }

  /**
   * C++ original:
   * ```cpp
   * bool        getTestPersistentStorage() const { return fTestPersistentStorage; }
   * ```
   */
  public fun getTestPersistentStorage(): Boolean {
    TODO("Implement getTestPersistentStorage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool        getTestPrecompileGraphite() const { return fTestPrecompileGraphite; }
   * ```
   */
  public fun getTestPrecompileGraphite(): Boolean {
    TODO("Implement getTestPrecompileGraphite")
  }

  /**
   * C++ original:
   * ```cpp
   * bool        getTestPipelineTracking() const { return fTestPipelineTracking; }
   * ```
   */
  public fun getTestPipelineTracking(): Boolean {
    TODO("Implement getTestPipelineTracking")
  }
}
