package com.redsurf.tv.vod

data class VodCategory(val id: String, val name: String)
data class VodMovie(val streamId: Int, val name: String, val streamIcon: String, val rating: Double)
data class Series(val seriesId: Int, val name: String, val cover: String, val plot: String)

object XtreamVodParser {
    fun parseVodCategories(jsonStr: String): List<VodCategory> {
        val list = mutableListOf<VodCategory>()
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(VodCategory(obj.getString("category_id"), obj.getString("category_name")))
            }
        } catch (e: Exception) {}
        return list
    }

    fun parseVodStreams(jsonStr: String): List<VodMovie> {
        val list = mutableListOf<VodMovie>()
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    VodMovie(
                        streamId = obj.getInt("stream_id"),
                        name = obj.getString("name"),
                        streamIcon = obj.optString("stream_icon", ""),
                        rating = obj.optDouble("rating", 0.0)
                    )
                )
            }
        } catch (e: Exception) {}
        return list
    }
}
