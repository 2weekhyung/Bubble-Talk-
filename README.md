# LunchPulse

> 팀의 대화가 오늘의 선택이 되는 곳

Redis와 WebSocket을 활용해 팀의 익명 대화, 점심 투표, 운영 모니터링을 하나의 흐름으로 연결한 실시간 참여 플랫폼입니다.

단순 채팅/투표 기능보다, 익명 사용자가 빠르게 입력하는 환경에서 발생하는 운영 문제를 다루는 데 초점을 맞췄습니다. 메시지 수명 관리, 반복 요청 제한, 중복 투표 방지, 채팅방 정원 제어, 금칙어 정책, 관리자 대시보드, 보안 이벤트 로그를 하나의 서비스 흐름으로 구현했습니다.

## 핵심 요약

| 구분 | 내용 |
| --- | --- |
| 주제 | 실시간 익명 채팅과 점심 메뉴 투표를 운영자가 제어하는 서비스 |
| 백엔드 | Java 17, Spring Boot 3.3, Spring Security, JPA, QueryDSL |
| 실시간 처리 | WebSocket, STOMP |
| 저장소 | MySQL, Redis |
| Redis 활용 | TTL, Set, Sorted Set, Lua Script, Rate Limiting |
| 화면 | Thymeleaf, Vanilla JS, Custom Admin Login |
| 인프라 | Docker, Docker Compose |
| 테스트 | JUnit 5, Mockito |

## 구현 범위

### 사용자 기능

- 익명 사용자의 실시간 채팅 메시지 송수신
- Redis TTL 기반 휘발성 채팅 메시지 관리
- 점심 메뉴 등록과 실시간 투표
- Redis Sorted Set 기반 실시간 랭킹 조회
- 공개방·비밀방 생성, 입장, 퇴장
- 공개 채팅방 최신순 10개 단위 페이징 조회
- 방별 최대 인원 제한
- 채팅방 생성 Rate Limiting
- 서버 발급 GuestID 기반 익명 사용자 식별

### 운영자 기능

- 관리자 대시보드
- 전체 WebSocket session 수 조회
- 채팅방 목록 최신순 20개 단위 페이징 및 상태/공개 범위 필터링
- 공개/비공개/종료 상태별 채팅방 통계 조회
- 실시간 채팅 모니터링
- 채팅방 강제 종료
- 투표 이벤트 시작/종료 및 운영 시간 변경
- 금칙어 추가/삭제와 Redis 캐시 갱신
- 시스템 공지 발송
- 보안 이벤트 로그 필터링, 날짜 범위 검색 및 페이지 조회
- stale WebSocket session 수동 정리
- 커스텀 관리자 로그인 화면

## 주요 엔티티

| 엔티티 | 테이블 | 역할 |
| --- | --- | --- |
| `ChatRoom` | `chat_room` | 공개방/비밀방의 메타데이터, 최대 인원, 방 상태, 종료 시각 관리 |
| `SecurityEventLog` | `security_event_log` | 방 생성/입장/퇴장, 메시지 전송, 관리자 조작, stale session 정리 등 사용자·운영 이벤트 기록 |
| `ForbiddenWord` | `forbidden_words` | 관리자 금칙어 정책 저장, Redis 캐시와 연동해 채팅 필터링에 사용 |
| `DailyMenu` | `daily_menus` | 투표 대상 메뉴 마스터 데이터 |
| `LunchHistory` | `TB_LUNCH_HISTORY` | Redis 실시간 투표 결과를 정산한 일별 최종 랭킹 이력 |

실시간 상태는 DB 엔티티로 모두 저장하지 않고 Redis에 분리했습니다. 예를 들어 채팅방의 현재 인원, 활성 WebSocket session, 중복 투표 여부, 휘발성 메시지는 Redis Set/ZSet/TTL을 사용하고, MySQL에는 장기 보관이 필요한 메타데이터와 운영 로그만 저장합니다.

## 주요 기술 의사결정

### 1. 채팅 메시지는 Redis TTL로 관리

채팅 메시지를 영구 저장하지 않고 Redis에 10초 TTL로 저장했습니다. 익명 실시간 채팅의 특성상 모든 메시지를 DB에 남기는 것보다, 새로 접속한 사용자가 최근 메시지만 자연스럽게 확인하고 이후 자동으로 사라지는 흐름이 더 적합하다고 판단했습니다.

