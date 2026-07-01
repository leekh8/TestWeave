# TestWeave 설계 — 보안 회귀 스캐너

> 2026-06-30 피벗 결정. 범용 QA 플랫폼 → **웹/API 보안 설정 회귀 스캐너**. 배경·근거는 이 문서 하단 "피벗 이유" 참고.

## 1. 핵심 개념

기능 테스트가 아니라 **보안 회귀(regression)** 가 제품의 심장이다. 단발 스캔("지금 HSTS가 없다")은 기존 도구(OWASP ZAP 등)로 충분하다. TestWeave의 차별점은 **직전 결과와 비교해 "후퇴"를 잡는 것**이다.

- `REGRESSION` — 직전 PASS → 이번 FAIL (보안이 나빠짐, 최우선 경보)
- `FIXED` — 직전 FAIL → 이번 PASS (개선됨)
- `NEW` — baseline에 없던 규칙
- `SAME` — 변화 없음

정기 실행(스케줄)과 결합하면 "이번 배포가 보안 설정을 후퇴시켰다"를 자동 감지한다.

## 2. 아키텍처

```
[등록] SecurityTarget (url, checkTypes)
   │
[스캔] ScanService.scan(targetId)
   │   ├─ baseline = 직전 스캔의 규칙별 상태 로드
   │   ├─ 각 SecurityCheck.run(target) → List<CheckOutcome>
   │   │      HeaderCheck / CookieCheck / TlsCheck (java.net.http, javax.net.ssl)
   │   ├─ ScanResult 저장 (PASS/FAIL/detail)
   │   └─ verdict(baseline, current) → Regression
   │
[출력] REST(JSON) · demo 프로파일(콘솔) · (로드맵) HTML 리포트
```

**설계 원칙 — 서버가 무거운 실행을 하지 않는다.** 이전 프로토타입은 웹 서버가 요청마다 `new ChromeDriver()`로 브라우저를 띄웠는데, 이는 (a) 무료 PaaS에서 실행 불가에 가깝고 (b) 동시성·안정성이 나쁘다. 보안 점검은 순수 HTTP/TLS 호출이라 브라우저가 필요 없고, 정기 실행은 서버 내부가 아니라 **CI(GitHub Actions)/CLI**에서 도는 것을 지향한다.

## 3. 확장 지점 — 새 점검 모듈 추가

`SecurityCheck` 인터페이스를 구현한 `@Component`를 추가하면 `ScanService`가 생성자 주입으로 자동 인식한다(코드 수정 불필요).

```java
@Component
public class AuthCheck implements SecurityCheck {
    public String type() { return "AUTH"; }
    public List<CheckOutcome> run(SecurityTarget t) { /* 무인증 요청 → 401/403 기대 */ }
}
```

`SecurityTarget.checkTypes`에 `"AUTH"`를 넣으면 즉시 실행 대상이 된다.

## 4. 도메인

| 엔티티 | 필드 | 역할 |
|---|---|---|
| `SecurityTarget` | name, url, checkTypes(콤마 구분) | 점검 대상 |
| `ScanResult` | target, checkType, rule, status, detail, scannedAt | 규칙 하나의 스캔 결과(이력 누적) |
| `Regression`(record) | checkType, rule, previous, current, verdict | 회귀 판정(저장 안 함, 응답용) |
| `CheckOutcome`(record) | rule, status, detail | 점검 모듈의 반환 단위 |

baseline은 `ScanResult` 이력에서 "규칙별 가장 최근 상태"로 계산한다(`findByTargetIdOrderByScannedAtDesc` → 각 rule 첫 등장).

## 5. 개발 순서 (각 단계가 독립 완결)

1. ✅ 구조 평탄화(중첩 Gradle 제거) + 도메인 피벗 + H2
2. ✅ `HeaderCheck` + 회귀 비교 + `demo` 콘솔 실행 (첫 완성점)
3. ✅ `CookieCheck` · `TlsCheck`
4. ⬜ HTML 리포트(Thymeleaf) — `REGRESSION` 강조
5. ⬜ `AuthCheck`(인가 회귀)
6. ⬜ GitHub Actions 스케줄(정기 회귀) + Slack 알림
7. ⬜ PostgreSQL 프로파일 분리 + 배포
8. ⬜ OWASP ZAP(DAST) 연동

## 6. 테스트 전략

- 순수 로직(`ScanService.verdict`)은 네트워크/DB 없이 단위 테스트 (`ScanServiceVerdictTest`)
- 점검 모듈은 로컬 테스트 서버(WireMock 등, 로드맵)로 헤더/쿠키 응답을 고정해 검증
- CI는 `./gradlew test` — 의존성 0의 순수 테스트만 빠르게

## 피벗 이유 (2026-06-30 결정 기록)

이전 범용 QA 플랫폼 방향은 다음 이유로 접었다:
- **차별화 부재** — Selenium+JUnit+Allure를 감싼 "플랫폼"은 이미 성숙 오픈소스가 많다.
- **과설계** — SaaS·클라우드·Slack·PDF까지 벌린 계획이 방치의 원인이었다.
- **아키텍처 결함** — 서버사이드 Selenium + 무료 PaaS는 배포 실패를 반복시켰다.
- **정체성** — 작성자는 보안 엔지니어. 보안 회귀 각도가 강점과 정합하고 차별화된다.

→ 도메인·점검 모듈만 보안용으로 교체하고, 범위를 헤더/쿠키/TLS 회귀로 좁혀 "완성 가능한 MVP"로 재출발.
