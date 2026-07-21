package idu.sba.backend.domain.review.service;

import idu.sba.backend.domain.review.entity.ReviewGuideline;
import idu.sba.backend.domain.review.repository.ReviewGuidelineRepository;
import idu.sba.backend.domain.user.entity.Level;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

// 최종 시스템 프롬프트 = 레벨 지침(공통 규칙이 이미 안에 포함됨) + prompt_plan_{plan}.txt
// 레벨 지침은 관리자가 DB(review_guidelines)로 덮어쓸 수 있고, 없으면 prompt_level_*.txt 파일이 폴백.
// (_common_rules.txt는 참고용 원본이고, 레벨 프롬프트 안에 이미 이어붙여진 채라 여기서 따로 안 붙임 — 중복 방지)
@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private static final String PROMPT_DIR = "prompts/";

    private final ReviewGuidelineRepository guidelineRepository;

    public String build(Level level, String planName) {
        String levelPrompt = resolveLevelGuideline(level);
        String planPrompt = readResource("prompt_plan_" + planName.toLowerCase() + ".txt");
        return levelPrompt + "\n\n" + planPrompt;
    }

    // 레벨 지침: DB에 있으면 그 값, 없으면 파일 폴백. 관리자 조회(GET)도 이걸 재사용해
    // "리뷰가 실제로 쓰는 값"을 그대로 화면에 보여준다.
    public String resolveLevelGuideline(Level level) {
        return guidelineRepository.findByLevel(level)
                .map(ReviewGuideline::getPromptGuideline)
                .orElseGet(() -> readResource("prompt_level_" + level.name().toLowerCase() + ".txt"));
    }

    private String readResource(String fileName) {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_DIR + fileName);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일을 읽을 수 없습니다: " + fileName, e);
        }
    }

}
