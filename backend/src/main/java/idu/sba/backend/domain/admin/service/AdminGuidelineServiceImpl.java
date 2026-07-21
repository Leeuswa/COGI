package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.review.entity.ReviewGuideline;
import idu.sba.backend.domain.review.repository.ReviewGuidelineRepository;
import idu.sba.backend.domain.review.service.PromptBuilder;
import idu.sba.backend.domain.user.entity.Level;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminGuidelineServiceImpl implements AdminGuidelineService {

    private final ReviewGuidelineRepository repository;
    private final PromptBuilder promptBuilder; // DB→파일 폴백 로직 재사용

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getGuidelines() {
        // 리뷰가 실제로 쓰는 값(DB 있으면 DB, 없으면 파일)을 레벨별로 그대로 보여준다
        Map<String, String> map = new LinkedHashMap<>();
        for (Level level : Level.values()) {
            map.put(level.name(), promptBuilder.resolveLevelGuideline(level));
        }
        return map;
    }

    @Override
    @Transactional
    public void saveGuideline(Level level, String promptGuideline) {
        // 있으면 수정, 없으면 생성 (레벨당 1행 upsert)
        repository.findByLevel(level)
                .ifPresentOrElse(
                        g -> g.update(promptGuideline),
                        () -> repository.save(ReviewGuideline.of(level, promptGuideline)));
    }
}
