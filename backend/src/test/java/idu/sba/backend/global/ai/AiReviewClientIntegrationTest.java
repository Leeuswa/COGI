package idu.sba.backend.global.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 AI API에 네트워크 호출을 보내는 통합 테스트.
//   !! 실행할 때마다 실제 과금이 발생

@SpringBootTest
class AiReviewClientIntegrationTest {

    @Autowired
    private AiReviewClient aiReviewClient;

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
        AiReviewClient.AiReview result = aiReviewClient.review(model, SAMPLE_CODE, "java", "BEGINNER");

        System.out.println("===== " + model.name() + " (" + model.id() + ") =====");
        System.out.println("summary: " + result.summary());
        result.toReviewComments().forEach(c ->
                System.out.println(" - [" + c.getCategory() + "/" + c.getSeverity() + "] " + c.getDescription()));

        assertThat(result.summary()).isNotBlank();
        assertThat(result.toReviewComments()).isNotEmpty();
    }
}