관련 코드:

- `src/main/java/com/lunchpulse/chat/service/ChatService.java`

### 2. 투표 랭킹은 Redis Sorted Set으로 처리

투표 점수는 Redis ZSet에 저장하고, 메뉴 ID를 member, 득표수를 score로 관리했습니다. 매 투표마다 DB 집계를 수행하지 않고 Redis에서 즉시 순위를 계산해 실시간 화면에 반영할 수 있도록 설계했습니다.

관련 코드:

- `src/main/java/com/lunchpulse/menu/service/MenuService.java`

### 3. 중복 투표와 점수 증가는 Lua Script로 원자 처리

`SADD`로 중복 여부를 기록하고 `ZINCRBY`로 점수를 올리는 두 명령을 Lua Script 하나로 묶었습니다. 오늘의 투표판에 없는 메뉴는 같은 Script 안에서 거절합니다. Redis 오류가 두 명령 사이에 발생해 “투표 기록만 남고 점수는 오르지 않는” 부분 실패를 방지합니다.

### 4. 채팅방 정원은 Redis Lua Script로 원자 처리

방 입장 시 현재 인원 확인(`SCARD`)과 session 추가(`SADD`)를 분리하면 동시 입장 상황에서 최대 인원을 초과할 수 있습니다. 이를 막기 위해 Lua Script로 두 작업을 하나의 원자적 흐름으로 처리했습니다.

관련 코드:

- `src/main/java/com/lunchpulse/chatroom/service/ChatRoomService.java`

### 5. 익명 사용자 식별은 GuestID 우선

익명 서비스에서도 도배, 중복 투표, 보안 이벤트 로그를 처리하려면 최소한의 식별자가 필요합니다. 서버 발급 GuestID를 우선 사용하고, 없을 경우 clientId, IP 순서로 fallback합니다.

### 6. 반복 요청 제한은 Redis TTL 기반으로 처리

사용자 입력이 많은 기능은 Redis TTL 키로 제한했습니다. 메뉴 추가와 채팅방 생성은 `SETNX + TTL`로 일정 시간 내 반복 생성을 막고, 채팅은 window/mute/last-message 키를 나누어 과도한 전송과 동일 메시지 반복을 차단합니다.

| 대상 | 정책 | Redis key |
| --- | --- | --- |
| 채팅 메시지 | 10초에 5개 초과 시 30초 제한, 동일 메시지 10초 내 연속 전송 차단 | `chat:ratelimit:{type}:{actorId}` |
| 메뉴 추가 | 같은 사용자 기준 30초에 1회 | `menu:add:ratelimit:{actorId}` |
| 채팅방 생성 | 같은 사용자 기준 30초에 1회 | `room:create:ratelimit:{actorId}` |
| 투표 | 같은 사용자가 같은 메뉴에 1회만 투표 | `lunch:voters:{yyyyMMdd}:{menuId}` |

### 7. API 에러 응답은 전역 핸들러로 표준화

컨트롤러별 `try-catch` 대신 `GlobalExceptionHandler`에서 REST API 실패 응답을 통일했습니다.

| 상황 | HTTP Status | code |
| --- | --- | --- |
| 성공 | 200 | `0000` |
| 비즈니스 예외 | 400 | `4000` 또는 지정 code |
| 투표 운영 시간 외 차단 | 403 | `4030` |
| 서버 내부 오류 | 500 | `5000` |

프론트 공통 AJAX 모듈은 실패 응답의 `code`, `status`, `message`, `result`를 보존해 화면에서 일관된 실패 메시지를 표시할 수 있습니다.

### 8. 컨테이너는 재현 가능한 빌드와 최소 권한으로 실행

Dockerfile은 Gradle 빌드 단계와 JRE 실행 단계를 분리한 multi-stage build를 사용합니다. 로컬에 미리 생성된 JAR가 없어도 같은 방식으로 빌드되며, 실행 컨테이너는 전용 non-root 사용자로 동작합니다. Docker Compose는 MySQL·Redis health check가 통과한 뒤 애플리케이션을 시작합니다.

