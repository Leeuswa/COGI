/*
 * ⚠️ 하드코딩 전용 폴더(src/data/mock) — 백엔드(Spring Boot) 붙으면 폴더째 삭제한다.
 *    삭제 절차: ① client.js 의 USE_MOCK=false ② 이 폴더 삭제 ③ client.js 상단 mock import 한 줄 삭제
 *
 * 규칙:
 *  - 엔티티당 딱 1건. (화면이 한 건이라도 그려지는지 테스트하는 용도)
 *  - 화면 컴포넌트는 이 폴더를 직접 import 하지 않는다. 반드시 api/client.js 를 거친다.
 *  - 필드명은 테이블 정의서(404COGI_분석_설계_통합본_3.xlsx) 컬럼명의 camelCase.
 *    백엔드 DTO를 이 이름으로 맞추면 프론트 수정이 거의 없다.
 */

export * from './user';
export * from './review';
export * from './learn';
export * from './growth';
export * from './plan';
export * from './admin';
export * from './terms';
export * from './team';
