package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.THashMap
import org.skia.foundation.SkColorType
import org.skia.gpu.ganesh.SkAlphaType

/**
 * C++ original:
 * ```cpp
 * class ExtendedOptions {
 * public:
 *     ExtendedOptions(const SkString& optionsString, bool* outParseSucceeded) {
 *         TArray<SkString> optionParts;
 *         SkStrSplit(optionsString.c_str(), ",", kStrict_SkStrSplitMode, &optionParts);
 *         for (int i = 0; i < optionParts.size(); ++i) {
 *             TArray<SkString> keyValueParts;
 *             SkStrSplit(optionParts[i].c_str(), "=", kStrict_SkStrSplitMode, &keyValueParts);
 *             if (keyValueParts.size() != 2) {
 *                 *outParseSucceeded = false;
 *                 return;
 *             }
 *             const SkString& key   = keyValueParts[0];
 *             const SkString& value = keyValueParts[1];
 *             if (fOptionsMap.find(key) == nullptr) {
 *                 fOptionsMap.set(key, value);
 *             } else {
 *                 // Duplicate values are not allowed.
 *                 *outParseSucceeded = false;
 *                 return;
 *             }
 *         }
 *         *outParseSucceeded = true;
 *     }
 *
 *     bool get_option_gpu_color(const char*  optionKey,
 *                               SkColorType* outColorType,
 *                               SkAlphaType* alphaType,
 *                               bool         optional = true) const {
 *         SkString* optionValue = fOptionsMap.find(SkString(optionKey));
 *         if (optionValue == nullptr) {
 *             return optional;
 *         }
 *         return parse_option_gpu_color(*optionValue, outColorType, alphaType);
 *     }
 *
 * #if defined(SK_GANESH)
 *     bool get_option_gpu_api(const char*                          optionKey,
 *                             SkCommandLineConfigGpu::ContextType* outContextType,
 *                             bool*                                outFakeGLESVersion2,
 *                             bool                                 optional = true) const {
 *         SkString* optionValue = fOptionsMap.find(SkString(optionKey));
 *         if (optionValue == nullptr) {
 *             return optional;
 *         }
 *         return parse_option_gpu_api(*optionValue, outContextType, outFakeGLESVersion2);
 *     }
 *
 *     bool get_option_gpu_surf_type(const char*                       optionKey,
 *                                   SkCommandLineConfigGpu::SurfType* outSurfType,
 *                                   bool                              optional = true) const {
 *         SkString* optionValue = fOptionsMap.find(SkString(optionKey));
 *         if (optionValue == nullptr) {
 *             return optional;
 *         }
 *         return parse_option_gpu_surf_type(*optionValue, outSurfType);
 *     }
 * #endif
 *
 * #if defined(SK_GRAPHITE)
 *     bool get_option_graphite_api(const char*                               optionKey,
 *                                  SkCommandLineConfigGraphite::ContextType* outContextType) const {
 *         SkString* optionValue = fOptionsMap.find(SkString(optionKey));
 *         if (optionValue == nullptr) {
 *             return false;
 *         }
 * #ifdef SK_DAWN
 *         if (optionValue->equals("dawn_d3d11")) {
 *             *outContextType = skgpu::ContextType::kDawn_D3D11;
 *             return true;
 *         }
 *         if (optionValue->equals("dawn_d3d12")) {
 *             *outContextType = skgpu::ContextType::kDawn_D3D12;
 *             return true;
 *         }
 *         if (optionValue->equals("dawn_mtl")) {
 *             *outContextType = skgpu::ContextType::kDawn_Metal;
 *             return true;
 *         }
 *         if (optionValue->equals("dawn_vk")) {
 *             *outContextType = skgpu::ContextType::kDawn_Vulkan;
 *             return true;
 *         }
 *         if (optionValue->equals("dawn_gl")) {
 *             *outContextType = skgpu::ContextType::kDawn_OpenGL;
 *             return true;
 *         }
 *         if (optionValue->equals("dawn_gles")) {
 *             *outContextType = skgpu::ContextType::kDawn_OpenGLES;
 *             return true;
 *         }
 * #endif
 * #ifdef SK_DIRECT3D
 *         if (optionValue->equals("direct3d")) {
 *             *outContextType = skgpu::ContextType::kDirect3D;
 *             return true;
 *         }
 * #endif
 * #ifdef SK_METAL
 *         if (optionValue->equals("metal")) {
 *             *outContextType = skgpu::ContextType::kMetal;
 *             return true;
 *         }
 * #endif
 * #ifdef SK_VULKAN
 *         if (optionValue->equals("vulkan")) {
 *             *outContextType = skgpu::ContextType::kVulkan;
 *             return true;
 *         }
 * #endif
 *
 *         return false;
 *     }
 * #endif
 *
 *     bool get_option_int(const char* optionKey, int* outInt, bool optional = true) const {
 *         SkString* optionValue = fOptionsMap.find(SkString(optionKey));
 *         if (optionValue == nullptr) {
 *             return optional;
 *         }
 *         return parse_option_int(*optionValue, outInt);
 *     }
 *
 *     bool get_option_bool(const char* optionKey, bool* outBool, bool optional = true) const {
 *         SkString* optionValue = fOptionsMap.find(SkString(optionKey));
 *         if (optionValue == nullptr) {
 *             return optional;
 *         }
 *         return parse_option_bool(*optionValue, outBool);
 *     }
 *
 * private:
 *     THashMap<SkString, SkString> fOptionsMap;
 * }
 * ```
 */
