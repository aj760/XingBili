package blbl.cat3399.feature.live

interface LiveNavigator {
    fun openAreaDetail(parentAreaId: Int, parentTitle: String, areaId: Int, areaTitle: String): Boolean
}

