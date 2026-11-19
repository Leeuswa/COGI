package idu.sba.backend.domain.demo;

import idu.sba.backend.domain.review.entity.IssueCategory;
import idu.sba.backend.domain.review.entity.IssueSeverity;

import java.util.List;

import static idu.sba.backend.domain.review.entity.IssueCategory.*;
import static idu.sba.backend.domain.review.entity.IssueSeverity.*;

/*
 * 테스트 계정에 심는 예시 내용. 가상의 쇼핑몰 백엔드(스프링 부트)를 8주 동안 만들어 온 기록이라는 설정이다.
 * 저장소 이름·사람 이름은 전부 지어낸 것이고, 지적 문장은 한 번씩만 쓴다.
 *
 * 날짜는 여기 두지 않는다. 몇 주 전 몇 요일인지만 적고, 실제 날짜는 로그인하는 날을 기준으로 DemoSeeder 가 계산한다.
 */
final class DemoSeedData {

    private DemoSeedData() {}

    // 상태: O=미해결, R=해결, I=의도한 코드(통계 제외), P=해결 요청 후 팀장 승인 대기
    record Issue(IssueCategory category, IssueSeverity severity, String file, int line, char status, String text) {}

    // author: 0=로그인 계정, 1=팀원1, 2=팀원2. repo: 0=sample-shop-api, 1=sample-board
    // weeksAgo 0 = 이번 주(오늘), day 1=월 … 5=금
    record Pr(int repo, int number, String title, int author, int weeksAgo, int day, String model, List<Issue> issues) {}

    record ReReview(int prNumber, int daysAfter, String model, List<Issue> issues) {}

    record Question(int prNumber, String question, String answer) {}

    record Quiz(String type, String question, String options, String answer, String wrongAnswer, String explain) {}

    record Card(String category, String level, String language, String model,
                String concept, String why, String bad, String good, String diff,
                String keyPoints, String pitfalls, String selfCheck,
                List<Quiz> quizzes, int correct, int wrong, int unsolved,
                boolean bookmarked, boolean completed) {}

    static Issue i(IssueCategory c, IssueSeverity s, String file, int line, char status, String text) {
        return new Issue(c, s, file, line, status, text);
    }

    // ── 예시 저장소 (github_repo_id 끝자리, 이름, 비공개 여부) ──
    static final String[] REPO_NAMES = {"sample-shop-api", "sample-board"};
    static final String MATE_REPO_NAME = "sample-chat-server"; // 팀원2가 초대를 보낸 저장소
    static final String UNLINKED_REPO_NAME = "sample-batch-jobs"; // GitHub 목록에만 있고 아직 연동 안 한 저장소

