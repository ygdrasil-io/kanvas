package org.graphiks.kanvas.build

/** Converts the official promotion rebaseline properties into CLI arguments. */
fun promotionRebaselineArguments(
    rebaseline: String?,
    priorComparison: String?,
    newComparison: String?,
): List<String> {
    require(rebaseline == null || rebaseline == "true" || rebaseline == "false") {
        "promotionRebaseline must be exactly true or false"
    }
    if (rebaseline != "true") {
        require(priorComparison == null && newComparison == null) {
            "promotionPriorComparison and promotionNewComparison require promotionRebaseline=true"
        }
        return emptyList()
    }
    require(!priorComparison.isNullOrBlank()) {
        "promotionPriorComparison is required when promotionRebaseline=true"
    }
    require(!newComparison.isNullOrBlank()) {
        "promotionNewComparison is required when promotionRebaseline=true"
    }
    return listOf(
        "--rebaseline",
        "--prior-comparison",
        priorComparison,
        "--new-comparison",
        newComparison,
    )
}
