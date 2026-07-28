package idu.sba.backend.domain.learning.repository;

import idu.sba.backend.domain.learning.entity.AiSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiSkillRepository extends JpaRepository<AiSkill, Long> {

    // 화면에서 고른 AI에 해당하는 스킬만 — 공용 큐레이션 행 + 내가 AI로 만든 행을 같이 준다. 남의 것은 안 섞인다
    @Query("select s from AiSkill s where s.provider = :provider and (s.userId is null or s.userId = :userId)")
    List<AiSkill> findByProviderForUser(@Param("provider") String provider, @Param("userId") Long userId);

    // 같은 사용자+provider+title이 이미 있는지 — 있으면 새로 안 만들고 갱신한다 (중복 방지).
    //
    // findFirst인 이유 — 컬럼에 유니크 제약이 없다. 지난 추천 복원(latest)이 이 메서드를 타는데,
    // 화면이 약점 기반과 자유 입력을 같이 꺼내고 개발 모드 StrictMode가 그걸 또 두 번 돌려서
    // 같은 (user, provider, title)을 동시에 넣으면 행이 둘이 될 수 있다.
    // 그때 findBy...(Optional)면 NonUniqueResultException으로 500이 난다. 첫 행만 집어 쓰고 넘어간다
    Optional<AiSkill> findFirstByUserIdAndProviderAndTitle(Long userId, String provider, String title);

    // category까지 같이 고르면 그 안에서 한 번 더 거른다 (값은 IssueCategory 문자열과 동일)
}