    // ── PR 과 리뷰 지적 ──
    static final List<Pr> PRS = List.of(
            // 8주 전
            new Pr(0, 101, "feat: 상품 목록 조회 API", 0, 8, 2, "claude-haiku-4-5", List.of(
                    i(BUG, MAJOR, "ProductController.java", 42, 'R',
                            "`page` 파라미터가 음수로 들어와도 그대로 `PageRequest.of` 에 넘깁니다. 음수면 IllegalArgumentException 이 나서 500 으로 끝납니다. `@Min(0)` 검증을 붙이거나 0 으로 보정하세요."),
                    i(PERFORMANCE, MAJOR, "ProductService.java", 58, 'R',
                            "목록을 돌면서 `product.getCategory().getName()` 을 불러 상품 수만큼 카테고리 조회가 더 나갑니다(N+1). fetch join 으로 한 번에 가져오세요:\n```java\n@Query(\"select p from Product p join fetch p.category\")\nList<Product> findAllWithCategory();\n```"),
                    i(CONVENTION, MINOR, "ProductDto.java", 12, 'R',
                            "DTO 필드 `product_name` 이 스네이크 케이스입니다. 자바 필드는 `productName` 처럼 카멜 케이스로 두고, JSON 이름이 꼭 필요하면 `@JsonProperty` 로 따로 지정하세요."))),
            new Pr(0, 102, "feat: 회원가입 비밀번호 저장", 0, 8, 4, "claude-haiku-4-5", List.of(
                    i(SECURITY, CRITICAL, "MemberService.java", 31, 'R',
                            "비밀번호를 평문 그대로 저장합니다. DB 가 새면 모든 계정이 바로 털립니다. `PasswordEncoder` 로 해시해서 저장하세요:\n```java\nmember.changePassword(passwordEncoder.encode(request.getPassword()));\n```"),
                    i(BUG, MAJOR, "MemberService.java", 25, 'R',
                            "이메일 중복 확인과 저장 사이에 다른 요청이 끼어들면 같은 이메일이 두 번 들어갑니다. 컬럼에 unique 제약을 걸고 `DataIntegrityViolationException` 을 잡아 409 로 돌려주세요."),
                    i(CODE_SMELL, MINOR, "MemberController.java", 18, 'R',
                            "컨트롤러가 리포지토리를 직접 주입받아 저장까지 합니다. 검증과 저장은 서비스로 옮기고 컨트롤러는 요청과 응답만 다루게 하세요."))),
            new Pr(0, 103, "chore: 빌드 설정 정리", 1, 8, 5, "claude-haiku-4-5", List.of(
                    i(CONVENTION, MINOR, "build.gradle", 30, 'R',
                            "쓰지 않는 `spring-boot-starter-webflux` 의존성이 남아 있습니다. 빼면 빌드 시간과 jar 크기가 줄고, 서블릿과 리액티브 설정이 섞일 일도 없어집니다."))),
            // 7주 전
            new Pr(0, 104, "feat: 장바구니 담기", 0, 7, 2, "claude-sonnet-5", List.of(
                    i(BUG, CRITICAL, "CartService.java", 47, 'R',
                            "같은 상품을 두 번 담으면 수량을 더하지 않고 새 행을 만듭니다. 장바구니에 같은 상품이 두 줄로 보이고 합계가 틀어집니다:\n```java\ncartItemRepository.findByCartIdAndProductId(cartId, productId)\n        .ifPresentOrElse(item -> item.addQuantity(qty),\n                () -> cartItemRepository.save(CartItem.of(cartId, productId, qty)));\n```"),
                    i(PERFORMANCE, MINOR, "CartService.java", 63, 'O',
                            "합계를 구할 때마다 장바구니 항목을 전부 읽어 자바에서 더합니다. `SUM(price * quantity)` 쿼리 하나로 DB 에서 계산하면 읽는 행이 줄어듭니다."),
                    i(SECURITY, MAJOR, "CartController.java", 29, 'R',
                            "`cartId` 를 경로에서 받은 그대로 씁니다. 남의 장바구니 번호를 넣으면 그 장바구니가 바뀝니다. 로그인한 사용자의 장바구니인지 먼저 확인하세요."))),
            new Pr(0, 105, "refactor: 주문 상태 정리", 0, 7, 4, "claude-haiku-4-5", List.of(
                    i(CODE_SMELL, MINOR, "OrderStatus.java", 5, 'R',
                            "주문 상태를 `\"PAID\"`, `\"SHIPPED\"` 같은 문자열 상수로 다룹니다. 오타가 나도 컴파일러가 못 잡습니다. enum 으로 바꾸면 switch 에서 빠진 상태도 경고로 보입니다."),
                    i(CONVENTION, MINOR, "OrderService.java", 88, 'R',
                            "메서드 이름 `Process()` 가 대문자로 시작합니다. 자바 메서드는 소문자 동사로 시작합니다(`process()`)."),
                    i(BUG, MAJOR, "OrderService.java", 102, 'R',
                            "`status == \"PAID\"` 로 문자열을 비교합니다. `==` 는 같은 객체인지를 보므로 DB 에서 읽은 값과는 거의 항상 false 입니다. `\"PAID\".equals(status)` 를 쓰세요."))),
            new Pr(0, 106, "feat: 상품 이미지 업로드", 2, 7, 5, "claude-sonnet-5", List.of(
                    i(SECURITY, CRITICAL, "ImageController.java", 33, 'P',
                            "파일 확장자만 보고 업로드를 허용해 `shell.jsp.png` 같은 이름도 통과합니다. 파일 앞부분의 매직 넘버와 MIME 타입을 함께 확인하고, 업로드 폴더는 실행 권한 없이 두세요."),
                    i(PERFORMANCE, MINOR, "ImageService.java", 51, 'R',
                            "원본 이미지를 그대로 내려줘 목록 화면이 무겁습니다. 업로드할 때 썸네일을 만들어 두고 목록에는 썸네일 주소를 내려주세요."))),
            // 6주 전
            new Pr(0, 107, "feat: 주문 생성", 0, 6, 2, "claude-sonnet-5", List.of(
                    i(BUG, CRITICAL, "OrderService.java", 54, 'R',
                            "재고 차감과 주문 저장이 한 트랜잭션에 묶여 있지 않습니다. 주문 저장에서 예외가 나면 재고만 줄어든 채 남습니다. 서비스 메서드에 `@Transactional` 을 붙여 함께 커밋되거나 함께 롤백되게 하세요."),
                    i(BUG, MAJOR, "StockService.java", 33, 'O',
                            "재고를 읽고 줄이는 사이에 잠금이 없어, 동시에 주문하면 재고가 음수가 됩니다. 조건부 UPDATE 한 줄로 처리하세요:\n```java\n@Modifying\n@Query(\"update Stock s set s.quantity = s.quantity - :qty where s.productId = :id and s.quantity >= :qty\")\nint decrease(@Param(\"id\") Long id, @Param(\"qty\") int qty);\n```\n바뀐 행이 0 이면 재고 부족으로 처리합니다."),
                    i(SECURITY, MAJOR, "OrderController.java", 40, 'R',
                            "요청 본문의 `price` 를 그대로 믿고 결제 금액을 만듭니다. 클라이언트가 가격을 바꿔 보내면 그 금액으로 주문됩니다. 가격은 서버에서 상품을 다시 조회해 계산하세요."))),
            new Pr(0, 108, "feat: 주문 내역 페이지", 0, 6, 4, "claude-haiku-4-5", List.of(
                    i(PERFORMANCE, MAJOR, "OrderQueryRepository.java", 21, 'R',
                            "`findByMemberId` 가 페이지 없이 주문 전체를 불러옵니다. 주문이 쌓일수록 응답이 느려집니다. `Pageable` 을 받아 필요한 만큼만 읽으세요."),
                    i(CONVENTION, MINOR, "OrderRes.java", 9, 'O',
                            "응답 DTO 이름이 `OrderRes` 와 `ProductResponse` 로 섞여 있습니다. 한 가지 규칙(`~Response`)으로 맞추면 파일을 찾기 쉬워집니다."))),
            new Pr(0, 109, "feat: 재고 부족 알림", 1, 6, 5, "claude-haiku-4-5", List.of(
                    i(BUG, MAJOR, "StockAlertService.java", 28, 'R',
                            "재고가 0 이 될 때마다 알림을 보내 같은 상품 알림이 여러 번 갑니다. 마지막 알림 시각을 저장해 두고 재입고 전까지는 한 번만 보내세요."))),
            // 5주 전
            new Pr(0, 110, "feat: 쿠폰 적용", 0, 5, 2, "claude-sonnet-5", List.of(
                    i(BUG, MAJOR, "CouponService.java", 37, 'R',
                            "만료일 비교에 `expiredAt.isAfter(LocalDate.now())` 를 써서 만료 당일 쿠폰이 거절됩니다. 당일까지 쓸 수 있어야 하면 `!expiredAt.isBefore(today)` 로 바꾸세요."),
                    i(SECURITY, CRITICAL, "CouponQueryDao.java", 18, 'R',
                            "쿠폰 코드를 문자열 연결로 쿼리에 붙입니다. `' OR '1'='1` 을 넣으면 아무 쿠폰이나 조회됩니다. 파라미터 바인딩을 쓰세요:\n```java\nString sql = \"SELECT * FROM coupon WHERE code = ?\";\nreturn jdbcTemplate.query(sql, couponMapper, code);\n```"),
                    i(CODE_SMELL, MINOR, "CouponService.java", 60, 'O',
                            "할인 계산 `if-else` 가 쿠폰 종류마다 길어집니다. 종류별 계산을 enum 메서드로 옮기면 새 쿠폰을 더할 때 이 메서드를 건드리지 않아도 됩니다."))),
            new Pr(0, 111, "fix: 결제 실패 처리", 0, 5, 4, "claude-haiku-4-5", List.of(
                    i(BUG, MAJOR, "PaymentService.java", 72, 'R',
                            "`catch (Exception e) {}` 로 예외를 삼켜 결제 실패가 성공처럼 지나갑니다. 로그를 남기고 주문 상태를 실패로 바꾼 뒤 다시 던지세요."),
                    i(SECURITY, MAJOR, "PaymentClient.java", 15, 'R',
                            "결제 대행사 비밀 키가 소스에 상수로 들어 있습니다. 저장소에 한 번 올라가면 키를 새로 발급해야 합니다. 환경 변수로 빼고 `@Value(\"${payment.secret-key}\")` 로 주입받으세요."))),
            new Pr(0, 112, "fix: 정산 금액 반올림", 2, 5, 3, "claude-haiku-4-5", List.of(
                    i(BUG, MAJOR, "SettlementService.java", 45, 'R',
                            "`double` 로 금액을 더해 소수점 오차가 쌓입니다. 정산 합계가 1원씩 어긋나는 원인입니다. 돈은 `BigDecimal` 이나 원 단위 `long` 으로 다루세요."))),
            new Pr(1, 7, "feat: 게시글 페이징", 0, 5, 5, "claude-haiku-4-5", List.of(
                    i(PERFORMANCE, MAJOR, "PostRepository.java", 15, 'R',
                            "`OFFSET` 이 커질수록 앞쪽 행을 모두 읽고 버려 뒤 페이지가 느려집니다. 마지막으로 본 id 를 기준으로 다음 묶음을 읽는 방식(커서 페이징)을 검토하세요."),
                    i(BUG, MINOR, "PostService.java", 40, 'R',
                            "삭제 표시된 글도 목록에 섞여 나옵니다. 조회 조건에 `deleted = false` 가 빠졌습니다."))),
            // 4주 전
            new Pr(0, 113, "feat: 상품 검색", 0, 4, 2, "gpt-5.6-terra", List.of(
                    i(PERFORMANCE, MAJOR, "ProductRepository.java", 27, 'O',
                            "`LIKE %:keyword%` 는 앞쪽 와일드카드 때문에 인덱스를 못 탑니다. 상품이 많아지면 전체 스캔이 됩니다. 접두 검색으로 바꾸거나 전문 검색 인덱스를 쓰세요."),
                    i(BUG, MINOR, "ProductService.java", 81, 'R',
                            "검색어 앞뒤 공백을 지우지 않아 `\" 셔츠\"` 와 `\"셔츠\"` 결과가 다릅니다. `keyword.strip()` 한 뒤 비어 있으면 전체 목록을 돌려주세요."))),
            new Pr(0, 114, "refactor: 오류 응답 통일", 0, 4, 4, "claude-haiku-4-5", List.of(
                    i(CODE_SMELL, MAJOR, "GlobalExceptionHandler.java", 22, 'R',
                            "컨트롤러마다 try-catch 로 오류 응답을 따로 만들어 모양이 제각각입니다. `@RestControllerAdvice` 한 곳에서 처리하면 응답 형식이 하나로 맞춰집니다."),
                    i(CONVENTION, MINOR, "ErrorCode.java", 14, 'R',
                            "에러 코드 이름이 `ERR_1`, `ERR_2` 처럼 번호뿐이라 뜻을 알 수 없습니다. `COUPON_EXPIRED` 처럼 상황이 드러나는 이름으로 바꾸세요."))),
            new Pr(0, 115, "feat: 이메일 인증", 1, 4, 3, "claude-haiku-4-5", List.of(
                    i(SECURITY, MAJOR, "VerificationService.java", 22, 'R',
                            "인증 코드를 `new Random()` 으로 만듭니다. 예측 가능한 난수라 코드를 맞힐 수 있습니다. `SecureRandom` 을 쓰세요."),
                    i(CONVENTION, MINOR, "VerificationService.java", 9, 'R',
                            "상수 `codeLength` 가 소문자입니다. `static final` 상수는 `CODE_LENGTH` 처럼 대문자와 밑줄로 씁니다."))),
            // 3주 전
            new Pr(0, 116, "feat: 상품 리뷰 작성", 0, 3, 2, "claude-sonnet-5", List.of(
                    i(SECURITY, MAJOR, "ReviewController.java", 35, 'R',
                            "리뷰 본문을 그대로 저장하고 화면에서 HTML 로 그립니다. `<script>` 를 넣으면 다른 사용자 브라우저에서 실행됩니다(XSS). 출력할 때 이스케이프하거나 저장할 때 허용 태그만 남기세요."),
                    i(BUG, MAJOR, "ReviewService.java", 49, 'O',
                            "별점을 1~5 로 막지 않아 0 이나 100 이 저장됩니다. 평균 별점이 틀어집니다. 요청 DTO 에 `@Min(1) @Max(5)` 를 붙이세요."))),
            new Pr(0, 117, "perf: 상품 상세 캐시", 0, 3, 4, "claude-haiku-4-5", List.of(
                    i(PERFORMANCE, MINOR, "ProductService.java", 104, 'R',
                            "상세 조회마다 리뷰 수를 `count()` 로 다시 셉니다. 상품에 `reviewCount` 칸을 두고 리뷰가 생길 때만 올리면 조회가 가벼워집니다."),
                    i(BUG, MAJOR, "ProductCacheConfig.java", 19, 'R',
                            "상품을 수정해도 캐시를 비우지 않아 옛 가격이 계속 보입니다. 수정 메서드에 `@CacheEvict(cacheNames = \"product\", key = \"#id\")` 를 붙이세요."))),
            new Pr(0, 118, "feat: 찜 목록", 2, 3, 3, "claude-haiku-4-5", List.of(
                    i(PERFORMANCE, MAJOR, "WishService.java", 31, 'O',
                            "찜 여부를 상품마다 따로 조회해 목록 한 번에 상품 수만큼 쿼리가 나갑니다. 상품 id 목록으로 `findByMemberIdAndProductIdIn` 한 번에 가져오세요."))),
            // 2주 전
            new Pr(0, 119, "feat: 배송지 관리", 0, 2, 2, "claude-haiku-4-5", List.of(
                    i(SECURITY, MAJOR, "AddressController.java", 27, 'R',
                            "배송지 삭제가 `addressId` 만 보고 지웁니다. 남의 배송지 번호를 넣어도 지워집니다. 지우기 전에 주인이 맞는지 확인하세요."),
                    i(CODE_SMELL, MINOR, "AddressService.java", 44, 'R',
                            "기본 배송지를 바꾸는 코드가 등록과 수정 두 곳에 복사돼 있습니다. 한 메서드로 모으면 한쪽만 고치는 실수가 사라집니다."))),
            new Pr(0, 120, "fix: 주문 집계 시간대", 0, 2, 4, "claude-sonnet-5", List.of(
                    i(BUG, MAJOR, "OrderStatService.java", 31, 'R',
                            "`LocalDate.now()` 가 서버 시간대를 따라가서, 서버를 UTC 로 옮기면 '오늘 주문' 집계가 9시간 어긋납니다. `Clock` 을 주입받아 `Asia/Seoul` 기준으로 계산하세요."))),
            new Pr(0, 121, "fix: 비밀번호 재설정 링크", 1, 2, 3, "claude-sonnet-5", List.of(
                    i(SECURITY, CRITICAL, "PasswordResetService.java", 40, 'R',
                            "재설정 토큰에 만료 시간이 없어 몇 달 전 메일 링크로도 비밀번호를 바꿀 수 있습니다. 30분 만료와 한 번 쓰면 무효가 되는 처리를 넣으세요."))),
            new Pr(1, 8, "feat: 댓글 알림", 0, 2, 5, "claude-haiku-4-5", List.of(
                    i(CODE_SMELL, MAJOR, "CommentService.java", 55, 'O',
                            "댓글 저장 메서드 안에서 메일까지 보내 저장 속도가 메일 서버에 묶입니다. 저장 뒤 이벤트를 발행하고 알림은 `@TransactionalEventListener` 에서 따로 보내세요."))),
            // 지난주
            new Pr(0, 122, "feat: 관리자 상품 등록", 0, 1, 2, "claude-opus-4-8", List.of(
                    i(SECURITY, CRITICAL, "AdminProductController.java", 21, 'R',
                            "관리자 API 에 권한 검사가 없어 로그인만 하면 누구나 상품을 등록할 수 있습니다. 보안 설정에서 `/api/admin/**` 을 `hasRole(\"ADMIN\")` 으로 막으세요."),
                    i(BUG, MINOR, "ProductImageService.java", 38, 'O',
                            "업로드 파일 이름을 그대로 저장해 이름이 같으면 앞 파일을 덮어씁니다. 저장 이름에 UUID 를 붙이고 원래 이름은 따로 보관하세요."))),
            new Pr(0, 123, "test: 주문 서비스 테스트 정리", 0, 1, 4, "claude-haiku-4-5", List.of(
                    i(CODE_SMELL, MINOR, "OrderServiceTest.java", 15, 'R',
                            "테스트마다 같은 주문 준비 코드가 20줄씩 반복돼 무엇을 시험하는지가 묻힙니다. 준비 코드는 픽스처 메서드로 빼세요."))),
            new Pr(0, 124, "refactor: 요청 로그 정리", 2, 1, 3, "claude-haiku-4-5", List.of(
                    i(SECURITY, MAJOR, "LoggingFilter.java", 26, 'O',
                            "요청 본문을 통째로 로그에 남겨 비밀번호와 카드 번호가 로그 파일에 쌓입니다. 민감한 필드는 가린 뒤 남기세요."),
                    i(CODE_SMELL, MINOR, "LoggingFilter.java", 48, 'R',
                            "`System.out.println` 이 남아 있습니다. 로그 수준을 조절할 수 있게 로거로 바꾸세요."))),
            // 이번 주 (오늘)
            new Pr(0, 125, "feat: 포인트 적립", 0, 0, 0, "claude-sonnet-5", List.of(
                    i(BUG, MAJOR, "PointService.java", 41, 'O',
                            "적립률을 `int` 나눗셈으로 계산해 1% 적립이 항상 0 이 됩니다. `BigDecimal` 로 계산하고 버림 규칙을 정하세요:\n```java\nBigDecimal point = price.multiply(new BigDecimal(\"0.01\"))\n        .setScale(0, RoundingMode.DOWN);\n```"),
                    i(PERFORMANCE, MINOR, "PointService.java", 66, 'O',
                            "포인트를 쌓을 때마다 회원의 전체 내역을 다시 합산합니다. 회원 행에 잔액을 두고 증감만 반영하세요."))),
            new Pr(0, 126, "feat: 배너 관리", 1, 0, 0, "claude-haiku-4-5", List.of(
                    i(CONVENTION, MINOR, "BannerController.java", 12, 'O',
                            "주소가 `/getBannerList` 처럼 동사로 시작합니다. REST 관례대로 `GET /api/banners` 로 바꾸세요.")))
    );

