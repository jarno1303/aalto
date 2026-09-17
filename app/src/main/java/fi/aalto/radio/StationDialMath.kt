package fi.aalto.radio

import kotlin.math.abs
import kotlin.math.roundToInt

internal const val DIAL_MAX_FLING_DETENTS = 4
private const val DIAL_FLING_PROJECTION_SECONDS = 0.10f

internal data class DialDragUpdate(
    val virtualPosition: Int,
    val offsetPx: Float,
    val crossedDetents: Int
)

/** Advances the continuous tuner offset while keeping the residual offset bounded. */
internal fun advanceDialDrag(
    virtualPosition: Int,
    offsetPx: Float,
    dragDeltaPx: Float,
    detentPx: Float
): DialDragUpdate {
    require(detentPx > 0f)

    var nextPosition = virtualPosition
    var nextOffset = offsetPx + dragDeltaPx
    var crossedDetents = 0

    while (nextOffset <= -detentPx) {
        nextOffset += detentPx
        nextPosition += 1
        crossedDetents += 1
    }
    while (nextOffset >= detentPx) {
        nextOffset -= detentPx
        nextPosition -= 1
        crossedDetents += 1
    }

    return DialDragUpdate(nextPosition, nextOffset, crossedDetents)
}

/** Chooses the nearest detent after adding a short, capped velocity projection. */
internal fun dialSnapTargetPosition(
    virtualPosition: Int,
    offsetPx: Float,
    velocityPxPerSecond: Float,
    detentPx: Float,
    maxTravelDetents: Int = DIAL_MAX_FLING_DETENTS
): Int {
    require(detentPx > 0f)
    val projectedDetents =
        -(offsetPx + velocityPxPerSecond * DIAL_FLING_PROJECTION_SECONDS) / detentPx
    val cappedDelta = projectedDetents
        .roundToInt()
        .coerceIn(
            -maxTravelDetents.coerceIn(0, DIAL_MAX_FLING_DETENTS),
            maxTravelDetents.coerceIn(0, DIAL_MAX_FLING_DETENTS)
        )
    return virtualPosition + cappedDelta
}

internal fun dialSnapDurationMillis(startOffsetPx: Float, targetOffsetPx: Float, detentPx: Float): Int {
    require(detentPx > 0f)
    val distanceInDetents = (abs(targetOffsetPx - startOffsetPx) / detentPx).coerceIn(0f, DIAL_MAX_FLING_DETENTS.toFloat())
    return (120f + distanceInDetents * 25f).roundToInt().coerceIn(120, 220)
}

internal fun dialPlaybackCommitCount(dragStartPosition: Int, targetPosition: Int): Int {
    return if (dragStartPosition == targetPosition) 0 else 1
}

internal fun dialVisibleSlotOffsets(stationCount: Int, settling: Boolean): IntArray {
    return when {
        stationCount <= 1 -> intArrayOf(0)
        stationCount == 2 -> intArrayOf(0, 1)
        settling -> IntArray(DIAL_MAX_FLING_DETENTS * 2 + 1) { it - DIAL_MAX_FLING_DETENTS }
        else -> intArrayOf(-1, 0, 1)
    }
}

internal fun circularStationIndex(position: Int, stationCount: Int): Int {
    require(stationCount > 0)
    return ((position % stationCount) + stationCount) % stationCount
}
