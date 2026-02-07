package org.blocpress.renderer;

import org.junit.jupiter.api.Test;

import static org.blocpress.util.ResourceUtil.*;
import static org.blocpress.util.ResourceUtil.extractRtfContent;
import static org.blocpress.util.ResourceUtil.loadDocumentAsBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FIXME: This requires libreoffice to be installed and available in the system path
 */
public class TransformTest {
//    @Test
    public void transformToPdf() throws Exception {
        byte[] odtBytes = loadDocumentAsBytes("/kuendigung_generated.odt");
        String expected = extractPdfContent(loadDocumentAsBytes("/kuendigung_generated.pdf"));


        String actual = extractPdfContent(LibreOfficeExporter.refreshAndTransform(odtBytes, OutputFormat.PDF));

        assertEquals(expected, actual);
    }

//    @Test
    public void transformToRtf() throws Exception {
        byte[] odtBytes = loadDocumentAsBytes("/kuendigung_generated.odt");
        String expected = extractRtfContent(loadDocumentAsBytes("/kuendigung_generated.rtf"));
        String actual = extractRtfContent(LibreOfficeExporter.refreshAndTransform(odtBytes, OutputFormat.RTF));

        assertEquals(expected, actual);
    }

}