    // 같은 PR 을 한 번 더 리뷰한 기록 — PR 상세의 리뷰 이력에 "심각 1건 → 0건" 이 보이게
    static final List<ReReview> RE_REVIEWS = List.of(
            new ReReview(102, 1, "claude-sonnet-5", List.of(
                    i(BUG, MINOR, "MemberService.java", 27, 'O',
                            "중복 가입은 unique 제약으로 막혔습니다. 남은 것은 예외 메시지가 영어 원문 그대로 응답에 실리는 점입니다. 사용자에게는 '이미 가입된 이메일입니다' 로 바꿔 내려주세요.")))
    );

    // PR 리뷰에 남긴 후속 질문
    static final List<Question> QUESTIONS = List.of(
            new Question(122, "보안 설정에서 막는 것과 @PreAuthorize 를 붙이는 것 중 뭐가 나아요?",
                    "둘 다 쓰는 편이 안전합니다. 보안 설정의 경로 규칙은 새 컨트롤러가 생겨도 기본으로 막아 주고, 메서드의 @PreAuthorize 는 경로가 바뀌어도 권한이 따라다닙니다. 관리자 API 가 /api/admin 아래 모여 있으니 경로 규칙을 기본으로 두고, 더 좁혀야 하는 메서드에만 @PreAuthorize 를 붙이세요."),
            new Question(122, "막힌 걸 테스트로는 어떻게 확인해요?",
                    "`@WithMockUser(roles = \"USER\")` 로 요청을 보내 403 이 오는지, `roles = \"ADMIN\"` 으로는 201 이 오는지 둘 다 확인하세요. 막히는 쪽 테스트가 있어야 나중에 설정이 풀려도 바로 잡힙니다.")
    );

