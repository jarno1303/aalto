package fi.aalto.radio

object FavoriteIds {
    val defaultFavorites = setOf(StationCatalog.DEFAULT_STATION_ID)

    fun toggle(currentIds: Set<String>, stationId: String): Set<String> {
        return if (stationId in currentIds) {
            currentIds - stationId
        } else {
            currentIds + stationId
        }
    }
}
