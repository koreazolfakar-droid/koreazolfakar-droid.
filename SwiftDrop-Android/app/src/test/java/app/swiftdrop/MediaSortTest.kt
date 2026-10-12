package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test

class MediaSortTest {
    @Test fun everySortUsesAStableUniqueIdTieBreaker() {
        for (sort in MediaSort.entries) {
            val order = mediaSortOrder(sort)
            assertTrue(order.contains("_id"))
            assertFalse(order.contains(";"))
        }
    }
    @Test fun sortDirectionsReflectUserSelection() {
        assertTrue(mediaSortOrder(MediaSort.Newest).contains("DESC"))
        assertTrue(mediaSortOrder(MediaSort.Oldest).contains("ASC"))
        assertTrue(mediaSortOrder(MediaSort.Name).contains("DISPLAY_NAME"))
        assertTrue(mediaSortOrder(MediaSort.Largest).contains("SIZE"))
    }
}
