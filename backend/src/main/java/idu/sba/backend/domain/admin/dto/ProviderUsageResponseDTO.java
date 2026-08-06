package idu.sba.backend.domain.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// 벤더(OpenAI/Anthropic) Admin API가 보고한 일자별 실사용량.
// 우리 ai_usage_logs 자체집계(AdminAiUsageResponseDTO)와는 별개 지표 — 건별 정보(유저/요청유형)는 벤더가 주지 않는다.
// cost는 벤더 청구 리포트 조회가 실패했거나 값이 없으면 null.
public record ProviderUsageResponseDTO(
        String vendor,        // OPENAI | CLAUDE
        LocalDate date,
        long inputTokens,
        long outputTokens,
        BigDecimal cost       // USD
) {}
