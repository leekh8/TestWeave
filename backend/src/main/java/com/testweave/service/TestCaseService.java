// 테스트케이스 처리
package com.testweave.service;

import com.testweave.domain.TestCase;
import com.testweave.domain.TestResult;
import com.testweave.dto.TestStats;
import com.testweave.repository.TestCaseRepository;
import com.testweave.repository.TestResultRepository;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TestCaseService {
    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;

    // 핵심 비즈니스 로직
    @PostConstruct
    public void setupDriverPath() {
        System.setProperty("webdriver.chrome.driver", "src/main/resources/driver/chromedriver");
    }


    // 테스트케이스 등록
    public Long saveTestCase(TestCase testCase) {
        testCase.setCreatedAt(LocalDateTime.now());
        testCaseRepository.save(testCase);
        return testCase.getId();
    }

    // 테스트 실행
    public Long runTest(Long testCaseId) {
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new IllegalArgumentException("테스트케이스가 존재하지 않습니다."));

        long startTime = System.currentTimeMillis();

        TestResult result = new TestResult();
        result.setTestCase(testCase);

        boolean pass;
        String errorMessage = null;

        if ("UI".equalsIgnoreCase(testCase.getType())) {
            pass = runUiTest(testCase.getTargetUrl(), result);
        } else {
            pass = runApiTest(testCase.getTargetUrl(), testCase.getHttpMethod(), testCase.getExpectedRes(), result);
        }

        long duration = System.currentTimeMillis() - startTime;

        result.setStatus(pass ? "PASS" : "FAIL");
        result.setExecutionTime(duration);
        result.setExecutedAt(LocalDateTime.now());

        testResultRepository.save(result);
        return result.getId();
    }

    // API 테스트 실행 로직
    private boolean runApiTest(String url, String method, String expected, TestResult result) {
        try {
            Response response = RestAssured.given().when().request(method, url);

            int statusCode = response.statusCode();
            String body = response.getBody().asString();

            // 에러 요약 메시지 추출
            String summary = "응답 코드: " + statusCode;

            // JSON 응답이면 에러 메시지 추출 시도
            try {
                String error = response.jsonPath().getString("error");
                String message = response.jsonPath().getString("message");
                if (error != null) summary += " | 오류: " + error;
                if (message != null) summary += " | 설명: " + message;
            } catch (Exception ignored) {
                if (body.length() > 200) {
                    summary += " | 응답 요약: " + body.substring(0, 200) + "...";
                } else {
                    summary += " | 응답: " + body;
                }
            }

            result.setErrorMessage(summary);

            return String.valueOf(statusCode).equals(expected);
        } catch (Exception e) {
            result.setErrorMessage("API 호출 예외: " + e.getMessage());
            return false;
        }
    }


    // UI 테스트 실행 로직 (크롬드라이버 필요)
    private boolean runUiTest(String url, TestResult result) {
        WebDriver driver = new ChromeDriver();
        try {
            driver.get(url);
            String title = driver.getTitle();
            boolean pass = title != null && !title.isEmpty();
            result.setErrorMessage("페이지 타이틀: " + title);
            return pass;
        } catch (Exception e) {
            result.setErrorMessage("UI 테스트 실패: " + e.getMessage());
            return false;
        } finally {
            driver.quit();
        }
    }


    public List<TestCase> getAllTestCases() {
        return testCaseRepository.findAll();
    }

    public List<TestResult> getAllResults() {
        return testResultRepository.findAll();
    }

    public List<TestResult> getFilteredResults(String status, String sort) {
        List<TestResult> results = testResultRepository.findAll();

        if (status != null && !status.isEmpty()) {
            results = results.stream()
                    .filter(r -> r.getStatus().equalsIgnoreCase(status))
                    .toList();
        }

        if ("time".equals(sort)) {
            results = results.stream()
                    .sorted(Comparator.comparingLong(TestResult::getExecutionTime).reversed())
                    .toList();
        } else if ("date".equals(sort)) {
            results = results.stream()
                    .sorted(Comparator.comparing(TestResult::getExecutedAt).reversed())
                    .toList();
        }

        return results;
    }

    public TestStats getTestStatistics() {
        List<TestResult> results = testResultRepository.findAll();
        int total = results.size();
        int pass = (int) results.stream().filter(r -> r.getStatus().equalsIgnoreCase("PASS")).count();
        int fail = total - pass;
        long avgTime = (long) results.stream()
                .mapToLong(TestResult::getExecutionTime)
                .average()
                .orElse(0);

        double rate = total > 0 ? (pass * 100.0) / total : 0.0;
        return new TestStats(total, pass, fail, rate, avgTime);
    }
}