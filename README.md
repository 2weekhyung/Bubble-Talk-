# 🗨️ BubbleTalk (버블톡)

**실시간 휘발성 익명 채팅 및 타임어택 점심 결정 시스템**

BubbleTalk은 공간에 피어났다 사라지는 시각적 채팅 경험과 익명성을 보장하는 실시간 커뮤니케이션 플랫폼입니다. 매일 점심시간에는 사용자들과 함께 오늘의 메뉴를 정하는 실시간 '점심 전쟁'이 열립니다.

---

## 🚀 주요 기능 (Core Features)

### 1. 실시간 팝업 채팅 (Bubble Chat)
- **휘발성 메시지**: 전송된 메시지는 화면에 랜덤하게 나타나 3~5초간 유지되다가 사라집니다. (Redis 기반 TTL 관리)
- **시각적 익명성**: 발신자 IP를 기반으로 고정된 색상을 부여하여 익명성을 유지하면서도 발신자를 구분할 수 있게 설계되었습니다.
- **도배 및 금칙어 방지**: Redis Rate Limiting 알고리즘으로 도배를 차단하며, 실시간 금칙어 필터링이 적용됩니다.

### 2. 점심 메뉴 난투극 (Lunch War)
- **타임어택 운영**: 매일 **09:00 ~ 14:00** 동안만 투표가 활성화됩니다. (어드민에서 동적 조정 가능)
- **실시간 랭킹**: Redis ZSET을 이용해 득표수와 순위를 실시간으로 브로드캐스팅합니다.
- **순위 역전 알림**: 1위가 바뀌는 순간 모든 사용자에게 드라마틱한 역전 알림을 전송합니다.
- **히스토리 기록**: 투표 종료 후 최종 결과는 DB(MySQL)로 이관되어 누적 관리됩니다.

### 3. 관리자 대시보드 (Admin Dashboard)
- **실시간 모니터링**: 접속자 수, 활성 메뉴 수, 누적 투표 수 및 실시간 채팅을 한눈에 관리합니다.
- **운영 제어**: 투표 이벤트 강제 시작/종료, 운영 시간 동적 설정, 데이터 초기화 기능을 제공합니다.
- **전역 시스템 공지**: 모든 접속자에게 실시간으로 시스템 공지 메시지를 브로드캐스팅할 수 있습니다.
- **콘텐츠 관리**: 실시간 금칙어(Forbidden Words) 추가/삭제 및 캐시 동기화 기능을 제공합니다.

---

## 🛠 기술 스택 (Tech Stack)

- **Backend**: Java 17, Spring Boot 3.3.0
- **Persistence**: Spring Data JPA, QueryDSL (Type-safe 쿼리), Spring Data Redis (Ranking, Caching, Rate Limiting)
- **Real-time**: WebSocket (STOMP), Redis Pub/Sub
- **Frontend**: Thymeleaf, Vanilla JS, Tailwind CSS (Neo-Neon Design), FontAwesome
- **Documentation**: Springdoc OpenAPI (Swagger UI)
- **Infrastructure**: Docker, Docker Compose (MySQL 8.0, Redis 7.0)

---

## 🏗 프로젝트 구조 (Actual Package Structure)

```text
com.bubbletalk/
├── admin.dashboard/       # 관리자 대시보드 (View/Rest Controller, Service)
├── base/                  # 공통 기반 클래스 (Entity, DTO)
├── chat/                  # 실시간 채팅 도메인
├── config/                # 설정 (WebSocket, Redis, Security, Interceptor)
├── global/                # 공통 상수, 예외 처리, 유틸리티
├── main/                  # 메인 화면 컨트롤러
├── menu/                  # 점심 메뉴 및 투표 도메인 (Scheduler 포함)
└── security/              # 금칙어 관리 및 보안 관련 로직
```

---

## ⚙️ 시작하기 (Getting Started)

### 1. 인프라 실행 (Docker Compose)
프로젝트 루트 폴더에서 아래 명령어를 실행하여 MySQL과 Redis를 구동합니다.
```bash
docker-compose up -d
```

### 2. 애플리케이션 빌드 및 실행
```bash
./gradlew bootRun
```
- **메인 화면**: `http://localhost:8080`
- **관리자 화면**: `http://localhost:8080/admin/dashboard`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`

---

## 🛠 트러블슈팅 기록
본 프로젝트의 개발 과정에서 발생한 다양한 기술적 이슈와 해결 과정은 [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)에 상세히 기록되어 있습니다. (총 40여 개의 이슈 해결 및 의사결정 기록 포함)

---

## 📝 라이선스
본 프로젝트는 교육 및 포트폴리오 목적으로 제작되었습니다.
