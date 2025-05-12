// 리포트 생성 서비스
package com.testweave.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.testweave.domain.TestResult;
import com.testweave.repository.TestResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final TestResultRepository testResultRepo;

    public String generateHtmlReport(Long resultId) throws IOException {
        TestResult result = testResultRepo.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("결과 없음"));

        String htmlContent = """
                <html lang="ko">
                 <head>
                  <meta charset="UTF-8">
                  <title>테스트 리포트</title>
                  <meta http-equiv="refresh" content="3;url=/testweave/testresult/list">
                 </head>
                 <body>
                  <h1>테스트 결과 리포트</h1>
                  <p>테스트 ID: %d</p>
                  <p>상태: %s</p>
                  <p>실행 시간: %d ms</p>
                  <p>실행 일시: %s</p>
                  <p>테스트 대상: %s</p>
                  <p>오류 메시지: %s</p>
                  
                  <a class="button" href="/testweave/testresult/list">← 결과 목록으로 바로 가기</a>
                  <p class="note">3초 후 자동으로 결과 목록 페이지로 이동합니다.</p>
                 </body>
                </html>
                """.formatted(
                result.getId(),
                result.getStatus(),
                result.getExecutionTime(),
                result.getExecutedAt(),
                result.getTestCase().getTargetUrl(),
                result.getErrorMessage() != null ? result.getErrorMessage() : "-"
        );

        // 파일로 저장
        String fileName = "report-" + result.getId() + ".html";
        Path path = Paths.get("src/main/resources/reports", fileName);
        Files.createDirectories(path.getParent());
        Files.writeString(path, htmlContent, StandardCharsets.UTF_8);

        return fileName; // 저장된 파일명 반환
    }

    public String generatePdfReport(Long resultId) throws IOException {
        TestResult result = testResultRepo.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("결과 없음"));

        String fileName = "report-" + result.getId() + ".pdf";
        Path filePath = Paths.get("src/main/resources/reports", fileName);
        Files.createDirectories(filePath.getParent());

        String content = """
                테스트 ID: %d
                상태: %s
                실행 시간: %d ms
                실행 일시: %s
                테스트 대상: %s
                """.formatted(
                result.getId(),
                result.getStatus(),
                result.getExecutionTime(),
                result.getExecutedAt(),
                result.getTestCase().getTargetUrl()
        );

        // PDF 생성
        PdfWriter writer = new PdfWriter(Files.newOutputStream(filePath));
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        doc.add(new Paragraph("테스트 결과 리포트"));
        doc.add(new Paragraph("테스트 ID: " + result.getId()));
        doc.add(new Paragraph("상태: " + result.getStatus()));
        doc.add(new Paragraph("실행 시간: " + result.getExecutionTime() + " ms"));
        doc.add(new Paragraph("실행 일시: " + result.getExecutedAt()));
        doc.add(new Paragraph("테스트 대상: " + result.getTestCase().getTargetUrl()));
        doc.add(new Paragraph("오류 메시지: " +
                (result.getErrorMessage() != null ? result.getErrorMessage() : "-")));

        doc.close();

        return fileName;
    }
}