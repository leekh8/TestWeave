package com.testweave.controller;

import com.testweave.domain.TestCase;
import com.testweave.domain.TestResult;
import com.testweave.service.ReportService;
import com.testweave.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/testweave")
@RequiredArgsConstructor
public class TestCasePageController {

    private final TestCaseService testCaseService;
    private final ReportService reportService;

    // 테스트케이스 등록 폼 보여주기
    @GetMapping("/testcase/form")
    public String showForm() {
        return "testcase_form";  // resources/templates/testcase_form.html
    }

    // 등록 후 목록으로 리다이렉트
    @PostMapping("/testcase/register")
    public String registerTest(@ModelAttribute TestCase testCase) {
        testCaseService.saveTestCase(testCase);
        return "redirect:/testweave/testcase/list";
    }

    @GetMapping("/testcase/list")
    public String listTestCases(Model model) {
        model.addAttribute("testCases", testCaseService.getAllTestCases());
        return "testcase_list";
    }

    @GetMapping("/testresult/list")
    public String showResults(@RequestParam(name = "status", required = false) String status,
                              @RequestParam(name = "sort", required = false) String sort,
                              Model model) {
        List<TestResult> results = testCaseService.getFilteredResults(status, sort);
        model.addAttribute("results", results);
        model.addAttribute("stats", testCaseService.getTestStatistics());
        return "testresult_list";
    }

    @GetMapping("/testcase/{id}/run")
    public String runAndRedirectToReport(@PathVariable Long id) throws IOException {
        Long resultId = testCaseService.runTest(id);
        String fileName = reportService.generateHtmlReport(resultId);
        return "redirect:/report/" + resultId; // 리포트 Controller에서 보여줌
    }
}