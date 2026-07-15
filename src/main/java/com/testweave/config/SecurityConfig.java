package com.testweave.config;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API 인증. 대상 등록·스캔 같은 쓰기 API와 H2 콘솔이 무인증으로 노출되면
 * 누구나 임의 대상을 스캔시키거나(스캐너 남용) 콘솔에서 임의 SQL을 실행할 수 있다.
 * 모든 요청을 HTTP Basic으로 보호한다.
 *
 * <p>자격증명: {@code spring.security.user.name}/{@code password}를 설정하지 않으면
 * Spring Boot가 기동 시 강한 임의 비밀번호를 생성해 로그에 출력한다(하드코딩 비밀번호 없음).
 * 안정적 운영 자격은 환경변수로 주입한다.
 *
 * <p>{@code ci} 프로파일은 웹 서버 없이 스캔만 하고 종료하므로 필터체인이 불필요 —
 * {@code @Profile("!ci")}로 제외한다.
 */
@Configuration
@Profile("!ci")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 상태 없는 REST + HTTP Basic — 브라우저 세션 CSRF 벡터가 없어 비활성.
                // 단 H2 콘솔은 예외로 CSRF를 남겨 폼 동작을 보장한다.
                .csrf(csrf -> csrf.ignoringRequestMatchers(PathRequest.toH2Console()).disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PathRequest.toH2Console()).authenticated()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                // H2 콘솔은 자기 프레임 안에서 렌더링 — 동일 출처 프레임 허용
                .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        return http.build();
    }
}
