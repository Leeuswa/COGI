package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.admin.dto.NoticeResponseDTO;
import idu.sba.backend.domain.admin.dto.SendNoticeResponseDTO;

import java.util.List;

// 관리자 전체 공지 메일 (ADM-003, API-019)
public interface AdminNoticeService {

    // 공지를 저장하고 활성 회원 전원에게 비동기 발송을 시작 → 대상 수 반환
    SendNoticeResponseDTO sendNoticeToAll(Long senderId, String subject, String content);

    // 공지 발송 이력 (최신순)
    List<NoticeResponseDTO> getNotices();
}
