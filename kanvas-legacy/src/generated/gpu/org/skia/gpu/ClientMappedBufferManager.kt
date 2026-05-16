package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * class ClientMappedBufferManager :
 *         public skgpu::TClientMappedBufferManager<Buffer, Context::ContextID> {
 * public:
 *     ClientMappedBufferManager(Context::ContextID ownerID)
 *             : TClientMappedBufferManager(ownerID) {}
 * }
 * ```
 */
public open class ClientMappedBufferManager public constructor(
  ownerID: Context.ContextID,
) : TClientMappedBufferManager(TODO()),
    Buffer,
    Context.ContextID
