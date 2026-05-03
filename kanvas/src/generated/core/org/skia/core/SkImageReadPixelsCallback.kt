package org.skia.core

import kotlin.Unit
import org.skia.gpu.ReadPixelsContext
import undefined.AsyncReadResult

public typealias SkImageReadPixelsCallback = (ReadPixelsContext, AsyncReadResult?) -> Unit
