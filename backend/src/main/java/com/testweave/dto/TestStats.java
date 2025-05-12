// DTO class
package com.testweave.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TestStats {
    private int totalCount;
    private int passCount;
    private int failCount;
    private double passRate;  // 백분율
    private long avgExecutionTime;
}
