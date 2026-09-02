package fi.aalto.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteReorderTest {
    @Test
    fun normalizeWorkingOrderKeepsKnownOrderAndAppendsNewFavorites() {
        val order = FavoriteReorder.normalizeWorkingOrder(
            orderedIds = listOf("radio-rock", "stale", "ylex", "radio-rock"),
            activeFavoriteIds = listOf("ylex", "radio-helsinki", "radio-rock")
        )

        assertEquals(listOf("radio-rock", "ylex", "radio-helsinki"), order)
    }

    @Test
    fun moveDraggedItemPreservesStableIds() {
        val order = FavoriteReorder.moveDraggedItem(
            orderedIds = listOf("ylex", "radio-rock", "radio-helsinki"),
            draggedId = "radio-helsinki",
            targetIndex = 0
        )

        assertEquals(listOf("radio-helsinki", "ylex", "radio-rock"), order)
    }

    @Test
    fun targetIndexUsesPixelCenters() {
        val orderedIds = listOf("a", "b", "c")
        val anchors = listOf(
            FavoriteItemAnchor(id = "a", topPx = 20, heightPx = 68),
            FavoriteItemAnchor(id = "b", topPx = 98, heightPx = 68),
            FavoriteItemAnchor(id = "c", topPx = 176, heightPx = 68)
        )

        assertEquals(
            0,
            FavoriteReorder.targetIndexForDraggedCenter(
                orderedIds = orderedIds,
                draggedId = "a",
                draggedCenterPx = 131f,
                visibleAnchors = anchors,
                fallbackItemExtentPx = 78f
            )
        )
        assertEquals(
            1,
            FavoriteReorder.targetIndexForDraggedCenter(
                orderedIds = orderedIds,
                draggedId = "a",
                draggedCenterPx = 133f,
                visibleAnchors = anchors,
                fallbackItemExtentPx = 78f
            )
        )
    }
}
