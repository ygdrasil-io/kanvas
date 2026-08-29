# Bounded horizontal dashed stroke

W144 ouvre une seule route native supplémentaire : un segment ouvert horizontal
de deux points, sur coordonnées device intégrales, largeur 4 px, cap `butt`,
join `miter`, sans AA, transformation identité ou translation, et dash exact
`[8,4]` avec phase zéro. La géométrie est préparée par `GPUStroke` puis
transportée comme `StrokeStencilEdgeFan` avec la preuve typée
`HorizontalDashedButtMiterV1`.

La preuve publique positive traverse l’analyse, le planner, le semantic
builder, le payload typé et le native stencil-cover route dans
`GPUFramePathApiInventoryTest`. La variante proche `[4,2]` reste refusée avec
`unsupported.core_primitive.stroke.dash_exact_lowering`; aucune capture pixel
CPU/GPU ni promotion GM n’est revendiquée.
