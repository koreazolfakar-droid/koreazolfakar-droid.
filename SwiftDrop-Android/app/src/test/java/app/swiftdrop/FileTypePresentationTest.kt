package app.swiftdrop

import app.swiftdrop.ui.components.FileCategory
import app.swiftdrop.ui.components.fileCategory
import app.swiftdrop.ui.components.fileFilterMatches
import app.swiftdrop.ui.components.isDocumentCategory
import app.swiftdrop.ui.components.mimeTypesForFileFilter
import app.swiftdrop.ui.screens.files.documentGroup
import org.junit.Assert.*
import org.junit.Test

class FileTypePresentationTest {
    @Test fun recognizableFormatsHaveConsistentIdentity() {
        val samples = mapOf(
            "Budget.XLSX" to FileCategory.Sheets,
            "report.xlsm" to FileCategory.Sheets,
            "table.csv" to FileCategory.Sheets,
            "Contract.PDF" to FileCategory.Pdf,
            "meeting.docx" to FileCategory.Word,
            "deck.pptx" to FileCategory.Slides,
            "IMG_001.HEIC" to FileCategory.Photo,
            "image.avif" to FileCategory.Photo,
            "movie.mkv" to FileCategory.Video,
            "recording.flac" to FileCategory.Audio,
            "archive.7z" to FileCategory.Archive,
            "application.apk" to FileCategory.Package,
            "notes.md" to FileCategory.Text,
            "raw.unknown" to FileCategory.Other
        )
        samples.forEach { (name,category) -> assertEquals(name,category,fileCategory(name)) }
    }

    @Test fun contentProviderMimeTypeWorksForMissingOrUnknownExtension() {
        assertEquals(FileCategory.Sheets,
            fileCategory("Sales data", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertEquals(FileCategory.Pdf,fileCategory("document","application/pdf"))
        assertEquals(FileCategory.Photo,fileCategory("content","image/heic"))
        assertEquals(FileCategory.Pdf,fileCategory("report.pdf","application/octet-stream"))
        assertEquals(FileCategory.Pdf,fileCategory("report.pdf","image/png"))
    }

    @Test fun documentFiltersNeverMistakeAnImageForADocument() {
        assertEquals("Sheets",documentGroup("ميزانية.XLSX"))
        assertEquals("PDF",documentGroup("statement","application/pdf"))
        assertNull(documentGroup("vacation.jpeg"))
        assertNull(documentGroup("audio.mp3"))
        assertTrue(fileFilterMatches("Budget.xlsx",null,"Sheets",true))
        assertFalse(fileFilterMatches("photo.jpeg",null,"All",true))
        assertTrue(fileFilterMatches("photo.jpeg",null,"Images",false))
        assertFalse(fileFilterMatches("photo.jpeg",null,"PDF",false))
        assertFalse(fileFilterMatches("Budget.xlsx",null,"PDF",false))
        assertTrue(fileFilterMatches("random.xyz",null,"Other",false))
        assertFalse(isDocumentCategory(FileCategory.Archive))
    }

    @Test fun pickerMimeFiltersMatchClickedChip() {
        assertArrayEquals(arrayOf("application/pdf"),mimeTypesForFileFilter("PDF",true))
        assertTrue(mimeTypesForFileFilter("Sheets",true).contains("text/csv"))
        assertTrue(mimeTypesForFileFilter("Word",true).any { it.contains("wordprocessing") })
        assertArrayEquals(arrayOf("image/*"),mimeTypesForFileFilter("Images",false))
        assertArrayEquals(arrayOf("*/*"),mimeTypesForFileFilter("All",false))
        assertTrue(mimeTypesForFileFilter("All",true).any { it.contains("spreadsheet") })
    }
}
