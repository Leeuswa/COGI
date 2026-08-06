package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.admin.dto.AdminAiUsageResponseDTO;
import idu.sba.backend.domain.review.entity.AiUsageLog;
import idu.sba.backend.domain.review.repository.AiUsageLogRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUsageServiceImpl implements AdminUsageService{

    private final AiUsageLogRepository aiUsageLogRepository;
    private final UserRepository userRepository;


    @Override
    @Transactional(readOnly = true)
    public List<AdminAiUsageResponseDTO> getAiUsage(LocalDate from, LocalDate to) {
        // from 00:00:00 ~ to 23:59:59.999 (to 당일 끝까지 포함)
        var start = from.atStartOfDay();
        var end = to.atTime(LocalTime.MAX);
        var logs = aiUsageLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);

        // userId → nickname 을 한 번의 IN 조회로 채운다 (로그마다 조회하면 N+1)
        var userIds = logs.stream().map(AiUsageLog::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> nickById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return logs.stream()
                .map(l -> AdminAiUsageResponseDTO.of(l, nickById.get(l.getUserId())))
                .toList();
    }
}