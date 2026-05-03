package org.skia.tools

import kotlin.Int
import org.skia.gpu.vk.VulkanBackendContext
import org.skia.gpu.vk.VulkanExtensions
import sk_gpu_test.TestVkFeatures
import undefined.PFN_vkDestroyDebugUtilsMessengerEXT
import undefined.VkDebugUtilsMessengerEXT

/**
 * C++ original:
 * ```cpp
 * class VulkanTestContext : public GraphiteTestContext {
 * public:
 *     ~VulkanTestContext() override;
 *
 *     static std::unique_ptr<GraphiteTestContext> Make();
 *
 *     skgpu::BackendApi backend() override { return skgpu::BackendApi::kVulkan; }
 *
 *     skgpu::ContextType contextType() override;
 *
 *     std::unique_ptr<skgpu::graphite::Context> makeContext(const TestOptions&) override;
 *
 *     const skgpu::VulkanBackendContext& getBackendContext() const {
 *         return fVulkan;
 *     }
 *
 * private:
 *     VulkanTestContext(const skgpu::VulkanBackendContext& vulkan,
 *                       const skgpu::VulkanExtensions* extensions,
 *                       const sk_gpu_test::TestVkFeatures* features,
 *                       VkDebugUtilsMessengerEXT debugMessenger,
 *                       PFN_vkDestroyDebugUtilsMessengerEXT destroyCallback)
 *             : fVulkan(vulkan)
 *             , fExtensions(extensions)
 *             , fFeatures(features)
 *             , fDebugMessenger(debugMessenger)
 *             , fDestroyDebugUtilsMessengerEXT(destroyCallback) {}
 *
 *     skgpu::VulkanBackendContext fVulkan;
 *     const skgpu::VulkanExtensions* fExtensions;
 *     const sk_gpu_test::TestVkFeatures* fFeatures;
 *     VkDebugUtilsMessengerEXT fDebugMessenger = VK_NULL_HANDLE;
 *     PFN_vkDestroyDebugUtilsMessengerEXT fDestroyDebugUtilsMessengerEXT = nullptr;
 * }
 * ```
 */
public open class VulkanTestContext public constructor(
  vulkan: VulkanBackendContext,
  extensions: VulkanExtensions?,
  features: TestVkFeatures?,
  debugMessenger: VkDebugUtilsMessengerEXT,
  destroyCallback: PFN_vkDestroyDebugUtilsMessengerEXT,
) : GraphiteTestContext() {
  /**
   * C++ original:
   * ```cpp
   * skgpu::VulkanBackendContext fVulkan
   * ```
   */
  private var fVulkan: Int = TODO("Initialize fVulkan")

  /**
   * C++ original:
   * ```cpp
   * const skgpu::VulkanExtensions* fExtensions
   * ```
   */
  private val fExtensions: Int? = TODO("Initialize fExtensions")

  /**
   * C++ original:
   * ```cpp
   * const sk_gpu_test::TestVkFeatures* fFeatures
   * ```
   */
  private val fFeatures: Int? = TODO("Initialize fFeatures")

  /**
   * C++ original:
   * ```cpp
   * VkDebugUtilsMessengerEXT fDebugMessenger
   * ```
   */
  private var fDebugMessenger: Int = TODO("Initialize fDebugMessenger")

  /**
   * C++ original:
   * ```cpp
   * PFN_vkDestroyDebugUtilsMessengerEXT fDestroyDebugUtilsMessengerEXT
   * ```
   */
  private var fDestroyDebugUtilsMessengerEXT: Int =
      TODO("Initialize fDestroyDebugUtilsMessengerEXT")

  /**
   * C++ original:
   * ```cpp
   * skgpu::BackendApi backend() override { return skgpu::BackendApi::kVulkan; }
   * ```
   */
  public override fun backend(): Int {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::ContextType contextType() override
   * ```
   */
  public override fun contextType(): Int {
    TODO("Implement contextType")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<skgpu::graphite::Context> makeContext(const TestOptions&) override
   * ```
   */
  public override fun makeContext(param0: TestOptions): Int {
    TODO("Implement makeContext")
  }

  /**
   * C++ original:
   * ```cpp
   * const skgpu::VulkanBackendContext& getBackendContext() const {
   *         return fVulkan;
   *     }
   * ```
   */
  public fun getBackendContext(): Int {
    TODO("Implement getBackendContext")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static std::unique_ptr<GraphiteTestContext> Make()
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
