package org.skia.gpu

import kotlin.Unit
import wgpu.`Instance& device`

public typealias DawnTickFunction = (`Instance& device`) -> Unit
