package idu.sba.backend.global.ai;

import idu.sba.backend.domain.review.service.PromptBuilder;
import idu.sba.backend.domain.user.entity.Level;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 AI API에 네트워크 호출을 보내는 통합 테스트.
//   !! 실행할 때마다 실제 과금이 발생
//
// AiReviewClient.AiReview(존재한 적 없는 타입)/4-arg review(...)를 참조하고 있어 컴파일이 안 되던 걸
// 실제 인터페이스(AiReviewResult 반환, 5-arg review)에 맞춰 최소 수정함 — 동작 자체는 검증 안 함(실제 과금 발생).

@SpringBootTest
class AiReviewClientIntegrationTest {

    @Autowired
    private AiReviewClient aiReviewClient;

    @Autowired
    private PromptBuilder promptBuilder;

    private static final String SAMPLE_CODE = """
            public class Sample {
                public static void main(String[] args) {
                    int x = 1
                    System.out.println(x);
                }
            }
            """;

    @Test
    void 비로그인_gemini_실제_호출() {
        callAndPrint(AiModel.GEMINI_FLASH);
    }

    @Test
    void 비로그인_groq_실제_호출() {
        callAndPrint(AiModel.GROQ_LLAMA_70B);
    }

    @Test
    void free_claude_haiku_실제_호출() {
        callAndPrint(AiModel.CLAUDE_HAIKU);
    }

    @Test
    void free_gpt_luna_실제_호출() {
        callAndPrint(AiModel.GPT_LUNA);
    }

    @Test
    void pro_claude_sonnet_실제_호출() {
        callAndPrint(AiModel.CLAUDE_SONNET);
    }

    @Test
    void pro_gpt_terra_실제_호출() {
        callAndPrint(AiModel.GPT_TERRA);
    }

    @Test
    void max_claude_opus_실제_호출() {
        callAndPrint(AiModel.CLAUDE_OPUS);
    }

    @Test
    void max_gpt_sol_실제_호출() {
        callAndPrint(AiModel.GPT_SOL);
    }

    private void callAndPrint(AiModel model) {
        String systemPrompt = promptBuilder.build(Level.BEGINNER, "FREE");
        AiReviewResult result = aiReviewClient.review(model, systemPrompt, SAMPLE_CODE, "java", AiInputType.PASTED_CODE);

        System.out.println("===== " + model.name() + " (" + model.id() + ") =====");
        System.out.println("summary: " + result.summary());
        result.issues().forEach(c ->
                System.out.println(" - [" + c.category() + "/" + c.severity() + "] " + c.description()));

        assertThat(result.summary()).isNotBlank();
        assertThat(result.issues()).isNotEmpty();
    }
}
