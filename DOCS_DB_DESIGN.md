# 📊 [버블톡] 데이터베이스 설계서 (V2.0 - High Performance)

본 문서는 **Hybrid Persistence(Redis + MySQL)** 전략을 채택한 버블톡의 데이터 저장 구조를 정의합니다.

---

## 1. 아키텍처 전략 (Technical Decision)
- **Redis (Source of Truth)**: 실시간 투표(`ZSet`), 중복 투표 체크(`Set`), 채팅 도배 방지 및 휘발성 메시지 관리.
- **MySQL (History Archive)**: 메뉴 마스터 정보 및 매일 12시 정산된 최종 결과 보관.
- **의사결정 배경**: 매 투표 시 발생하는 DB I/O를 제거하여 대규모 트래픽 상황에서의 병목 현상을 해결하고, 데이터 관리 포인트를 일원화하여 정합성을 확보함.

---

## 2. 테이블 정의서 (MySQL)

### 2.1 메뉴 마스터 테이블 (`daily_menus`)
*전장에 투입된 적이 있는 모든 메뉴의 이름을 관리하는 마스터 정보입니다.*

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT (PK) | Auto Increment | 고유 식별자 |
| `menu_name` | VARCHAR(255) | NOT NULL, UNIQUE | 메뉴 명칭 (중복 등록 방지) |
| `created_at` | DATETIME | DEFAULT NOW() | 최초 등록 일시 |

### 2.2 점심 이력 테이블 (`TB_LUNCH_HISTORY`)
*매일 12시 정각, 스케줄러에 의해 Redis에서 MySQL로 스냅샷 이관된 최종 데이터입니다.*

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT (PK) | Auto Increment | 고유 식별자 |
| `target_date` | DATE | NOT NULL | 투표가 진행된 날짜 |
| `menu_name` | VARCHAR(255) | NOT NULL | 메뉴 명칭 |
| `vote_count` | BIGINT | NOT NULL | 최종 득표수 (Redis 이관 데이터) |
| `ranking` | INT | NOT NULL | 최종 순위 (1위, 2위 등) |

### 2.3 금칙어 관리 테이블 (`forbidden_words`)
*채팅 필터링을 위한 금칙어 목록을 관리합니다. (Redis 캐시와 동기화됨)*

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT (PK) | Auto Increment | 고유 식별자 |
| `word` | VARCHAR(255) | NOT NULL, UNIQUE | 금칙어 텍스트 |
| `created_at` | DATETIME | DEFAULT NOW() | 등록 일시 |

---

## 3. 휘발성 데이터 모델링 (Redis)

| 기능 | 자료구조 (Type) | 키 구조 (Key) | 설명 |
| :--- | :--- | :--- | :--- |
| **실시간 랭킹** | `ZSet` | `lunch:ranking:{yyyyMMdd}` | `menuId`를 Member로, 득표수를 Score로 관리 |
| **중복 투표 체크** | `Set` | `lunch:voters:{yyyyMMdd}:{menuId}` | 투표자 식별 정보를 저장하여 1인 1회 제한 |
| **휘발성 채팅** | `Value` | `chat:bubble:{uuid}` | TTL(10초)을 설정하여 자동 삭제 구현 |
| **금칙어 캐시** | `Set` | `chat:forbidden` | 고속 필터링을 위해 DB 데이터를 메모리에 상주 |
| **이벤트 상태** | `Value` | `lunch:event:status` | `OPEN` / `CLOSED` 상태 관리 |
| **채팅 도배 방지** | `Value` | `chat:ratelimit:{type}:{actorId}` | GuestID 우선 식별자를 기준으로 메시지 윈도우·음소거·중복 전송 제한 |
| **메뉴 추가 제한** | `Value` | `menu:add:ratelimit:{actorId}` | 동일 익명 요청자의 메뉴 추가를 30초에 1회로 제한 |
| **전체 활성 세션** | `Set` | `chat:active:sessions` | 활성 WebSocket session ID와 전역 접속자 수 관리 |

---

## 4. 변경 이력 (V1.0 → V2.0)
- **`votes` 테이블 삭제**: 영구 보관 필요성이 낮은 개별 로그를 제거하고 Redis Set으로 대체하여 성능 최적화.
- **`daily_menus.final_score` 삭제**: 데이터 중복을 피하고 Redis를 유일한 실시간 점수 원천으로 정의.
- **정산 메커니즘 확립**: 12시 정각 스케줄러를 통한 Redis → MySQL 이관 프로세스 공식화.

