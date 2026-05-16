package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * class NonseekableStream : public NotAssetMemStream {
 * public:
 *     NonseekableStream(sk_sp<SkData> data) : INHERITED(std::move(data)) {}
 *
 *     bool rewind() override {
 *         return false;
 *     }
 *
 *     bool seek(size_t /* position */) override {
 *         return false;
 *     }
 * private:
 *     using INHERITED = NotAssetMemStream;
 * }
 * ```
 */
public open class NonseekableStream : NotAssetMemStream()
