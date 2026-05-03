package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.UInt
import kotlin.Unit
import kotlin.`string& label`

public typealias ContextOptionsPipelineCachingCallback = (
  Any,
  Any,
  `string& label`,
  UInt,
  Boolean,
  Any,
) -> Unit
