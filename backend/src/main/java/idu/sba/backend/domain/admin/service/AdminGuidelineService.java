package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.user.entity.Level;

import java.util.Map;

// 관리자 수준별 리뷰 지침 (ADM-004, API-020~021)
public interface AdminGuidelineService {

    // { "BEGINNER": "지침…", "INTERMEDIATE": "…", "ADVANCED": "…" }
    Map<String, String> getGuidelines();

    void saveGuideline(Level level, String promptGuideline);
}
