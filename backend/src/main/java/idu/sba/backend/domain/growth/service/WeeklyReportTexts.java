package idu.sba.backend.domain.growth.service;

import java.util.ArrayList;
import java.util.List;

// 주간 리포트 문구 — 메일(WeeklyReportSender)과 화면(WeeklyReportService)이 같은 규칙을 쓰도록 한 곳에 둔다
final class WeeklyReportTexts {

    private WeeklyReportTexts() {}

    // 카테고리 코드 → 한글 (프론트 CATEGORY_KO와 동일)
    static String catKo(String code) {
        if (code == null) return null;
        return switch (code) {
            case "BUG" -> "버그";
            case "PERFORMANCE" -> "성능";
            case "CODE_SMELL" -> "코드 냄새";
            case "CONVENTION" -> "컨벤션";
            case "SECURITY" -> "보안";
            default -> code;
        };
    }

    // 코기의 다음 주 추천
    static List<String> nextActions(String topCategory, int issues, int resolved) {
        List<String> a = new ArrayList<>();
        if (topCategory != null) a.add(catKo(topCategory) + " 유형 학습카드를 복습해보세요.");
        if (resolved < issues) a.add("미해결 이슈를 스튜디오에서 마저 판정해보세요.");
        a.add("이번 주도 PR을 올려 리뷰를 받아보세요.");
        return a;
    }
}
