package org.skia.utils

import org.skia.foundation.SkSp

public typealias PromiseImageTextureFulfillProc = (PromiseImageTextureContext) -> SkSp<GrPromiseImageTexture>
