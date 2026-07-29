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

// 최종 시스템 프롬프트 = 공통 규칙(_common_rules.txt, 코드 고정) + 레벨 지침 + prompt_plan_{plan}.txt
// 레벨 지침은 관리자가 DB(review_guidelines)로 덮어쓸 수 있고, 없으면 prompt_level_*.txt 파일이 폴백.
// 예전엔 공통 규칙 전체를 레벨 파일 3개에 각각 복사해뒀었는데(주석으로 "중복 방지"라 적어놓고 실제로는
// 3곳에 중복 — 하나만 고치고 나머지를 깜빡하면 조용히 어긋나는 위험이 있었음), 여기서 한 번만
// 이어붙이도록 바꿔 단일 소스로 정리(2026-07-29). 부수 효과로 관리자 콘솔(getGuidelines/saveGuideline)이
// 이제 "레벨별 커스터마이징 가능한 부분"만 보여주고, JSON 스키마 등 스키마-critical 규칙은
// 관리자가 실수로 지우지 못하게 코드에서 항상 강제한다.
@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private static final String PROMPT_DIR = "prompts/";

    private final ReviewGuidelineRepository guidelineRepository;

    public String build(Level level, String planName) {
        String commonRules = readResource("_common_rules.txt");
        String levelPrompt = resolveLevelGuideline(level);
        String planPrompt = readResource("prompt_plan_" + planName.toLowerCase() + ".txt");
        return commonRules + "\n\n" + levelPrompt + "\n\n" + planPrompt;
    }

    // 리뷰 후속 질문용 — 새 프롬프트 파일을 따로 만들지 않고, 리뷰용 지침을 그대로 재사용하되
    // "출력은 JSON 스키마가 아니라 대화체 텍스트" 라는 오버라이드만 뒤에 덧붙인다. 수준별 설명 방식
    // (용어 풀어쓰기·비유·어투)은 리뷰와 동일하게 적용되는 게 맞아서 그 부분은 그대로 살린다.
    public String buildFollowUpQuestion(Level level) {
        String levelPrompt = readResource("_common_rules.txt") + "\n\n" + resolveLevelGuideline(level);
        String override = """

                ─────────────────────────────
                [후속 질문 모드 — 위 출력 형식 규칙은 이번 요청에는 적용하지 않습니다]
                지금 요청은 새 코드 리뷰가 아니라, 방금 나온 리뷰 결과에 대해 사용자가 후속 질문을 한 것입니다.
                - 위에서 정의한 JSON 스키마({"summary":...,"issues":[...]})는 무시하고, 순수 대화체 한국어
                  텍스트로만 답합니다. 코드 블록이 필요하면 위 지침대로 마크다운(```언어\\n코드\\n```)으로
                  표시하되, 전체 응답을 JSON으로 감싸지 않습니다.
                - 위에 있는 "수준별" 설명 방식(용어를 처음 등장할 때 풀어서 설명하기, 실생활 비유는 최대 1개,
                  평가하듯 말하지 않기 등)은 이번에도 그대로 지킵니다.
                - 아래 [리뷰 맥락]에 있는 원본 코드와 이미 발견된 이슈 목록을 참고해서, 사용자의 질문에
                  직접적으로 답합니다.
                - 질문과 무관한 새로운 문제를 먼저 나서서 지적하지 않습니다. 근거 없는 이슈를 지어내지 않습니다.
                """;
        return levelPrompt + "\n" + override;
    }

    // 이슈 재검증 [설계 추론] — 새 리뷰가 아니라 "이슈 하나가 고쳐졌는지"만 좁게 판정.
    // 후속 질문과 마찬가지로 새 프롬프트 파일 없이 리뷰용 수준별 지침을 재사용하되, 이번엔
    // 대화체 텍스트도 아니고 최소 JSON({"stillPresent":...,"explanation":...})만 응답하게 한다.
    public String buildReverify(Level level) {
        String levelPrompt = readResource("_common_rules.txt") + "\n\n" + resolveLevelGuideline(level);
        String override = """

                ─────────────────────────────
                [이슈 재검증 모드 — 위 출력 형식 규칙은 이번 요청에는 적용하지 않습니다]
                지금 요청은 새 리뷰가 아니라, 아래 [원래 이슈]에서 지적된 문제 딱 하나가
                사용자가 새로 제출한 [수정된 코드]에서 여전히 남아있는지만 판정하는 것입니다.
                - 다른 새로운 문제를 찾아서 지적하지 않습니다. 오직 원래 이슈가 해결됐는지만 봅니다.
                - 다른 텍스트 없이 반드시 다음 JSON 형식으로만 답합니다(키 이름 그대로, camelCase):
                  {"stillPresent": true 또는 false, "explanation": "판단 이유를 한두 문장으로"}
                - stillPresent는 원래 문제가 여전히 있으면 true, 실제로 고쳐졌으면 false입니다.
                - explanation은 위에 있는 "수준별" 설명 방식(용어 풀어쓰기·비유·평가하지 않는 어투)을
                  그대로 지켜서 씁니다. JSON으로 감싸는 것 외엔 마크다운 코드펜스를 쓰지 않습니다.
                """;
        return levelPrompt + "\n" + override;
    }

    // Groq(llama) 전용 언어 강제 규칙 — 게스트 리뷰가 Groq로 폴백할 때만 프롬프트 끝에 덧붙인다.
    // 다른 프롬프트처럼 파일(prompt_groq_language.txt)로 관리한다.
    public String readGroqLanguageRule() {
        return readResource("prompt_groq_language.txt");
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
