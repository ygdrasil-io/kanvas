package org.skia.core

import kotlin.Boolean
import kotlin.Char
import kotlin.FloatArray
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkImageInfo
import org.skia.math.SkMatrix
import org.skia.memory.SkArenaAlloc
import org.skia.modules.SkcmsTransferFunction
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SkRasterPipeline {
 * public:
 *     explicit SkRasterPipeline(SkArenaAlloc*);
 *
 *     SkRasterPipeline(const SkRasterPipeline&) = delete;
 *     SkRasterPipeline(SkRasterPipeline&&)      = default;
 *
 *     SkRasterPipeline& operator=(const SkRasterPipeline&) = delete;
 *     SkRasterPipeline& operator=(SkRasterPipeline&&)      = default;
 *
 *     void reset();
 *
 *     void append(SkRasterPipelineOp, void* = nullptr);
 *     void append(SkRasterPipelineOp op, const void* ctx) { this->append(op,const_cast<void*>(ctx)); }
 *     void append(SkRasterPipelineOp, uintptr_t ctx);
 *
 *     // Append all stages to this pipeline.
 *     void extend(const SkRasterPipeline&);
 *
 *     // Runs the pipeline in 2d from (x,y) inclusive to (x+w,y+h) exclusive.
 *     void run(size_t x, size_t y, size_t w, size_t h) const;
 *
 *     // Allocates a thunk which amortizes run() setup cost in alloc.
 *     std::function<void(size_t, size_t, size_t, size_t)> compile() const;
 *
 *     // Callers can inspect the stage list for debugging purposes.
 *     struct StageList {
 *         StageList*          prev;
 *         SkRasterPipelineOp  stage;
 *         void*               ctx;
 *     };
 *
 *     static const char* GetOpName(SkRasterPipelineOp op);
 *     const StageList* getStageList() const { return fStages; }
 *     int getNumStages() const { return fNumStages; }
 *
 *     // Prints the entire StageList using SkDebugf.
 *     void dump() const;
 *
 *     // Appends a stage for the specified matrix.
 *     // Tries to optimize the stage by analyzing the type of matrix.
 *     void appendMatrix(SkArenaAlloc*, const SkMatrix&);
 *
 *     // Appends a stage for a constant uniform color.
 *     // Tries to optimize the stage based on the color.
 *     void appendConstantColor(SkArenaAlloc*, const float rgba[4]);
 *
 *     void appendConstantColor(SkArenaAlloc* alloc, const SkColor4f& color) {
 *         this->appendConstantColor(alloc, color.vec());
 *     }
 *
 *     // Like appendConstantColor() but only affecting r,g,b, ignoring the alpha channel.
 *     void appendSetRGB(SkArenaAlloc*, const float rgb[3]);
 *
 *     void appendSetRGB(SkArenaAlloc* alloc, const SkColor4f& color) {
 *         this->appendSetRGB(alloc, color.vec());
 *     }
 *
 *     void appendLoad(SkColorType, const SkRasterPipelineContexts::MemoryCtx*);
 *     void appendLoadDst(SkColorType, const SkRasterPipelineContexts::MemoryCtx*);
 *     void appendStore(SkColorType, const SkRasterPipelineContexts::MemoryCtx*);
 *
 *     void appendClampIfNormalized(const SkImageInfo&);
 *
 *     void appendTransferFunction(const skcms_TransferFunction&);
 *
 *     void appendStackRewind();
 *
 *     bool empty() const { return fStages == nullptr; }
 *
 * private:
 *     bool buildLowpPipeline(SkRasterPipelineStage* ip) const;
 *     void buildHighpPipeline(SkRasterPipelineStage* ip) const;
 *
 *     using StartPipelineFn = void (*)(size_t, size_t, size_t, size_t,
 *                                      SkRasterPipelineStage* program,
 *                                      SkSpan<SkRasterPipelineContexts::MemoryCtxPatch>,
 *                                      uint8_t*);
 *     StartPipelineFn buildPipeline(SkRasterPipelineStage*) const;
 *
 *     void uncheckedAppend(SkRasterPipelineOp, void*);
 *     int stagesNeeded() const;
 *
 *     void addMemoryContext(SkRasterPipelineContexts::MemoryCtx*,
 *                           int bytesPerPixel,
 *                           bool load,
 *                           bool store);
 *     uint8_t* tailPointer();
 *
 *     SkArenaAlloc*                        fAlloc;
 *     SkRasterPipelineContexts::RewindCtx* fRewindCtx;
 *     // A linked list of stages. fStages and getStageList() return the latest stage.
 *     StageList*                  fStages;
 *     uint8_t*                    fTailPointer;
 *     int                         fNumStages;
 *
 *     // Only 1 in 2 million CPU-backend pipelines used more than two MemoryCtxs.
 *     // (See the comment in SkRasterPipelineOpContexts.h for how MemoryCtx patching works)
 *     skia_private::STArray<2, SkRasterPipelineContexts::MemoryCtxInfo> fMemoryCtxInfos;
 * }
 * ```
 */
public open class SkRasterPipeline public constructor(
  alloc: SkArenaAlloc?,
) {
  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc*                        fAlloc
   * ```
   */
  private var fAlloc: SkArenaAlloc? = TODO("Initialize fAlloc")

  /**
   * C++ original:
   * ```cpp
   * SkRasterPipelineContexts::RewindCtx* fRewindCtx
   * ```
   */
  private var fRewindCtx: RewindCtx? = TODO("Initialize fRewindCtx")

  /**
   * C++ original:
   * ```cpp
   * StageList*                  fStages
   * ```
   */
  private var fStages: StageList? = TODO("Initialize fStages")

  /**
   * C++ original:
   * ```cpp
   * uint8_t*                    fTailPointer
   * ```
   */
  private var fTailPointer: Int? = TODO("Initialize fTailPointer")

  /**
   * C++ original:
   * ```cpp
   * int                         fNumStages
   * ```
   */
  private var fNumStages: Int = TODO("Initialize fNumStages")

  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<2, SkRasterPipelineContexts::MemoryCtxInfo> fMemoryCtxInfos
   * ```
   */
  private var fMemoryCtxInfos: Int = TODO("Initialize fMemoryCtxInfos")

  /**
   * C++ original:
   * ```cpp
   * SkRasterPipeline::SkRasterPipeline(SkArenaAlloc* alloc) : fAlloc(alloc) {
   *     this->reset();
   * }
   * ```
   */
  public constructor(param0: SkRasterPipeline) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRasterPipeline& operator=(const SkRasterPipeline&) = delete
   * ```
   */
  public fun assign(param0: SkRasterPipeline) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRasterPipeline& operator=(SkRasterPipeline&&)      = default
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::reset() {
   *     // We intentionally leave the alloc alone here; we don't own it.
   *     fRewindCtx   = nullptr;
   *     fStages      = nullptr;
   *     fTailPointer = nullptr;
   *     fNumStages   = 0;
   *     fMemoryCtxInfos.clear();
   * }
   * ```
   */
  public fun append(op: SkRasterPipelineOp, ctx: Unit? = TODO()) {
    TODO("Implement append")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::append(SkRasterPipelineOp op, void* ctx) {
   *     SkASSERT(op != Op::uniform_color);            // Please use appendConstantColor().
   *     SkASSERT(op != Op::unbounded_uniform_color);  // Please use appendConstantColor().
   *     SkASSERT(op != Op::set_rgb);                  // Please use appendSetRGB().
   *     SkASSERT(op != Op::unbounded_set_rgb);        // Please use appendSetRGB().
   *     SkASSERT(op != Op::parametric);               // Please use appendTransferFunction().
   *     SkASSERT(op != Op::gamma_);                   // Please use appendTransferFunction().
   *     SkASSERT(op != Op::PQish);                    // Please use appendTransferFunction().
   *     SkASSERT(op != Op::HLGish);                   // Please use appendTransferFunction().
   *     SkASSERT(op != Op::HLGinvish);                // Please use appendTransferFunction().
   *     SkASSERT(op != Op::stack_checkpoint);         // Please use appendStackRewind().
   *     SkASSERT(op != Op::stack_rewind);             // Please use appendStackRewind().
   *     this->uncheckedAppend(op, ctx);
   * }
   * ```
   */
  public fun append(param0: SkRasterPipelineOp, ctx: ULong) {
    TODO("Implement append")
  }

  /**
   * C++ original:
   * ```cpp
   * void append(SkRasterPipelineOp op, const void* ctx) { this->append(op,const_cast<void*>(ctx)); }
   * ```
   */
  public fun extend(src: SkRasterPipeline) {
    TODO("Implement extend")
  }

  /**
   * C++ original:
   * ```cpp
   * void append(SkRasterPipelineOp, uintptr_t ctx)
   * ```
   */
  public fun run(
    x: ULong,
    y: ULong,
    w: ULong,
    h: ULong,
  ) {
    TODO("Implement run")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::extend(const SkRasterPipeline& src) {
   *     if (src.empty()) {
   *         return;
   *     }
   *     // Create a rewind context if `src` has one already, but we don't. If we _do_ already have one,
   *     // we need to keep it, since we already have rewind ops that reference it. Either way, we need
   *     // to rewrite all the rewind ops to point to _our_ rewind context; we only get that checkpoint.
   *     if (src.fRewindCtx && !fRewindCtx) {
   *         fRewindCtx = fAlloc->make<SkRasterPipelineContexts::RewindCtx>();
   *     }
   *     auto stages = fAlloc->makeArrayDefault<StageList>(src.fNumStages);
   *
   *     int n = src.fNumStages;
   *     const StageList* st = src.fStages;
   *     while (n --> 1) {
   *         stages[n]      = *st;
   *         stages[n].prev = &stages[n-1];
   *
   *         // We make sure that all ops use _our_ stack context and tail pointer.
   *         switch (stages[n].stage) {
   *             case Op::stack_rewind: {
   *                 stages[n].ctx = fRewindCtx;
   *                 break;
   *             }
   *             case Op::init_lane_masks: {
   *                 auto* ctx = (SkRasterPipelineContexts::InitLaneMasksCtx*)stages[n].ctx;
   *                 ctx->tail = this->tailPointer();
   *                 break;
   *             }
   *             case Op::branch_if_all_lanes_active: {
   *                 auto* ctx = (SkRasterPipelineContexts::BranchIfAllLanesActiveCtx*)stages[n].ctx;
   *                 ctx->tail = this->tailPointer();
   *                 break;
   *             }
   *             default:
   *                 break;
   *         }
   *
   *         st = st->prev;
   *     }
   *     stages[0]      = *st;
   *     stages[0].prev = fStages;
   *
   *     fStages = &stages[src.fNumStages - 1];
   *     fNumStages += src.fNumStages;
   *     for (const SkRasterPipelineContexts::MemoryCtxInfo& info : src.fMemoryCtxInfos) {
   *         this->addMemoryContext(info.context, info.bytesPerPixel, info.load, info.store);
   *     }
   * }
   * ```
   */
  public fun compile(): Int {
    TODO("Implement compile")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::run(size_t x, size_t y, size_t w, size_t h) const {
   *     if (this->empty()) {
   *         return;
   *     }
   *
   *     int stagesNeeded = this->stagesNeeded();
   *
   *     // Best to not use fAlloc here... we can't bound how often run() will be called.
   *     AutoSTMalloc<32, SkRasterPipelineStage> program(stagesNeeded);
   *
   *     size_t numMemoryCtxs = fMemoryCtxInfos.size();
   *     AutoSTMalloc<2, SkRasterPipelineContexts::MemoryCtxPatch> patches(numMemoryCtxs);
   *     for (size_t i = 0; i < numMemoryCtxs; ++i) {
   *         patches[i].info = fMemoryCtxInfos[i];
   *         patches[i].backup = nullptr;
   *         memset(patches[i].scratch, 0, sizeof(patches[i].scratch));
   *     }
   *
   *     auto start_pipeline = this->buildPipeline(program.get() + stagesNeeded);
   *     start_pipeline(x, y, x + w, y + h, program.get(),
   *                    SkSpan{patches.data(), numMemoryCtxs},
   *                    fTailPointer);
   * }
   * ```
   */
  public fun getStageList(): StageList {
    TODO("Implement getStageList")
  }

  /**
   * C++ original:
   * ```cpp
   * std::function<void(size_t, size_t, size_t, size_t)> SkRasterPipeline::compile() const {
   *     if (this->empty()) {
   *         return [](size_t, size_t, size_t, size_t) {};
   *     }
   *
   *     int stagesNeeded = this->stagesNeeded();
   *
   *     SkRasterPipelineStage* program = fAlloc->makeArray<SkRasterPipelineStage>(stagesNeeded);
   *
   *     size_t numMemoryCtxs = fMemoryCtxInfos.size();
   *     SkRasterPipelineContexts::MemoryCtxPatch* patches =
   *             fAlloc->makeArray<SkRasterPipelineContexts::MemoryCtxPatch>(numMemoryCtxs);
   *     for (size_t i = 0; i < numMemoryCtxs; ++i) {
   *         patches[i].info = fMemoryCtxInfos[i];
   *         patches[i].backup = nullptr;
   *         memset(patches[i].scratch, 0, sizeof(patches[i].scratch));
   *     }
   *     uint8_t* tailPointer = fTailPointer;
   *
   *     auto start_pipeline = this->buildPipeline(program + stagesNeeded);
   *     return [=](size_t x, size_t y, size_t w, size_t h) {
   *         start_pipeline(x, y, x + w, y + h, program,
   *                        {patches, numMemoryCtxs},
   *                        tailPointer);
   *     };
   * }
   * ```
   */
  public fun getNumStages(): Int {
    TODO("Implement getNumStages")
  }

  /**
   * C++ original:
   * ```cpp
   * const StageList* getStageList() const { return fStages; }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * int getNumStages() const { return fNumStages; }
   * ```
   */
  public fun appendMatrix(alloc: SkArenaAlloc?, matrix: SkMatrix) {
    TODO("Implement appendMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::dump() const {
   *     SkDebugf("SkRasterPipeline, %d stages\n", fNumStages);
   *     std::vector<const char*> stages;
   *     for (auto st = fStages; st; st = st->prev) {
   *         stages.push_back(GetOpName(st->stage));
   *     }
   *     std::reverse(stages.begin(), stages.end());
   *     for (const char* name : stages) {
   *         SkDebugf("\t%s\n", name);
   *     }
   *     SkDebugf("\n");
   * }
   * ```
   */
  public fun appendConstantColor(alloc: SkArenaAlloc?, rgba: FloatArray) {
    TODO("Implement appendConstantColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::appendMatrix(SkArenaAlloc* alloc, const SkMatrix& matrix) {
   *     SkMatrix::TypeMask mt = matrix.getType();
   *
   *     if (mt == SkMatrix::kIdentity_Mask) {
   *         return;
   *     }
   *     if (mt == SkMatrix::kTranslate_Mask) {
   *         float* trans = alloc->makeArrayDefault<float>(2);
   *         trans[0] = matrix.getTranslateX();
   *         trans[1] = matrix.getTranslateY();
   *         this->append(Op::matrix_translate, trans);
   *     } else if ((mt | (SkMatrix::kScale_Mask | SkMatrix::kTranslate_Mask)) ==
   *                      (SkMatrix::kScale_Mask | SkMatrix::kTranslate_Mask)) {
   *         float* scaleTrans = alloc->makeArrayDefault<float>(4);
   *         scaleTrans[0] = matrix.getScaleX();
   *         scaleTrans[1] = matrix.getScaleY();
   *         scaleTrans[2] = matrix.getTranslateX();
   *         scaleTrans[3] = matrix.getTranslateY();
   *         this->append(Op::matrix_scale_translate, scaleTrans);
   *     } else {
   *         float* storage = alloc->makeArrayDefault<float>(9);
   *         matrix.get9(storage);
   *         if (!matrix.hasPerspective()) {
   *             // note: asAffine and the 2x3 stage really only need 6 entries
   *             this->append(Op::matrix_2x3, storage);
   *         } else {
   *             this->append(Op::matrix_perspective, storage);
   *         }
   *     }
   * }
   * ```
   */
  public fun appendConstantColor(alloc: SkArenaAlloc?, color: SkColor4f) {
    TODO("Implement appendConstantColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::appendConstantColor(SkArenaAlloc* alloc, const float rgba[4]) {
   *     // r,g,b might be outside [0,1], but alpha should probably always be in [0,1].
   *     SkASSERT(0 <= rgba[3] && rgba[3] <= 1);
   *
   *     if (rgba[0] == 0 && rgba[1] == 0 && rgba[2] == 0 && rgba[3] == 1) {
   *         this->append(Op::black_color);
   *     } else if (rgba[0] == 1 && rgba[1] == 1 && rgba[2] == 1 && rgba[3] == 1) {
   *         this->append(Op::white_color);
   *     } else {
   *         auto ctx = alloc->make<SkRasterPipelineContexts::UniformColorCtx>();
   *         skvx::float4 color = skvx::float4::Load(rgba);
   *         color.store(&ctx->r);
   *
   *         // uniform_color requires colors in range and can go lowp,
   *         // while unbounded_uniform_color supports out-of-range colors too but not lowp.
   *         if (0 <= rgba[0] && rgba[0] <= rgba[3] &&
   *             0 <= rgba[1] && rgba[1] <= rgba[3] &&
   *             0 <= rgba[2] && rgba[2] <= rgba[3]) {
   *             // To make loads more direct, we store 8-bit values in 16-bit slots.
   *             color = color * 255.0f + 0.5f;
   *             ctx->rgba[0] = (uint16_t)color[0];
   *             ctx->rgba[1] = (uint16_t)color[1];
   *             ctx->rgba[2] = (uint16_t)color[2];
   *             ctx->rgba[3] = (uint16_t)color[3];
   *             this->uncheckedAppend(Op::uniform_color, ctx);
   *         } else {
   *             this->uncheckedAppend(Op::unbounded_uniform_color, ctx);
   *         }
   *     }
   * }
   * ```
   */
  public fun appendSetRGB(alloc: SkArenaAlloc?, rgb: FloatArray) {
    TODO("Implement appendSetRGB")
  }

  /**
   * C++ original:
   * ```cpp
   * void appendConstantColor(SkArenaAlloc* alloc, const SkColor4f& color) {
   *         this->appendConstantColor(alloc, color.vec());
   *     }
   * ```
   */
  public fun appendSetRGB(alloc: SkArenaAlloc?, color: SkColor4f) {
    TODO("Implement appendSetRGB")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::appendSetRGB(SkArenaAlloc* alloc, const float rgb[3]) {
   *     auto arg = alloc->makeArrayDefault<float>(3);
   *     arg[0] = rgb[0];
   *     arg[1] = rgb[1];
   *     arg[2] = rgb[2];
   *
   *     auto op = Op::unbounded_set_rgb;
   *     if (0 <= rgb[0] && rgb[0] <= 1 &&
   *         0 <= rgb[1] && rgb[1] <= 1 &&
   *         0 <= rgb[2] && rgb[2] <= 1)
   *     {
   *         op = Op::set_rgb;
   *     }
   *
   *     this->uncheckedAppend(op, arg);
   * }
   * ```
   */
  public fun appendLoad(ct: SkColorType, ctx: MemoryCtx?) {
    TODO("Implement appendLoad")
  }

  /**
   * C++ original:
   * ```cpp
   * void appendSetRGB(SkArenaAlloc* alloc, const SkColor4f& color) {
   *         this->appendSetRGB(alloc, color.vec());
   *     }
   * ```
   */
  public fun appendLoadDst(ct: SkColorType, ctx: MemoryCtx?) {
    TODO("Implement appendLoadDst")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::appendLoad(SkColorType ct, const SkRasterPipelineContexts::MemoryCtx* ctx) {
   *     switch (ct) {
   *         case kUnknown_SkColorType: SkASSERT(false); break;
   *
   *         case kAlpha_8_SkColorType:           this->append(Op::load_a8,      ctx); break;
   *         case kA16_unorm_SkColorType:         this->append(Op::load_a16,     ctx); break;
   *         case kA16_float_SkColorType:         this->append(Op::load_af16,    ctx); break;
   *         case kRGB_565_SkColorType:           this->append(Op::load_565,     ctx); break;
   *         case kARGB_4444_SkColorType:         this->append(Op::load_4444,    ctx); break;
   *         case kR8G8_unorm_SkColorType:        this->append(Op::load_rg88,    ctx); break;
   *         case kR16_unorm_SkColorType:         this->append(Op::load_r16,     ctx); break;
   *         case kR16G16_unorm_SkColorType:      this->append(Op::load_rg1616,  ctx); break;
   *         case kR16G16_float_SkColorType:      this->append(Op::load_rgf16,   ctx); break;
   *         case kRGBA_8888_SkColorType:         this->append(Op::load_8888,    ctx); break;
   *         case kRGBA_1010102_SkColorType:      this->append(Op::load_1010102, ctx); break;
   *         case kR16G16B16A16_unorm_SkColorType:this->append(Op::load_16161616,ctx); break;
   *         case kRGBA_F16Norm_SkColorType:
   *         case kRGBA_F16_SkColorType:          this->append(Op::load_f16,     ctx); break;
   *         case kRGBA_F32_SkColorType:          this->append(Op::load_f32,     ctx); break;
   *         case kRGBA_10x6_SkColorType:         this->append(Op::load_10x6,    ctx); break;
   *
   *         case kGray_8_SkColorType:            this->append(Op::load_a8, ctx);
   *                                              this->append(Op::alpha_to_gray);
   *                                              break;
   *
   *         case kR8_unorm_SkColorType:          this->append(Op::load_a8, ctx);
   *                                              this->append(Op::alpha_to_red);
   *                                              break;
   *
   *         case kRGB_888x_SkColorType:          this->append(Op::load_8888, ctx);
   *                                              this->append(Op::force_opaque);
   *                                              break;
   *
   *         case kBGRA_1010102_SkColorType:      this->append(Op::load_1010102, ctx);
   *                                              this->append(Op::swap_rb);
   *                                              break;
   *
   *         case kRGB_101010x_SkColorType:       this->append(Op::load_1010102, ctx);
   *                                              this->append(Op::force_opaque);
   *                                              break;
   *
   *         case kBGR_101010x_SkColorType:       this->append(Op::load_1010102, ctx);
   *                                              this->append(Op::force_opaque);
   *                                              this->append(Op::swap_rb);
   *                                              break;
   *
   *         case kBGRA_10101010_XR_SkColorType:  this->append(Op::load_10101010_xr, ctx);
   *                                              this->append(Op::swap_rb);
   *                                              break;
   *
   *         case kBGR_101010x_XR_SkColorType:    this->append(Op::load_1010102_xr, ctx);
   *                                              this->append(Op::force_opaque);
   *                                              this->append(Op::swap_rb);
   *                                              break;
   *         case kRGB_F16F16F16x_SkColorType:    this->append(Op::load_f16, ctx);
   *                                              this->append(Op::force_opaque);
   *                                              break;
   *
   *         case kBGRA_8888_SkColorType:         this->append(Op::load_8888, ctx);
   *                                              this->append(Op::swap_rb);
   *                                              break;
   *
   *         case kSRGBA_8888_SkColorType:
   *             this->append(Op::load_8888, ctx);
   *             this->appendTransferFunction(*skcms_sRGB_TransferFunction());
   *             break;
   *     }
   * }
   * ```
   */
  public fun appendStore(ct: SkColorType, ctx: MemoryCtx?) {
    TODO("Implement appendStore")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::appendLoadDst(SkColorType ct,
   *                                      const SkRasterPipelineContexts::MemoryCtx* ctx) {
   *     switch (ct) {
   *         case kUnknown_SkColorType: SkASSERT(false); break;
   *
   *         case kAlpha_8_SkColorType:            this->append(Op::load_a8_dst,      ctx); break;
   *         case kA16_unorm_SkColorType:          this->append(Op::load_a16_dst,     ctx); break;
   *         case kA16_float_SkColorType:          this->append(Op::load_af16_dst,    ctx); break;
   *         case kRGB_565_SkColorType:            this->append(Op::load_565_dst,     ctx); break;
   *         case kARGB_4444_SkColorType:          this->append(Op::load_4444_dst,    ctx); break;
   *         case kR8G8_unorm_SkColorType:         this->append(Op::load_rg88_dst,    ctx); break;
   *         case kR16_unorm_SkColorType:          this->append(Op::load_r16_dst,     ctx); break;
   *         case kR16G16_unorm_SkColorType:       this->append(Op::load_rg1616_dst,  ctx); break;
   *         case kR16G16_float_SkColorType:       this->append(Op::load_rgf16_dst,   ctx); break;
   *         case kRGBA_8888_SkColorType:          this->append(Op::load_8888_dst,    ctx); break;
   *         case kRGBA_1010102_SkColorType:       this->append(Op::load_1010102_dst, ctx); break;
   *         case kR16G16B16A16_unorm_SkColorType: this->append(Op::load_16161616_dst,ctx); break;
   *         case kRGBA_F16Norm_SkColorType:
   *         case kRGBA_F16_SkColorType:           this->append(Op::load_f16_dst,     ctx); break;
   *         case kRGBA_F32_SkColorType:           this->append(Op::load_f32_dst,     ctx); break;
   *         case kRGBA_10x6_SkColorType:          this->append(Op::load_10x6_dst,    ctx); break;
   *
   *         case kGray_8_SkColorType:             this->append(Op::load_a8_dst, ctx);
   *                                               this->append(Op::alpha_to_gray_dst);
   *                                               break;
   *
   *         case kR8_unorm_SkColorType:           this->append(Op::load_a8_dst, ctx);
   *                                               this->append(Op::alpha_to_red_dst);
   *                                               break;
   *
   *         case kRGB_888x_SkColorType:           this->append(Op::load_8888_dst, ctx);
   *                                               this->append(Op::force_opaque_dst);
   *                                               break;
   *
   *         case kBGRA_1010102_SkColorType:       this->append(Op::load_1010102_dst, ctx);
   *                                               this->append(Op::swap_rb_dst);
   *                                               break;
   *
   *         case kRGB_101010x_SkColorType:        this->append(Op::load_1010102_dst, ctx);
   *                                               this->append(Op::force_opaque_dst);
   *                                               break;
   *
   *         case kBGR_101010x_SkColorType:        this->append(Op::load_1010102_dst, ctx);
   *                                               this->append(Op::force_opaque_dst);
   *                                               this->append(Op::swap_rb_dst);
   *                                               break;
   *
   *         case kBGR_101010x_XR_SkColorType:     this->append(Op::load_1010102_xr_dst, ctx);
   *                                               this->append(Op::force_opaque_dst);
   *                                               this->append(Op::swap_rb_dst);
   *                                               break;
   *
   *         case kBGRA_10101010_XR_SkColorType:   this->append(Op::load_10101010_xr_dst, ctx);
   *                                               this->append(Op::swap_rb_dst);
   *                                               break;
   *         case kRGB_F16F16F16x_SkColorType:     this->append(Op::load_f16_dst, ctx);
   *                                               this->append(Op::force_opaque_dst);
   *                                               break;
   *
   *         case kBGRA_8888_SkColorType:          this->append(Op::load_8888_dst, ctx);
   *                                               this->append(Op::swap_rb_dst);
   *                                               break;
   *
   *         case kSRGBA_8888_SkColorType:
   *             // TODO: We could remove the double-swap if we had _dst versions of all the TF stages
   *             this->append(Op::load_8888_dst, ctx);
   *             this->append(Op::swap_src_dst);
   *             this->appendTransferFunction(*skcms_sRGB_TransferFunction());
   *             this->append(Op::swap_src_dst);
   *             break;
   *     }
   * }
   * ```
   */
  public fun appendClampIfNormalized(info: SkImageInfo) {
    TODO("Implement appendClampIfNormalized")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::appendStore(SkColorType ct, const SkRasterPipelineContexts::MemoryCtx* ctx) {
   *     switch (ct) {
   *         case kUnknown_SkColorType: SkASSERT(false); break;
   *
   *         case kAlpha_8_SkColorType:            this->append(Op::store_a8,      ctx); break;
   *         case kR8_unorm_SkColorType:           this->append(Op::store_r8,      ctx); break;
   *         case kA16_unorm_SkColorType:          this->append(Op::store_a16,     ctx); break;
   *         case kA16_float_SkColorType:          this->append(Op::store_af16,    ctx); break;
   *         case kRGB_565_SkColorType:            this->append(Op::store_565,     ctx); break;
   *         case kARGB_4444_SkColorType:          this->append(Op::store_4444,    ctx); break;
   *         case kR8G8_unorm_SkColorType:         this->append(Op::store_rg88,    ctx); break;
   *         case kR16_unorm_SkColorType:          this->append(Op::store_r16,     ctx); break;
   *         case kR16G16_unorm_SkColorType:       this->append(Op::store_rg1616,  ctx); break;
   *         case kR16G16_float_SkColorType:       this->append(Op::store_rgf16,   ctx); break;
   *         case kRGBA_8888_SkColorType:          this->append(Op::store_8888,    ctx); break;
   *         case kRGBA_1010102_SkColorType:       this->append(Op::store_1010102, ctx); break;
   *         case kR16G16B16A16_unorm_SkColorType: this->append(Op::store_16161616,ctx); break;
   *         case kRGBA_F16Norm_SkColorType:
   *         case kRGBA_F16_SkColorType:           this->append(Op::store_f16,     ctx); break;
   *         case kRGBA_F32_SkColorType:           this->append(Op::store_f32,     ctx); break;
   *         case kRGBA_10x6_SkColorType:          this->append(Op::store_10x6,    ctx); break;
   *
   *         case kRGB_888x_SkColorType:           this->append(Op::force_opaque);
   *                                               this->append(Op::store_8888, ctx);
   *                                               break;
   *
   *         case kBGRA_1010102_SkColorType:       this->append(Op::swap_rb);
   *                                               this->append(Op::store_1010102, ctx);
   *                                               break;
   *
   *         case kRGB_101010x_SkColorType:        this->append(Op::force_opaque);
   *                                               this->append(Op::store_1010102, ctx);
   *                                               break;
   *
   *         case kBGR_101010x_SkColorType:        this->append(Op::force_opaque);
   *                                               this->append(Op::swap_rb);
   *                                               this->append(Op::store_1010102, ctx);
   *                                               break;
   *
   *         case kBGR_101010x_XR_SkColorType:     this->append(Op::force_opaque);
   *                                               this->append(Op::swap_rb);
   *                                               this->append(Op::store_1010102_xr, ctx);
   *                                               break;
   *         case kRGB_F16F16F16x_SkColorType:     this->append(Op::force_opaque);
   *                                               this->append(Op::store_f16, ctx);
   *                                               break;
   *
   *         case kBGRA_10101010_XR_SkColorType:   this->append(Op::swap_rb);
   *                                               this->append(Op::store_10101010_xr, ctx);
   *                                               break;
   *
   *         case kGray_8_SkColorType:             this->append(Op::bt709_luminance_or_luma_to_alpha);
   *                                               this->append(Op::store_a8, ctx);
   *                                               break;
   *
   *         case kBGRA_8888_SkColorType:          this->append(Op::swap_rb);
   *                                               this->append(Op::store_8888, ctx);
   *                                               break;
   *
   *         case kSRGBA_8888_SkColorType:
   *             this->appendTransferFunction(*skcms_sRGB_Inverse_TransferFunction());
   *             this->append(Op::store_8888, ctx);
   *             break;
   *     }
   * }
   * ```
   */
  public fun appendTransferFunction(tf: SkcmsTransferFunction) {
    TODO("Implement appendTransferFunction")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::appendClampIfNormalized(const SkImageInfo& info) {
   *     if (SkColorTypeIsNormalized(info.colorType())) {
   *         this->uncheckedAppend(Op::clamp_01, nullptr);
   *     }
   * }
   * ```
   */
  public fun appendStackRewind() {
    TODO("Implement appendStackRewind")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::appendTransferFunction(const skcms_TransferFunction& tf) {
   *     void* ctx = const_cast<void*>(static_cast<const void*>(&tf));
   *     switch (skcms_TransferFunction_getType(&tf)) {
   *         default: SkASSERT(false); break;
   *
   *         case skcms_TFType_sRGBish:
   *             if (tf.a == 1 && tf.b == 0 && tf.c == 0 && tf.d == 0 && tf.e == 0 && tf.f == 0) {
   *                 this->uncheckedAppend(Op::gamma_, ctx);
   *             } else {
   *                 this->uncheckedAppend(Op::parametric, ctx);
   *             }
   *             break;
   *         case skcms_TFType_PQish:     this->uncheckedAppend(Op::PQish,     ctx); break;
   *         case skcms_TFType_HLGish:    this->uncheckedAppend(Op::HLGish,    ctx); break;
   *         case skcms_TFType_HLGinvish: this->uncheckedAppend(Op::HLGinvish, ctx); break;
   *     }
   * }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::appendStackRewind() {
   *     if (!fRewindCtx) {
   *         fRewindCtx = fAlloc->make<SkRasterPipelineContexts::RewindCtx>();
   *     }
   *     this->uncheckedAppend(Op::stack_rewind, fRewindCtx);
   * }
   * ```
   */
  private fun buildLowpPipeline(ip: SkRasterPipelineStage?): Boolean {
    TODO("Implement buildLowpPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const { return fStages == nullptr; }
   * ```
   */
  private fun buildHighpPipeline(ip: SkRasterPipelineStage?) {
    TODO("Implement buildHighpPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRasterPipeline::buildLowpPipeline(SkRasterPipelineStage* ip) const {
   *     if (gForceHighPrecisionRasterPipeline || fRewindCtx) {
   *         return false;
   *     }
   *     // Stages are stored backwards in fStages; to compensate, we assemble the pipeline in reverse
   *     // here, back to front.
   *     prepend_to_pipeline(ip, SkOpts::just_return_lowp, /*ctx=*/nullptr);
   *     for (const StageList* st = fStages; st; st = st->prev) {
   *         int opIndex = (int)st->stage;
   *         if (opIndex >= kNumRasterPipelineLowpOps || !SkOpts::ops_lowp[opIndex]) {
   *             // This program contains a stage that doesn't exist in lowp.
   *             return false;
   *         }
   *         prepend_to_pipeline(ip, SkOpts::ops_lowp[opIndex], st->ctx);
   *     }
   *     return true;
   * }
   * ```
   */
  private fun buildPipeline(ip: SkRasterPipelineStage?): SkRasterPipelineStartPipelineFn {
    TODO("Implement buildPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::buildHighpPipeline(SkRasterPipelineStage* ip) const {
   *     // We assemble the pipeline in reverse, since the stage list is stored backwards.
   *     prepend_to_pipeline(ip, SkOpts::just_return_highp, /*ctx=*/nullptr);
   *     for (const StageList* st = fStages; st; st = st->prev) {
   *         int opIndex = (int)st->stage;
   *         prepend_to_pipeline(ip, SkOpts::ops_highp[opIndex], st->ctx);
   *     }
   *
   *     // stack_checkpoint and stack_rewind are only implemented in highp. We only need these stages
   *     // when generating long (or looping) pipelines from SkSL. The other stages used by the SkSL
   *     // Raster Pipeline generator will only have highp implementations, because we can't execute SkSL
   *     // code without floating point.
   *     if (fRewindCtx) {
   *         const int rewindIndex = (int)Op::stack_checkpoint;
   *         prepend_to_pipeline(ip, SkOpts::ops_highp[rewindIndex], fRewindCtx);
   *     }
   * }
   * ```
   */
  private fun uncheckedAppend(op: SkRasterPipelineOp, ctx: Unit?) {
    TODO("Implement uncheckedAppend")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRasterPipeline::StartPipelineFn SkRasterPipeline::buildPipeline(SkRasterPipelineStage* ip) const {
   *     // We try to build a lowp pipeline first; if that fails, we fall back to a highp float pipeline.
   *     if (this->buildLowpPipeline(ip)) {
   *         return SkOpts::start_pipeline_lowp;
   *     }
   *
   *     this->buildHighpPipeline(ip);
   *     return SkOpts::start_pipeline_highp;
   * }
   * ```
   */
  private fun stagesNeeded(): Int {
    TODO("Implement stagesNeeded")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterPipeline::uncheckedAppend(SkRasterPipelineOp op, void* ctx) {
   *     bool isLoad = false, isStore = false;
   *     SkColorType ct = kUnknown_SkColorType;
   *
   * #define COLOR_TYPE_CASE(stage_ct, sk_ct) \
   *     case Op::load_##stage_ct:            \
   *     case Op::load_##stage_ct##_dst:      \
   *         ct = sk_ct;                      \
   *         isLoad = true;                   \
   *         break;                           \
   *     case Op::store_##stage_ct:           \
   *         ct = sk_ct;                      \
   *         isStore = true;                  \
   *         break;
   *
   *     switch (op) {
   *         COLOR_TYPE_CASE(a8, kAlpha_8_SkColorType)
   *         COLOR_TYPE_CASE(565, kRGB_565_SkColorType)
   *         COLOR_TYPE_CASE(4444, kARGB_4444_SkColorType)
   *         COLOR_TYPE_CASE(8888, kRGBA_8888_SkColorType)
   *         COLOR_TYPE_CASE(rg88, kR8G8_unorm_SkColorType)
   *         COLOR_TYPE_CASE(16161616, kR16G16B16A16_unorm_SkColorType)
   *         COLOR_TYPE_CASE(a16, kA16_unorm_SkColorType)
   *         COLOR_TYPE_CASE(r16, kR16_unorm_SkColorType)
   *         COLOR_TYPE_CASE(rg1616, kR16G16_unorm_SkColorType)
   *         COLOR_TYPE_CASE(f16, kRGBA_F16_SkColorType)
   *         COLOR_TYPE_CASE(af16, kA16_float_SkColorType)
   *         COLOR_TYPE_CASE(rgf16, kR16G16_float_SkColorType)
   *         COLOR_TYPE_CASE(f32, kRGBA_F32_SkColorType)
   *         COLOR_TYPE_CASE(1010102, kRGBA_1010102_SkColorType)
   *         COLOR_TYPE_CASE(1010102_xr, kBGR_101010x_XR_SkColorType)
   *         COLOR_TYPE_CASE(10101010_xr, kBGRA_10101010_XR_SkColorType)
   *         COLOR_TYPE_CASE(10x6, kRGBA_10x6_SkColorType)
   *
   * #undef COLOR_TYPE_CASE
   *
   *         case Op::debug_r:
   *         case Op::debug_g:
   *         case Op::debug_b:
   *         case Op::debug_a:
   *         case Op::debug_r_255:
   *         case Op::debug_g_255:
   *         case Op::debug_b_255:
   *         case Op::debug_a_255:
   *         case Op::debug_x:
   *         case Op::debug_y: {
   *             ct = kRGBA_8888_SkColorType;
   *             isStore = true;
   *             break;
   *         }
   *         // Odd stage that doesn't have a load variant (appendLoad uses load_a8 + alpha_to_red)
   *         case Op::store_r8: {
   *             ct = kR8_unorm_SkColorType;
   *             isStore = true;
   *             break;
   *         }
   *         case Op::srcover_rgba_8888: {
   *             ct = kRGBA_8888_SkColorType;
   *             isLoad = true;
   *             isStore = true;
   *             break;
   *         }
   *         case Op::scale_u8:
   *         case Op::lerp_u8: {
   *             ct = kAlpha_8_SkColorType;
   *             isLoad = true;
   *             break;
   *         }
   *         case Op::scale_565:
   *         case Op::lerp_565: {
   *             ct = kRGB_565_SkColorType;
   *             isLoad = true;
   *             break;
   *         }
   *         case Op::emboss: {
   *             // Special-case, this op uses a context that holds *two* MemoryCtxs
   *             SkRasterPipelineContexts::EmbossCtx* embossCtx =
   *                     (SkRasterPipelineContexts::EmbossCtx*)ctx;
   *             this->addMemoryContext(&embossCtx->add,
   *                                    SkColorTypeBytesPerPixel(kAlpha_8_SkColorType),
   *                                    /*load=*/true, /*store=*/false);
   *             this->addMemoryContext(&embossCtx->mul,
   *                                    SkColorTypeBytesPerPixel(kAlpha_8_SkColorType),
   *                                    /*load=*/true, /*store=*/false);
   *             break;
   *         }
   *         case Op::init_lane_masks: {
   *             auto* initCtx = (SkRasterPipelineContexts::InitLaneMasksCtx*)ctx;
   *             initCtx->tail = this->tailPointer();
   *             break;
   *         }
   *         case Op::branch_if_all_lanes_active: {
   *             auto* branchCtx = (SkRasterPipelineContexts::BranchIfAllLanesActiveCtx*)ctx;
   *             branchCtx->tail = this->tailPointer();
   *             break;
   *         }
   *         default:
   *             break;
   *     }
   *
   *     fStages = fAlloc->make<StageList>(StageList{fStages, op, ctx});
   *     fNumStages += 1;
   *
   *     if (isLoad || isStore) {
   *         SkASSERT(ct != kUnknown_SkColorType);
   *         this->addMemoryContext((SkRasterPipelineContexts::MemoryCtx*)ctx,
   *                                SkColorTypeBytesPerPixel(ct),
   *                                isLoad,
   *                                isStore);
   *     }
   * }
   * ```
   */
  private fun addMemoryContext(
    ctx: MemoryCtx?,
    bytesPerPixel: Int,
    load: Boolean,
    store: Boolean,
  ) {
    TODO("Implement addMemoryContext")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkRasterPipeline::stagesNeeded() const {
   *     // Add 1 to budget for a `just_return` stage at the end.
   *     int stages = fNumStages + 1;
   *
   *     // If we have any stack_rewind stages, we will need to inject a stack_checkpoint stage.
   *     if (fRewindCtx) {
   *         stages += 1;
   *     }
   *     return stages;
   * }
   * ```
   */
  private fun tailPointer(): Int {
    TODO("Implement tailPointer")
  }

  public data class StageList public constructor(
    public var prev: StageList?,
    public var stage: SkRasterPipelineOp,
    public var ctx: Unit?,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * const char* SkRasterPipeline::GetOpName(SkRasterPipelineOp op) {
     *     const char* name = "";
     *     switch (op) {
     *     #define M(x) case Op::x: name = #x; break;
     *         SK_RASTER_PIPELINE_OPS_ALL(M)
     *     #undef M
     *     }
     *     return name;
     * }
     * ```
     */
    public fun getOpName(op: SkRasterPipelineOp): Char {
      TODO("Implement getOpName")
    }
  }
}
