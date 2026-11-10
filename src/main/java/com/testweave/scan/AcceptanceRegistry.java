package com.testweave.scan;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 위험 수용 등록부. 고칠 수 없거나 당장 고치지 않기로 한 미충족 항목을
 * 사유와 기한을 붙여 기록해 두고, 그 기간 동안만 미충족 집계에서 뺀다.
 *
 * <p>이 기능이 필요한 이유는 영원히 빨간 숫자가 사람을 무감각하게 만들기 때문이다.
 * 실측 사례: 이 저장소의 CI 대상인 GitHub Pages는 응답 헤더를 붙일 방법이 없어서
 * 4건이 매일 같은 값으로 FAIL이었다. 고칠 수 없는 것이 매일 경고로 뜨면 사람은
 * 경고 전체를 안 보게 되고, 그때 진짜 회귀가 같은 화면에 섞여 들어온다.
 *
 * <p>그래서 수용은 "못 고쳤다"가 아니라 <b>"알고 있고, 이유가 있고, 언제 다시 볼지
 * 정했다"</b>는 기록이다. 세 가지가 다 있어야 수용이고, 하나라도 빠지면 그냥 방치다.
 *
 * <h2>지키는 선</h2>
 * <ul>
 *   <li>회귀(REGRESSION)와 소멸(MISSING)은 수용할 수 없다. 수용은 "지금 상태를 안다"는
 *       선언인데 회귀는 상태가 바뀐 것이라 전제가 이미 깨졌다. 여기서 양보하면
 *       이 도구는 스캐너가 아니라 알리바이가 된다.</li>
 *   <li>기한은 필수다. 무기한 수용은 잊힌 수용과 구별되지 않는다.</li>
 *   <li>기한 상한이 있다. 10년 뒤 날짜를 적는 것은 무기한을 우회하는 것과 같다.</li>
 *   <li>형식이 틀린 줄은 무시하지 않고 기동을 실패시킨다. 조용히 넘기면 오타 하나로
 *       수용이 사라지거나(덜 위험) 엉뚱한 규칙에 붙는다(더 위험).</li>
 * </ul>
 */
@Component
public class AcceptanceRegistry {

    /**
     * 등록부 파일. 기본값은 저장소에 커밋되는 {@code exceptions.txt}다.
     *
     * <p>대상 목록(targets)과 달리 이 파일은 일부러 형상관리 대상으로 둔다.
     * 누가 언제 무엇을 왜 수용했는지가 git 이력에 남아야 그게 감사 증적이 된다.
     * 업무 대상처럼 공개하면 안 되는 내용은 {@code exceptions.local.txt}에 두고,
     * 그 파일이 있으면 그쪽이 이긴다.
     */
    @Value("${testweave.exceptions-file:exceptions.txt}")
    private String file;

    @Value("${testweave.exceptions-local-file:exceptions.local.txt}")
    private String localFile;

    /** 수용 기한 상한(일). 기본 1년. */
    @Value("${testweave.exceptions-max-days:365}")
    private long maxDays;

    private List<Entry> entries = List.of();

    /** 이번 실행에서 실제로 적용된 등록부 줄. 안 쓰인 줄을 찾아내려고 모은다. */
    private final Set<String> used = new LinkedHashSet<>();

    /** 등록부 한 줄. */
    public record Entry(String url, String checkType, String rule, LocalDate until, String reason) {
        public String key() {
            return url + "|" + checkType + "|" + rule;
        }
    }

    @PostConstruct
    void load() {
        Path path = Path.of(localFile);
        if (!Files.exists(path)) {
            path = Path.of(file);
        }
        if (!Files.exists(path)) {
            entries = List.of();
            return;
        }
        try {
            entries = parse(Files.readAllLines(path, StandardCharsets.UTF_8), maxDays);
        } catch (IOException e) {
            throw new UncheckedIOException("수용 등록부를 읽을 수 없습니다: " + path.toAbsolutePath(), e);
        }
        if (!entries.isEmpty()) {
            System.out.printf("수용 등록부: %s (%d건)%n", path.toAbsolutePath(), entries.size());
        }
    }

