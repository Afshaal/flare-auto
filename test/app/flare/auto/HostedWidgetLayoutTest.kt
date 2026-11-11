package app.flare.auto

import org.junit.Assert.*
import org.junit.Test

class HostedWidgetLayoutTest {
    @Test fun hostedInstancesKeepTheirIdentityWhenResized() {
        val first=GridTile("widget:42",0,0,4,3)
        assertTrue(first.valid())
        assertEquals("widget:42",first.resizeEdge(2,1,0).type)
        assertTrue(GridTile.validPage(listOf(first,GridTile("widget:43",4,0,4,3))))
        assertFalse(GridTile.validPage(listOf(first,GridTile("widget:42",4,0,4,3))))
    }
    @Test fun malformedWidgetIdsCannotBecomeSavedCards() {
        for(type in listOf("widget:-1","widget:0","widget:abc","widget:99999999999999999")) assertFalse(GridTile(type,0,0,4,3).valid())
    }
}
