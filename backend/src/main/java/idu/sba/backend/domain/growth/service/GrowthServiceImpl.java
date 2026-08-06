package idu.sba.backend.domain.growth.service;

import idu.sba.backend.domain.growth.dto.GrowthCompareResponseDTO;
import idu.sba.backend.domain.growth.dto.GrowthTrendResponseDTO;
import idu.sba.backend.domain.repo.entity.RepoMember;
import idu.sba.backend.domain.repo.entity.RepoRole;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주간 이슈 발생/해결 집계.
 * DB는 주 단위 GROUP BY로 "데이터 있는 주만"(sparse) 돌려주므로, 빈 주는 여기서 0으로 채워 항상 N주를 맞춘다.
 * 네이티브 집계값은 BigInteger/BigDecimal 등으로 와서 (Number).longValue()로 안전 변환한다.
 */
@Service
@RequiredArgsConstructor
public class GrowthServiceImpl implements GrowthService {

    private final ReviewIssueRepository reviewIssueRepository;
    private final RepoMemberRepository repoMemberRepository;
    private final UserRepository userRepository;
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("M/d");

    @Override
    @Transactional(readOnly = true)
    public List<GrowthTrendResponseDTO> getGrowthTrend(Long currentUserId, Long teamId, String period, Long memberId) {
        // 이 레포 멤버만 조회 가능
        RepoMember me = repoMemberRepository.findByRepoIdAndUserId(teamId, currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_REPO_MEMBER));
        List<Long> targetUserIds = resolveTargets(teamId, currentUserId, me.getRole(), memberId);

        // 기간(4/8/12주) → 각 주의 월요일 뼈대. weekStarts[0]이 곧 조회 시작일
        int weeks = switch (period == null ? "4W" : period) { case "8W" -> 8; case "12W" -> 12; default -> 4; };
        LocalDate thisMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        List<LocalDate> weekStarts = new ArrayList<>();
        for (int i = weeks - 1; i >= 0; i--) weekStarts.add(thisMonday.minusWeeks(i));

        // 집계 결과(sparse): 주("yyyy-MM-dd") → [발생, 해결]
        Map<String, long[]> byWeek = new HashMap<>();
        for (Object[] r : reviewIssueRepository.aggregateWeekly(targetUserIds, weekStarts.get(0).atStartOfDay())) {
            byWeek.put(String.valueOf(r[0]),
                    new long[]{ ((Number) r[1]).longValue(), ((Number) r[2]).longValue() });
        }

        // 뼈대(N주)에 값을 끼워넣고, 데이터 없는 주는 0으로
        return weekStarts.stream().map(ws -> {
            long[] v = byWeek.getOrDefault(ws.toString(), new long[]{0, 0});
            return new GrowthTrendResponseDTO(ws.format(LABEL), v[0], v[1]);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GrowthCompareResponseDTO getGrowthCompare(Long currentUserId, Long teamId, String period) {
        // 겹쳐보기는 개인별 데이터가 드러나므로 팀장(OWNER)만
        RepoMember me = repoMemberRepository.findByRepoIdAndUserId(teamId, currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_REPO_MEMBER));
        if (me.getRole() != RepoRole.OWNER) throw new BusinessException(ErrorCode.NOT_REPO_MEMBER);

        // 팀원 전원 + 표시 이름 맵(findAllById로 한 번에 — 멤버마다 조회하는 N+1 방지)
        List<RepoMember> members = repoMemberRepository.findByRepoId(teamId);
        List<Long> userIds = members.stream().map(RepoMember::getUserId).toList();
        Map<Long, String> nameById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> "@" + (u.getGithubUsername() != null ? u.getGithubUsername() : u.getNickname())));

        int weeks = switch (period == null ? "4W" : period) { case "8W" -> 8; case "12W" -> 12; default -> 4; };
        LocalDate thisMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        List<LocalDate> weekStarts = new ArrayList<>();
        for (int i = weeks - 1; i >= 0; i--) weekStarts.add(thisMonday.minusWeeks(i));
        List<String> labels = weekStarts.stream().map(w -> w.format(LABEL)).toList();

        // userId → (주 문자열 → 발생수)
        Map<Long, Map<String, Long>> byUser = new HashMap<>();
        for (Object[] r : reviewIssueRepository.aggregateWeeklyByMember(userIds, weekStarts.get(0).atStartOfDay())) {
            Long uid = ((Number) r[0]).longValue();
            byUser.computeIfAbsent(uid, k -> new HashMap<>())
                    .put(String.valueOf(r[1]), ((Number) r[2]).longValue());
        }

        List<GrowthCompareResponseDTO.Series> series = members.stream().map(m -> {
            Map<String, Long> wk = byUser.getOrDefault(m.getUserId(), Map.of());
            List<Long> issues = weekStarts.stream().map(ws -> wk.getOrDefault(ws.toString(), 0L)).toList();
            return new GrowthCompareResponseDTO.Series(nameById.get(m.getUserId()), issues);
        }).toList();

        return new GrowthCompareResponseDTO(labels, series);
    }

    private List<Long> resolveTargets(Long teamId, Long currentUserId, RepoRole myRole, Long memberId) {
        if (memberId != null) {
            // 팀원은 누굴 요청하든 본인으로 고정(남 조회 차단). 팀장만 지정 대상 조회.
            if (myRole != RepoRole.OWNER) return List.of(currentUserId);
            if (!repoMemberRepository.existsByRepoIdAndUserId(teamId, memberId))
                throw new BusinessException(ErrorCode.NOT_REPO_MEMBER);
            return List.of(memberId);
        }
        return repoMemberRepository.findByRepoId(teamId).stream()
                .map(RepoMember::getUserId).toList();
    }

}
