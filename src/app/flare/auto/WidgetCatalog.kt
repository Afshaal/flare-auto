package app.flare.auto
internal object WidgetCatalog {
    data class Style(val type:String,val name:String,val group:String,val sizes:List<Pair<Int,Int>>)
    val styles=listOf(
        Style("clock","Clock with projection","Clocks",listOf(4 to 6,4 to 3,8 to 6)),
        Style("clock_only","Classic clock","Clocks",listOf(3 to 3,4 to 3,4 to 6)),
        Style("clock_minimal","Minimal clock","Clocks",listOf(2 to 2,4 to 3,8 to 6)),
        Style("clock_date","Clock with date","Clocks",listOf(3 to 2,4 to 3,8 to 6)),
        Style("media","Artwork player","Media",listOf(4 to 6,4 to 3,8 to 6)),
        Style("media_wide","Wide player","Media",listOf(4 to 3,8 to 3,8 to 4)),
        Style("media_compact","Compact player","Media",listOf(3 to 2,4 to 2,8 to 2)),
        Style("media_album","Album player","Media",listOf(4 to 3,5 to 4,8 to 6)),
        Style("projection","CarPlay & Android Auto","Projection",listOf(4 to 2,6 to 2,8 to 2)),
        Style("offline_map","Offline map","Maps",listOf(5 to 6,8 to 4,4 to 3)),
        Style("apps","Application shortcuts","Applications",listOf(2 to 2,4 to 3,8 to 3)),
        Style("weather","Weather","Weather",listOf(3 to 2,4 to 3,4 to 6)),
        Style("weather_solar","Weather & solar times","Weather",listOf(3 to 3,4 to 3,4 to 6)))
    fun fit(page:List<GridTile>,type:String,w:Int,h:Int):GridTile? {
        val tile=GridTile(type,0,0,w,h)
        if(!tile.valid() || page.any { it.type==type }) return null
        for(y in 0..6-h) for(x in 0..8-w) tile.copy(x=x,y=y).let { next->if(GridTile.validPage(page+next)) return next }
        return null
    }
}
