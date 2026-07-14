# TestWeave — Web Security Regression Scanner

> 웹/API의 보안 설정(응답 헤더·TLS·쿠키 플래그)을 점검하고, **직전 결과(baseline) 대비 "회귀"** 를 잡아내는 도구.

## Why

배포를 거듭하다 보면 보안 설정이 조용히 후퇴합니다 — 어제까지 있던 `HSTS`가 빠지거나, 쿠키의 `Secure` 플래그가 사라지거나, 인증서 만료가 임박합니다. 단발성 스캐너는 "지금 상태"만 보여줄 뿐, **"지난번보다 나빠졌다"** 는 알려주지 않습니다.

TestWeave는 대상별 점검 결과를 저장하고, 매 스캔을 직전 결과와 비교해 **`REGRESSION`(PASS→FAIL, 후퇴) / `FIXED`(FAIL→PASS, 개선) / `NEW` / `SAME`** 으로 판정합니다. 정기 실행(예: GitHub Actions 스케줄)과 결합하면 보안 설정의 후퇴를 배포 직후 감지할 수 있습니다.

## Status

🟢 **MVP + 정기 회귀 동작** — 2026-06-30 보안 회귀 스캐너로 피벗, 2026-07-02 HTML 리포트·`ci` 프로파일·GitHub Actions 스케줄 스캔까지 배선 완료. 아래 "구현됨"은 코드에 실제 배선된 것, "로드맵"은 아직입니다.

## Features

**구현됨 (MVP 골격):**
- **점검 대상 관리** — `SecurityTarget`(name/url/checkTypes) 등록·조회 (`GET/POST /api/targets`)
- **보안 점검 모듈** (`SecurityCheck` 인터페이스 — 새 모듈은 구현체 추가만으로 자동 인식)
  - `HeaderCheck` — HSTS · CSP · X-Frame-Options · X-Content-Type-Options · Referrer-Policy. **존재만이 아니라 값의 강도까지 검증** — HSTS `max-age≥1년`, X-Frame-Options ∈ {DENY,SAMEORIGIN}, CSP `unsafe-inline`/와일드카드 거부, Referrer-Policy 유효 토큰, X-Content-Type-Options `nosniff`
  - `CookieCheck` — `Set-Cookie`의 Secure / HttpOnly / SameSite 플래그 (속성 토큰 파싱 — 값 속 문자열 오탐 방지). **`SameSite=None`은 `Secure` 동반 필수**로 검증
  - `TlsCheck` — HTTPS 사용 여부 · **호스트네임(SAN/CN) 검증** · **협상 프로토콜 버전 검증(TLS 1.2 미만 FAIL)** · 서버 인증서 만료 임박(임계 14일, 규칙명 고정 → PASS→FAIL 회귀 추적)
  - `SsrfGuard` — 모든 검사는 fetch 전에 대상 URL을 검증해 **내부/사설/링크로컬 주소(루프백·10/8·172.16/12·192.168/16·메타데이터 169.254.169.254)를 차단**한다 (스캐너 자체 SSRF 방어). *잔여: 리다이렉트 기반 SSRF는 후속 과제*
- **회귀 판정** — 스캔 결과를 직전 baseline과 비교해 `NEW/REGRESSION/FIXED/SAME` 산출 (`POST /api/targets/{id}/scan`)
- **HTML 리포트** — `REGRESSION` 강조 리포트 (`POST /api/targets/{id}/report`, ci 모드는 `reports/scan-report.html` 파일 생성)
- **정기 회귀 (GitHub Actions)** — `ci` 프로파일: 서버 없이 스캔→리포트 파일→종료. baseline은 H2 파일 DB를 actions/cache로 실행 간 유지. 매일 06:20 KST 스케줄, `REGRESSION`/스캔 오류 시 워크플로우 실패 → GitHub 기본 알림
- **데모 실행** — `demo` 프로파일로 띄우면 샘플 대상을 스캔해 회귀 판정을 콘솔에 출력

**로드맵:**
- [ ] `AuthCheck` — 보호 엔드포인트가 무인증 시 401/403을 반환하는지(인가 회귀)
- [ ] Slack 알림 (현재는 워크플로우 실패 → GitHub 기본 알림)
- [ ] 운영 DB(PostgreSQL) 프로파일 분리 + 서버 상시 배포
- [ ] OWASP ZAP(DAST) 연동으로 실제 취약점 스캔까지 확장

## Stack

- **Java 17**, **Spring Boot 3.4.4** (Spring Web, Spring Data JPA)
- **H2** — 로컬 개발은 인메모리, `ci` 프로파일은 파일 DB(baseline 영속). 운영 PostgreSQL은 로드맵
- HTTP/TLS 점검은 **표준 라이브러리**(`java.net.http`, `javax.net.ssl`) — 외부 의존성 최소화
- **Thymeleaf** (엔진 코어만) — HTML 리포트 렌더링
- Lombok, JUnit 5

> 이전 프로토타입의 Selenium · REST Assured · iText · Mail 의존성은 피벗하며 제거했습니다.

## Run

```bash
# 일반 실행 (localhost:8080)
./gradlew bootRun

# 데모: 샘플 대상 스캔 결과를 콘솔에 출력
./gradlew bootRun --args='--spring.profiles.active=demo'

# CI 모드: 서버 없이 스캔 → reports/scan-report.html + summary.json 생성 후 종료
#   대상은 src/main/resources/application-ci.properties 의 testweave.ci.targets
./gradlew bootRun --args='--spring.profiles.active=ci'
```

REST 예시:

```bash
# 대상 등록
curl -X POST localhost:8080/api/targets -H 'Content-Type: application/json' \
  -d '{"name":"example","url":"https://example.com","checkTypes":"HEADER,COOKIE,TLS"}'

# 스캔 + 회귀 판정
curl -X POST localhost:8080/api/targets/1/scan
```

H2 콘솔: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:testweave`, user `sa`)

## Structure

```
src/main/java/com/testweave/
  TestweaveApplication.java          # Spring Boot 진입점
  domain/
    SecurityTarget.java              # 점검 대상 (url, checkTypes)
    ScanResult.java                  # 규칙별 스캔 결과 (PASS/FAIL)
  check/
    SecurityCheck.java               # 점검 모듈 인터페이스
    CheckOutcome.java                # 규칙 하나의 결과 (record)
    HeaderCheck.java / CookieCheck.java / TlsCheck.java
  scan/
    Regression.java                  # 회귀 판정 결과 (record)
  service/
    ScanService.java                 # 스캔 실행 + baseline 대비 회귀 계산
  report/
    ReportService.java               # HTML 리포트 렌더링 (Thymeleaf 엔진 직접 구성)
    TargetReport.java                # 리포트 단위 (record)
  repository/                        # Spring Data JPA
  controller/
    ScanController.java              # REST: 대상 등록/조회, 스캔/리포트 실행
  config/
    DemoDataRunner.java              # demo 프로파일 CLI 실행기
    CiScanRunner.java                # ci 프로파일: 스캔 → 리포트 파일 → 종료
src/main/resources/
  templates/report.html              # 리포트 템플릿 (REGRESSION 강조)
  application-ci.properties          # ci 대상/체크 설정 + H2 파일 DB
.github/workflows/security-scan.yml  # 매일 06:20 KST 스케줄 스캔 + 회귀 시 실패
```

설계 상세는 [`docs/DESIGN.md`](docs/DESIGN.md) 참고.
