package idu.sba.backend.global.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiModelTest {

    @Test
    void 모델_ID로_provider를_올바르게_찾는다() {
        assertThat(AiModel.fromId("gemini-3.5-flash")).isEqualTo(AiModel.GEMINI_FLASH);
        assertThat(AiModel.fromId("llama-3.3-70b-versatile")).isEqualTo(AiModel.GROQ_LLAMA_70B);
        assertThat(AiModel.fromId("claude-haiku-4-5")).isEqualTo(AiModel.CLAUDE_HAIKU);
        assertThat(AiModel.fromId("claude-sonnet-5")).isEqualTo(AiModel.CLAUDE_SONNET);
        assertThat(AiModel.fromId("claude-opus-4-8")).isEqualTo(AiModel.CLAUDE_OPUS);
        assertThat(AiModel.fromId("gpt-5.6-luna")).isEqualTo(AiModel.GPT_LUNA);
        assertThat(AiModel.fromId("gpt-5.6-terra")).isEqualTo(AiModel.GPT_TERRA);
        assertThat(AiModel.fromId("gpt-5.6-sol")).isEqualTo(AiModel.GPT_SOL);
    }

    @Test
    void 각_모델은_올바른_provider에_매핑된다() {
        assertThat(AiModel.GEMINI_FLASH.provider()).isEqualTo(AiProvider.GEMINI);
        assertThat(AiModel.GROQ_LLAMA_70B.provider()).isEqualTo(AiProvider.GROQ);
        assertThat(AiModel.CLAUDE_HAIKU.provider()).isEqualTo(AiProvider.CLAUDE);
        assertThat(AiModel.CLAUDE_SONNET.provider()).isEqualTo(AiProvider.CLAUDE);
        assertThat(AiModel.CLAUDE_OPUS.provider()).isEqualTo(AiProvider.CLAUDE);
        assertThat(AiModel.GPT_LUNA.provider()).isEqualTo(AiProvider.OPENAI);
        assertThat(AiModel.GPT_TERRA.provider()).isEqualTo(AiProvider.OPENAI);
        assertThat(AiModel.GPT_SOL.provider()).isEqualTo(AiProvider.OPENAI);
    }

    @Test
    void 존재하지_않는_모델_ID면_예외를_던진다() {
        assertThatThrownBy(() -> AiModel.fromId("no-such-model"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-model");
    }
}
