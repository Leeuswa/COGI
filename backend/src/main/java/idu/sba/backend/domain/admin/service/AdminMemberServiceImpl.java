package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.admin.dto.AdminMemberResponseDTO;
import idu.sba.backend.domain.payment.entity.Plan;
import idu.sba.backend.domain.payment.repository.PlanRepository;
import idu.sba.backend.domain.user.entity.Role;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminMemberServiceImpl implements AdminMemberService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AdminMemberResponseDTO> getMembers() {
        // planId → name 은 회원마다 반복되므로 플랜을 한 번만 읽어 map으로 조회(N+1 방지)
        Map<Long, String> planNames = planRepository.findAll().stream()
                .collect(Collectors.toMap(Plan::getId, Plan::getName));

        return userRepository.findAll().stream()
                .map(u -> AdminMemberResponseDTO.of(u, planName(u.getPlanId(), planNames)))
                .toList();
    }

    @Override
    @Transactional
    public void changeStatus(Long userId, Long adminId , UserStatus status) {
        if (adminId.equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_STATUS_OWN_ROLE);
        }
        findUser(userId).changeStatus(status);
    }

    @Override
    @Transactional
    public void changeRole(Long userId, Long adminId, Role role) {
        if (adminId.equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }
        findUser(userId).changeRole(role);
    }

    // 회원 영구 삭제 — 되돌릴 수 없으므로 정지(SUSPENDED)·탈퇴(WITHDRAWN) 회원만 지운다(활성 계정 오조작 방지).
    // 본인 삭제도 막는다(관리자 락아웃 방지). 탈퇴 회원은 30일 배치로도 정리되지만 관리자가 즉시 삭제도 가능.
    @Override
    @Transactional
    public void deleteMember(Long userId, Long adminId) {
        if (adminId.equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_STATUS_OWN_ROLE);
        }
        User user = findUser(userId);
        UserStatus status = user.getStatus();
        if (status != UserStatus.SUSPENDED && status != UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_DELETABLE);
        }
        userRepository.delete(user);
    }

    // planId null(=미구독)이거나 삭제된 플랜이면 FREE로 표기
    private String planName(Long planId, Map<Long, String> planNames) {
        if (planId == null) return "FREE";
        return planNames.getOrDefault(planId, "FREE");
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