    // ── 붙여넣기 리뷰 (Java) ──
    static final String PASTE_CODE = """
            public class UserService {

                private final UserRepository userRepository;

                public UserService(UserRepository userRepository) {
                    this.userRepository = userRepository;
                }

                public User find(Long id) {
                    return userRepository.findById(id).get();
                }

                public void removeInactive(List<User> users) {
                    for (int i = 0; i < users.size(); i++) {
                        if (!users.get(i).isActive()) {
                            users.remove(i);
                        }
                    }
                }

                public boolean isExpired(User user) {
                    long age = System.currentTimeMillis() / 1000 - user.getLastLoginEpoch();
                    return age > 86400 * 90;
                }
            }
            """;
    static final List<Issue> PASTE_ISSUES = List.of(
            i(BUG, MAJOR, "UserService.java", 10, 'O',
                    "`findById(id).get()` 은 값이 없으면 NoSuchElementException 을 던져 원인을 알기 어렵습니다. `orElseThrow(() -> new UserNotFoundException(id))` 로 뜻이 드러나는 예외를 던지세요."),
            i(BUG, MINOR, "UserService.java", 16, 'O',
                    "앞에서부터 돌며 `users.remove(i)` 를 부르면 지운 다음 원소를 건너뜁니다. 비활성 사용자가 연달아 있으면 하나가 남습니다. `users.removeIf(u -> !u.isActive())` 를 쓰세요."),
            i(CODE_SMELL, MINOR, "UserService.java", 23, 'I',
                    "`86400 * 90` 이 무엇인지 이름이 없습니다. `Duration.ofDays(90).toSeconds()` 처럼 뜻이 보이게 쓰세요."));

    // ── 파일 업로드 리뷰 (Python) ──
    static final String UPLOAD_NAME = "report_job.py";
    static final String UPLOAD_CODE = """
            import json


            def collect(rows, items=[]):
                for row in rows:
                    if row.get("total", 0) > 0:
                        items.append(row)
                return items


            def save(path, items):
                f = open(path, "w", encoding="utf-8")
                f.write(json.dumps(items, ensure_ascii=False))


            def run(source):
                try:
                    rows = json.load(open(source, encoding="utf-8"))
                    save("report.json", collect(rows))
                except:
                    print("failed")
            """;
    static final List<Issue> UPLOAD_ISSUES = List.of(
            i(BUG, MAJOR, "report_job.py", 4, 'O',
                    "기본 인자 `items=[]` 는 호출 사이에 같은 리스트를 함께 씁니다. 두 번째 호출부터 앞 결과가 섞입니다. `items=None` 으로 받고 함수 안에서 새 리스트를 만드세요."),
            i(BUG, MAJOR, "report_job.py", 12, 'O',
                    "`open()` 으로 연 파일을 닫지 않습니다. 쓰다가 예외가 나면 내용이 디스크에 안 남을 수 있습니다. `with open(path, \"w\", encoding=\"utf-8\") as f:` 로 여세요."),
            i(BUG, MINOR, "report_job.py", 20, 'O',
                    "맨 `except:` 는 Ctrl+C(KeyboardInterrupt)까지 삼킵니다. 잡을 예외를 `json.JSONDecodeError`, `OSError` 로 좁히고 원인을 로그에 남기세요."));

