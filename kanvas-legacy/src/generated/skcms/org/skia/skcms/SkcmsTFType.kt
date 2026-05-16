package org.skia.skcms

import kotlin.Any

public enum class SkcmsTFType {
  skcms_TFType_Invalid,
  skcms_TFType_sRGBish,
  skcms_TFType_PQish,
  skcms_TFType_HLGish,
  skcms_TFType_HLGinvish,
  skcms_TFType_PQ,
  skcms_TFType_HLG,
}

public typealias SkcmsTFType = Any
