package app.flare.auto
import org.junit.Assert.*
import org.junit.Test
class DashboardBehaviorTest {
    @Test fun touchingEdgesAreNotOverlap() {
        val a=GridTile("clock",0,0,4,6); val b=GridTile("media",4,0,4,6)
        assertTrue(GridTile.validPage(listOf(a,b)))
        assertFalse(GridTile.validPage(listOf(a,b.copy(x=3))))
    }
    @Test fun invalidAndDuplicateCardsAreRejected() {
        assertFalse(GridTile("clock",6,0,3,6).valid())
        assertFalse(GridTile("clock",0,0,3,2).valid())
        assertFalse(GridTile.validPage(listOf(GridTile("clock",0,0,4,6),GridTile("clock",4,0,4,6))))
        assertTrue(GridTile.validPage(emptyList()))
    }
    @Test fun bannersCoalesceAndBoundQueue() {
        val q=BannerQueue<Pair<String,Int>> { it.first }
        q.offer("a" to 1); q.offer("b" to 2); q.offer("a" to 3)
        assertEquals("a" to 3,q.next())
        q.offer("c" to 4); q.offer("d" to 5); q.offer("e" to 6)
        assertEquals("c" to 4,q.next()); q.remove("d")
        assertEquals("e" to 6,q.next()); assertNull(q.next())
        q.offer("x" to 7); q.clear(); assertNull(q.next())
    }
    @Test fun threeCardsFitAndProjectionHasItsOwnSize() {
        assertTrue(GridTile.validPage(listOf(GridTile("clock",0,0,4,6),GridTile("media",4,0,4,3),GridTile("apps",4,3,4,3))))
        assertTrue(GridTile("projection",0,0,4,2).valid())
        assertTrue(GridTile("clock_minimal",0,0,2,2).valid())
    }
    @Test fun everyCornerResizesWithoutMovingItsOppositeAnchor() {
        val tile=GridTile("clock",2,1,4,4)
        val tl=tile.resized(0,-1,-1); assertEquals(6,tl.x+tl.w); assertEquals(5,tl.y+tl.h)
        val tr=tile.resized(1,1,-1); assertEquals(2,tr.x); assertEquals(5,tr.y+tr.h)
        val bl=tile.resized(2,-1,1); assertEquals(6,bl.x+bl.w); assertEquals(1,bl.y)
        val br=tile.resized(3,1,1); assertEquals(2,br.x); assertEquals(1,br.y)
        for(t in listOf(tl,tr,bl,br)) assertTrue(t.valid())
        assertTrue(tile.resized(0,-100,-100).valid())
    }
}