    // ── 학습 카드 · 퀴즈 ──
    // correct 만큼 정답, wrong 만큼 오답 제출을 심는다. 등급은 정답 수로 정해진다(3=노랑, 7=초록, 8=해결+).
    // unsolved: 뒤에서부터 이만큼은 아직 안 푼 문제로 남긴다
    static final List<Card> CARDS = List.of(
            new Card("BUG", "INTERMEDIATE", "Java", "claude-sonnet-5",
                    "여러 테이블을 바꾸는 작업은 한 트랜잭션에 묶어야 한다. 하나라도 실패하면 전부 되돌려야 데이터가 앞뒤가 맞는다. 스프링에서는 서비스 메서드에 @Transactional 을 붙이면 메서드가 끝날 때 함께 커밋되고, 런타임 예외가 나면 함께 롤백된다.",
                    "주문은 저장됐는데 재고가 안 줄거나, 재고는 줄었는데 주문이 없는 상태가 생긴다. 이런 어긋남은 나중에 정산할 때야 발견되고, 어느 쪽이 맞는지 사람이 손으로 맞춰야 한다.",
                    "public Long order(OrderRequest req) {\n    stockService.decrease(req.productId(), req.qty());\n    Order order = orderRepository.save(Order.of(req));   // 여기서 예외가 나면 재고만 줄어 있다\n    return order.getId();\n}",
                    "@Transactional\npublic Long order(OrderRequest req) {\n    stockService.decrease(req.productId(), req.qty());\n    Order order = orderRepository.save(Order.of(req));\n    return order.getId();\n}",
                    "메서드에 @Transactional 을 붙여 재고 차감과 주문 저장이 같은 트랜잭션에서 일어나게 했다. 주문 저장이 실패하면 재고 차감도 함께 롤백된다.",
                    "함께 성공하거나 함께 실패해야 하는 작업은 한 메서드에 모으고 @Transactional 을 붙인다\n체크 예외는 기본으로 롤백되지 않는다 — rollbackFor 를 지정하거나 런타임 예외로 감싼다\n같은 클래스 안에서 부른 메서드에는 @Transactional 이 적용되지 않는다",
                    "private 메서드에 @Transactional 을 붙인다\ntry-catch 로 예외를 삼켜 롤백이 일어나지 않게 만든다\n조회만 하는 메서드에 readOnly 없이 쓰기 트랜잭션을 연다",
                    "두 개 이상의 저장 작업이 한 트랜잭션 안에 있는가?\n예외를 삼키는 catch 블록이 롤백을 막고 있지 않은가?\n같은 클래스 안의 호출에 트랜잭션을 기대하고 있지 않은가?",
                    List.of(
                            new Quiz("MULTIPLE_CHOICE", "@Transactional 메서드 안에서 기본 설정으로 롤백이 일어나는 경우는?",
                                    "IOException 같은 체크 예외가 날 때\nIllegalStateException 같은 런타임 예외가 날 때\n메서드가 null 을 돌려줄 때\n로그를 남길 때",
                                    "IllegalStateException 같은 런타임 예외가 날 때", "IOException 같은 체크 예외가 날 때",
                                    "스프링은 기본으로 런타임 예외와 Error 에서만 롤백한다. 체크 예외도 되돌리려면 rollbackFor 를 지정해야 한다."),
                            new Quiz("OX", "같은 클래스 안에서 this.save() 로 부른 @Transactional 메서드에도 트랜잭션이 걸린다.",
                                    "O\nX", "X", "O",
                                    "@Transactional 은 프록시를 거칠 때만 동작한다. 같은 객체 안에서 직접 부르면 프록시를 거치지 않아 트랜잭션이 걸리지 않는다."),
                            new Quiz("SHORT_ANSWER", "체크 예외에서도 롤백되게 하려면 @Transactional 의 어떤 속성을 쓰나?",
                                    null, "rollbackFor", "noRollbackFor",
                                    "@Transactional(rollbackFor = Exception.class) 처럼 지정하면 체크 예외에서도 롤백된다.")),
                    8, 2, 0, true, true),
            new Card("SECURITY", "INTERMEDIATE", "Java", "claude-sonnet-5",
                    "사용자 입력을 SQL 문자열에 직접 이어 붙이면 입력이 쿼리 구조를 바꿀 수 있다. 값은 항상 바인딩 파라미터(?, :name)로 넘겨 쿼리 구조와 데이터를 나눠야 한다. JPA 의 메서드 이름 쿼리와 @Query 의 :파라미터는 자동으로 바인딩된다.",
                    "로그인 우회, 다른 사람 데이터 조회, 테이블 삭제까지 이어질 수 있다. 한 번 새면 되돌릴 수 없고, 개인정보 유출 신고 대상이 된다.",
                    "String sql = \"SELECT * FROM coupon WHERE code = '\" + code + \"'\";\nreturn jdbcTemplate.query(sql, couponMapper);",
                    "String sql = \"SELECT * FROM coupon WHERE code = ?\";\nreturn jdbcTemplate.query(sql, couponMapper, code);",
                    "문자열 연결을 없애고 ? 자리에 값을 넘겼다. 입력에 따옴표가 들어 있어도 값으로만 취급된다.",
                    "값은 바인딩 파라미터로 넘긴다\n정렬 컬럼처럼 바인딩이 안 되는 자리는 허용 목록으로 검사한다\nDB 계정에는 필요한 권한만 준다",
                    "@Query 안에서 문자열 연결로 JPQL 을 만든다\n입력값에서 따옴표만 지우면 안전하다고 여긴다\n오류 메시지에 SQL 원문을 그대로 내보낸다",
                    "쿼리 문자열에 + 로 붙는 사용자 입력이 있는가?\nORDER BY 에 들어가는 값을 허용 목록으로 막았는가?\nDB 오류 내용이 응답에 그대로 나가지 않는가?",
                    List.of(
                            new Quiz("MULTIPLE_CHOICE", "다음 중 SQL 인젝션을 막는 방법으로 가장 알맞은 것은?",
                                    "입력에서 작은따옴표를 지운다\nPreparedStatement 로 값을 바인딩한다\n쿼리를 대문자로 쓴다\n입력 길이를 10자로 줄인다",
                                    "PreparedStatement 로 값을 바인딩한다", "입력에서 작은따옴표를 지운다",
                                    "바인딩하면 DB 가 쿼리 구조를 먼저 해석하고 값은 나중에 넣는다. 값이 쿼리 구조를 바꿀 수 없다."),
                            new Quiz("OX", "ORDER BY 뒤의 컬럼 이름도 ? 바인딩으로 넘길 수 있다.",
                                    "O\nX", "X", "O",
                                    "바인딩은 값 자리에만 쓸 수 있다. 컬럼 이름은 허용 목록에서 골라 넣어야 한다."),
                            new Quiz("FILL_BLANK", "JPQL 에서 이름 있는 파라미터는 \"where c.code = ___\" 처럼 콜론을 붙여 쓴다. 빈칸에 들어갈 것은? (파라미터 이름은 code)",
                                    null, ":code", "code",
                                    "@Query(\"select c from Coupon c where c.code = :code\") 에 @Param(\"code\") 로 값을 넘긴다."),
                            new Quiz("SHORT_ANSWER", "DB 계정에 꼭 필요한 권한만 주는 원칙을 무엇이라 하나?",
                                    null, "최소 권한", "전체 권한",
                                    "최소 권한 원칙. 인젝션이 뚫려도 할 수 있는 일이 줄어든다.")),
                    7, 2, 0, true, false),
            new Card("PERFORMANCE", "INTERMEDIATE", "Java", "claude-haiku-4-5",
                    "연관 엔티티를 지연 로딩으로 두고 목록을 돌며 꺼내면, 목록 1번 + 항목 수만큼 추가 조회가 나간다(N+1). 목록 화면에서 함께 쓸 연관 엔티티는 fetch join 이나 @EntityGraph 로 한 번에 가져온다.",
                    "개발할 때는 데이터가 적어 티가 안 나다가, 운영에서 목록 한 번에 수백 개 쿼리가 나가 응답이 느려지고 DB 연결이 바닥난다.",
                    "List<Product> products = productRepository.findAll();\nfor (Product p : products) {\n    names.add(p.getCategory().getName());   // 상품마다 카테고리 조회\n}",
                    "@Query(\"select p from Product p join fetch p.category\")\nList<Product> findAllWithCategory();",
                    "카테고리를 fetch join 으로 함께 읽어 쿼리가 한 번만 나간다.",
                    "목록에서 함께 쓰는 연관은 fetch join 이나 @EntityGraph 로 가져온다\n개발 중에 SQL 로그를 켜서 쿼리 개수를 센다\n컬렉션 fetch join 에는 페이징을 함께 쓰지 않는다",
                    "즉시 로딩(EAGER)으로 바꿔 N+1 이 사라졌다고 여긴다\n컬렉션 두 개를 한 번에 fetch join 한다\n쿼리 개수를 확인하지 않고 응답 시간만 본다",
                    "목록 조회 한 번에 쿼리가 몇 개 나가는가?\n반복문 안에서 지연 로딩 필드를 꺼내고 있지 않은가?\n컬렉션 fetch join 에 페이징을 걸고 있지 않은가?",
                    List.of(
                            new Quiz("MULTIPLE_CHOICE", "상품 10개 목록에서 각 상품의 카테고리 이름을 지연 로딩으로 꺼내면 쿼리는 모두 몇 번 나가는가? (카테고리는 모두 다름)",
                                    "1번\n2번\n11번\n20번", "11번", "2번",
                                    "목록 1번 + 상품마다 카테고리 1번씩 10번 = 11번. 이것이 N+1 이다."),
                            new Quiz("OX", "FetchType.EAGER 로 바꾸면 N+1 문제가 항상 사라진다.",
                                    "O\nX", "X", "O",
                                    "JPQL 로 목록을 읽으면 EAGER 여도 연관을 따로 조회해 N+1 이 그대로 생긴다."),
                            new Quiz("SHORT_ANSWER", "Spring Data JPA 에서 메서드에 붙여 연관을 함께 읽게 하는 애너테이션은?",
                                    null, "@EntityGraph", "@Transactional",
                                    "@EntityGraph(attributePaths = \"category\") 처럼 쓴다."),
                            new Quiz("MULTIPLE_CHOICE", "컬렉션 fetch join 과 페이징을 함께 쓰면 생기는 일은?",
                                    "DB 에서 정확히 페이징된다\n전체를 메모리로 읽은 뒤 자른다\n쿼리가 실패한다\n아무 일도 없다",
                                    "전체를 메모리로 읽은 뒤 자른다", "DB 에서 정확히 페이징된다",
                                    "하이버네이트는 경고를 남기고 결과 전체를 메모리에 올린 다음 페이징한다. 데이터가 많으면 메모리가 부족해진다.")),
                    4, 2, 1, false, false),
            new Card("CODE_SMELL", "BEGINNER", "Java", "claude-haiku-4-5",
                    "컨트롤러는 요청을 받고 응답을 돌려주는 일만 한다. 검증, 계산, 저장 같은 업무 규칙은 서비스로 옮긴다. 그래야 같은 규칙을 다른 입구(배치, 메시지)에서도 그대로 쓰고, 테스트도 웹 없이 할 수 있다.",
                    "규칙이 컨트롤러마다 흩어지면 한 곳만 고치고 다른 곳을 잊는다. 같은 회원가입인데 웹과 관리자 화면의 결과가 달라진다.",
                    "@PostMapping(\"/members\")\npublic Long join(@RequestBody JoinRequest req) {\n    if (memberRepository.existsByEmail(req.email())) throw new IllegalStateException();\n    return memberRepository.save(Member.of(req)).getId();\n}",
                    "@PostMapping(\"/members\")\npublic Long join(@RequestBody @Valid JoinRequest req) {\n    return memberService.join(req);\n}",
                    "중복 확인과 저장을 MemberService.join 으로 옮겼다. 컨트롤러는 입력 검증(@Valid)과 위임만 한다.",
                    "컨트롤러는 요청 해석과 응답 만들기만 한다\n업무 규칙은 서비스 한 곳에 둔다\n서비스는 웹 객체(HttpServletRequest 등)를 받지 않는다",
                    "컨트롤러에서 리포지토리를 직접 부른다\n서비스가 ResponseEntity 를 돌려준다\n같은 검증을 여러 컨트롤러에 복사한다",
                    "컨트롤러에 if 로 된 업무 규칙이 남아 있지 않은가?\n서비스 메서드를 웹 없이 테스트할 수 있는가?",
                    List.of(
                            new Quiz("OX", "컨트롤러에서 리포지토리를 직접 불러 저장하는 것이 권장된다.",
                                    "O\nX", "X", "O",
                                    "업무 규칙이 컨트롤러에 박히면 다른 입구에서 재사용할 수 없고 테스트도 어려워진다."),
                            new Quiz("MULTIPLE_CHOICE", "다음 중 서비스 계층에 두기 가장 알맞은 것은?",
                                    "요청 JSON 해석\n이메일 중복 확인\nHTTP 상태 코드 정하기\n쿠키 쓰기",
                                    "이메일 중복 확인", "HTTP 상태 코드 정하기",
                                    "중복 확인은 어디서 가입하든 지켜야 하는 업무 규칙이라 서비스에 둔다."),
                            new Quiz("SHORT_ANSWER", "요청 DTO 의 검증 애너테이션을 동작시키려고 컨트롤러 파라미터에 붙이는 애너테이션은?",
                                    null, "@Valid", "@Autowired",
                                    "@RequestBody @Valid JoinRequest req 처럼 붙이면 @NotBlank 같은 제약이 검사된다.")),
                    3, 1, 1, false, false),
            new Card("CONVENTION", "BEGINNER", "Java", "claude-haiku-4-5",
                    "자바는 이름만 보고 종류를 알 수 있게 규칙이 정해져 있다. 클래스는 대문자로 시작하는 명사(OrderService), 메서드는 소문자로 시작하는 동사(placeOrder), 변수는 카멜 케이스(orderCount), static final 상수는 대문자와 밑줄(MAX_PAGE_SIZE)로 쓴다.",
                    "규칙이 섞이면 이름만 보고 상수인지 변수인지, 메서드인지 클래스인지 헷갈린다. 리뷰 때 정작 봐야 할 로직보다 이름 지적이 먼저 나온다.",
                    "public class order_service {\n    static final int maxPage = 100;\n    public void Process(int Order_count) { }\n}",
                    "public class OrderService {\n    static final int MAX_PAGE = 100;\n    public void process(int orderCount) { }\n}",
                    "클래스는 파스칼 케이스, 상수는 대문자와 밑줄, 메서드와 변수는 카멜 케이스로 바꿨다.",
                    "클래스는 대문자로 시작하는 명사로 짓는다\n메서드는 소문자로 시작하는 동사로 짓는다\n상수는 대문자와 밑줄로 쓴다",
                    "약어를 섞어 `ordCnt` 처럼 줄인다\n불린 변수에 is 를 빼고 `active` 와 `flag` 를 섞어 쓴다",
                    "static final 상수가 모두 대문자인가?\n메서드 이름이 무엇을 하는지 동사로 드러나는가?",
                    List.of(
                            new Quiz("MULTIPLE_CHOICE", "자바 관례에 맞는 상수 이름은?",
                                    "maxPageSize\nMaxPageSize\nMAX_PAGE_SIZE\nmax_page_size", "MAX_PAGE_SIZE", "maxPageSize",
                                    "static final 상수는 대문자와 밑줄로 쓴다."),
                            new Quiz("OX", "메서드 이름은 대문자로 시작해도 관례에 맞다.",
                                    "O\nX", "X", "O",
                                    "메서드는 소문자로 시작하는 카멜 케이스다. 대문자로 시작하면 클래스와 헷갈린다."),
                            new Quiz("SHORT_ANSWER", "orderCount 처럼 첫 단어는 소문자, 이어지는 단어는 대문자로 시작하는 표기를 무엇이라 하나?",
                                    null, "카멜 케이스", "스네이크 케이스",
                                    "카멜 케이스. 낙타 등처럼 중간이 솟아 있어 붙은 이름이다.")),
                    1, 2, 1, false, false),
            new Card("BUG", "BEGINNER", "Python", "claude-haiku-4-5",
                    "파이썬의 기본 인자는 함수를 정의할 때 한 번만 만들어진다. 리스트나 딕셔너리 같은 바뀌는 값을 기본 인자로 두면 호출할 때마다 같은 객체를 함께 쓴다. 기본값은 None 으로 두고 함수 안에서 새로 만든다.",
                    "처음 한 번은 맞게 동작해서 테스트를 통과하고, 같은 프로세스에서 두 번째로 부를 때부터 앞 결과가 섞여 나온다. 배치 작업에서 어제 데이터가 오늘 보고서에 끼는 식으로 나타난다.",
                    "def collect(rows, items=[]):\n    for row in rows:\n        items.append(row)\n    return items",
                    "def collect(rows, items=None):\n    if items is None:\n        items = []\n    for row in rows:\n        items.append(row)\n    return items",
                    "기본값을 None 으로 바꾸고 호출할 때마다 새 리스트를 만든다.",
                    "바뀌는 값(list, dict, set)을 기본 인자로 두지 않는다\n기본값은 None 으로 두고 함수 안에서 만든다",
                    "기본 인자 리스트를 캐시처럼 쓰는 코드를 그대로 둔다\n`items = items or []` 로 써서 빈 리스트를 넘겨도 새로 만들어 버린다",
                    "기본 인자에 [] 나 {} 가 있는가?\n같은 함수를 두 번 불러도 결과가 섞이지 않는가?",
                    List.of(
                            new Quiz("OX", "def f(x=[]) 의 기본 리스트는 호출할 때마다 새로 만들어진다.",
                                    "O\nX", "X", "O",
                                    "기본 인자는 함수를 정의할 때 한 번만 평가된다."),
                            new Quiz("SHORT_ANSWER", "바뀌는 값 대신 기본 인자로 두는 값은?",
                                    null, "None", "[]",
                                    "None 으로 두고 함수 안에서 새 리스트를 만든다.")),
                    0, 0, 2, false, false)
    );