public data class ExtendedOptions public constructor(
  /**
   * C++ original:
   * ```cpp
   * THashMap<SkString, SkString> fOptionsMap
   * ```
   */
  private var fOptionsMap: THashMap<String, String>,
) {
  /**
   * C++ original:
   * ```cpp
   * bool get_option_gpu_color(const char*  optionKey,
   *                               SkColorType* outColorType,
   *                               SkAlphaType* alphaType,
   *                               bool         optional = true) const {
   *         SkString* optionValue = fOptionsMap.find(SkString(optionKey));
   *         if (optionValue == nullptr) {
   *             return optional;
   *         }
   *         return parse_option_gpu_color(*optionValue, outColorType, alphaType);
   *     }
   * ```
   */
  public fun getOptionGpuColor(
    optionKey: String?,
    outColorType: SkColorType?,
    alphaType: SkAlphaType?,
    optional: Boolean = TODO(),
  ): Boolean {
    TODO("Implement getOptionGpuColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool get_option_graphite_api(const char*                               optionKey,
   *                                  SkCommandLineConfigGraphite::ContextType* outContextType) const {
   *         SkString* optionValue = fOptionsMap.find(SkString(optionKey));
   *         if (optionValue == nullptr) {
   *             return false;
   *         }
   * #ifdef SK_DAWN
   *         if (optionValue->equals("dawn_d3d11")) {
   *             *outContextType = skgpu::ContextType::kDawn_D3D11;
   *             return true;
   *         }
   *         if (optionValue->equals("dawn_d3d12")) {
   *             *outContextType = skgpu::ContextType::kDawn_D3D12;
   *             return true;
   *         }
   *         if (optionValue->equals("dawn_mtl")) {
   *             *outContextType = skgpu::ContextType::kDawn_Metal;
   *             return true;
   *         }
   *         if (optionValue->equals("dawn_vk")) {
   *             *outContextType = skgpu::ContextType::kDawn_Vulkan;
   *             return true;
   *         }
   *         if (optionValue->equals("dawn_gl")) {
   *             *outContextType = skgpu::ContextType::kDawn_OpenGL;
   *             return true;
   *         }
   *         if (optionValue->equals("dawn_gles")) {
   *             *outContextType = skgpu::ContextType::kDawn_OpenGLES;
   *             return true;
   *         }
   * #endif
   * #ifdef SK_DIRECT3D
   *         if (optionValue->equals("direct3d")) {
   *             *outContextType = skgpu::ContextType::kDirect3D;
   *             return true;
   *         }
   * #endif
   * #ifdef SK_METAL
   *         if (optionValue->equals("metal")) {
   *             *outContextType = skgpu::ContextType::kMetal;
   *             return true;
   *         }
   * #endif
   * #ifdef SK_VULKAN
   *         if (optionValue->equals("vulkan")) {
   *             *outContextType = skgpu::ContextType::kVulkan;
   *             return true;
   *         }
   * #endif
   *
   *         return false;
   *     }
   * ```
   */
  public fun getOptionGraphiteApi(optionKey: String?, outContextType: SkCommandLineConfigGraphiteContextType?): Boolean {
    TODO("Implement getOptionGraphiteApi")
  }

  /**
   * C++ original:
   * ```cpp
   * bool get_option_int(const char* optionKey, int* outInt, bool optional = true) const {
   *         SkString* optionValue = fOptionsMap.find(SkString(optionKey));
   *         if (optionValue == nullptr) {
   *             return optional;
   *         }
   *         return parse_option_int(*optionValue, outInt);
   *     }
   * ```
   */
  public fun getOptionInt(
    optionKey: String?,
    outInt: Int?,
    optional: Boolean = TODO(),
  ): Boolean {
    TODO("Implement getOptionInt")
  }

  /**
   * C++ original:
   * ```cpp
   * bool get_option_bool(const char* optionKey, bool* outBool, bool optional = true) const {
   *         SkString* optionValue = fOptionsMap.find(SkString(optionKey));
   *         if (optionValue == nullptr) {
   *             return optional;
   *         }
   *         return parse_option_bool(*optionValue, outBool);
   *     }
   * ```
   */
  public fun getOptionBool(
    optionKey: String?,
    outBool: Boolean?,
    optional: Boolean = TODO(),
  ): Boolean {
    TODO("Implement getOptionBool")
  }
}
