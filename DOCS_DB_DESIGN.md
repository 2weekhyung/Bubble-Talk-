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
| **도배 방지** | `Value` | `chat:ratelimit:{ip}` | IP별 1초당 요청 횟수 카운트 |

---

## 4. 변경 이력 (V1.0 → V2.0)
- **`votes` 테이블 삭제**: 영구 보관 필요성이 낮은 개별 로그를 제거하고 Redis Set으로 대체하여 성능 최적화.
- **`daily_menus.final_score` 삭제**: 데이터 중복을 피하고 Redis를 유일한 실시간 점수 원천으로 정의.
- **정산 메커니즘 확립**: 12시 정각 스케줄러를 통한 Redis → MySQL 이관 프로세스 공식화.
