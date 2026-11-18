package idu.sba.backend.domain.demo;

import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.global.security.JwtProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/*
 * 테스트 로그인 한 번 = 그 계정 데이터를 처음 상태로 되돌리고 토큰을 내준다.
 * 같은 계정 버튼을 여러 사람이 동시에 눌러도 되돌리기가 겹치지 않게 계정 종류마다 잠금 하나를 둔다
 * (서버가 여러 대여도 DemoSeeder 가 계정 행을 FOR UPDATE 로 잠가 한 번 더 막는다).
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "cogi.demo", name = "enabled", havingValue = "true")
public class DemoLoginService {

    private final DemoSeeder seeder;
    private final DemoAccountRegistry registry;
    private final JwtProvider jwtProvider;
    private final TransactionTemplate tx;
    private final Map<DemoKind, ReentrantLock> locks = new EnumMap<>(DemoKind.class);

    public DemoLoginService(DemoSeeder seeder, DemoAccountRegistry registry, JwtProvider jwtProvider,
                            PlatformTransactionManager transactionManager) {
        this.seeder = seeder;
        this.registry = registry;
        this.jwtProvider = jwtProvider;
        this.tx = new TransactionTemplate(transactionManager);
        for (DemoKind k : DemoKind.values()) locks.put(k, new ReentrantLock(true));
    }

    /** 데이터를 되돌린 뒤 발급한 액세스 토큰. 잠금을 오래 못 잡으면 null (호출한 쪽이 429 로 답한다) */
    public String resetAndIssueToken(DemoKind kind) throws InterruptedException {
        ReentrantLock lock = locks.get(kind);
        if (!lock.tryLock(20, TimeUnit.SECONDS)) {
            return null;
        }
        try {
            long started = System.currentTimeMillis();
            User account = tx.execute(status -> seeder.resetAndSeed(kind));
            registry.load(); // 처음 만든 계정이면 여기서 테스트 계정으로 알아본다
            log.info("테스트 로그인 — {} 계정 데이터를 처음 상태로 되돌림 ({}ms)", kind.path(), System.currentTimeMillis() - started);
            return jwtProvider.createToken(account.getId(), account.getRole().name());
        } finally {
            lock.unlock();
        }
    }
}
