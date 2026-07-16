package idu.sba.backend.domain.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_usage_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId; //nullable — 게스트 호출이면 null(이 경로는 항상 로그인 사용자)
    private String modelName;
    private Integer inputTokens;
    private Integer outputTokens;

    @Column(precision = 10, scale = 4)
    private BigDecimal cost;

    private String requestType; //REVIEW 고정(이번 범위) — LEARNING_CARD/SKILL_RECOMMEND는 해당 기능 담당자 몫

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
    }

    private AiUsageLog(Long userId, String modelName, Integer inputTokens, Integer outputTokens,
                        BigDecimal cost, String requestType){
        this.userId = userId;
        this.modelName = modelName;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cost = cost;
        this.requestType = requestType;
    }

    public static AiUsageLog of(Long userId, String modelName, int inputTokens, int outputTokens,
                                 double cost, String requestType){
        return new AiUsageLog(userId, modelName, inputTokens, outputTokens, BigDecimal.valueOf(cost), requestType);
    }

}
