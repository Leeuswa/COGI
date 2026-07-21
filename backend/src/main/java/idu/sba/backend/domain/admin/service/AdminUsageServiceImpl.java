package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.admin.dto.AdminAiUsageResponseDTO;
import idu.sba.backend.domain.review.repository.AiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUsageServiceImpl implements AdminUsageService{

    private final AiUsageLogRepository aiUsageLogRepository;


    @Override
    @Transactional(readOnly = true)
    public List<AdminAiUsageResponseDTO> getAiUsage(LocalDate from, LocalDate to) {
        // from 00:00:00 ~ to 23:59:59.999 (to 당일 끝까지 포함)
        var start = from.atStartOfDay();
        var end = to.atTime(LocalTime.MAX);
        return aiUsageLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end).stream()
                .map(AdminAiUsageResponseDTO::of)
                .toList();
    }
}