## 아키텍처

```text
[Client]
   |
   | HTTP / WebSocket(STOMP)
   v
[Spring Boot]
   |
   |-- ChatService
   |     |-- 메시지 검증
   |     |-- 금칙어 필터링
   |     |-- Redis TTL 저장
   |     |-- Redis Rate Limiting
   |
   |-- MenuService
   |     |-- 메뉴 등록
   |     |-- Redis ZSet 랭킹
   |     |-- Redis Set 중복 투표 방지
   |
   |-- ChatRoomService
   |     |-- 공개방/비밀방 관리
   |     |-- 공개방 최신순 페이징
   |     |-- Redis Set 기반 현재 인원 계산
   |     |-- Lua Script 기반 정원 제어
   |     |-- 방 생성 Rate Limiting
   |
   |-- AdminDashboardService
   |     |-- 운영 현황 조회
   |     |-- 관리자 채팅방 최신순 페이징
   |     |-- 방 종료
   |     |-- 관리자 이벤트 처리
   |
   |-- MySQL
   |     |-- 채팅방 메타데이터
   |     |-- 메뉴 마스터
   |     |-- 투표 결과 히스토리
   |     |-- 금칙어
   |     |-- 보안 이벤트 로그
   |
   |-- Redis
         |-- 휘발성 메시지
         |-- 실시간 랭킹
         |-- 중복 투표 Set
         |-- WebSocket session Set
         |-- Rate Limit key
```

## Redis 키 설계

| 목적 | 자료구조 | 예시 키 |
| --- | --- | --- |
| 휘발성 채팅 메시지 | String + TTL | `chat:bubble:{uuid}` |
| 채팅 Rate Limit | String + TTL | `chat:ratelimit:{type}:{actorId}` |
| 실시간 투표 랭킹 | Sorted Set | `lunch:ranking:{yyyyMMdd}` |
| 중복 투표 방지 | Set | `lunch:voters:{yyyyMMdd}:{menuId}` |
| 전체 활성 세션 | Set | `chat:active:sessions` |
| 메뉴 추가 제한 | String + TTL | `menu:add:ratelimit:{actorId}` |
| 채팅방 생성 제한 | String + TTL | `room:create:ratelimit:{actorId}` |
| 방별 활성 세션 | Set | `room:{roomCode}:sessions` |
| 방별 익명 사용자 | Set | `room:{roomCode}:guests` |
| session과 사용자 매핑 | Hash | `room:{roomCode}:session-actors` |
| session이 입장한 방 목록 | Set | `room:session:rooms:{sessionId}` |
| 금칙어 캐시 | Set | `chat:forbidden` |

상세 DB/Redis 설계는 [DOCS_DB_DESIGN.md](./DOCS_DB_DESIGN.md)에 정리했습니다.

## 패키지 구조

```text
com.lunchpulse
├── admin.dashboard   # 관리자 대시보드
├── chat              # 실시간 채팅
├── chatroom          # 채팅방 생성/입장/퇴장/종료
├── config            # WebSocket, Redis, Security 설정
├── guest             # GuestID 발급/관리
├── menu              # 메뉴 등록, 투표, 랭킹
├── security          # 금칙어 정책
├── securitylog       # 사용자 행위 로그
└── global            # 공통 예외, 상수
```

## 트러블슈팅 사례

