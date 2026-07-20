package idu.sba.backend.domain.review.service;

import idu.sba.backend.domain.user.entity.Level;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

// 최종 시스템 프롬프트 = prompt_level_{level}.txt(공통 규칙이 이미 안에 포함됨) + prompt_plan_{plan}.txt
// (COGI_설계결정_정리.md 기준. _common_rules.txt는 참고용 원본이고, prompt_level_*.txt 안에
//  이미 그 내용이 이어붙여진 채로 들어있어서 여기서 따로 또 붙이지 않는다 — 중복 방지)
@Component
public class PromptBuilder {

    private static final String PROMPT_DIR = "prompts/";

    public String build(Level level, String planName) {
        String levelPrompt = readResource("prompt_level_" + level.name().toLowerCase() + ".txt");
        String planPrompt = readResource("prompt_plan_" + planName.toLowerCase() + ".txt");
        return levelPrompt + "\n\n" + planPrompt;
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
