package idu.sba.backend.domain.learning.repository;

import idu.sba.backend.domain.learning.entity.WeaknessStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeaknessStatRepository extends JpaRepository<WeaknessStat, Long> {

    // 재집계 전에 기존 통계를 비운다 (조회 때마다 최신값으로 갈아끼우기).
    //
    // 파생 쿼리(deleteByUserId)로 두면 행을 하나씩 SELECT한 뒤 id로 DELETE한다.
    // 조회 요청이 겹치면(개발 모드 StrictMode가 useEffect를 두 번 돌리는 경우 포함)
    // 뒤 트랜잭션이 이미 지워진 행을 지우려다 "unexpected row count [0]"로 터져 500이 났다.
    // 벌크 DELETE 한 방이면 지울 게 없어도 0건으로 끝나고 예외가 안 난다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WeaknessStat w where w.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
