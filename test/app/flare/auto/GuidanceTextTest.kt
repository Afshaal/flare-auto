package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Test

class GuidanceTextTest {
    @Test
    fun englishInstructionsAreSpokenUnchanged() {
        assertEquals("turn left onto Sir Syed Road", OfflineGuidance.speakable("turn left onto Sir Syed Road"))
        assertEquals("arrive at destination", OfflineGuidance.speakable("arrive at destination"))
    }

    @Test
    fun urduOnlyStreetNamesAreDroppedForTheVoice() {
        assertEquals("turn left", OfflineGuidance.speakable("turn left onto کالج روڈ"))
        assertEquals("At roundabout, take exit 2", OfflineGuidance.speakable("At roundabout, take exit 2 onto مال روڈ"))
        assertEquals("continue", OfflineGuidance.speakable("continue مال"))
    }

    @Test
    fun searchKindLabels() {
        assertEquals("Fast food", OfflineDestinationPicker.kindLabel("fast_food"))
        assertEquals("Marketplace", OfflineDestinationPicker.kindLabel("marketplace"))
    }
}