    // 학습 계획 — CARDS[2](성능, 노랑)에 7일 계획을 달력에 등록해 둔다. 등록일은 사흘 전
    static final int PLAN_CARD = 2;
    static final int PLAN_STARTED_DAYS_AGO = 3;
    static final String PLAN_STEPS = """
            [{"dayOffset":0,"title":"N+1 직접 만들어 보기","focus":"상품 10개를 넣고 카테고리 이름을 꺼내는 목록 API 를 만든 뒤 SQL 로그로 쿼리 수를 센다","resource":"spring.jpa.show-sql=true 로 로그 켜기","minutes":20,"checkpoint":"쿼리가 11번 나가는 것을 로그로 확인했는가?"},
             {"dayOffset":1,"title":"어제 로그 다시 보기","focus":"어제 남긴 SQL 로그에서 반복되는 select 줄만 골라 표시한다","resource":"","minutes":5,"checkpoint":"반복 쿼리가 어느 줄에서 시작되는지 짚을 수 있는가?"},
             {"dayOffset":2,"title":"fetch join 으로 고치기","focus":"리포지토리에 join fetch 쿼리를 추가하고 같은 API 의 쿼리 수를 다시 센다","resource":"Hibernate 문서의 fetch join 항목","minutes":25,"checkpoint":"쿼리가 1번으로 줄었는가?"},
             {"dayOffset":3,"title":"고친 코드 훑기","focus":"어제 고친 리포지토리 메서드를 다시 열어 바뀐 줄만 눈으로 확인한다","resource":"","minutes":5,"checkpoint":"왜 한 번에 읽히는지 한 문장으로 말할 수 있는가?"},
             {"dayOffset":4,"title":"@EntityGraph 로 바꿔 보기","focus":"같은 조회를 @EntityGraph 로 바꾸고 생성되는 SQL 을 fetch join 버전과 비교한다","resource":"Claude 에게 \\"두 SQL 의 차이를 표로 정리해줘\\" 라고 묻기","minutes":20,"checkpoint":"두 방식의 SQL 차이를 설명할 수 있는가?"},
             {"dayOffset":5,"title":"비교표 다시 읽기","focus":"어제 만든 비교표에서 페이징이 걸릴 때 달라지는 점만 다시 읽는다","resource":"","minutes":5,"checkpoint":"컬렉션 fetch join 과 페이징을 같이 쓰면 안 되는 까닭을 아는가?"},
             {"dayOffset":6,"title":"내 PR 에서 찾아보기","focus":"최근 PR 의 목록 API 하나를 골라 쿼리 수를 세고 N+1 이 있으면 고친다","resource":"코기 리뷰 이력의 성능 지적","minutes":30,"checkpoint":"내 코드에서 N+1 을 하나 이상 찾아 고쳤는가?"},
             {"dayOffset":7,"title":"한 주 정리","focus":"이번 주에 고친 쿼리 수 변화를 한 줄씩 적는다","resource":"","minutes":5,"checkpoint":"전후 쿼리 수를 숫자로 적었는가?"}]
            """;
    // 계획만 만들고 아직 달력에 등록하지 않은 카드
    static final int PLAN_DRAFT_CARD = 1;
    static final String PLAN_DRAFT_STEPS = """
            [{"dayOffset":0,"title":"문자열 연결 쿼리 찾기","focus":"프로젝트에서 + 로 SQL 을 만드는 곳을 검색해 목록을 만든다","resource":"IDE 에서 \\"SELECT .* \\\\+\\" 정규식 검색","minutes":15,"checkpoint":"찾은 곳을 모두 적었는가?"},
             {"dayOffset":1,"title":"바인딩으로 바꾸기","focus":"찾은 곳 중 하나를 ? 바인딩으로 고치고 따옴표가 든 입력으로 시험한다","resource":"JdbcTemplate query(sql, mapper, args) 문서","minutes":20,"checkpoint":"' OR '1'='1 을 넣어도 결과가 비어 있는가?"},
             {"dayOffset":2,"title":"고친 곳 다시 보기","focus":"어제 고친 쿼리를 다시 열어 바인딩 자리 수와 인자 수가 맞는지 본다","resource":"","minutes":5,"checkpoint":"인자 순서가 맞는가?"}]
            """;