| 문제 | 원인 | 해결 |
| --- | --- | --- |
| 실시간 랭킹 반영 지연 | REST 처리 후 화면 갱신 이벤트가 없음 | 투표 성공 후 WebSocket으로 최신 랭킹 브로드캐스팅 |
| 투표 부분 실패 가능성 | 중복 기록과 점수 증가가 별도 명령 | Lua Script로 `SADD`와 `ZINCRBY`를 원자 처리 |
| 채팅 도배 | 익명 사용자가 짧은 시간에 반복 전송 | Redis `INCR`/`EXPIRE` 기반 Rate Limiting 적용 |
| 채팅방 생성 도배 | 같은 사용자가 방을 반복 생성할 수 있음 | Redis `SETNX`/TTL 기반으로 30초에 1회만 생성 허용 |
| 방 정원 초과 | 현재 인원 확인과 session 추가 사이 경쟁 조건 | Redis Lua Script로 `SCARD`와 `SADD` 원자 처리 |
| 공개방 목록 과다 노출 | 공개방 전체 목록을 한 번에 렌더링 | 최신 생성순 10개 단위 페이징으로 변경 |
| 관리자 방 목록 과다 조회 | 관리자 화면에서 전체 방을 한 번에 조회 | 최신 생성순 20개 단위 페이징으로 변경 |
| 관리자 방 목록 탐색 불편 | 전체 방을 상태/공개 여부와 무관하게 표시 | 공개/비공개, OPEN/FULL/CLOSED 필터 추가 |
| 보안 이벤트 로그 탐색 불편 | API는 Page 응답이지만 화면은 첫 페이지만 표시 | 이전/다음 버튼, 총 개수, 날짜 범위 필터 추가 |
| API 에러 응답 불일치 | 컨트롤러별 직접 예외 처리와 전역 예외 처리가 혼재 | `BusinessException`은 HTTP 400, 서버 오류는 HTTP 500으로 표준화 |
| 기본 로그인 화면 노출 | Spring Security 기본 로그인 페이지 사용 | 서비스 테마에 맞춘 커스텀 관리자 로그인 화면 적용 |
| 금칙어 반영 지연 | DB 변경 후 Redis 캐시 미갱신 | 관리자 캐시 갱신 API와 cache-aside 로직 추가 |
| stale session 잔존 | 비정상 종료 시 Redis session Set에 값이 남음 | WebSocket disconnect 처리와 관리자 수동 cleanup 기능 추가 |

상세한 문제 해결 과정은 [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)에 기록했습니다.

## 실행 방법

### 1. MySQL, Redis 실행

```bash
cp .env.example .env
docker compose up -d --build
```

Windows PowerShell에서는 `Copy-Item .env.example .env`를 사용하세요. 실행 전에 `.env`의 비밀번호를 반드시 변경해야 합니다.

### 2. 애플리케이션 실행

Windows:

```bash
gradlew.bat bootRun
```

macOS/Linux:

```bash
./gradlew bootRun
```

### 3. 접속 경로

| 화면 | URL |
| --- | --- |
| 메인 화면 | `http://localhost:8080` |
| 관리자 로그인 | `http://localhost:8080/login` |
| 관리자 대시보드 | `http://localhost:8080/admin/dashboard` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` (관리자 로그인 필요) |

IDE에서 `dev` 프로필로 실행할 때만 기본 관리자 계정(`admin / admin1234`)을 사용할 수 있습니다. `prod` 프로필은 `ADMIN_USERNAME`, `ADMIN_PASSWORD`, DB 접속 환경변수가 없으면 기동하지 않도록 구성했습니다.

## 테스트

```bash
gradlew.bat test
```

현재 테스트는 서비스 계층의 핵심 정책을 중심으로 작성했습니다.

- 채팅 메시지 검증
- 금칙어 필터링
- 채팅 Rate Limiting
- 공개방/비밀방 생성
- 채팅방 생성 Rate Limiting
- 공개방 페이징 조회
- 관리자 채팅방 페이징 조회
- 방 정원 초과 검증
- Redis Lua 기반 session 등록
- 방 종료와 Redis key 정리
- WebSocket disconnect 시 session 정리
- 관리자 대시보드 summary 계산
- 전역 API 에러 응답 처리

## 현재 한계와 개선 계획

이 프로젝트는 학습 및 포트폴리오 목적의 단일 애플리케이션 인스턴스 구조를 기준으로 구현했습니다. 운영 수준으로 확장하려면 아래 항목을 보완해야 합니다.

- 다중 서버 환경을 위한 공유 WebSocket session registry 설계
- Redis 장애 시 복구 전략과 degraded mode 정의
- Testcontainers 기반 MySQL/Redis 통합 테스트 추가
- 운영 환경에서 `ddl-auto: update` 제거 및 Flyway/Liquibase 도입
- 관리자 계정 정책 강화
- Redis `KEYS` 사용 지점의 `SCAN` 전환
- 부하 테스트를 통한 Rate Limit 임계값 검증

## 라이선스

본 프로젝트는 교육 및 포트폴리오 목적으로 제작되었습니다.
