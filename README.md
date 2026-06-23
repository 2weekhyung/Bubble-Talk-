# LiveOpsBoard

**Redis/WebSocket 기반 실시간 채팅·투표 운영 관리 시스템**

LiveOpsBoard는 WebSocket과 Redis를 활용해 실시간 메시지 브로드캐스팅, 투표 랭킹, Rate Limiting, 관리자 운영 제어 기능을 구현한 Spring Boot 기반 실시간 운영 관리 시스템입니다.

단순 채팅 서비스 구현을 넘어, 실시간 사용자 입력이 많은 환경에서 메시지 수명 관리, 반복 요청 제한, 순위 집계, 운영 시간 제어, 금칙어 정책 관리 기능을 관리자 대시보드와 함께 제공하는 것을 목표로 했습니다.

> 기존 프로젝트명: BubbleTalk

---

## 1. 프로젝트 개요

LiveOpsBoard는 익명 채팅과 실시간 투표 기능을 중심으로, 사용자 입력이 빠르게 발생하는 환경에서 운영자가 시스템 상태와 정책을 제어할 수 있도록 구성한 실시간 운영 관리 시스템입니다.

채팅 메시지는 WebSocket/STOMP를 통해 실시간으로 전달되며, Redis TTL을 활용해 일정 시간 후 자동 만료됩니다. 투표 데이터는 Redis Sorted Set을 기반으로 집계하고, 랭킹 변화는 WebSocket을 통해 사용자 화면에 즉시 반영됩니다.

---

## 2. 핵심 구현 목표

- WebSocket/STOMP 기반 실시간 메시지 브로드캐스팅 구현
- Redis TTL을 활용한 휘발성 메시지 상태 관리
- Redis Sorted Set 기반 실시간 투표 랭킹 처리
- Redis 기반 Rate Limiting을 통한 반복 요청 제한
- Redis Pub/Sub을 활용한 실시간 이벤트 전파 구조 구현
- 관리자 대시보드를 통한 운영 시간, 공지, 금칙어, 데이터 초기화 제어
- Docker Compose 기반 MySQL/Redis 실행 환경 구성

---

## 3. 주요 기능

### 1. 실시간 메시지 브로드캐스팅

사용자가 전송한 메시지를 WebSocket/STOMP 기반으로 전체 사용자에게 실시간 전달합니다.  
메시지는 Redis TTL을 활용해 일정 시간 동안만 유지되도록 설계하여, 화면에 잠시 표시된 후 사라지는 휘발성 메시지 흐름을 구현했습니다.

### 2. Redis 기반 Rate Limiting

동일 사용자의 반복 요청을 제한하기 위해 Redis 기반 Rate Limiting을 적용했습니다.  
짧은 시간 동안 과도한 메시지 전송이 발생할 경우 요청을 제한하여 서비스 안정성과 운영 정책을 유지할 수 있도록 했습니다.

### 3. 실시간 투표 랭킹

투표 데이터는 Redis Sorted Set을 활용해 관리했습니다.  
득표수 변경 시 실시간으로 순위를 계산하고, WebSocket을 통해 사용자 화면에 랭킹 변화를 즉시 반영합니다.

### 4. 관리자 운영 대시보드

관리자는 대시보드를 통해 접속자 수, 활성 메뉴 수, 누적 투표 수, 실시간 채팅 현황을 확인할 수 있습니다.  
또한 투표 이벤트 시작/종료, 운영 시간 변경, 시스템 공지 발송, 금칙어 관리, 데이터 초기화 기능을 수행할 수 있습니다.

### 5. 금칙어 정책 관리

관리자 화면에서 금칙어를 추가하거나 삭제할 수 있으며, 변경된 정책은 실시간 채팅 필터링에 반영됩니다.  
이를 통해 운영자가 서비스 상황에 맞게 콘텐츠 정책을 조정할 수 있도록 구현했습니다.

---

## 4. 아키텍처

```text
[Client]
   |
   | WebSocket / HTTP
   v
[Spring Boot Application]
   |
   |-- WebSocket/STOMP: 실시간 채팅 및 투표 이벤트 브로드캐스팅
   |-- Admin Controller: 운영 시간, 공지, 금칙어, 데이터 초기화 제어
   |-- Chat Service: 메시지 처리 및 Rate Limiting
   |-- Vote Service: 투표 처리 및 랭킹 계산
   |
   |-- Redis
   |    |-- TTL: 휘발성 메시지 관리
   |    |-- ZSET: 실시간 랭킹 관리
   |    |-- Pub/Sub: 이벤트 전파
   |    |-- Rate Limit: 반복 요청 제한
   |
   |-- MySQL
        |-- 투표 결과 히스토리
        |-- 메뉴 데이터
        |-- 금칙어 정책
        |-- 운영 설정
```

### 패키지 구조

