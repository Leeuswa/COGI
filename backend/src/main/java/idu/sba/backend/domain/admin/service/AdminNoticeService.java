package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.admin.dto.NoticeResponseDTO;
import idu.sba.backend.domain.admin.dto.SendNoticeResponseDTO;

import java.util.List;

// 관리자 전체 공지 메일 (ADM-003, API-019)
public interface AdminNoticeService {

    // 공지 저장 + 활성 회원 전원에게 인앱 알림. urgent=true면 이메일까지 비동기 발송. → 대상 수 반환
    SendNoticeResponseDTO sendNoticeToAll(Long senderId, String subject, String content, boolean urgent);

    // 공지 발송 이력 (최신순)
    List<NoticeResponseDTO> getNotices();

    // 발송 이력에서 공지 삭제
    void deleteNotice(Long noticeId);
}
