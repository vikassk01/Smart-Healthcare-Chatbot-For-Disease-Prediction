package com.swasthai.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.awt.Color; 

@Service
public class PdfReportService {

    public byte[] createPdfReport(String title, String content) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, baos);
            document.open();

          
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Font.NORMAL, Color.BLACK);
            Paragraph titlePara = new Paragraph(title, titleFont);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(20);
            document.add(titlePara);

            
            String plainContent = content
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("\\*", "-"); 

            Font contentFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Font.NORMAL, Color.BLACK);
            Paragraph contentPara = new Paragraph(plainContent, contentFont);
            document.add(contentPara);

        } catch (DocumentException e) {
            e.printStackTrace();
        } finally {
            document.close();
        }
        return baos.toByteArray();
    }
}