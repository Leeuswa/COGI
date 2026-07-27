package idu.sba.backend.domain.learning.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

// 학습카드·퀴즈 프롬프트 조립. review의 PromptBuilder와 같은 방식으로 classpath txt를 읽어 이어붙인다.
// 차등 축이 둘이다 — 사용자 수준(초/중/고)과 실제로 호출되는 모델의 티어.
@Component
public class LearningPromptBuilder {

    private static final String DIR = "prompts/learning/"; // 프롬프트 파일이 모여 있는 경로

    // 카드 생성 프롬프트 = 공통 규칙 + 수준별 지침 + 모델 티어 보정
    public String buildCard(String level, String modelName) {
        return read("card_common.txt")
                + "\n\n" + read("card_level_" + levelSuffix(level) + ".txt")
                + "\n\n" + read("model_tier_" + tierOf(modelName) + ".txt");
    }

    // 퀴즈 생성 프롬프트 — 유형은 파일이 아니라 문장 한 줄로 덧붙인다(유형별 규칙은 quiz_common에 다 있음)
    public String buildQuiz(String level, String questionType, String modelName) {
        return read("quiz_common.txt")
                + "\n\n" + read("quiz_level_" + levelSuffix(level) + ".txt")
                + "\n\n" + read("model_tier_" + tierOf(modelName) + ".txt")
                + "\n\n이번에 낼 문제 유형: " + questionType;
    }

    // 학습 계획 프롬프트 — 수준은 안 타고 모델 티어만 반영한다(계획 자체는 등급으로 갈리므로)
    public String buildStudyPlan(String modelName) {
        return read("study_plan.txt")
                + "\n\n" + read("model_tier_" + tierOf(modelName) + ".txt");
    }

    // 수준 문자열 → 파일 접미사. 비었거나 모르는 값이면 초급으로 떨어뜨린다
    private String levelSuffix(String level) {
        return switch (level == null ? "" : level.toUpperCase()) {
            case "INTERMEDIATE" -> "intermediate";
            case "ADVANCED" -> "advanced";
            default -> "beginner";
        };
    }

    // 모델 id → 티어. 프론트 constants.ts의 MODEL_TIERS와 값을 맞춰 뒀다.
    // AiModel enum은 다른 팀원 코드라 건드리지 않으려고 여기서 따로 매핑한다
    private int tierOf(String modelName) {
        return switch (modelName == null ? "" : modelName) {
            case "claude-sonnet-5", "gpt-5.6-terra" -> 2;
            case "claude-opus-4-8", "gpt-5.6-sol" -> 3;
            default -> 1;
        };
    }

    // 프롬프트 파일 읽기 — 없으면 배포가 잘못된 것이라 그대로 터뜨린다
    private String read(String fileName) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(DIR + fileName).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일을 읽을 수 없습니다: " + fileName, e);
        }
    }
}