---

## 5. 채팅방 영구 정보와 실시간 상태

MySQL의 `chat_room`에는 방 코드, 이름, 설명, 공개 여부, 최대 인원,
상태와 생성/수정/종료 시각만 저장한다. 참여자 테이블과 채팅 메시지
영구 저장 테이블은 만들지 않는다.

### 5.1 `chat_room` 테이블

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Increment | 채팅방 식별자 |
| `room_code` | VARCHAR(30) | NOT NULL, UNIQUE | URL과 초대에 사용하는 8자리 방 코드 |
| `name` | VARCHAR(100) | NOT NULL | 채팅방 이름 |
| `description` | VARCHAR(255) | NULL | 채팅방 설명 |
| `is_private` | TINYINT(1) | NOT NULL, DEFAULT 0 | 비밀방 여부. 비밀방은 공개 목록에서 제외 |
| `max_participants` | INT | NOT NULL, DEFAULT 10 | 최대 인원. 서비스 검증 범위는 2~50명 |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT `OPEN` | `OPEN`, `FULL`, `CLOSED` |
| `created_date` | DATETIME(6) | NULL | 현재 `BaseEntity` 매핑 기준 생성 시각 |
| `modified_date` | DATETIME(6) | NULL | 현재 `BaseEntity` 매핑 기준 수정 시각 |
| `closed_at` | DATETIME(6) | NULL | 방 종료 시각 |

인덱스:

- `room_code` UNIQUE: 고유 방 코드 보장과 코드 기반 조회
- `(is_private, status, created_date)`: 공개 상태 방의 최신순 목록 조회 보조

roomCode는 애플리케이션에서 대문자·숫자 8자리로 생성한다. 최종 중복
방지는 DB UNIQUE 제약이 담당하며, 충돌 시 최대 10회 새로운 코드로 재시도한다.

### 5.2 Redis 실시간 상태

Redis 실시간 키:

- `chat:active:sessions`: 전체 활성 WebSocket session ID Set
- `room:{roomCode}:sessions`: 방별 활성 session ID Set과 현재 인원 기준
- `room:{roomCode}:guests`: 운영·확장용 익명 요청자 ID Set
- `room:{roomCode}:session-actors`: session ID와 익명 요청자 ID 매핑 Hash
- `room:session:rooms:{sessionId}`: session이 입장한 roomCode Set

방 세션 등록은 Lua script에서 `SCARD`와 `SADD`를 원자적으로 처리해
동시 입장 요청에서도 최대 인원을 넘지 않도록 한다.

HTTP join은 입장 가능 여부와 방 정보만 확인하며 실제 인원 증가는
WebSocket session 등록 시 발생한다. 현재 인원은
`room:{roomCode}:sessions`의 `SCARD`를 기준으로 계산한다.

### 5.3 검증 범위

- 공개방·비밀방 생성과 DB 저장
- 공개방 목록에서 비밀방 제외
- roomCode 상세 조회와 코드 입장
- 최대 인원 도달 시 `FULL` 계산 및 추가 입장 차단
- disconnect 시 room session/guest 정리 단위 테스트

SockJS endpoint 응답은 확인했지만 전역 WebSocket 채팅의 실제 브라우저
송수신과 다중 탭 reconnect 시나리오는 아직 별도 수동 검증이 필요하다.

---

## 6. 관리자 대시보드 집계 모델

관리자 대시보드는 별도의 통계 테이블을 만들지 않고 현재 MySQL과 Redis의
원천 데이터를 읽어 운영 상태를 계산한다.

### 6.1 Summary API 집계

`GET /api/admin/dashboard/summary`

