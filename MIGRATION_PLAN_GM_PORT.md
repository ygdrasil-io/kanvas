# Plan de portage des GM Skia → kanvas-skia

Statut du portage des Graphics Modules (GM) C++ de Skia vers `kanvas-skia/src/main/kotlin/org/skia/tests/`.

**Source de référence** : `/Users/chaos/workspace/kanvas-forge/skia-main/gm/` (437 fichiers `.cpp`)
**Cible** : `kanvas-skia/src/main/kotlin/org/skia/tests/*GM.kt` (236 GMs Kotlin sur `origin/master`, dont 206 rattachés à un `.cpp` et 30 sans correspondance — voir fin de document)
**Dernier fetch** : `origin/master` à `c7a5802` (PR #301, phase D2.6 — clôture D2)

## Résumé

| Statut | Fichiers `.cpp` | Pourcentage |
|---|---:|---:|
| Porté (≥ 1 GM mappé) | 150 | 34% |
| Non porté | 287 | 66% |
| **Total** | **437** | **100%** |

GMs Kotlin sans correspondance dans le tree de référence (probablement plus récents) : **30** — listés en fin de document.

### Nouveautés depuis le précédent snapshot

Phase D2 (PRs #284 → #301) a ajouté 8 nouveaux ports GM sur `origin/master` :
- `DestColorGM.kt` (← `destcolor.cpp`)
- `ImageDitherGM.kt` (← `imagedither.cpp`)
- `KawaseBlurRtGM.kt` (← `kawase_blur_rt.cpp`)
- `RuntimeIntrinsicsCommonGM.kt`, `RuntimeIntrinsicsExponentialGM.kt`, `RuntimeIntrinsicsGeometricGM.kt`, `RuntimeIntrinsicsMatrixGM.kt`, `RuntimeIntrinsicsRelationalGM.kt` (tous ← `runtimeintrinsics.cpp`, rejoignant `RuntimeIntrinsicsTrigGM.kt` déjà présent)

Net : 3 nouveaux `.cpp` couverts (`destcolor`, `imagedither`, `kawase_blur_rt`) → passage de 148 à **150 `.cpp` portés** sur 437.

## Méthodologie

Un fichier `.cpp` est marqué **porté** s'il existe au moins un fichier Kotlin `*GM.kt` dans `kanvas-skia/src/main/kotlin/org/skia/tests/` qui :
- correspond au nom du `.cpp` après normalisation (lowercase, suppression de `_` et `-`), **ou**
- contient le nom (ou la macro `DEF_SIMPLE_GM`/`DEF_GM`) du GM Kotlin à l'intérieur du `.cpp`.

Un même `.cpp` peut enregistrer plusieurs GMs : la colonne **GMs Kotlin** liste tous les ports rattachés.

⚠️ La présence d'un `*GM.kt` ne garantit pas la conformité visuelle au PNG de référence — voir `MIGRATION_PLAN.md` pour le suivi de qualité.

## Table de portage

| `.cpp` source | Porté | GMs Kotlin |
|---|:---:|---|
| `3d.cpp` | ❌ | — |
| `aaa.cpp` | ✅ | `AnalyticAntialiasConvexGM.kt`, `AnalyticAntialiasGeneralGM.kt`, `AnalyticAntialiasInverseGM.kt` |
| `aaclip.cpp` | ✅ | `AaclipGM.kt`, `ClipCubicGM.kt` |
| `aarecteffect.cpp` | ❌ | — |
| `aarectmodes.cpp` | ✅ | `AaRectModesGM.kt` |
| `aaxfermodes.cpp` | ✅ | `AAXfermodesGM.kt` |
| `addarc.cpp` | ✅ | `AddArcGM.kt`, `FillCircleGM.kt`, `StrokeCircleGM.kt` |
| `all_bitmap_configs.cpp` | ❌ | — |
| `alpha_image.cpp` | ❌ | — |
| `alphagradients.cpp` | ✅ | `AlphaGradientsGM.kt` |
| `analytic_gradients.cpp` | ✅ | `AnalyticGradientShaderGM.kt` |
| `androidblendmodes.cpp` | ✅ | `AndroidBlendModesGM.kt` |
| `animated_gif.cpp` | ❌ | — |
| `animated_image_orientation.cpp` | ❌ | — |
| `animatedimageblurs.cpp` | ❌ | — |
| `anisotropic.cpp` | ❌ | — |
| `annotated_text.cpp` | ✅ | `AnnotatedTextGM.kt` |
| `arcofzorro.cpp` | ✅ | `ArcOfZorroGM.kt` |
| `arcto.cpp` | ✅ | `ArcToGM.kt`, `Bug593049GM.kt` |
| `arithmode.cpp` | ✅ | `ArithmodeGM.kt`, `ArithmodeBlenderGM.kt` |
| `asyncrescaleandread.cpp` | ❌ | — |
| `attributes.cpp` | ❌ | — |
| `b_119394958.cpp` | ✅ | `B119394958GM.kt` |
| `backdrop.cpp` | ❌ | — |
| `backdrop_imagefilter_croprect.cpp` | ❌ | — |
| `badpaint.cpp` | ✅ | `BadPaintGM.kt` |
| `batchedconvexpaths.cpp` | ✅ | `BatchedConvexPathsGM.kt` |
| `bc1_transparency.cpp` | ❌ | — |
| `beziereffects.cpp` | ❌ | — |
| `beziers.cpp` | ✅ | `BeziersGM.kt` |
| `bicubic.cpp` | ❌ | — |
| `bigblurs.cpp` | ✅ | `BigBlursGM.kt` |
| `bigmatrix.cpp` | ✅ | `BigMatrixGM.kt` |
| `bigrect.cpp` | ✅ | `BigRectGM.kt` |
| `bigrrectaaeffect.cpp` | ❌ | — |
| `bigtext.cpp` | ✅ | `BigTextGM.kt` |
| `bigtileimagefilter.cpp` | ❌ | — |
| `bitmapcopy.cpp` | ❌ | — |
| `bitmapfilters.cpp` | ❌ | — |
| `bitmapimage.cpp` | ❌ | — |
| `bitmappremul.cpp` | ❌ | — |
| `bitmaprect.cpp` | ✅ | `BitmapRectRoundingGM.kt`, `DrawBitmapRect3GM.kt` |
| `bitmaprecttest.cpp` | ✅ | `BitmapRectTestGM.kt` |
| `bitmapshader.cpp` | ❌ | — |
| `bitmaptiled.cpp` | ❌ | — |
| `bleed.cpp` | ❌ | — |
| `blurcircles.cpp` | ✅ | `BlurCirclesGM.kt` |
| `blurcircles2.cpp` | ❌ | — |
| `blurignorexform.cpp` | ❌ | — |
| `blurimagevmask.cpp` | ❌ | — |
| `blurpositioning.cpp` | ❌ | — |
| `blurquickreject.cpp` | ✅ | `BlurQuickRejectGM.kt` |
| `blurrect.cpp` | ❌ | — |
| `blurredclippedcircle.cpp` | ✅ | `BlurredClippedCircleGM.kt` |
| `blurroundrect.cpp` | ✅ | `SimpleBlurRoundRectGM.kt` |
| `blurs.cpp` | ✅ | `Blur2RectsGM.kt`, `Blur2RectsNonNinepatchGM.kt` |
| `blurtextsmallradii.cpp` | ❌ | — |
| `bmpfilterqualityrepeat.cpp` | ❌ | — |
| `bug12866.cpp` | ✅ | `Bug12866GM.kt`, `Bug40810065GM.kt` |
| `bug5252.cpp` | ✅ | `Bug5252GM.kt` |
| `bug530095.cpp` | ✅ | `Bug530095GM.kt`, `Bug591993GM.kt` |
| `bug615686.cpp` | ✅ | `Bug615686GM.kt` |
| `bug6643.cpp` | ❌ | — |
| `bug6783.cpp` | ✅ | `Bug6783GM.kt` |
| `bug9331.cpp` | ✅ | `Bug9331GM.kt` |
| `circle_sizes.cpp` | ✅ | `CircleSizesGM.kt` |
| `circulararcs.cpp` | ✅ | `Bug406747427GM.kt`, `CircularArcsGM.kt`, `OneBadArcGM.kt` |
| `circularclips.cpp` | ✅ | `CircularClipsGM.kt` |
| `clear_swizzle.cpp` | ❌ | — |
| `clip_error.cpp` | ❌ | — |
| `clip_sierpinski_region.cpp` | ❌ | — |
| `clip_strokerect.cpp` | ✅ | `ClipStrokeRectGM.kt` |
| `clipdrawdraw.cpp` | ✅ | `ClipDrawDrawGM.kt` |
| `clippedbitmapshaders.cpp` | ❌ | — |
| `clipshader.cpp` | ❌ | — |
| `clockwise.cpp` | ❌ | — |
| `closedcappedhairlines.cpp` | ❌ | — |
| `collapsepaths.cpp` | ✅ | `CollapsePathsGM.kt` |
| `color4f.cpp` | ❌ | — |
| `coloremoji.cpp` | ❌ | — |
| `coloremoji_blendmodes.cpp` | ❌ | — |
| `colorfilteralpha8.cpp` | ❌ | — |
| `colorfilterimagefilter.cpp` | ❌ | — |
| `colorfilters.cpp` | ❌ | — |
| `colormatrix.cpp` | ✅ | `ColorMatrixGM.kt` |
| `colorspace.cpp` | ❌ | — |
| `colorwheel.cpp` | ✅ | `ColorWheelNativeGM.kt` |
| `colrv1.cpp` | ❌ | — |
| `complexclip.cpp` | ❌ | — |
| `complexclip2.cpp` | ❌ | — |
| `complexclip3.cpp` | ❌ | — |
| `complexclip4.cpp` | ❌ | — |
| `complexclip_blur_tiled.cpp` | ❌ | — |
| `composecolorfilter.cpp` | ✅ | `ComposeColorFilterGM.kt` |
| `composeshader.cpp` | ❌ | — |
| `compositor_quads.cpp` | ❌ | — |
| `compressed_textures.cpp` | ❌ | — |
| `concavepaths.cpp` | ✅ | `ConcavePathsGM.kt` |
| `conicpaths.cpp` | ✅ | `ArcCircleGapGM.kt`, `ConicPathsGM.kt`, `LargeCircleGM.kt`, `LargeOvalsGM.kt` |
| `constcolorprocessor.cpp` | ❌ | — |
| `convex_all_line_paths.cpp` | ✅ | `ConvexLineOnlyPathsGM.kt` |
| `convexpaths.cpp` | ✅ | `ConvexPathsGM.kt` |
| `convexpolyclip.cpp` | ❌ | — |
| `convexpolyeffect.cpp` | ❌ | — |
| `coordclampshader.cpp` | ❌ | — |
| `copy_to_4444.cpp` | ❌ | — |
| `crbug_1041204.cpp` | ❌ | — |
| `crbug_1073670.cpp` | ✅ | `Crbug1073670GM.kt` |
| `crbug_1086705.cpp` | ✅ | `Crbug1086705GM.kt` |
| `crbug_1113794.cpp` | ✅ | `Crbug1113794GM.kt` |
| `crbug_1139750.cpp` | ✅ | `Crbug1139750GM.kt` |
| `crbug_1156804.cpp` | ❌ | — |
| `crbug_1162942.cpp` | ❌ | — |
| `crbug_1167277.cpp` | ❌ | — |
| `crbug_1174186.cpp` | ❌ | — |
| `crbug_1174354.cpp` | ❌ | — |
| `crbug_1177833.cpp` | ❌ | — |
| `crbug_1257515.cpp` | ✅ | `Crbug1257515GM.kt` |
| `crbug_1313579.cpp` | ❌ | — |
| `crbug_224618.cpp` | ❌ | — |
| `crbug_478659067.cpp` | ❌ | — |
| `crbug_691386.cpp` | ❌ | — |
| `crbug_788500.cpp` | ✅ | `Crbug788500GM.kt` |
| `crbug_847759.cpp` | ✅ | `Crbug847759GM.kt` |
| `crbug_884166.cpp` | ✅ | `Crbug884166GM.kt` |
| `crbug_887103.cpp` | ✅ | `Crbug887103GM.kt` |
| `crbug_892988.cpp` | ✅ | `Crbug892988GM.kt` |
| `crbug_899512.cpp` | ✅ | `Crbug899512GM.kt` |
| `crbug_905548.cpp` | ❌ | — |
| `crbug_908646.cpp` | ✅ | `Crbug908646GM.kt` |
| `crbug_913349.cpp` | ✅ | `Crbug913349GM.kt` |
| `crbug_918512.cpp` | ❌ | — |
| `crbug_938592.cpp` | ✅ | `Crbug938592GM.kt` |
| `crbug_946965.cpp` | ✅ | `Crbug946965GM.kt` |
| `crbug_947055.cpp` | ✅ | `Crbug947055GM.kt` |
| `crbug_996140.cpp` | ✅ | `Crbug996140GM.kt` |
| `crop_imagefilter.cpp` | ❌ | — |
| `croppedrects.cpp` | ✅ | `CroppedRectsGM.kt` |
| `crosscontextimage.cpp` | ❌ | — |
| `cubicpaths.cpp` | ✅ | `Bug5099GM.kt`, `Bug6083GM.kt`, `ClippedCubicGM.kt`, `ClippedCubic2GM.kt`, `CubicClosePathGM.kt`, `CubicPathGM.kt`, `CubicPathShaderGM.kt` |
| `daa.cpp` | ❌ | — |
| `dashcircle.cpp` | ❌ | — |
| `dashcubics.cpp` | ❌ | — |
| `dashing.cpp` | ✅ | `DashingGM.kt`, `LongWavyLineGM.kt`, `PathEffectGM.kt` |
| `degeneratesegments.cpp` | ❌ | — |
| `destcolor.cpp` | ✅ | `DestColorGM.kt` |
| `dftext.cpp` | ❌ | — |
| `dftext_blob_persp.cpp` | ❌ | — |
| `discard.cpp` | ❌ | — |
| `displacement.cpp` | ❌ | — |
| `distantclip.cpp` | ✅ | `DistantClipGM.kt` |
| `draw_bitmap_rect_skbug4374.cpp` | ❌ | — |
| `drawable.cpp` | ❌ | — |
| `drawatlas.cpp` | ❌ | — |
| `drawatlascolor.cpp` | ❌ | — |
| `drawbitmaprect.cpp` | ❌ | — |
| `drawglyphs.cpp` | ❌ | — |
| `drawimageset.cpp` | ❌ | — |
| `drawlines_with_local_matrix.cpp` | ❌ | — |
| `drawminibitmaprect.cpp` | ❌ | — |
| `drawquadset.cpp` | ❌ | — |
| `drawregion.cpp` | ❌ | — |
| `drawregionmodes.cpp` | ❌ | — |
| `dropshadowimagefilter.cpp` | ✅ | `DropShadowImageFilterGM.kt` |
| `drrect.cpp` | ✅ | `DRRectGM.kt` |
| `drrect_small_inner.cpp` | ✅ | `DRRectSmallInnerGM.kt` |
| `dstreadshuffle.cpp` | ❌ | — |
| `ducky_yuv_blend.cpp` | ❌ | — |
| `emboss.cpp` | ❌ | — |
| `emptypath.cpp` | ✅ | `EmptyPathGM.kt` |
| `emptyshader.cpp` | ✅ | `EmptyShaderGM.kt` |
| `encode.cpp` | ❌ | — |
| `encode_alpha_jpeg.cpp` | ❌ | — |
| `encode_color_types.cpp` | ❌ | — |
| `encode_platform.cpp` | ❌ | — |
| `encode_srgb.cpp` | ❌ | — |
| `exoticformats.cpp` | ❌ | — |
| `fadefilter.cpp` | ✅ | `FadeFilterGM.kt` |
| `fatpathfill.cpp` | ✅ | `FatPathFillGM.kt` |
| `fiddle.cpp` | ✅ | `FiddleGM.kt` |
| `fillrect_gradient.cpp` | ✅ | `FillrectGradientGM.kt` |
| `filltypes.cpp` | ✅ | `FillTypeGM.kt`, `FillTypesGM.kt` |
| `filltypespersp.cpp` | ✅ | `FillTypePerspGM.kt` |
| `filterbug.cpp` | ❌ | — |
| `filterfastbounds.cpp` | ❌ | — |
| `filterindiabox.cpp` | ❌ | — |
| `flippity.cpp` | ❌ | — |
| `fontations.cpp` | ❌ | — |
| `fontations_ft_compare.cpp` | ❌ | — |
| `fontcache.cpp` | ❌ | — |
| `fontmgr.cpp` | ❌ | — |
| `fontregen.cpp` | ❌ | — |
| `fontscaler.cpp` | ❌ | — |
| `fontscalerdistortable.cpp` | ❌ | — |
| `fp_sample_chaining.cpp` | ❌ | — |
| `fpcoordinateoverride.cpp` | ❌ | — |
| `fwidth_squircle.cpp` | ❌ | — |
| `gammatext.cpp` | ❌ | — |
| `getpostextpath.cpp` | ❌ | — |
| `giantbitmap.cpp` | ❌ | — |
| `glyph_pos.cpp` | ❌ | — |
| `gm.cpp` | ❌ | — |
| `gpu_blur_utils.cpp` | ❌ | — |
| `gradient_dirty_laundry.cpp` | ❌ | — |
| `gradient_matrix.cpp` | ❌ | — |
| `gradients.cpp` | ✅ | `ClampedGradientsGM.kt`, `GradientsGM.kt`, `RgbwSweepGradientGM.kt`, `SmallColorStopGM.kt`, `SweepTilingGM.kt` |
| `gradients_2pt_conical.cpp` | ❌ | — |
| `gradients_degenerate.cpp` | ❌ | — |
| `gradients_no_texture.cpp` | ❌ | — |
| `gradtext.cpp` | ✅ | `ChromeGradTextGM.kt`, `GradTextGM.kt` |
| `graphite_replay.cpp` | ❌ | — |
| `graphitestart.cpp` | ❌ | — |
| `grayscalejpg.cpp` | ❌ | — |
| `hairlines.cpp` | ✅ | `HairlineSubdivGM.kt`, `HairlinesGM.kt`, `SquareHairGM.kt` |
| `hairmodes.cpp` | ✅ | `HairModesGM.kt` |
| `hardstop_gradients.cpp` | ✅ | `HardstopGradientShaderGM.kt` |
| `hardstop_gradients_many.cpp` | ✅ | `HardstopGradientsManyGM.kt` |
| `hdr_pip_blur.cpp` | ❌ | — |
| `hello_bazel_world.cpp` | ❌ | — |
| `highcontrastfilter.cpp` | ❌ | — |
| `hittestpath.cpp` | ❌ | — |
| `hsl.cpp` | ❌ | — |
| `hugepath.cpp` | ❌ | — |
| `image.cpp` | ❌ | — |
| `image_pict.cpp` | ❌ | — |
| `image_shader.cpp` | ❌ | — |
| `imageblur.cpp` | ❌ | — |
| `imageblur2.cpp` | ❌ | — |
| `imageblurclampmode.cpp` | ❌ | — |
| `imageblurrepeatmode.cpp` | ❌ | — |
| `imageblurtiled.cpp` | ❌ | — |
| `imagedither.cpp` | ✅ | `ImageDitherGM.kt` |
| `imagefilters.cpp` | ❌ | — |
| `imagefiltersbase.cpp` | ❌ | — |
| `imagefiltersclipped.cpp` | ❌ | — |
| `imagefilterscropexpand.cpp` | ❌ | — |
| `imagefilterscropped.cpp` | ❌ | — |
| `imagefiltersgraph.cpp` | ❌ | — |
| `imagefiltersscaled.cpp` | ❌ | — |
| `imagefiltersstroked.cpp` | ❌ | — |
| `imagefilterstransformed.cpp` | ❌ | — |
| `imagefiltersunpremul.cpp` | ❌ | — |
| `imagefromyuvtextures.cpp` | ❌ | — |
| `imagemagnifier.cpp` | ❌ | — |
| `imagemakewithfilter.cpp` | ❌ | — |
| `imagemasksubset.cpp` | ❌ | — |
| `imageresizetiled.cpp` | ❌ | — |
| `imagesource.cpp` | ❌ | — |
| `imagesource2.cpp` | ❌ | — |
| `internal_links.cpp` | ❌ | — |
| `inverseclip.cpp` | ✅ | `InverseClipGM.kt` |
| `inversepaths.cpp` | ✅ | `InversePathsGM.kt`, `InverseWindingmodeFiltersGM.kt` |
| `jpg_color_cube.cpp` | ❌ | — |
| `kawase_blur_rt.cpp` | ✅ | `KawaseBlurRtGM.kt` |
| `labyrinth.cpp` | ❌ | — |
| `largeclippedpath.cpp` | ❌ | — |
| `largeglyphblur.cpp` | ❌ | — |
| `lattice.cpp` | ❌ | — |
| `lazytiling.cpp` | ❌ | — |
| `lcdblendmodes.cpp` | ❌ | — |
| `lcdoverlap.cpp` | ❌ | — |
| `lcdtext.cpp` | ❌ | — |
| `lighting.cpp` | ✅ | `LightingGM.kt` |
| `linepaths.cpp` | ✅ | `LinePathGM.kt` |
| `localmatriximagefilter.cpp` | ❌ | — |
| `localmatriximageshader.cpp` | ❌ | — |
| `localmatrixshader.cpp` | ❌ | — |
| `lumafilter.cpp` | ✅ | `LumaFilterGM.kt` |
| `luminosity.cpp` | ✅ | `LuminosityOverflowGM.kt` |
| `mac_aa_explorer.cpp` | ❌ | — |
| `make_raster_image.cpp` | ❌ | — |
| `makecolorspace.cpp` | ❌ | — |
| `mandoline.cpp` | ❌ | — |
| `manypathatlases.cpp` | ❌ | — |
| `manypaths.cpp` | ✅ | `ManyCirclesGM.kt`, `ManyRRectsGM.kt` |
| `matrixconvolution.cpp` | ❌ | — |
| `matriximagefilter.cpp` | ✅ | `MatrixImageFilterGM.kt` |
| `mesh.cpp` | ❌ | — |
| `mipmap.cpp` | ❌ | — |
| `mirrortile.cpp` | ✅ | `MirrorTileGM.kt` |
| `mixedtextblobs.cpp` | ❌ | — |
| `mixercolorfilter.cpp` | ❌ | — |
| `modecolorfilters.cpp` | ❌ | — |
| `morphology.cpp` | ✅ | `MorphologyGM.kt` |
| `nearesthalfpixelimage.cpp` | ❌ | — |
| `nested.cpp` | ✅ | `NestedGM.kt` |
| `ninepatchstretch.cpp` | ❌ | — |
| `nonclosedpaths.cpp` | ✅ | `NonClosedPathsGM.kt` |
| `offsetimagefilter.cpp` | ❌ | — |
| `orientation.cpp` | ❌ | — |
| `ovals.cpp` | ✅ | `OvalGM.kt` |
| `overdrawcanvas.cpp` | ❌ | — |
| `overdrawcolorfilter.cpp` | ❌ | — |
| `overstroke.cpp` | ❌ | — |
| `p3.cpp` | ❌ | — |
| `palette.cpp` | ❌ | — |
| `patch.cpp` | ✅ | `PatchAlphaTestGM.kt`, `PatchPrimitiveGM.kt` |
| `path_stroke_with_zero_length.cpp` | ❌ | — |
| `patharcto.cpp` | ❌ | — |
| `pathcontourstart.cpp` | ❌ | — |
| `patheffects.cpp` | ❌ | — |
| `pathfill.cpp` | ✅ | `Bug7792GM.kt`, `RotatedCubicPathGM.kt` |
| `pathinterior.cpp` | ✅ | `PathInteriorGM.kt` |
| `pathmaskcache.cpp` | ✅ | `PathMaskCacheGM.kt` |
| `pathmeasure.cpp` | ❌ | — |
| `pathopsblend.cpp` | ✅ | `PathOpsBlendGM.kt` |
| `pathopsinverse.cpp` | ✅ | `PathOpsInverseGM.kt` |
| `pathreverse.cpp` | ❌ | — |
| `pdf_never_embed.cpp` | ❌ | — |
| `perlinnoise.cpp` | ✅ | `PerlinNoiseGM.kt`, `PerlinNoiseRotatedGM.kt` |
| `perspimages.cpp` | ❌ | — |
| `perspshaders.cpp` | ❌ | — |
| `persptext.cpp` | ❌ | — |
| `picture.cpp` | ✅ | `PictureGM.kt`, `PictureCullRectGM.kt` |
| `pictureimagefilter.cpp` | ❌ | — |
| `pictureimagegenerator.cpp` | ❌ | — |
| `pictureshader.cpp` | ❌ | — |
| `pictureshadercache.cpp` | ❌ | — |
| `pictureshadertile.cpp` | ❌ | — |
| `plus.cpp` | ❌ | — |
| `points.cpp` | ❌ | — |
| `poly2poly.cpp` | ❌ | — |
| `polygonoffset.cpp` | ❌ | — |
| `polygons.cpp` | ✅ | `ConjoinedPolygonsGM.kt`, `PolygonsGM.kt` |
| `postercircle.cpp` | ❌ | — |
| `preservefillrule.cpp` | ❌ | — |
| `quadpaths.cpp` | ✅ | `QuadClosePathGM.kt`, `QuadPathGM.kt` |
| `radial_gradient_precision.cpp` | ✅ | `RadialGradientPrecisionGM.kt` |
| `rasterhandleallocator.cpp` | ❌ | — |
| `readpixels.cpp` | ❌ | — |
| `recordopts.cpp` | ❌ | — |
| `rect_poly_stroke.cpp` | ✅ | `RectPolyStrokeGM.kt` |
| `rectangletexture.cpp` | ❌ | — |
| `rendertomipmappedyuvimageplanes.cpp` | ❌ | — |
| `repeated_bitmap.cpp` | ❌ | — |
| `resizeimagefilter.cpp` | ❌ | — |
| `rippleshadergm.cpp` | ❌ | — |
| `roundrects.cpp` | ✅ | `RoundRectGM.kt` |
| `rrect.cpp` | ✅ | `RRectGM.kt` |
| `rrectclipdrawpaint.cpp` | ✅ | `RRectClipDrawPaintGM.kt` |
| `rrects.cpp` | ❌ | — |
| `rsxtext.cpp` | ❌ | — |
| `runtimecolorfilter.cpp` | ✅ | `RuntimeColorFilterGM.kt` |
| `runtimefunctions.cpp` | ❌ | — |
| `runtimeimagefilter.cpp` | ❌ | — |
| `runtimeintrinsics.cpp` | ✅ | `RuntimeIntrinsicsCommonGM.kt`, `RuntimeIntrinsicsExponentialGM.kt`, `RuntimeIntrinsicsGeometricGM.kt`, `RuntimeIntrinsicsMatrixGM.kt`, `RuntimeIntrinsicsRelationalGM.kt`, `RuntimeIntrinsicsTrigGM.kt` |
| `runtimeshader.cpp` | ❌ | — |
| `samplerstress.cpp` | ❌ | — |
| `savelayer.cpp` | ❌ | — |
| `scaledemoji.cpp` | ❌ | — |
| `scaledemoji_rendering.cpp` | ❌ | — |
| `scaledrects.cpp` | ✅ | `ClipLargeRectGM.kt`, `ScaledRectsGM.kt` |
| `scaledstrokes.cpp` | ✅ | `ScaledStrokesGM.kt` |
| `shadermaskfilter.cpp` | ❌ | — |
| `shaderpath.cpp` | ✅ | `ShaderPathGM.kt` |
| `shadertext3.cpp` | ❌ | — |
| `shadowutils.cpp` | ❌ | — |
| `shallowgradient.cpp` | ❌ | — |
| `shapes.cpp` | ✅ | `InnerShapesGM.kt`, `SimpleShapesGM.kt` |
| `sharedcorners.cpp` | ❌ | — |
| `showmiplevels.cpp` | ❌ | — |
| `simpleaaclip.cpp` | ❌ | — |
| `simplerect.cpp` | ✅ | `SimpleRectGM.kt` |
| `skbug1719.cpp` | ✅ | `Skbug1719GM.kt` |
| `skbug_12212.cpp` | ❌ | — |
| `skbug_257.cpp` | ❌ | — |
| `skbug_4868.cpp` | ✅ | `Skbug4868GM.kt` |
| `skbug_5321.cpp` | ❌ | — |
| `skbug_8664.cpp` | ❌ | — |
| `skbug_8955.cpp` | ❌ | — |
| `skbug_9319.cpp` | ✅ | `Skbug9319GM.kt` |
| `skbug_9819.cpp` | ❌ | — |
| `slug.cpp` | ❌ | — |
| `smallarc.cpp` | ✅ | `SmallArcGM.kt` |
| `smallcircles.cpp` | ✅ | `SmallCirclesGM.kt` |
| `smallpaths.cpp` | ✅ | `SmallPathsGM.kt` |
| `spritebitmap.cpp` | ✅ | `SpriteBitmapGM.kt` |
| `srcmode.cpp` | ❌ | — |
| `srgb.cpp` | ❌ | — |
| `stlouisarch.cpp` | ✅ | `StLouisArchGM.kt` |
| `stringart.cpp` | ✅ | `StringArtGM.kt` |
| `stroke_rect_shader.cpp` | ✅ | `StrokeRectShaderGM.kt` |
| `strokedlines.cpp` | ❌ | — |
| `strokefill.cpp` | ✅ | `Bug339297GM.kt`, `Bug6987GM.kt` |
| `strokerect.cpp` | ❌ | — |
| `strokerect_anisotropic.cpp` | ✅ | `StrokerectAnisotropicGM.kt` |
| `strokerects.cpp` | ✅ | `StrokeRectsGM.kt` |
| `strokes.cpp` | ✅ | `CubicStrokeGM.kt`, `InnerJoinGeometryGM.kt`, `QuadCapGM.kt`, `Skbug12244GM.kt`, `StrokesGM.kt`, `Strokes2GM.kt`, `Strokes4GM.kt`, `TeenyStrokesGM.kt`, `ZeroLineStrokeGM.kt` |
| `stroketext.cpp` | ❌ | — |
| `subsetshader.cpp` | ❌ | — |
| `surface.cpp` | ❌ | — |
| `swizzle.cpp` | ❌ | — |
| `tablecolorfilter.cpp` | ❌ | — |
| `tablemaskfilter.cpp` | ❌ | — |
| `tallstretchedbitmaps.cpp` | ❌ | — |
| `testgradient.cpp` | ✅ | `TestGradientGM.kt` |
| `texelsubset.cpp` | ❌ | — |
| `text_scale_skew.cpp` | ✅ | `TextScaleSkewGM.kt` |
| `textblob.cpp` | ✅ | `TextBlobGM.kt` |
| `textblobblockreordering.cpp` | ✅ | `TextBlobBlockReorderingGM.kt` |
| `textblobcolortrans.cpp` | ✅ | `TextBlobColorTransGM.kt` |
| `textblobgeometrychange.cpp` | ❌ | — |
| `textblobmixedsizes.cpp` | ❌ | — |
| `textblobrandomfont.cpp` | ❌ | — |
| `textblobshader.cpp` | ✅ | `TextBlobShaderGM.kt` |
| `textblobtransforms.cpp` | ❌ | — |
| `textblobuseaftergpufree.cpp` | ❌ | — |
| `texteffects.cpp` | ❌ | — |
| `thinconcavepaths.cpp` | ✅ | `ThinConcavePathsGM.kt` |
| `thinrects.cpp` | ✅ | `ThinRectsGM.kt` |
| `thinstrokedrects.cpp` | ✅ | `ThinStrokedRectsGM.kt` |
| `tiledscaledbitmap.cpp` | ❌ | — |
| `tileimagefilter.cpp` | ❌ | — |
| `tilemodes.cpp` | ❌ | — |
| `tilemodes_alpha.cpp` | ❌ | — |
| `tilemodes_scaled.cpp` | ❌ | — |
| `tinybitmap.cpp` | ✅ | `TinyBitmapGM.kt` |
| `transparency.cpp` | ❌ | — |
| `trickycubicstrokes.cpp` | ✅ | `TrickyCubicStrokesGM.kt` |
| `typeface.cpp` | ❌ | — |
| `unpremul.cpp` | ❌ | — |
| `userfont.cpp` | ❌ | — |
| `variedtext.cpp` | ❌ | — |
| `vertices.cpp` | ✅ | `VerticesPerspectiveGM.kt` |
| `verylargebitmap.cpp` | ❌ | — |
| `video_decoder.cpp` | ❌ | — |
| `wacky_yuv_formats.cpp` | ❌ | — |
| `widebuttcaps.cpp` | ✅ | `WideButtCapsGM.kt` |
| `windowrectangles.cpp` | ❌ | — |
| `workingspace.cpp` | ❌ | — |
| `xfermodeimagefilter.cpp` | ❌ | — |
| `xfermodes.cpp` | ✅ | `XfermodesGM.kt` |
| `xfermodes2.cpp` | ❌ | — |
| `xfermodes3.cpp` | ❌ | — |
| `ycbcrimage.cpp` | ❌ | — |
| `yuv420_odd_dim.cpp` | ❌ | — |
| `yuvtorgbsubset.cpp` | ❌ | — |

## GMs Kotlin sans `.cpp` correspondant

Ces 30 fichiers `*GM.kt` n'ont pas pu être rattachés à un `.cpp` du tree de référence (probablement issus d'une version plus récente de Skia, ou correspondant à des bugs/crbug pas encore présents en upstream local) :

- `B340982297GM.kt`
- `BlurLargeRRectsGM.kt`
- `ConicalGradients2ptInsideGM.kt`
- `ConicalGradients2ptOutsideGM.kt`
- `CornerDiscretePathEffectGM.kt`
- `Crbug10141204GM.kt`
- `Crbug1472747GM.kt`
- `Crbug640176GM.kt`
- `Crbug888453GM.kt`
- `ImageFilterBlurDropShadowGM.kt`
- `ImageFilterOffsetGM.kt`
- `PathArcToSkbug9077GM.kt`
- `PathCapsFillsGridGM.kt`
- `PathHugeCrbug800804GM.kt`
- `PathInvFillGM.kt`
- `PathOpsSkbug10155GM.kt`
- `PathSkbug11859GM.kt`
- `PathSkbug11886GM.kt`
- `PlusMergesAaGM.kt`
- `ShallowGradientConicalGM.kt`
- `ShallowGradientLinearNoditherGM.kt`
- `ShallowGradientRadialNoditherGM.kt`
- `ShallowGradientSweepGM.kt`
- `Skbug13047GM.kt`
- `StrokeRectsRotatedGM.kt`
- `StrokerectAnisotropic5408GM.kt`
- `ThinRoundRectsGM.kt`
- `TrickyCubicStrokesLargeRadiusGM.kt`
- `VerticesCollapsedGM.kt`
- `ZeroControlStrokeGM.kt`
