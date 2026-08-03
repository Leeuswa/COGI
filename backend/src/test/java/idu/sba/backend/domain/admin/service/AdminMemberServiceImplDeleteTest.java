package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.payment.repository.PlanRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

// 삭제는 되돌릴 수 없어서 "정지된 계정만"이 유일한 안전장치 — 그 규칙만 검증한다.
@ExtendWith(MockitoExtension.class)
class AdminMemberServiceImplDeleteTest {

    @Mock private UserRepository userRepository;
    @Mock private PlanRepository planRepository;

    @InjectMocks private AdminMemberServiceImpl service;

    private static final Long ADMIN_ID = 1L, TARGET_ID = 2L;

    private User target(UserStatus status) {
        User u = User.builder().email("t@x.com").password("p").nickname("대상").build();
        ReflectionTestUtils.setField(u, "id", TARGET_ID);
        u.changeStatus(status);
        return u;
    }

    @Test
    void 정지된_계정은_삭제된다() {
        User t = target(UserStatus.SUSPENDED);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(t));

        service.deleteMember(TARGET_ID, ADMIN_ID);

        verify(userRepository).delete(t);
    }

    @Test
    void 활성_계정은_삭제되지_않는다() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target(UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.deleteMember(TARGET_ID, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_SUSPENDED);

        verify(userRepository, never()).delete(any());
    }

    @Test
    void 탈퇴_계정도_삭제_버튼으로는_지울_수_없다() { // 30일 배치로만 정리된다
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target(UserStatus.WITHDRAWN)));

        assertThatThrownBy(() -> service.deleteMember(TARGET_ID, ADMIN_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 본인은_삭제할_수_없다() {
        assertThatThrownBy(() -> service.deleteMember(ADMIN_ID, ADMIN_ID))
                .isInstanceOf(BusinessException.class);

        verify(userRepository, never()).delete(any());
    }
}