```text
com.bubbletalk/
├── admin.dashboard/       # 관리자 대시보드
├── base/                  # 공통 기반 클래스
├── chat/                  # 실시간 채팅 도메인
├── config/                # WebSocket, Redis, Security 설정
├── global/                # 공통 상수, 예외 처리, 유틸리티
├── main/                  # 메인 화면 컨트롤러
├── menu/                  # 메뉴 및 투표 도메인
└── security/              # 금칙어 관리 및 보안 관련 로직
```

---

## 5. 기술 스택

- **Backend**: Java 17, Spring Boot 3.3.0
- **Persistence**: Spring Data JPA, QueryDSL, Spring Data Redis
- **Real-time**: WebSocket, STOMP, Redis Pub/Sub
- **Frontend**: Thymeleaf, Vanilla JS, Tailwind CSS, FontAwesome
- **Documentation**: Springdoc OpenAPI, Swagger UI
- **Infrastructure**: Docker, Docker Compose, MySQL 8.0, Redis 7.0

---

## 6. Redis 활용 포인트

| 활용 영역 | Redis 자료구조/기능 | 적용 이유 |
|---|---|---|
| 휘발성 메시지 | TTL | 메시지를 일정 시간 후 자동 만료 처리하기 위해 사용 |
| 반복 요청 제한 | String / TTL | 동일 사용자의 짧은 시간 내 연속 메시지 전송을 제한하기 위해 사용 |
| 중복 투표 방지 | Set | 사용자별 투표 여부를 빠르게 확인하고 중복 투표를 차단하기 위해 사용 |
| 실시간 랭킹 | Sorted Set | 투표 점수 기반 순위 계산을 빠르게 처리하기 위해 사용 |
| 실시간 이벤트 전파 | Pub/Sub | 채팅, 투표, 공지 이벤트를 실시간으로 브로드캐스팅하기 위해 사용 |
| 금칙어 캐싱 | Set / Cache Aside | DB 조회 없이 빠르게 필터링 정책을 적용하고, 관리자 변경 사항을 캐시에 반영하기 위해 사용 |
| 운영 상태 관리 | String | 투표 이벤트의 OPEN/CLOSED 상태를 관리하기 위해 사용 |

---

## 7. 관리자 대시보드

관리자 대시보드는 운영자가 서비스 상태를 확인하고 운영 정책을 제어할 수 있는 화면입니다.

- 실시간 접속자 수, 활성 메뉴 수, 누적 투표 수 확인
- 실시간 채팅 모니터링
- 투표 이벤트 시작/종료 및 운영 시간 변경
- 시스템 공지 발송
- 금칙어 추가/삭제 및 Redis 캐시 갱신
- 당일 랭킹 및 투표 이력 초기화
- 과거 우승 메뉴 이력 조회

---

## 8. 트러블슈팅

개발 과정에서 발생한 기술적 이슈와 해결 과정은 [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)에 상세히 기록했습니다.  
아래는 README에서 바로 확인할 수 있도록 정리한 대표 사례입니다.

| 문제 | 원인 | 해결 |
|---|---|---|
| 실시간 랭킹 동기화 지연 | 메뉴 추가 또는 투표 후 DB/Redis 상태가 화면에 즉시 반영되지 않아 새로고침이 필요한 문제 발생 | REST API로 투표 트랜잭션을 처리한 뒤 WebSocket/STOMP로 최신 랭킹을 브로드캐스팅하도록 개선 |
| 반복 메시지 전송 문제 | 익명 채팅 특성상 동일 사용자의 짧은 시간 내 연속 요청을 제한하는 정책 필요 | WebSocket Handshake 단계에서 IP를 추출하고 Redis INCR/EXPIRE 기반 Rate Limiting을 적용 |
| 금칙어 정책 반영 지연 | 금칙어를 DB에 추가해도 Redis 캐시가 갱신되지 않으면 필터링에 반영되지 않는 Cache Aside 정합성 문제 발생 | 관리자 캐시 갱신 API와 캐시 미스 시 DB 재로딩 로직을 추가하여 필터링 정책 반영 경로 보완 |

---

## 9. 실행 방법

### 1. 인프라 실행

프로젝트 루트에서 MySQL과 Redis를 실행합니다.

```bash
docker-compose up -d
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

- **메인 화면**: `http://localhost:8080`
- **관리자 화면**: `http://localhost:8080/admin/dashboard`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`

---

## 10. 향후 개선 사항

- 관리자 인증/인가 정책 고도화
- 실시간 지표 수집 및 대시보드 시각화 강화
- 투표 결과 정산 및 히스토리 조회 기능 확장
- Redis 장애 상황을 고려한 복구 전략 보완
- 부하 테스트를 통한 Rate Limiting 임계값 검증

---

## 라이선스

본 프로젝트는 교육 및 포트폴리오 목적으로 제작되었습니다.