    /**
     * 등록부 파싱. 순수 함수라 테스트로 고정한다.
     *
     * <p>형식: {@code 대상URL | 모듈 | 규칙 | 기한(YYYY-MM-DD) | 사유}
     *
     * <p>지난 기한은 오류가 아니다. 만료된 수용은 지워야 할 것이 아니라 <b>보여야 할 것</b>이라,
     * 파싱을 통과시킨 뒤 판정 단계에서 EXPIRED로 표면화한다.
     */
    static List<Entry> parse(List<String> lines, long maxDays) {
        List<Entry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int no = i + 1;
            String[] f = line.split("[|]", -1);
            if (f.length != 5) {
                throw new IllegalStateException(no + "번째 줄: 칸이 5개여야 합니다"
                        + " (대상URL | 모듈 | 규칙 | 기한 | 사유). 실제 " + f.length + "개: " + line);
            }
            String url = f[0].trim();
            String checkType = f[1].trim();
            String rule = f[2].trim();
            String rawUntil = f[3].trim();
            String reason = f[4].trim();
            if (url.isEmpty() || checkType.isEmpty() || rule.isEmpty()) {
                throw new IllegalStateException(no + "번째 줄: 대상URL, 모듈, 규칙은 비울 수 없습니다: " + line);
            }
            if (reason.isEmpty()) {
                // 사유 없는 수용은 다음 사람이 판단을 이어받을 수 없다. 그건 수용이 아니라 방치다.
                throw new IllegalStateException(no + "번째 줄: 사유가 없습니다. 왜 수용하는지 적으세요: " + line);
            }
            LocalDate until;
            try {
                until = LocalDate.parse(rawUntil);
            } catch (DateTimeParseException e) {
                throw new IllegalStateException(no + "번째 줄: 기한이 YYYY-MM-DD 형식이 아닙니다: " + rawUntil);
            }
            long span = ChronoUnit.DAYS.between(today, until);
            if (span > maxDays) {
                throw new IllegalStateException(no + "번째 줄: 기한이 상한(" + maxDays + "일)을 넘습니다 ("
                        + span + "일 뒤). 먼 날짜는 무기한 수용을 우회하는 것과 같습니다: " + rawUntil);
            }
            Entry entry = new Entry(url, checkType, rule, until, reason);
            if (!seen.add(entry.key())) {
                // 같은 항목이 두 줄이면 어느 기한이 유효한지 읽는 사람마다 달라진다.
                throw new IllegalStateException(no + "번째 줄: 같은 항목이 이미 등록돼 있습니다: " + entry.key());
            }
            out.add(entry);
        }
        return out;
    }

    /** 실행 단위로 사용 흔적을 초기화한다. 한 회차에 안 쓰인 줄을 정확히 세기 위해서다. */
    public void resetUsage() {
        used.clear();
    }

    /**
     * 판정 하나에 등록부를 적용한다. 해당 없으면 null.
     */
    public Acceptance decide(String url, Regression r, LocalDate today) {
        Entry e = find(url, r.checkType(), r.rule());
        if (e == null) {
            return null;
        }
        if ("REGRESSION".equals(r.verdict()) || "MISSING".equals(r.verdict())) {
            // 등록부에 있어도 적용하지 않는다. 있다는 사실 자체는 보여준다.
            // 수용해 둔 항목이 회귀했다는 것은 수용 판단의 근거가 바뀌었다는 뜻이다.
            used.add(e.key());
            return new Acceptance(e.until(), e.reason(), Acceptance.State.REJECTED, 0);
        }
        if (!"FAIL".equals(r.current())) {
            // 이미 충족된 항목. 수용이 필요 없어졌으니 등록부에서 지울 대상이다.
            return null;
        }
        used.add(e.key());
        long left = ChronoUnit.DAYS.between(today, e.until());
        Acceptance.State state = left < 0 ? Acceptance.State.EXPIRED : Acceptance.State.ACTIVE;
        return new Acceptance(e.until(), e.reason(), state, left);
    }

    private Entry find(String url, String checkType, String rule) {
        for (Entry e : entries) {
            if (e.url().equals(url) && e.checkType().equals(checkType) && e.rule().equals(rule)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 이번 회차에 한 번도 적용되지 않은 등록부 줄.
     *
     * <p>규칙명이 바뀌었거나, 대상에서 빠졌거나, 이미 고쳐졌는데 줄만 남은 경우다.
     * 등록부는 정확히 이렇게 썩는다. 쌓인 줄이 많아질수록 아무도 안 읽게 되고,
     * 그때부터는 등록부가 있으나 마나다.
     */
    public List<Entry> unusedEntries() {
        return entries.stream().filter(e -> !used.contains(e.key())).toList();
    }

    public List<Entry> entries() {
        return entries;
    }

    /**
     * Spring 없이 등록부를 직접 주입한다. 테스트와 임베딩 용도다.
     * 생성자를 늘리지 않는 이유는 @Component 후보 생성자가 둘이 되면 주입이 모호해지기 때문이다.
     */
    public void configure(List<Entry> entries, long maxDays) {
        this.entries = List.copyOf(entries);
        this.maxDays = maxDays;
        this.used.clear();
    }
}
