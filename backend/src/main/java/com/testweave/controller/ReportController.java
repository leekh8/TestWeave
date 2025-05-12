// 리포트 생성 API
package com.testweave.controller;

import com.testweave.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequiredArgsConstructor
@RequestMapping("/report")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/{id}")
    public ResponseEntity<Resource> downloadReport(@PathVariable Long id) throws IOException {
        // HTML 파일 생성
        String fileName = reportService.generateHtmlReport(id);

        // 파일 경로 지정
        Path filePath = Paths.get("src/main/resources/reports", fileName);

        // Spring Resource로 변환
        Resource resource = new FileSystemResource(filePath.toFile());

        // HTML 파일 그대로 브라우저 출력
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }

    @GetMapping("/pdf/{id}")
    public ResponseEntity<Resource> downloadPdf(@PathVariable Long id) throws IOException {
        String fileName = reportService.generatePdfReport(id); // PDF 생성
        Path filePath = Paths.get("src/main/resources/reports", fileName);
        Resource resource = new FileSystemResource(filePath.toFile());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

}