    // ── 스킬 추천 (마지막 추천 결과가 화면에 그대로 다시 뜬다) ──
    static final String SKILL_BY_WEAKNESS_INPUT = "카테고리: BUG, 언어: Java, 발생 횟수: 18회\n카테고리: SECURITY, 언어: Java, 발생 횟수: 8회\n카테고리: PERFORMANCE, 언어: Java, 발생 횟수: 7회";
    static final String SKILL_BY_WEAKNESS_RESULT = """
            {"items":[
             {"provider":"CLAUDE","title":"트랜잭션 경계 점검 요청","why":"버그 지적의 상당수가 저장 순서와 롤백 범위에서 나왔습니다. 서비스 메서드를 통째로 보여 주고 경계를 짚어 달라고 하면 빠뜨린 곳을 빨리 찾습니다.","howTo":"서비스 클래스 하나를 붙여 넣고 아래 프롬프트를 그대로 보냅니다.","prompt":"이 서비스에서 함께 성공하거나 함께 실패해야 하는 저장 작업을 찾아 줘. @Transactional 이 빠졌거나 예외를 삼켜 롤백이 안 되는 곳을 줄 번호와 함께 알려 줘."},
             {"provider":"CHATGPT","title":"쿼리 바인딩 검사표 만들기","why":"보안 지적 8건 중 3건이 입력값이 쿼리나 금액 계산에 그대로 들어간 경우입니다. 검사표를 한 번 만들어 두면 PR 올리기 전에 스스로 걸러 낼 수 있습니다.","howTo":"Custom Instructions 에 아래 문장을 넣어 두고 코드를 물을 때마다 함께 보게 합니다.","prompt":"코드를 볼 때마다 사용자 입력이 SQL, 파일 경로, 금액 계산에 그대로 쓰이는 곳이 있는지 먼저 확인하고 표로 알려 줘."},
             {"provider":"GEMINI","title":"쿼리 수 비교 분석","why":"성능 지적 대부분이 목록 조회의 N+1 과 페이징 누락입니다. SQL 로그를 통째로 넣고 반복되는 쿼리를 묶어 달라고 하면 원인 메서드를 바로 찾습니다.","howTo":"show-sql 로그를 복사해 붙여 넣고 아래처럼 묻습니다.","prompt":"이 SQL 로그에서 같은 모양으로 반복되는 쿼리를 묶어 횟수를 세고, 어떤 엔티티 연관 때문에 생겼는지 추정해 줘."}
            ]}
            """;
    static final String SKILL_FREE_INPUT = "JPA 쿼리가 몇 번 나가는지 매번 로그로 세기가 번거로워요";
    static final String SKILL_FREE_RESULT = """
            {"items":[
             {"provider":"COPILOT","title":"쿼리 수 검사 테스트 만들기","why":"쿼리 개수를 테스트로 고정해 두면 N+1 이 다시 생길 때 빌드에서 바로 걸립니다.","howTo":"테스트 파일을 열고 Copilot Chat 에 아래 프롬프트를 보냅니다.","prompt":"Hibernate Statistics 로 이 리포지토리 메서드가 실행하는 쿼리 수를 세고, 2개를 넘으면 실패하는 JUnit 테스트를 만들어 줘."},
             {"provider":"CLAUDE","title":"로그 요약 부탁하기","why":"긴 SQL 로그를 사람이 세는 대신 요약을 받으면 어디서 늘었는지 바로 보입니다.","howTo":"요청 하나 분량의 SQL 로그를 붙여 넣습니다.","prompt":"이 로그에서 select 문을 테이블별로 묶어 개수를 세 줘. 반복되는 묶음이 있으면 어떤 코드에서 나왔을지 추정해 줘."}
            ]}
            """;

