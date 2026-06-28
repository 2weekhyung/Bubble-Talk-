# LiveOpsBoard

Redis와 WebSocket을 활용한 실시간 익명 채팅·투표 운영 관리 시스템입니다.

단순 채팅/투표 기능보다, 익명 사용자가 빠르게 입력하는 환경에서 발생하는 운영 문제를 다루는 데 초점을 맞췄습니다. 메시지 수명 관리, 반복 요청 제한, 중복 투표 방지, 채팅방 정원 제어, 금칙어 정책, 관리자 대시보드, 보안 이벤트 로그를 하나의 서비스 흐름으로 구현했습니다.

> 기존 프로젝트명: BubbleTalk

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
- 채팅방 목록, 공개/비공개/종료 상태 조회
- 실시간 채팅 모니터링
- 채팅방 강제 종료
- 투표 이벤트 시작/종료 및 운영 시간 변경
- 금칙어 추가/삭제와 Redis 캐시 갱신
- 시스템 공지 발송
- 보안 이벤트 로그 조회
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

- `src/main/java/com/bubbletalk/chat/service/ChatService.java`

### 2. 투표 랭킹은 Redis Sorted Set으로 처리

투표 점수는 Redis ZSet에 저장하고, 메뉴 ID를 member, 득표수를 score로 관리했습니다. 매 투표마다 DB 집계를 수행하지 않고 Redis에서 즉시 순위를 계산해 실시간 화면에 반영할 수 있도록 설계했습니다.

관련 코드:

- `src/main/java/com/bubbletalk/menu/service/MenuService.java`

### 3. 중복 투표는 Redis Set으로 방지

`SADD`의 반환값을 이용해 최초 투표인 경우에만 점수를 증가시켰습니다. 중복 확인과 점수 증가 사이에서 발생할 수 있는 중복 반영 문제를 줄이기 위한 구조입니다.

### 4. 채팅방 정원은 Redis Lua Script로 원자 처리

방 입장 시 현재 인원 확인(`SCARD`)과 session 추가(`SADD`)를 분리하면 동시 입장 상황에서 최대 인원을 초과할 수 있습니다. 이를 막기 위해 Lua Script로 두 작업을 하나의 원자적 흐름으로 처리했습니다.

관련 코드:

- `src/main/java/com/bubbletalk/chatroom/service/ChatRoomService.java`

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
com.bubbletalk
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
| 중복 투표 가능성 | 중복 확인과 점수 증가가 분리됨 | Redis `SADD` 결과가 최초 추가일 때만 ZSet score 증가 |
| 채팅 도배 | 익명 사용자가 짧은 시간에 반복 전송 | Redis `INCR`/`EXPIRE` 기반 Rate Limiting 적용 |
| 채팅방 생성 도배 | 같은 사용자가 방을 반복 생성할 수 있음 | Redis `SETNX`/TTL 기반으로 30초에 1회만 생성 허용 |
| 방 정원 초과 | 현재 인원 확인과 session 추가 사이 경쟁 조건 | Redis Lua Script로 `SCARD`와 `SADD` 원자 처리 |
| 공개방 목록 과다 노출 | 공개방 전체 목록을 한 번에 렌더링 | 최신 생성순 10개 단위 페이징으로 변경 |
| API 에러 응답 불일치 | 컨트롤러별 직접 예외 처리와 전역 예외 처리가 혼재 | `BusinessException`은 HTTP 400, 서버 오류는 HTTP 500으로 표준화 |
| 기본 로그인 화면 노출 | Spring Security 기본 로그인 페이지 사용 | 서비스 테마에 맞춘 커스텀 관리자 로그인 화면 적용 |
| 금칙어 반영 지연 | DB 변경 후 Redis 캐시 미갱신 | 관리자 캐시 갱신 API와 cache-aside 로직 추가 |
| stale session 잔존 | 비정상 종료 시 Redis session Set에 값이 남음 | WebSocket disconnect 처리와 관리자 수동 cleanup 기능 추가 |

상세한 문제 해결 과정은 [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)에 기록했습니다.

## 실행 방법

### 1. MySQL, Redis 실행

```bash
docker-compose up -d
```

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
| Swagger UI | `http://localhost:8080/swagger-ui.html` |

기본 관리자 계정은 로컬 개발용입니다.

```text
username: admin
password: admin1234
```

운영 환경에서는 `ADMIN_USERNAME`, `ADMIN_PASSWORD` 환경변수로 반드시 변경해야 합니다.

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
- Swagger 공개 범위 제한
- Redis `KEYS` 사용 지점의 `SCAN` 전환
- 부하 테스트를 통한 Rate Limit 임계값 검증

## 라이선스

본 프로젝트는 교육 및 포트폴리오 목적으로 제작되었습니다.
