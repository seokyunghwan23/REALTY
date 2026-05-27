package com.realty.Realtymate.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IPBlacklistManager {
    private static final Logger logger = LoggerFactory.getLogger(IPBlacklistManager.class);

    private static final String BLACKLIST_FILE = "config/ip-blacklist.txt";
    private static final String LOG_DIR = "logs/blocked-ips";
    private static final int MAX_ATTEMPTS = 1; // 1번만 잘못된 요청해도 즉시 차단
    // 영구 차단 - 자동 해제 없음

    // IP별 시도 횟수 추적
    private final Map<String, Integer> attemptCounter = new ConcurrentHashMap<>();

    // IP별 차단 시간 추적
    private final Map<String, LocalDateTime> blockTime = new ConcurrentHashMap<>();

    // 블랙리스트 IP 집합
    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        try {
            // 디렉토리 생성
            Files.createDirectories(Paths.get("config"));
            Files.createDirectories(Paths.get(LOG_DIR));

            // 블랙리스트 파일 로드
            loadBlacklist();
        } catch (IOException e) {
            // 초기화 실패 시에만 에러 로그
            logger.error("❌ IP Blacklist Manager 초기화 실패", e);
        }
    }

    /**
     * 블랙리스트 파일에서 IP 목록 로드
     */
    private void loadBlacklist() throws IOException {
        Path path = Paths.get(BLACKLIST_FILE);
        if (Files.exists(path)) {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                if (!line.trim().isEmpty() && !line.startsWith("#")) {
                    String[] parts = line.split(",");
                    String ip = parts[0].trim();
                    blacklist.add(ip);

                    // 차단 시간 복원 (있는 경우)
                    if (parts.length > 1) {
                        try {
                            LocalDateTime time = LocalDateTime.parse(parts[1].trim());
                            blockTime.put(ip, time);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
    }

    /**
     * 블랙리스트를 파일에 저장
     */
    private synchronized void saveBlacklist() {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("# IP Blacklist - Auto-generated");
            lines.add("# Format: IP,BlockTime");
            lines.add("# Updated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            lines.add("");

            for (String ip : blacklist) {
                LocalDateTime time = blockTime.get(ip);
                if (time != null) {
                    lines.add(ip + "," + time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                } else {
                    lines.add(ip);
                }
            }

            Files.write(Paths.get(BLACKLIST_FILE), lines);
        } catch (IOException ignored) {
        }
    }

    /**
     * IP가 차단되었는지 확인
     */
    public boolean isBlocked(String ip) {
        return blacklist.contains(ip);
    }

    /**
     * 잘못된 요청 기록 및 자동 차단
     */
    public void recordInvalidRequest(String ip, String reason) {
        if (ip == null || ip.isEmpty()) {
            return;
        }

        // 이미 차단된 IP면 로그만 기록
        if (blacklist.contains(ip)) {
            logBlockedRequest(ip, reason);
            return;
        }

        // 시도 횟수 증가
        int attempts = attemptCounter.merge(ip, 1, Integer::sum);

        // 임계값 초과 시 블랙리스트 추가
        if (attempts >= MAX_ATTEMPTS) {
            addToBlacklist(ip, reason);
            attemptCounter.remove(ip); // 카운터 초기화
        }
    }

    /**
     * 블랙리스트에 IP 추가
     */
    private void addToBlacklist(String ip, String reason) {
        blacklist.add(ip);
        blockTime.put(ip, LocalDateTime.now());
        saveBlacklist();
        logBlacklistAction(ip, reason, "BLOCKED");
    }

    /**
     * 블랙리스트에서 IP 제거
     */
    public void removeFromBlacklist(String ip) {
        if (blacklist.remove(ip)) {
            blockTime.remove(ip);
            attemptCounter.remove(ip);
            saveBlacklist();
            logBlacklistAction(ip, "Manual removal", "UNBLOCKED");
        }
    }

    /**
     * 블랙리스트 통계 정보 (로그 없음)
     */
    public void logBlacklistStats() {
        // 로그 없이 조용히 실행
    }

    /**
     * 차단된 요청 로그 기록
     */
    private void logBlockedRequest(String ip, String reason) {
        try {
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Path logFile = Paths.get(LOG_DIR, "blocked-" + date + ".log");

            String logEntry = String.format("[%s] BLOCKED: IP=%s, Reason=%s%n",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    ip, reason);

            Files.write(logFile, logEntry.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    /**
     * 블랙리스트 변경 로그 기록
     */
    private void logBlacklistAction(String ip, String reason, String action) {
        try {
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Path logFile = Paths.get(LOG_DIR, "blacklist-changes-" + date + ".log");

            String logEntry = String.format("[%s] %s: IP=%s, Reason=%s%n",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    action, ip, reason);

            Files.write(logFile, logEntry.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    /**
     * 블랙리스트 정보 조회
     */
    public Map<String, Object> getBlacklistInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("totalBlocked", blacklist.size());
        info.put("blacklist", new ArrayList<>(blacklist));
        info.put("attemptCounter", new HashMap<>(attemptCounter));
        return info;
    }

    /**
     * 시도 횟수 카운터 초기화
     */
    public void resetAttemptCounter() {
        attemptCounter.clear();
    }
}