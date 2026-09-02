package fi.aalto.radio

internal data class FavoriteItemAnchor(
    val id: String,
    val topPx: Int,
    val heightPx: Int
) {
    val centerPx: Float
        get() = topPx + heightPx / 2f
}

internal object FavoriteReorder {
    fun normalizeWorkingOrder(
        orderedIds: List<String>,
        activeFavoriteIds: List<String>
    ): List<String> {
        if (activeFavoriteIds.isEmpty()) return emptyList()

        val activeIds = activeFavoriteIds.toSet()
        val seen = LinkedHashSet<String>(activeFavoriteIds.size)
        val normalized = ArrayList<String>(activeFavoriteIds.size)

        orderedIds.forEach { stationId ->
            if (stationId in activeIds && seen.add(stationId)) {
                normalized += stationId
            }
        }
        activeFavoriteIds.forEach { stationId ->
            if (seen.add(stationId)) {
                normalized += stationId
            }
        }

        return normalized
    }

    fun moveDraggedItem(
        orderedIds: List<String>,
        draggedId: String,
        targetIndex: Int
    ): List<String> {
        val currentIndex = orderedIds.indexOf(draggedId)
        if (currentIndex == -1) return orderedIds

        val withoutDragged = orderedIds.toMutableList()
        withoutDragged.removeAt(currentIndex)
        withoutDragged.add(
            index = targetIndex.coerceIn(0, withoutDragged.size),
            element = draggedId
        )

        return withoutDragged
    }

    fun targetIndexForDraggedCenter(
        orderedIds: List<String>,
        draggedId: String,
        draggedCenterPx: Float,
        visibleAnchors: List<FavoriteItemAnchor>,
        fallbackItemExtentPx: Float
    ): Int {
        val withoutDragged = orderedIds.filterNot { it == draggedId }
        if (withoutDragged.isEmpty()) return 0

        val itemExtentPx = fallbackItemExtentPx.takeIf { it > 0f } ?: 1f
        val indexById = orderedIds.withIndex().associate { (index, stationId) ->
            stationId to index
        }
        val anchorById = visibleAnchors.associateBy { it.id }
        val fallbackOriginPx = visibleAnchors
            .mapNotNull { anchor ->
                indexById[anchor.id]?.let { index -> index to (anchor.centerPx - index * itemExtentPx) }
            }
            .minByOrNull { it.first }
            ?.second
            ?: 0f

        val targetIndex = withoutDragged.count { stationId ->
            val stationIndex = indexById[stationId] ?: return@count false
            val centerPx = anchorById[stationId]?.centerPx
                ?: fallbackOriginPx + stationIndex * itemExtentPx
            draggedCenterPx > centerPx
        }

        return targetIndex.coerceIn(0, withoutDragged.size)
    }
}
