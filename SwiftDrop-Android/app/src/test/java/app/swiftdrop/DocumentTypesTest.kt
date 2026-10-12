package app.swiftdrop

import app.swiftdrop.ui.screens.files.documentGroup
import org.junit.Assert.*
import org.junit.Test

class DocumentTypesTest {
    @Test fun filtersSupportCaseAndUnicodeNames() {
        assertEquals("PDF",documentGroup("عقد.PDF"))
        assertEquals("Word",documentGroup("Draft.docx"))
        assertEquals("Sheets",documentGroup("Budget.ods"))
        assertEquals("Slides",documentGroup("Talk.pptx"))
        assertEquals("Text",documentGroup("Notes.md"))
    }
    @Test fun mediaAndExtensionlessFilesDoNotBecomeDocuments() {
        for(name in listOf("Photo.jpg","Track.mp3","Video.mp4","App.apk","README"))
            assertNull(name,documentGroup(name))
    }
}
