package app.swiftdrop
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class MediaGroupingTest {
    @Test fun differentMomentsOfOneCalendarDayHaveOneKey() {
        val zone=ZoneId.of("Asia/Beirut")
        assertEquals(mediaDayKey(1791676800,zone),mediaDayKey(1791676860,zone))
    }
    @Test fun changesDateAtLocalMidnight() {
        val zone=ZoneId.of("UTC")
        assertNotEquals(mediaDayKey(86400,zone),mediaDayKey(86399,zone))
    }
    @Test fun unknownDateHasPredictableKey() {
        assertEquals("unknown",mediaDayKey(0))
        assertEquals("unknown",mediaDayKey(-1))
    }
    @Test fun onlyChronologicalSortsUseDateSections() {
        assertTrue(groupedByDay(MediaSort.Newest))
        assertTrue(groupedByDay(MediaSort.Oldest))
        assertFalse(groupedByDay(MediaSort.Name))
        assertFalse(groupedByDay(MediaSort.Largest))
    }
}
