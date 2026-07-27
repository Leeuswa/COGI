package idu.sba.backend.domain.inquiry.dto;

import idu.sba.backend.domain.inquiry.entity.Inquiry;

import java.time.LocalDateTime;

// 문의 한 건 (사용자·관리자 공용). asker* 는 관리자 목록에서 사용.
public record InquiryResponseDTO(
        Long id,
        String title,
        String content,
        String answer,      // 미답변이면 null
        boolean answered,
        String askerEmail,
        String askerNickname,
        LocalDateTime createdAt,
        LocalDateTime answeredAt
) {
    public static InquiryResponseDTO of(Inquiry i) {
        return new InquiryResponseDTO(
                i.getId(), i.getTitle(), i.getContent(), i.getAnswer(), i.isAnswered(),
                i.getAskerEmail(), i.getAskerNickname(), i.getCreatedAt(), i.getAnsweredAt());
    }
}
