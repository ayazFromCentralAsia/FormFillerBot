package com.example.bot.FormFillerBot.service;

import com.example.bot.FormFillerBot.model.User;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class DocumentService {
    public File generateDocument(User user) {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph paragraph = doc.createParagraph();
            XWPFRun run = paragraph.createRun();

            run.setText("Анкета пользователя");
            run.addBreak();
            run.setText("ФИО: " + user.getLastName() + " " + user.getFirstName() + " " + user.getMiddleName());
            run.addBreak();
            run.setText("Дата рождения: " + user.getBirthDate());
            run.addBreak();
            run.setText("Пол: " + user.getGender());

            File outputFile = File.createTempFile("document", ".docx");
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                doc.write(out);
            }

            return outputFile;
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при создании документа", e);
        }
    }
}