package org.graphiks.kanvas.gpu.renderer.layers

/** Concrete draw-layer planner that maps semantic layer labels to first-route draw layers. */
class FirstRouteDrawLayerPlanner : GPUDrawLayerPlanner {
    override fun plan(layerLabels: List<String>): List<GPUDrawLayer> {
        return layerLabels.mapIndexed { index, label ->
            GPUDrawLayer(
                layerId = "draw-layer:$label",
                scopeId = GPULayerScopeID("layer:$label"),
                orderBand = "order-band:${index + 1}",
                insertionLabels = listOf("first-route-native", "order-preserving"),
            )
        }
    }
}