| 필드 | 원천 | 계산 방식 |
| :--- | :--- | :--- |
| `totalRooms` | MySQL `chat_room` | 전체 방 개수 |
| `publicRooms` | MySQL `chat_room` | `is_private=false` 개수 |
| `privateRooms` | MySQL `chat_room` | `is_private=true` 개수 |
| `openRooms` | MySQL + Redis | Redis 현재 인원을 반영한 실효 상태가 `OPEN`인 방 |
| `fullRooms` | MySQL + Redis | 현재 인원이 최대 인원 이상인 방 |
| `closedRooms` | MySQL `chat_room` | 저장 상태가 `CLOSED`인 방 |
| `activeSessions` | Redis `chat:active:sessions` | Set `SCARD` |
| `activeGuests` | 미제공 | 전역 GuestID Set이 없어 정확하게 계산할 수 없으므로 `null` 처리 |
| `todayMenuCount` | Redis `lunch:ranking:{yyyyMMdd}` | ZSet `ZCARD` |
| `todayVoteCount` | Redis `lunch:ranking:{yyyyMMdd}` | ZSet 전체 score 합산 |
| `redisAvailable` | Redis | Summary 집계 중 Redis 접근 성공 여부 |

기존 사용자용 Top 10 랭킹 응답은 관리자 전체 통계 계산에 사용하지 않는다.

### 6.2 관리자 채팅방 목록

`GET /api/admin/rooms`

- 공개방·비밀방을 모두 반환한다.
- OPEN·FULL·CLOSED 방을 모두 반환한다.
- 최신 생성순으로 정렬한다.
- 현재 인원은 `room:{roomCode}:sessions` Set의 `SCARD`로 계산한다.
- Redis 조회 실패 시 해당 방의 현재 인원은 0으로 fallback한다.

### 6.3 관리자 WebSocket 소비 구조

관리자 페이지는 하나의 SockJS/STOMP 연결에서 다음 topic을 구독한다.

- `/topic/user-count`: 전역 활성 WebSocket session 수
- `/topic/bubbles`: 전역 채팅 모니터

채팅 모니터는 `ChatMessage.content`를 출력하고 `senderGuestId`,
`senderClientId`, `senderIp`, `roomCode`를 운영 식별 정보로 사용한다.
roomCode가 없으면 전역 메시지로 표시한다.

### 6.4 미구현 범위

- 전역 GuestID Set과 정확한 활성 Guest 수
- 운영 이벤트 로그 테이블
- 관리자 감사 로그 테이블
- 방별 `/topic/rooms/{roomCode}/bubbles` 모니터링

---

## 7. 관리자 운영 안정화 데이터 처리

### 7.1 방 종료

`POST /api/admin/rooms/{roomCode}/close`

1. MySQL `chat_room.status`를 `CLOSED`로 변경한다.
2. 최초 종료 시 `closed_at`을 기록한다.
3. DB 변경을 flush한 뒤 Redis 실시간 상태 정리를 시도한다.
4. Redis 정리가 실패해도 DB의 CLOSED 상태는 유지한다.

Redis 정리 대상:

- `room:{roomCode}:sessions`
- `room:{roomCode}:guests`
- `room:{roomCode}:session-actors`

방 session Set의 각 sessionId에 대해서는
`room:session:rooms:{sessionId}`에서 종료한 roomCode만 제거한다. 전역 활성
session과 다른 방의 reverse mapping은 유지한다.

### 7.2 Stale session 수동 정리

`POST /api/admin/realtime/cleanup-stale-sessions`

현재 서버는 메모리 `ActiveWebSocketSessionRegistry`에 실제 connect된
sessionId를 추적한다. 관리자 cleanup은 다음 Redis 정보를 검사한다.

- `chat:active:sessions`
- `room:*:sessions`

Redis session 합집합 중 현재 서버 registry에 없는 session만 stale로
판단하고 전역·방별 session 상태와 reverse mapping을 제거한다.

응답:

| 필드 | 설명 |
| :--- | :--- |
| `scannedSessions` | 검사한 고유 session 수 |
| `removedSessions` | 제거한 stale session 수 |
| `scannedRooms` | 검사한 방 session Set 수 |
| `affectedRooms` | 정리로 영향을 받은 방 수 |
| `message` | 처리 결과 또는 Redis 오류 설명 |

Redis 접근 실패 시 제거 수 0과 오류 메시지를 반환하며 자동 스케줄 정리는
수행하지 않는다.

### 7.3 적용 범위 제한

- 현재 session registry는 단일 애플리케이션 인스턴스 기준이다.
- 다중 인스턴스에서는 공유 registry 또는 인스턴스별 session 소유권 키가 필요하다.
- 운영 이벤트·감사 로그 테이블은 추가하지 않았다.
- rate limit·중복 투표 차단 횟수 통계는 추가하지 않았다.
- 방별 채팅 topic 모니터링은 추가하지 않았다.