    // ── 알림 (아이콘, 제목, 본문, 이동 경로, 몇 시간 전) ──
    static final String[][] NOTIFICATIONS = {
            {"📈", "주간 리포트 도착", "지난주 이슈 발생이 전주보다 줄었어요. 해결률도 확인해 보세요.", "/app/growth", "70"},
            {"🔴", "새 약점이 잡혔어요", "Java 보안 지적이 3번 넘게 반복됐어요.", "/app/weakness", "50"},
            {"📗", "학습 계획 4일차", "@EntityGraph 로 바꿔 보기 — 오늘 20분이면 돼요.", "/app/cards", "20"},
            {"🔍", "PR 리뷰가 끝났어요", "sample-shop-api #125 에서 지적 2건이 나왔어요.", "/app/prs", "2"},
            {"🐶", "코기가 배고파해요", "오늘 퀴즈를 풀면 코인을 받아 밥을 줄 수 있어요.", "/app", "1"}
    };

    // ── GitHub 에서 실시간으로 읽는 화면용 예시 (스튜디오 PR 가져오기) ──
    // 아직 리뷰하지 않은 열린 PR. 리뷰 실행은 AI 를 부르므로 횟수 제한을 받는다
    record OpenPr(int number, String title, int author, List<String[]> files) {}

    static final List<OpenPr> OPEN_PRS = List.of(
            new OpenPr(127, "feat: 주문 취소", 0, List.of(
                    new String[]{"src/main/java/com/example/shop/order/OrderCancelService.java", """
                            @@ -0,0 +1,28 @@
                            +package com.example.shop.order;
                            +
                            +@Service
                            +@RequiredArgsConstructor
                            +public class OrderCancelService {
                            +
                            +    private final OrderRepository orderRepository;
                            +    private final StockService stockService;
                            +
                            +    public void cancel(Long orderId) {
                            +        Order order = orderRepository.findById(orderId).get();
                            +        if (order.getStatus() == "SHIPPED") {
                            +            throw new IllegalStateException("배송이 시작된 주문입니다");
                            +        }
                            +        order.cancel();
                            +        for (OrderLine line : order.getLines()) {
                            +            stockService.increase(line.getProductId(), line.getQuantity());
                            +        }
                            +    }
                            +}"""},
                    new String[]{"src/main/java/com/example/shop/order/OrderController.java", """
                            @@ -58,6 +58,12 @@ public class OrderController {
                                 }
                            +
                            +    @PostMapping("/{orderId}/cancel")
                            +    public ResponseEntity<Void> cancel(@PathVariable Long orderId) {
                            +        orderCancelService.cancel(orderId);
                            +        return ResponseEntity.ok().build();
                            +    }
                             }"""})),
            new OpenPr(128, "feat: 상품 문의 등록", 1, List.<String[]>of(
                    new String[]{"src/main/java/com/example/shop/qna/QuestionService.java", """
                            @@ -0,0 +1,22 @@
                            +package com.example.shop.qna;
                            +
                            +@Service
                            +@RequiredArgsConstructor
                            +public class QuestionService {
                            +
                            +    private final JdbcTemplate jdbcTemplate;
                            +
                            +    public List<Question> search(String keyword) {
                            +        String sql = "SELECT * FROM question WHERE title LIKE '%" + keyword + "%'";
                            +        return jdbcTemplate.query(sql, new QuestionRowMapper());
                            +    }
                            +
                            +    public void write(Long memberId, String title, String body) {
                            +        jdbcTemplate.update("INSERT INTO question(member_id, title, body) VALUES (?, ?, ?)",
                            +                memberId, title, body);
                            +    }
                            +}"""}))
    );
}
