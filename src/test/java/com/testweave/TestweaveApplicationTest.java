package com.testweave;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 전체 스프링 컨텍스트가 기동되는지 확인 — SecurityConfig(HTTP Basic)·
 * validation·JPA·체크 모듈(SafeHttpFetcher 주입 포함) 배선 회귀를 잡는다.
 */
@SpringBootTest
class TestweaveApplicationTest {

    @Test
    void contextLoads() {
    }
}
