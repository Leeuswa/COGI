package idu.sba.backend.domain.payment.dto;


// GET /api/plans 응답용 - 요금제 목록 보여줄 때 씀
public record PlanResponseDTO(
    Long id,               // 요금제 ID
    String name,            // 요금제 이름 (FREE/PRO/MAX)
    int dailyCreditLimit,   // 하루에 쓸 수 있는 크레딧 한도
    String allowedModels,   // 이 요금제에서 쓸 수 있는 AI 모델 목록 (콤마 구분 문자열)
    int price                // 월 구독료 (원)
) {}
