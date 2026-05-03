package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * class MtlThreadSafeResourceProvider final : public ThreadSafeResourceProvider {
 * public:
 *     MtlThreadSafeResourceProvider(std::unique_ptr<ResourceProvider>);
 * }
 * ```
 */
public class MtlThreadSafeResourceProvider public constructor(
  resourceProvider: ResourceProvider?,
) : ThreadSafeResourceProvider(TODO())
