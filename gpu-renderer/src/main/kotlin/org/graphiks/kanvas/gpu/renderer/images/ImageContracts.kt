package org.graphiks.kanvas.gpu.renderer.images

/** Descriptor for an image source used by material or filter routes. */
class GPUImageSourceDescriptor

/** Full image pipeline plan from source to GPU-consumed texture. */
class GPUImagePipelinePlan

/** Encoded image byte source descriptor. */
class GPUEncodedImageSource

/** Registry of accepted Kanvas image codecs. */
class GPUImageCodecRegistry

/** Descriptor for one image codec implementation. */
class GPUImageCodecDescriptor

/** Decode request including frame, color, and orientation facts. */
class GPUImageDecodeRequest

/** Accepted or refused still-image decode plan. */
class GPUImageDecodePlan

/** Decode result descriptor before upload planning. */
class GPUImageDecodeResult

/** Animated image plan including frame dependency facts. */
class GPUAnimatedImagePlan

/** Metadata for one animated image frame. */
class GPUImageFrameInfo

/** Deterministic frame selection plan. */
class GPUImageFrameSelection

/** Color decode and conversion plan for image pixels. */
class GPUImageColorDecodePlan

/** Orientation correction plan for image pixels. */
class GPUImageOrientationPlan

/** Pixel layout and alpha plan for decoded image data. */
class GPUImagePixelPlan

/** Mipmap preparation and refusal plan. */
class GPUImageMipmapPlan

/** Upload plan for CPU-prepared image pixels. */
class GPUImageUploadPlan

/** Stable artifact key for uploaded image pixels. */
class GPUImageUploadArtifactKey

/** Typed CPU-prepared uploaded texture artifact. */
class UploadedTextureArtifact

/** Diagnostic emitted by image pipeline planning. */
class GPUImageDiagnostic
