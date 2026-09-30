# COGI · Code Guide

1조 Fable팀 (정상연, 유지환, 홍성찬, 이수환)

AI 코드 리뷰 받고, 자주 틀리는 부분을 약점으로 모아서 학습카드·퀴즈로 공부하게 해주는 서비스.

## 현재 상태 (5주차)

- 요구사항 정의서, 기능 정의서, 개발 일정 작성 → `docs/`
- 자료 / 레퍼런스 수집 → `docs/자료수집.md`
- 프론트: 프로젝트 생성, 랜딩(임시), 로그인·회원가입 화면만 (기능 연결 X)
- 백엔드: Spring Boot 프로젝트 생성, 서버 확인용 `/api/health` 하나

## 실행

```bash
# 백엔드 (Java 25)
cd backend
./gradlew bootRun        # http://localhost:8080/api/health

# 프론트
cd frontend
npm install
npm run dev              # http://localhost:5173
```
