package com.stacktivity.yandeximagesearchengine.data.model

data class YandexResponse(
//        val cnt: String,
//        val type: String,
        val blocks: List<Blocks>,
        val captcha: Captcha?
)

data class Blocks(
        val name: Name,
        val params: Params,
        val html: String
)

data class Name(val block: String/*, val mods: Mods*/)

data class Params(
        val count: Int,
//        val mods: Mods,
//        val prevPageUrl: String,
//        val nextPageUrl: String,
//        val item: Item,
//        val hoverTime: Int,
        val lastPage: Int,
        val bundles: ArrayList<Any?>
)

/*
data class Mods(
        val infinite: String,
        val navigation: String,
        val complain: String,
        val prefetch: String,
        val height: String,
        val grid_counter: Boolean,
        val vertical_shadowed: Boolean,
        val viewer: Boolean,
        val viewer_history: Boolean,
        val small_top_padding: String,
        val scroll_up: Boolean
)
*/

//data class Item(val selected: String)