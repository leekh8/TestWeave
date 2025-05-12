// 테스트케이스 등록, 실행
package com.testweave.controller;

import com.testweave.domain.TestCase;
import com.testweave.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/testcase")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;

    // 테스트케이스 등록
    @PostMapping
    public ResponseEntity<Long> register(@RequestBody TestCase testCase) {
        return ResponseEntity.ok(testCaseService.saveTestCase(testCase));
    }

    // 테스트 실행 API (TestResult 저장 후 id 반환)
    @PostMapping("/{id}/run")
    public ResponseEntity<Long> run(@PathVariable Long id) {
        Long resultId = testCaseService.runTest(id);
        return ResponseEntity.ok(resultId);  // 성공 시 TestResult ID 반환
    }
}