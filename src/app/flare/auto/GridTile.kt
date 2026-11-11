package app.flare.auto

internal data class GridTile(val type:String,val x:Int,val y:Int,val w:Int,val h:Int) {
    val minWidth get()=if((type=="apps" || type=="clock_only" || type=="clock_minimal" || HostedWidgets.id(type)!=null)) 2 else 3
    val minHeight get()=if((type=="clock" || type=="media" || type=="media_wide")) 3 else 2
    fun overlaps(other:GridTile)=x<other.x+other.w && x+w>other.x && y<other.y+other.h && y+h>other.y
    fun valid()=(type in TYPES || HostedWidgets.id(type)!=null) && x>=0 && y>=0 && w>=minWidth && h>=minHeight && x+w<=8 && y+h<=6
    fun resized(corner:Int,dx:Int,dy:Int):GridTile {
        val left=corner==0 || corner==2; val top=corner==0 || corner==1
        val nx=if(left) (x+dx).coerceIn(0,x+w-minWidth) else x
        val ny=if(top) (y+dy).coerceIn(0,y+h-minHeight) else y
        val nw=if(left) x+w-nx else (w+dx).coerceIn(minWidth,8-x)
        val nh=if(top) y+h-ny else (h+dy).coerceIn(minHeight,6-y)
        return copy(x=nx,y=ny,w=nw,h=nh)
    }
    fun resizeEdge(edge:Int,dx:Int,dy:Int):GridTile = when(edge) {
        0->resized(0,dx,0)
        1->resized(0,0,dy)
        2->resized(3,dx,0)
        else->resized(3,0,dy)
    }
    companion object {
        val TYPES=listOf("clock","clock_only","clock_minimal","media","media_wide","media_compact","projection","apps","weather","weather_solar","offline_map")
        fun validPage(tiles:List<GridTile>)=tiles.all { it.valid() } && tiles.map { if(it.type.startsWith("media")) "media" else it.type }.distinct().size==tiles.size &&
            tiles.indices.all { i -> (i+1 until tiles.size).none { j -> tiles[i].overlaps(tiles[j]) } }
    }
}
