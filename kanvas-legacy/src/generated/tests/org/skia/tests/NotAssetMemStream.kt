package org.skia.tests

import org.skia.foundation.SkStream

/**
 * C++ original:
 * ```cpp
 * class NotAssetMemStream : public SkStream {
 * public:
 *     NotAssetMemStream(sk_sp<SkData> data) : fStream(std::move(data)) {}
 *
 *     bool hasPosition() const override {
 *         return false;
 *     }
 *
 *     bool hasLength() const override {
 *         return false;
 *     }
 *
 *     size_t peek(void* buf, size_t bytes) const override {
 *         return fStream.peek(buf, bytes);
 *     }
 *     size_t read(void* buf, size_t bytes) override {
 *         return fStream.read(buf, bytes);
 *     }
 *     bool rewind() override {
 *         return fStream.rewind();
 *     }
 *     bool isAtEnd() const override {
 *         return fStream.isAtEnd();
 *     }
 * private:
 *     SkMemoryStream fStream;
 * }
 * ```
 */
public open class NotAssetMemStream : SkStream()
