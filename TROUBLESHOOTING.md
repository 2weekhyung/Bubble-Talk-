# 🛠 트러블슈팅 및 기술적 의사결정 기록

이 문서는 개발 과정에서 발생한 기술적 문제와 이를 해결하기 위한 고민의 흔적을 기록합니다. (이직 포트폴리오/면접 대비용)

---

## 📅 2026-04-24: 실시간 점심 전쟁 시스템 구축

### 1. 실시간 순위 반영 및 데이터 동기화 이슈
- **문제**: 사용자가 메뉴를 추가하거나 투표했을 때, 다른 사용자의 화면에 즉시 반영되지 않고 새로고침을 해야만 데이터가 갱신되는 문제.
- **해결**: 
    - **REST API + WebSocket(STOMP) 결합**: 투표 작업은 트랜잭션 보장을 위해 REST API(`POST`)로 처리하고, 성공 시 `SimpMessagingTemplate`을 사용하여 `/topic/menus` 채널로 최신 랭킹 리스트를 브로드캐스팅하도록 설계.
    - **결과**: 사용자가 투표 버튼을 누르는 즉시 전장의 모든 클라이언트 화면에서 게이지 바가 실시간으로 움직이는 UX 구현.

### 2. 무분별한 중복 투표 방지 (Redis 활용)
- **문제**: 특정 사용자가 API를 연타하여 한 메뉴의 점수를 비정상적으로 올리는 어뷰징 가능성.
- **해결**:
    - **Redis Set 자료구조 활용**: `lunch:voters:{date}:{menuId}` 키를 생성하고 사용자의 IP를 `SET`에 저장. 
    - **로직**: 투표 시 Redis의 `isMember`를 체크하여 이미 존재하는 IP라면 DB 연산을 수행하지 않도록 차단.
    - **이유**: DB에서 매번 투표 이력을 조회하는 것보다 In-Memory DB인 Redis를 활용하는 것이 성능상 유리하며, 실시간성이 높은 서비스 특성에 적합하다고 판단.

### 3. 익명 채팅의 도배 및 보안 이슈
- **문제**: 익명 서비스 특성상 도배(Spam) 및 부적절한 단어 사용으로 인한 서비스 질 저하 우려.
- **해결**:
    - **Rate Limiting (도배 방지)**: Redis의 `INCR`와 `EXPIRE` 기능을 조합하여 1초당 3회 이상의 메시지 전송 시 차단하는 슬라이딩 윈도우(정확히는 고정 윈도우) 알고리즘 적용.
    - **금칙어 필터링**: `ForbiddenWord` 엔티티를 별도로 구성하여 관리자가 DB에서 금칙어를 제어할 수 있게 하고, 채팅 전송 시 서버 사이드에서 `replace` 로직을 통해 `***`로 치환 처리.

### 4. 사용자 경험(UX) 강화를 위한 시각적 피드백
- **문제**: 실시간 전쟁이라는 컨셉에 비해 초기 UI가 너무 정적이고 평범함.
- **해결**:
    - **Glassmorphism & Neon Design**: 반투명 유리 질감과 네온 글로우 효과를 적용하여 '사이버 전장' 느낌 극대화.
    - **Interaction**: 투표 클릭 시 `transform`을 이용한 진동 효과와 1등 메뉴에 대한 `box-shadow` 애니메이션을 추가하여 시각적 보상 제공.

### 5. API 응답 규격 통일 및 DTO 구조화를 통한 유지보수성 향상 (2026-04-25)
- **문제**: 
    - 엔티티(Entity) 클래스가 API 응답에 직접 노출되어 내부 DB 구조가 외부에 드러나는 보안 이슈 및 순환 참조 위험성 존재.
    - 도메인 간 DTO가 혼재되어 프로젝트 확장 시 데이터 구조 파악이 어렵고, 응답 포맷이 일관되지 않아 클라이언트 측의 예외 처리가 복잡해짐.
- **해결**:
    - **Layered Architecture 준수**: 모든 API 통신에서 엔티티 대신 전용 DTO를 사용하도록 리팩토링.
    - **DTO 패키지 구조 세분화**: 각 도메인 하위에 `dto.req`(요청), `dto.res`(응답) 디렉토리를 만들어 상황별 DTO를 엄격히 분리. 특히 `DailyMenuResDto` 내부에 `MenuListResDto`를 포함하는 계층 구조를 설계하여 데이터 응집도 향상.
    - **응답 규격 표준화(`BaseResDto`)**: 성공/실패 코드(`code`), 메시지(`message`), 실제 데이터(`result`)를 담는 공통 Wrapper 클래스를 도입하고, `ResponseEntity`를 통해 HTTP 상태 코드를 제어하도록 개선.
- **결과**:
    - 서버 내부 구현의 은닉성(Encapsulation) 강화.
    - API 명세가 명확해져 프론트엔드 개발자와의 협업 효율 및 클라이언트 측 데이터 파싱 로직의 단순화 달성.

### 7. Redis TTL을 활용한 휘발성 채팅 및 IP 기반 도배 방지 (2026-04-28)
- **문제**: 
    - 익명 채팅의 특성상 도배 위험이 높으나 기존 세션 ID 방식은 브라우저 재접속으로 우회 가능.
    - '휘발성' 컨셉임에도 서버 메모리나 DB에 기록이 없어 새로 접속한 사용자는 이전 대화를 전혀 볼 수 없음.
- **해결**:
    - **Handshake Interceptor 도입**: 웹소켓 연결 시점에 `ServletServerHttpRequest`를 가로채 실제 클라이언트 IP를 추출하고 WebSocket Session Attributes에 저장.
    - **Redis Rate Limiting**: 추출된 IP를 키로 하여 Redis에서 1초당 요청 횟수를 카운트(Fixed Window 알고리즘).
    - **Redis TTL History**: 모든 메시지를 Redis에 10초 TTL로 저장. `keys` 패턴 검색을 통해 현재 '살아있는' 메시지만 반환하는 REST API(`GET /api/chat/active`)를 구축하여 초기 데이터 동기화 문제 해결.
- **결과**:
    - 강력한 IP 기반 차단으로 서비스 안정성 확보.
    - 서버 재시작이나 페이지 새로고침 시에도 '사라지기 전의 버블'들을 유지하여 자연스러운 UX 제공.

## 📅 2026-04-25: JPA Auditing 및 MappedSuperclass를 활용한 공통 필드 자동화

    - **`BaseEntity` 도입**: `@MappedSuperclass`를 사용하여 모든 엔티티가 공통으로 상속받을 수 있는 기반 클래스 설계.
    - **JPA Auditing 활성화**: `@EnableJpaAuditing`과 `@EntityListeners(AuditingEntityListener.class)`를 조합하여 애플리케이션 레벨에서 날짜 입력을 자동화.
    - **DTO 계층 구조 통일**: 입력 전용 DTO들이 `BaseDto`를 상속받도록 하여, 향후 API 요청 시 생성 일시나 요청자 정보 등 공통 메타데이터를 일관되게 처리할 수 있는 구조 확보.
- **결과**:
    - 중복 코드 제거로 엔티티 클래스가 비즈니스 로직에만 집중할 수 있게 됨.
    - 시스템에 의해 날짜가 강제 입력됨으로써 데이터 신뢰도(Data Integrity) 확보.
    - 객체지향의 상속과 다형성을 활용하여 향후 필드 확장이 용이한 구조 구축.

### 7. Java Record 도입을 통한 Response DTO 최적화 (2026-04-25)
- **고민**: 
    - API 응답 데이터는 한 번 생성된 후 변경되지 않아야 하는 불변성(Immutability)이 중요함.
    - 기존 클래스 기반 DTO는 `getter`, `equals`, `hashCode` 등 반복적인 보일러플레이트 코드가 발생하며, 필드에 실수로 `setter`를 추가할 가능성이 있음.
- **해결**:
    - **Java Record 적용**: Java 14+부터 도입된 `record` 타입을 `DailyMenuResDto` 등에 적용.
    - **의사결정**: 상속이 필요한 입력용(`req`) DTO는 `class`를 유지하고, 데이터 전달이 목적인 응답용(`res`) DTO는 `record`를 사용하여 불변성을 강제함.
- **결과**:
    - 코드의 양이 대폭 줄어들고 가독성이 향상됨.
    - 불변 객체로서 데이터 신뢰성이 높아졌으며, 최신 Java 문법을 활용한 현대적인 설계 구조를 갖춤.

### 8. REST API 400/500 에러 해결 및 실시간 랭킹 반영 로직 개선 (2026-04-27)
- **문제**: 
    - **400 Bad Request**: 메뉴 추가 및 투표 시 프론트엔드에서 데이터를 URL 파라미터로 전송하여, 서버의 `@RequestBody` 형식을 충족하지 못함.
    - **500 Internal Server Error**: Redis에서 가져온 메뉴 ID 데이터가 문자열/JSON 직렬화 과정에서 오염되어 Java의 `Long` 파싱 시 예외 발생.
    - **실시간성 결여**: 새로운 메뉴 추가 시 DB에는 저장되나 Redis ZSET에는 즉시 반영되지 않아, 첫 투표가 발생하기 전까지 랭킹 목록에 나타나지 않는 이슈.
- **해결**:
    - **통신 규격 정합성 확보**: `main.js`의 AJAX 요청 방식을 `JSON.stringify`를 이용한 JSON 바디 전송으로 수정하여 서버 규격과 일치시킴.
    - **방어적 프로그래밍(Defensive Programming)**: `MenuService`에서 Redis 데이터를 가져올 때 따옴표 제거 및 예외 처리를 추가하여 데이터 타입 불일치로 인한 서버 다운 방지.
    - **즉각적 데이터 동기화**: `MenuService.saveMenu()` 시점에 Redis ZSET에 0점으로 미리 등록하여, 메뉴 생성 즉시 실시간 전장에 노출되도록 개선.
    - **직렬화 안정성 확보**: 최신 Java Record 사용 시 특정 환경의 Jackson 라이브러리에서 발생할 수 있는 직렬화 이슈를 고려하여, 응답 DTO를 표준 Class 구조로 재조정.
- **결과**:
    - API 통신의 안정성이 확보되었으며, 메뉴 추가부터 투표까지의 흐름이 새로고침 없이 실시간으로 매끄럽게 연결됨.
    - 데이터 정합성 오류에 강한 견고한 백엔드 로직 구축.

### 9. 메뉴 추가 알림의 전역 브로드캐스팅 구현 (2026-04-27)
- **문제**: 메뉴 추가 시 "전장 투입"이라는 시각적 피드백(버블 알림)이 메뉴를 추가한 본인의 화면에만 나타나고, 다른 사용자들에게는 목록만 바뀔 뿐 시각적 알림이 공유되지 않아 '왁자지껄'한 전장 분위기가 살지 않음.
- **해결**:
    - **이벤트 전파 구조 개선**: 메뉴 추가 API 호출 시 서버에서 메뉴 목록(`/topic/menus`)뿐만 아니라 채팅 채널(`/topic/bubbles`)로도 시스템 메시지를 브로드캐스팅하도록 로직 수정.
    - **서버 사이드 메시지 생성**: `MenuSocketController`에 `broadcastSystemMessage` 메서드를 추가하여 모든 클라이언트에게 동일한 알림 문구를 전송.
    - **프론트엔드 동기화**: `main.js`에서 로컬로 알림을 생성하던 코드를 제거하고, 웹소켓 구독 로직에서 `isSystem` 플래그에 따라 특수 효과를 적용하도록 통합.
- **결과**:
    - 누군가 메뉴를 추가하면 접속 중인 모든 사용자의 화면에 "🚀 [메뉴명] 전장 투입!" 알림이 동시에 떠오름으로써 실시간 상호작용성(Interactivity)과 전장 컨셉의 몰입감 극대화.

### 10. 중복 메뉴 등록 방지 및 투표 피드백 강화 (2026-04-27)
- **문제**: 
    - 동일한 이름의 메뉴가 무분별하게 중복 등록되어 전장이 난잡해지는 이슈.
    - 투표 버튼을 눌렀을 때 중복 투표인 경우 서버에서는 무시되지만, 사용자에게는 아무런 피드백이 없어 투표가 된 것인지 알 수 없는 불친절한 UX.
- **해결**:
    - **중복 메뉴 검증 로직**: `MenuRepository`에 `existsByMenuName` 메서드를 추가하고, `MenuService.saveMenu()` 호출 시 이미 등록된 메뉴인 경우 `IllegalArgumentException`을 던져 등록을 차단.
    - **명시적 예외 처리 및 응답**: 서버 컨트롤러에서 예외 발생 시 `400 Bad Request`와 함께 명확한 에러 메시지(예: "이미 이 메뉴에 화력을 지원하셨습니다!")를 반환하도록 수정.
    - **프론트엔드 피드백 강화**: 
        - 투표 성공 시에는 기존과 같이 화력 지원 알림을 띄우고, 중복 투표 등 실패 시에는 `❌` 아이콘과 함께 서버에서 보내온 에러 메시지를 버블 알림으로 표시하여 즉각적인 인지 유도.
        - 메뉴 추가 실패 시 `alert`을 통해 중복 등록임을 경고.
- **결과**:
    - 불필요한 중복 데이터 생성을 방지하여 데이터 정합성 유지.
    - 사용자의 모든 액션(성공 및 실패)에 대해 명확한 시각적 피드백을 제공함으로써 시스템의 신뢰성과 사용 편의성 향상.

### 11. 중복 메뉴 입력 시 자동 투표 전환 및 동적 투표 버그 해결 (2026-04-27)
- **문제**: 
    - 사용자가 이미 전장에 있는 메뉴를 추가하려고 할 때 `alert` 경고창이 뜨는 것은 흐름을 끊는 부정적인 UX임.
    - 새로 추가된 메뉴에 대해 즉시 투표를 시도할 때 클라이언트 측 ID 매핑 문제로 반응이 없는 버그 발생.
- **해결**:
    - **Seamless UX 구현**: 프론트엔드(`main.js`)에서 메뉴 추가 시 중복 에러가 발생하면, `alert` 대신 현재 목록에서 해당 메뉴의 ID를 찾아 즉시 `vote()` 함수를 자동 호출하도록 개선.
    - **동적 ID 바인딩 보정**: `vote` 함수 호출 시 전달되는 ID 값을 `Number()`로 강제 형변환하여 데이터 타입 불일치로 인한 통신 오류 차단.
    - **시각적 안내**: "이미 투입된 메뉴입니다! 자동으로 화력 지원..." 이라는 부드러운 안내 버블을 띄워 사용자가 상황을 자연스럽게 인지하게 함.
- **결과**:
    - 사용자의 의도(메뉴 투입/선택)를 중단 없이 최우선으로 반영하는 매끄러운 사용자 여정(User Journey) 완성.
    - 실시간으로 추가된 메뉴에 대해서도 즉각적인 상호작용이 가능하도록 시스템 안정성 강화.

### 12. 메뉴 추가 시 자동 투표 통합 및 실시간 업데이트 정합성 확보 (2026-04-27)
- **문제**: 
    - 메뉴 추가 후 별도로 투표를 해야 하는 번거로움과 중복 입력 시 흐름이 끊기는 문제.
    - 투표 시 숫자가 즉시 변하지 않고 새로고침을 해야 반영되는 실시간성 결여 현상.
    - 간헐적으로 발생하는 500 에러로 인한 서비스 불안정.
- **해결**:
    - **메뉴 추가-투표 프로세스 통합**: `saveAndVote` 메서드를 신설하여 신규 메뉴 등록 시 즉시 +1 투표가 이루어지도록 로직을 일원화.
    - **중복 메뉴 자동 투표 전환**: 사용자가 이미 존재하는 메뉴명을 입력하면 별도의 경고 없이 해당 메뉴에 대한 투표(`increaseVote`)로 자동 연결되도록 백엔드 수준에서 처리.
    - **실시간 데이터 동기화 강화**: 소켓 전송 전 데이터 정합성을 확보하고, 프론트엔드에서 데이터 타입을 엄격히 체크(`Number()`)하여 브로드캐스트 데이터가 즉시 화면에 렌더링되지 않던 버그 해결.
    - **예외 처리 및 로깅 강화**: 랭킹 조회 및 투표 로직에 `try-catch` 블록을 보강하고 상세 로그를 남겨 500 에러의 원인을 추적 및 방어함.
- **결과**:
    - "입력 = 참여"라는 직관적인 UX를 구현하여 사용자 참여 허들을 낮춤.
    - 지연 없는 실시간 순위 변동 시각화를 통해 '전쟁' 컨셉의 생동감 극대화 및 시스템 안정성 확보.

### 13. QueryDSL을 활용한 커스텀 쿼리 구현 및 유연한 검색 환경 구축 (2026-04-27)
- **문제**: 
    - `JpaRepository`의 기본 메서드만으로는 복잡한 조건의 메뉴 검색이나 향후 확장될 필터링 기능을 구현하는 데 한계가 있음.
    - 문자열 기반의 메서드 이름 선언 방식보다 컴파일 시점에 오류를 잡을 수 있는 타입 안정성(Type Safety)이 필요함.
- **해결**:
    - **QueryDSL 인터페이스 확장**: `MenuRepositoryCustom`에 `findByMenuName`을 추가하고 `MenuRepositoryImpl`에서 QClass를 활용한 실제 쿼리 로직을 구현.
    - **타입 안정성 확보**: 단순 문자열 쿼리가 아닌 QDailyMenu 객체를 사용하여 필드명 오타나 타입 불일치를 개발 단계에서 원천 차단.
- **결과**:
    - 백엔드 로직의 유지보수성이 향상되었으며, 향후 동적 쿼리나 복잡한 통계 쿼리 도입을 위한 기술적 기반을 공고히 함.

### 14. JPA 엔티티와 DB 스키마 간 컬럼명 불일치 해결 (2026-04-27)
- **문제**: 투표 저장 시 `SQL Error: 1364, Field 'menu_id' doesn't have a default value` 에러 발생.
- **원인**: 
    - DB 스키마에는 외래키 컬럼명이 `menu_id`로 정의되어 있으나, JPA `Vote` 엔티티에서는 `@JoinColumn(name = "daily_menu_id")`로 설정되어 발생한 불일치.
    - JPA가 존재하지 않는 컬럼(`daily_menu_id`)에 값을 넣으려다 보니, 필수 컬럼인 `menu_id`가 누락된 것으로 처리됨.
- **해결**:
    - `Vote` 엔티티의 `@JoinColumn` 설정을 DB 스키마 규격인 `menu_id`로 변경하여 매핑 정합성 확보.
- **결과**:
    - 데이터 저장 로직의 물리적 오류를 해결하고 DB 제약 조건을 완벽히 충족함.

---

## 📅 2026-05-03: 시스템 최적화 및 아키텍처 단순화 (피드백 반영)

### 15. Vote 엔티티 삭제 및 Redis Source of Truth 전략 채택
- **문제**: 
    - 투표 시마다 Redis 연산과 더불어 DB `INSERT`(Vote) 및 `UPDATE`(DailyMenu)가 동시에 발생하여 대량 트래픽 상황에서 DB 병목 우려.
    - 투표 점수가 Redis, DailyMenu(score), Vote(이력) 세 곳에 분산되어 있어 데이터 정합성 관리가 어렵고 버그 발생 확률이 높음.
- **해결**:
    - **과감한 엔티티 삭제**: 개별 투표 이력을 저장하던 `Vote` 엔티티를 삭제하고 관련 패키지 및 로직을 제거함.
    - **Redis 중심 설계**: 투표 진행 시간 동안은 Redis(`ZSET`, `SET`)를 유일한 Source of Truth로 활용하여 고성능 처리 보장.
    - **지연 쓰기(Write-behind) 패턴**: 매 투표마다 DB를 건드리는 대신, 정산 시점(12시)에 Redis의 최종 결과를 DB(`LunchHistory`)에 한 번만 기록하도록 변경.
- **결과**:
    - DB 쓰기 부하를 획기적으로 줄여 '타임어택' 상황에서도 안정적인 응답 속도 확보.
    - 데이터 관리 포인트 일원화로 정합성 문제 해결 및 코드 복잡도 감소.

### 16. 금칙어 필터링 성능 고도화 (DB 조회 -> Redis 캐싱)
- **문제**: 채팅 메시지 전송 시마다 금칙어 목록을 확인하기 위해 DB `findAll`을 호출하여 성능 저하 및 DB 부하 유발.
- **해결**:
    - **Cache Aside + Initializer**: 애플리케이션 시작 시 DB의 금칙어를 Redis(`Set`)에 로드하고, `ChatService`에서는 Redis 캐시를 우선 조회하도록 변경.
    - **실시간 동기화**: 금칙어 추가/삭제 시 Redis 캐시도 즉시 갱신하여 데이터 일관성 유지.
- **결과**:
    - 메시지 전송 시 DB I/O를 제거하여 채팅 응답 속도 향상 및 대규모 접속자 수용 능력 강화.

### 17. Redis 키 관리 체계화
- **문제**: 코드 곳곳에 하드코딩된 Redis 키 접두사들이 흩어져 있어 오타 위험 및 유지보수가 어려운 상태.
- **해결**:
    - **`RedisKey` Enum 확장**: 채팅 메시지(`CHAT_BUBBLE`) 등 누락된 키들을 Enum에 통합하고, 모든 서비스 레이어에서 이 Enum을 통해서만 Redis 키를 생성하도록 강제.
- **결과**:
    - 매직 스트링 제거로 코드 가독성 향상 및 잠재적인 오타 버그 방지.

### 18. 타임 어택 잠금 시스템 (11:00 ~ 12:00) 구현
- **문제**: 점심 메뉴 투표가 24시간 열려 있어 '전쟁'이라는 타임 어택 컨셉의 긴장감과 희소성이 떨어짐.
- **해결**:
    - **Redis 상태값 관리**: `lunch:event:status` 키를 통해 전장의 활성화 상태(`OPEN`/`CLOSED`)를 관리.
    - **Spring Scheduler 연동**: 매일 11시(`OPEN`)와 12시(`CLOSED`)에 자동으로 상태를 변경하는 스케줄러 구현.
    - **Interceptor 레벨 차단**: `HandlerInterceptor`를 도입하여 이벤트 시간이 아닐 때 들어오는 투표 및 메뉴 추가 API 요청을 403 Forbidden 에러와 함께 원천 차단.
- **결과**:
    - 서비스의 핵심 컨셉인 '타임 어택'을 시스템적으로 강제하여 사용자 참여 집중도 향상.
    - 불필요한 시간에 발생하는 서버 자원 낭비 방지.

### 19. 전략적 로깅 시스템 구축 (Logback Rotation)
- **고민**: 운영 중인 서버에서 문제가 발생했을 때, 로그가 파일 하나에 계속 쌓이면 파일 크기가 너무 커져 분석이 어렵고 디스크 용량을 압박함.
- **해결**:
    - **Logback 도입**: `logback-spring.xml` 설정을 통해 콘솔과 파일을 분리하여 로그 기록.
    - **날짜별 로테이션 적용**: `TimeBasedRollingPolicy`를 사용하여 매일 새로운 로그 파일을 생성하고, 이전 로그는 자동 압축(`.gz`) 보관.
    - **리소스 최적화**: 저사양 배포 환경을 고려하여 로그 보관 주기를 30일로 설정하고, 전체 로그 용량 상한선(`totalSizeCap`)을 3GB로 제한하여 안정성 확보.
- **결과**:
    - 체계적인 이력 관리를 통해 이슈 발생 시 신속한 트래킹 가능.
    - 서버 디스크 풀(Disk Full) 장애를 사전에 방지하는 운영 안정성 확보.

---

## 📅 2026-05-11: Redis 직렬화 오류 및 리소스 정합성 해결

### 20. Redis LocalDateTime 직렬화 예외 해결
- **문제**: 채팅 메시지 저장 시 `java.time.LocalDateTime` 타입을 Jackson이 기본적으로 직렬화하지 못해 `SerializationException` 발생. 이로 인해 Redis에 메시지가 저장되지 않고 실시간 채팅이 중단되는 현상 발생.
- **해결**:
    - **커스텀 ObjectMapper 설정**: `RedisConfig`에서 `GenericJackson2JsonRedisSerializer`를 생성할 때, `JavaTimeModule`이 등록된 커스텀 `ObjectMapper`를 주입하도록 수정.
    - **Default Typing 활성화**: 다형성 처리를 위해 `activateDefaultTyping` 설정을 추가하여 JSON 데이터에 클래스 정보(`@class`)가 포함되도록 함.
    - **날짜 포맷 최적화**: `WRITE_DATES_AS_TIMESTAMPS`를 `false`로 설정하여 날짜 데이터가 가독성 있는 ISO-8601 문자열로 저장되도록 개선.
- **결과**:
    - 채팅 메시지의 생성 시간(`timestamp`)이 정상적으로 Redis에 저장되고 조회됨으로써 실시간 채팅 기능 복구 및 데이터 정합성 확보.

### 21. favicon.ico 누락으로 인한 불필요한 에러 로그 제거
- **문제**: 브라우저가 자동으로 요청하는 `favicon.ico` 리소스가 서버에 없어 `NoResourceFoundException`이 지속적으로 로그에 기록됨.
- **해결**: 
    - `src/main/resources/static/` 경로에 기본 `favicon.ico` 파일을 추가하여 리소스 요청에 대응.
- **결과**:
    - 서버 로그의 가독성이 향상되고 불필요한 에러 트래킹 비용 감소.

### 22. Docker 빌드 프로세스 최적화 (멀티스테이지 -> JAR 복사 방식)
- **고민**: 기존 멀티스테이지 빌드는 Docker 컨테이너 내부에서 전체 소스를 빌드하므로, 빌드 속도가 느리고 CI/CD 환경에서 캐시 활용이 제한적임.
- **해결**:
    - **빌드 주체 변경**: 컨테이너 내부 빌드 대신, 호스트(로컬/CI 서버)에서 `./gradlew bootJar`를 통해 JAR를 먼저 생성하도록 변경.
    - **Dockerfile 경량화**: `Dockerfile`을 실행 전용(JRE 기반)으로 작성하고 `COPY build/libs/*.jar app.jar` 명령어를 통해 이미 빌드된 아티팩트만 포함하도록 최적화.
    - **docker-compose 연동**: `docker-compose.yml`에 `build: .` 컨텍스트를 추가하여 로컬 JAR 기반의 이미지 생성을 용이하게 함.
- **결과**:
    - Docker 이미지 빌드 시간이 대폭 단축됨 (수 분 -> 수 초).
    - 불필요한 빌드 도구(JDK, Gradle 등)가 최종 이미지에 포함되지 않아 이미지 크기 감소 및 보안성 향상.

### 23. Docker 빌드 권한 오류 해결 (`.dockerignore` 활용)
- **문제**: 서버에서 `docker-compose up --build` 실행 시 `mysql_data` 폴더 내의 시스템 파일에 대한 읽기 권한이 없어 빌드가 중단되는 현상 발생.
- **해결**: 
    - **`.dockerignore` 도입**: 애플리케이션 이미지 빌드에 불필요한 `mysql_data/`, `.gradle/` 등의 경로를 빌드 컨텍스트에서 제외.
- **결과**: 
    - 권한 충돌 문제를 원천 차단하고, 도커 데몬으로 전송되는 빌드 컨텍스트 크기를 줄여 빌드 성능 향상.

### 24. Docker Compose 버전 호환성 이슈 (`KeyError: 'ContainerConfig'`)
- **문제**: 구버전 `docker-compose`(1.29.x)와 최신 Docker Engine 간의 이미지 구조 해석 차이로 인해 컨테이너 재생성(Recreating) 단계에서 에러 발생.
- **해결**: 
    - **워크라운드 적용**: 기존 컨테이너를 완전히 삭제(`down`)한 후 새 이미지를 기반으로 다시 실행(`up`)하여 메타데이터 충돌 방지.
    - **장기적 대안**: 향후 서버 도커를 업데이트하여 최신 표준인 `docker compose`(하이픈 없음) 명령어를 사용하도록 권고.
- **결과**: 
    - 인프라 환경의 한계를 이해하고 안정적인 배포 프로세스 확보.

### 25. 휘발성 채팅 시각적 피드백 오류 (CSS 애니메이션 누락)
- **문제**: 서버에는 메시지 삭제 로직이 정상 작동하나, 클라이언트 화면에서 채팅 버블이 사라지지 않고 무한히 쌓이는 현상 발견.
- **원인**: JavaScript에서는 애니메이션 종료 이벤트(`animationend`) 발생 시 요소를 제거하도록 설계되었으나, 정작 CSS에 해당 애니메이션(`@keyframes`) 정의가 누락되어 이벤트가 발생하지 않음.
- **해결**: 
    - `main.css`에 `popAndStay` 애니메이션을 추가하여 나타남-머무름-사라짐(Fade-out)의 3단계 흐름을 정의.
- **결과**: 
    - 의도한 '휘발성 채팅' 컨셉을 시각적으로 완벽히 구현하고, 브라우저 메모리 누수(DOM 요소 무한 증식) 방지.

### 26. 실시간 접속자 수(LIVE) 연동 구현
- **문제**: 상단 UI의 "LIVE" 숫자가 `1.2K`로 하드코딩되어 있어 실제 접속 현황을 반영하지 못함.
- **해결**:
    - **WebSocketEventListener 도입**: `SessionConnectEvent`와 `SessionDisconnectEvent`를 감지하는 스프링 이벤트 리스너를 구축.
    - **Redis 기반 카운팅**: 분산 환경을 고려하여 Redis의 `increment` 연산을 통해 접속자 수를 원자적(Atomic)으로 관리.
    - **실시간 브로드캐스팅**: 숫자가 변경될 때마다 `/topic/user-count` 채널로 현재 접속자 수를 전송하고, 프론트엔드에서 이를 구독하여 DOM을 실시간 업데이트.
- **결과**: 
    - 실제 사용자 접속 현황을 시각화함으로써 '실시간 전장'이라는 서비스 정체성을 강화하고 사용자 참여도 향상.

### 27. 테스트 편의를 위한 타임 어택 이벤트 시간 확장
- **고민**: 기존 11:00 ~ 12:00 설정은 테스트 가능한 시간이 짧아 기능 검증에 제약이 있음.
- **해결**: 
    - `MenuScheduler`의 크론식을 수정하여 운영 시간을 **09:00 ~ 14:00**로 대폭 확장.
    - `LunchEventInterceptor`의 차단 안내 메시지를 변경된 시간에 맞춰 업데이트.
- **결과**: 
    - 개발 및 QA 단계에서 넉넉한 시간 동안 기능을 검증할 수 있는 환경 구축.

---

## 📅 2026-05-12: 실시간 채팅 시각화 개선 및 시스템 안정화

### 28. 채팅 메시지 발신자별 색상 구분
- **문제**: 내가 보낸 채팅과 다른 사람이 보낸 채팅을 구분하기 어렵고, 동일한 발신자의 메시지가 다른 색으로 나올 수 있는 현상.
- **해결**:
    - **IP 기반 발신자 식별**: 백엔드(`MainViewController`)에서 접속자 IP를 추출하여 프론트엔드(`CLIENT_IP`)로 전달.
    - **본인 메시지 강조**: `main.js`에서 `senderIp`와 `CLIENT_IP`를 비교하여 본인의 메시지는 전용 색상(파란색 계열)으로 강조.
    - **고정 색상 해싱**: 다른 사용자의 메시지는 IP 주소를 기반으로 해싱하여 고정된 색상을 부여함으로써, 동일 발신자에게는 항상 같은 색상이 노출되도록 구현.
- **결과**: 채팅 가독성이 향상되고 사용자 간 식별이 용이해짐.
- **적용 파일**: `MainViewController.java`, `main.js`

### 29. 채팅 버블 입력창 가림 현상 해결
- **문제**: 채팅 메시지 버블이 화면 하단의 입력창 뒤로 숨거나 가려져 내용 확인이 어려운 이슈.
- **해결**:
    - **생성 범위 제한**: `main.js`의 `createBullet` 함수 내 버블 생성 `top` 위치 범위를 상단(10% ~ 55%)으로 제한하여 입력창과의 간섭 차단.
    - **레이어 순서 조정**: 버블의 `z-index`를 조정하여 입력창보다 항상 상위에 렌더링되도록 처리.
- **결과**: 어떠한 해상도에서도 채팅 내용이 입력창에 가려지지 않는 안정적인 UI 확보.
- **적용 파일**: `main.js`

### 30. 서버 재시작 시 투표 상태 초기화 로직 개선
- **문제**: 투표 운영 시간(09:00~14:00) 중 서버를 재시작할 경우, Redis의 투표 상태(`OPEN`/`CLOSED`)가 동기화되지 않아 투표 기능이 활성화되지 않는 현상.
- **해결**:
    - **애플리케이션 컨텍스트 초기화 연동**: `MenuScheduler`에 `@PostConstruct`를 활용한 `init()` 메서드 추가.
    - **상태 자동 복구**: 서버 시작 시 현재 시간을 즉시 체크하여 운영 시간 내라면 Redis의 `LUNCH_EVENT_STATUS`를 즉시 `OPEN`으로 설정하도록 보완.
- **결과**: 서버 장애나 재배포 후에도 중단 없는 서비스 운영 가능.
- **적용 파일**: `MenuScheduler.java`

### 31. 도배 경고 알림의 개인화 (Targeted Notification)
- **문제**: 특정 사용자의 도배 감지 시 발생하는 경고 메시지가 전체 사용자에게 브로드캐스트되어 서비스 이용에 혼선을 주는 이슈.
- **해결**:
    - **예외 처리 구조 변경**: `ChatService`에서 도배 감지 시 `BusinessException`을 발생시키고, 이를 소켓 컨트롤러에서 가로채도록 수정.
    - **`@SendToUser` 활용**: 웹소켓의 `@MessageExceptionHandler`와 `@SendToUser` 어노테이션을 사용하여, 예외 메시지를 전체가 아닌 해당 사용자에게만 1:1로 전송.
    - **전용 구독 채널 구축**: `main.js`에서 `/user/topic/errors` 채널을 추가로 구독하여 개인화된 알림을 수신하도록 처리.
- **결과**: 불필요한 시스템 메시지 노출을 줄여 전체적인 서비스 정밀도 향상.
- **적용 파일**: `ChatService.java`, `ChatSocketController.java`, `main.js`

### 32. 금칙어 필터링 미작동 및 캐시 정합성(Cache Consistency) 문제 해결
- **문제**: DB에 금칙어(예: '바보')를 직접 추가했음에도 실제 채팅에서 필터링이 적용되지 않고 그대로 노출되는 현상 발생.
- **원인**: 
    - 성능 최적화를 위해 도입한 **Cache Aside 패턴**에서 발생한 데이터 불일치.
    - `ChatService`는 고속 처리를 위해 Redis 캐시를 우선 참조하는데, 외부(DB 직접 수정)에서 데이터가 변경될 경우 Redis는 이를 인지하지 못해 이전의 빈 목록(Stale Data)을 계속 유지함.
- **해결**:
    - **캐시 동기화 API 구현**: 관리자가 명시적으로 캐시를 갱신할 수 있는 `/api/admin/forbidden-words/refresh` 엔드포인트를 추가하여 운영 중에도 데이터 정합성을 확보할 수 있게 함.
    - **관찰성(Observability) 강화**: `ChatService`에 필터링 과정의 로그(적용 목록, 매칭 단어)를 추가하여 이슈 발생 시 원인을 즉시 파악할 수 있도록 개선.
    - **방어적 캐시 로드**: `getForbiddenWords()` 메서드 내에서 캐시가 비어있을 경우 DB에서 자동으로 로드하는 로직을 보강하여 'Cold Start' 상황에 대응.
- **결과**: 고성능 필터링을 유지하면서도 데이터 변경 사항을 즉각적 혹은 명시적으로 반영할 수 있는 신뢰성 있는 시스템 구축.
- **적용 파일**: `ForbiddenWordRestController.java`, `ChatService.java`, `ForbiddenWordService.java`

### 33. 사용자 편의를 위한 드래그형 화면 리사이저(Resizer) 구현
- **고민**: 상단의 채팅 영역(대나무숲)과 하단의 투표 섹션의 비중을 사용자의 환경이나 선호도에 따라 조절할 수 없는 고정형 UI의 한계.
- **해결**:
    - **리사이저 바(Resizer Bar) 도입**: 두 섹션 사이에 마우스 호버 시 반응하는 리사이저 요소를 배치하고, `cursor: row-resize` 속성을 통해 직관적인 UX 제공.
    - **마우스 이벤트 핸들링**: `mousedown`, `mousemove`, `mouseup` 이벤트를 조합하여 드래그 중인 마우스의 `clientY` 값을 실시간으로 계산, 상단 섹션의 `height`를 동적으로 조절하는 로직 구현.
    - **임계치 설정(Constraint)**: 화면이 너무 작아지거나 커져 레이아웃이 깨지는 것을 방지하기 위해 최소 100px, 최대 60vh의 높이 제한(Clamp) 적용.
    - **드래그 상태 최적화**: 드래그 중 텍스트 선택이 되지 않도록 `user-select: none` 처리 및 `body` 클래스 제어를 통해 매끄러운 조작감 확보.
- **결과**: 사용자가 자신의 작업 환경에 맞춰 정보 밀도를 직접 조절할 수 있는 유연한 인터페이스 완성.
- **적용 파일**: `main.html`, `main.css`, `main.js`

### 34. 투표 시스템 정합성 오류 및 알림 브로드캐스팅 범위 수정
- **문제**: 
    - 메뉴 추가 시 내부적으로 자동 투표가 이루어짐에도 불구하고, 클라이언트에게 '이미 투표했다'는 에러 알림이 발생하는 버그.
    - 투표 성공/실패 알림이 전체 사용자에게 브로드캐스트되어 개인의 행동이 불필요하게 공표되는 프라이버시 및 UX 저해 이슈.
- **원인**: 
    - **로직 중첩**: `saveAndVote`가 내부적으로 `increaseVote`를 호출하여 Redis에 투표 이력을 남기는데, 이후 프론트엔드에서 중복 메뉴로 판단하여 다시 투표를 시도하거나 백엔드 예외 처리가 미흡했음.
    - **과도한 브로드캐스트**: `MenuRestController`에서 투표 성공 시 웹소켓을 통해 모든 사용자에게 시스템 메시지를 전송하도록 설계되어 있었음.
- **해결**:
    - **알림 주체 변경 (Server -> Client)**: 서버의 전체 시스템 메시지 브로드캐스트 로직을 제거하고, 프론트엔드(`main.js`)의 AJAX 응답 결과에 따라 '본인의 화면'에만 알림 버블을 띄우도록 수정.
    - **예외 처리 정교화**: `BusinessException`을 정확히 캐치하여 프론트엔드로 에러 메시지를 전달하도록 백엔드 컨트롤러 리팩토링.
    - **UX 최적화**: "입력 = 참여" 컨셉을 유지하되, 이미 참여한 사용자가 다시 시도할 때만 경고를 주고, 성공 시에는 본인에게만 시각적 보상(화력 지원 알림)을 제공하도록 변경.
- **결과**: 네트워크 트래픽 감소 및 사용자 개인 행동에 최적화된 프라이빗한 피드백 시스템 구축.
- **적용 파일**: `MenuRestController.java`, `main.js`

### 35. Springdoc OpenAPI(Swagger) 도입을 통한 API 문서 자동화
- **고민**: 프로젝트 규모가 커짐에 따라 프론트엔드와 백엔드 간의 API 규격 공유가 수동(문서 작성)으로 이루어져 데이터 불일치 및 소통 비용이 증가함.
- **해결**:
    - **Swagger UI 도입**: `springdoc-openapi-starter-webmvc-ui` 의존성을 추가하여 별도의 문서 작성 없이 코드 기반의 API 명세 자동 생성 환경 구축.
    - **전역 설정(SwaggerConfig)**: API 제목, 설명, 버전 정보를 관리하는 전역 설정 클래스를 구축하여 문서의 가독성과 전문성 확보.
    - **상세 어노테이션 적용**: 각 컨트롤러와 메서드에 `@Tag`, `@Operation` 등을 적용하여 API의 역할과 상세 동작 과정을 설명함.
- **가치**:
    - **협업 효율 극대화**: 프론트엔드 개발자가 서버를 띄운 후 바로 API를 테스트해 볼 수 있는 인터랙티브한 환경 제공.
    - **문서 최신화**: 코드 변경 시 문서가 자동으로 동기화되어 소스 코드와 문서 간의 정합성 문제 해결.
- **적용 파일**: `build.gradle`, `SwaggerConfig.java`, 각 REST 컨트롤러들

### 36. 서비스 운영을 위한 관리자 대시보드(Admin Dashboard) 구축
- **고민**: 금칙어 관리, 캐시 갱신, 이벤트 상태 변경 등 운영에 필요한 작업들이 API 직접 호출 방식으로 이루어져 운영 효율이 낮고 실시간 모니터링이 어려운 문제.
- **해결**:
    - **통합 관리 화면 구축**: Thymeleaf와 Vanilla JS를 활용하여 실시간 지표(Live Users, Active Menus, Total Votes)를 한눈에 볼 수 있는 대시보드 구현.
    - **실시간 통계 연동**: 웹소켓을 재활용하여 대시보드에서도 서버 새로고침 없이 실시간 접속자 수를 모니터링할 수 있도록 구현.
    - **시스템 제어권 강화**:
        - **금칙어 CRUD**: 화면에서 즉시 금칙어를 추가/삭제하고 Redis 캐시를 갱신할 수 있는 인터페이스 제공.
        - **이벤트 강제 제어**: 스케줄러에 의존하지 않고 운영자가 즉시 투표를 시작(OPEN)하거나 종료(CLOSED)할 수 있는 관리자 전용 API 및 UI 구현.
        - **데이터 초기화**: 비정상적인 데이터 발생 시 오늘의 랭킹 및 투표 이력을 일괄 삭제할 수 있는 안전장치(Reset) 마련.
- **가치**: "개발만 하는 것이 아니라 서비스의 전체 생명주기와 운영 편의성까지 고려하는 개발자"라는 차별화된 포인트 확보.
- **적용 파일**: `AdminViewController.java`, `MenuRestController.java`, `admin/dashboard.html`, `admin.js`

### 37. 어드민 대시보드 기능 고도화 (이력 조회 및 실시간 모니터링)

- **고민**: 기존 어드민 페이지는 통계 요약과 금칙어 관리 기능만 제공하여 과거 데이터 분석 및 상세 실시간 모니터링에 한계가 있음.
- **해결**:
    - **과거 우승 메뉴 조회**: `TB_LUNCH_HISTORY` 테이블에 쌓인 데이터를 조회하는 REST API를 구축하고, 대시보드에 표(Table) 형태로 시각화했습니다. 날짜별로 최종 우승한 메뉴와 득표수를 한눈에 확인할 수 있도록 구현했습니다.
    - **실시간 채팅 모니터링**: 어드민이 별도의 채팅창에 참여하지 않고도 서비스 내 발생하는 대화를 실시간으로 모니터링할 수 있도록 웹소켓(`/topic/chat`)을 연동했습니다. 발신자 IP를 기반으로 고유 색상을 부여하여 식별력을 높였습니다.
    - **UI/UX 개선**: Tailwind CSS를 활용해 다크 모드 기반의 세련된 대시보드 레이아웃을 구성하고, 애니메이션(`animate-slide-in`)을 추가하여 실시간성을 강조했습니다.
- **가치**: 운영 데이터의 자산화(History)와 관리 효율성(Monitoring)을 동시에 달성하여, 실무적인 운영 시스템으로서의 완성도를 높였습니다.
- **적용 파일**: `AdminViewController.java`, `MenuRestController.java`, `MenuService.java`, `dashboard.html`, `admin.js`, `LunchHistoryResDto.java`

## 📅 2026-05-16: 서버 시간대(TimeZone) 불일치 해결 및 투표 시간 로직 정교화

### 38. Docker 컨테이너 시간대(UTC)와 한국 시간(KST) 차이로 인한 스케줄러 오작동 해결
- **문제**: 
    - 점심 투표 운영 시간을 09:00 ~ 14:00으로 설정했으나, 실제로는 오후 6시(18:00 KST)가 되어서야 투표가 활성화되는 현상 발생.
    - 원인 분석 결과, Docker 컨테이너 환경의 기본 시간대가 **UTC(세계 표준시)**로 설정되어 있어 한국 시간보다 9시간 느리게 작동함. (한국 18:00 = UTC 09:00)
- **해결**:
    - **JVM 레벨 TimeZone 고정**: `BubbleTalkApplication.java`에 `@PostConstruct`를 활용하여 서버 시작 시 `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))`를 실행하도록 설정.
    - **이유**: Docker 환경 변수(`TZ`) 설정에 의존하지 않고, 애플리케이션 코드 레벨에서 한국 시간 기준의 비즈니스 로직(점심 투표)을 보장하기 위함.
- **결과**: 인프라 환경(로컬, 도커, 클라우드 등)에 상관없이 항상 한국 시간(KST) 기준으로 09:00에 전쟁이 시작되고 14:00에 정산되는 일관성 확보.

### 39. 투표 시간 비교 로직의 경계값 누락 방지 (!isBefore 활용)
- **문제**: `MenuScheduler.java`의 초기화 로직에서 `now.isAfter(LocalTime.of(9, 0))`을 사용하여, 정확히 09:00:00에 서버가 재시작될 경우 `false`로 처리되어 투표가 활성화되지 않는 미세한 버그 가능성 존재.
- **해결**:
    - **경계값 포함 비교**: `isAfter` 대신 `!now.isBefore(LocalTime.of(9, 0))` 형식을 사용하여 **09시 00분 00초**를 포함하도록 로직 정교화.
- **결과**: 스케줄러의 작동 시간대와 서버 재시작 시 상태 복구 로직 간의 시간적 공백(1초 미만)을 완전히 제거하여 시스템의 정밀도 향상.

### 40. 메인 화면 사용자 경험(UX) 고도화 (타이머, 통계, 알림, 추천)
- **문제**: 서비스의 핵심인 '점심 전쟁' 컨셉에 비해 메인 화면이 다소 정적이고 사용자 참여를 유도하는 시각적 장치가 부족함.
- **해결**:
    - **실시간 카운트다운 타이머**: 어드민에서 설정한 종료 시간을 기준으로 실시간 남은 시간을 표시. 1시간 미만 시 색상 변경으로 긴박감 조성.
    - **어제의 우승자 노출**: `LunchHistory` 데이터를 활용하여 전날의 승리 메뉴를 상단에 배치함으로써 서비스의 연속성 부여.
    - **실시간 순위 역전 알림**: 웹소켓 데이터 수신 시 이전 1위와 현재 1위를 비교하여, 순위가 바뀌는 드라마틱한 순간을 전역 시스템 메시지로 브로드캐스팅.
    - **상황별 메뉴 추천**: 현재 날씨나 시간대(Mocking)에 맞는 메뉴를 시스템이 지능적으로 제안하는 섹션 추가.
- **가치**: "기술적 구현을 넘어, 어떻게 하면 사용자가 서비스에 더 몰입하고 재미를 느낄 수 있을까?"에 대한 UX적 고민을 실무 레벨에서 풀어냄.

---

## 📅 2026-06-24: 비회원 식별·동시성·채팅방 도메인 안정화

### 41. GuestID 기반 익명 사용자 식별 구조 개선
- **문제**:
    - IP만으로 사용자를 구분하면 회사·학교·공용 네트워크처럼 여러 사용자가 하나의 공인 IP를 공유할 때 서로 같은 사용자로 오인될 수 있음.
    - 반대로 모바일 네트워크 전환이나 프록시 사용으로 IP가 바뀌면 동일 사용자가 새로운 사용자처럼 인식되는 문제가 있음.
    - 브라우저의 `sessionStorage` 기반 `clientId`는 탭 종료 시 사라지고 클라이언트가 임의 변경할 수 있어 핵심 식별자로 사용하기 어려움.
- **해결**:
    - 서버가 UUID 기반 `BT_GUEST_ID` 쿠키를 발급하도록 `GuestIdSupport`를 도입.
    - 쿠키에 30일 만료, `HttpOnly`, `SameSite=Lax`를 적용하고 운영 환경에서 `Secure` 옵션을 활성화할 수 있도록 설정화.
    - 사용자 식별 우선순위를 `guestId → clientId → IP`로 통일.
    - 기존 `sessionStorage clientId`는 이전 클라이언트와의 호환을 위해 보조 식별자로 유지하고, IP도 운영 로그와 fallback 용도로 유지.
- **검증**:
    - 메인 페이지 최초 요청 시 UUID 형식의 `BT_GUEST_ID` 쿠키 발급 확인.
    - 메뉴·채팅·채팅방 요청에서 GuestID가 최우선으로 사용되고, 쿠키가 없을 때 clientId와 IP로 순차 fallback되는 구조 확인.
- **결과**: 로그인이나 사용자 테이블 없이도 브라우저 단위의 지속적인 익명 사용자 식별 기반을 확보.
- **적용 파일**: `GuestIdSupport.java`, `MainController.java`, `MenuRestController.java`, `ChatSocketController.java`, `IpHandshakeInterceptor.java`, `application.yml`, `main.js`

### 42. 투표 중복 방지 Redis SADD 원자성 개선
- **문제**:
    - 기존 `SISMEMBER → ZINCRBY → SADD` 방식은 중복 여부 확인과 투표자 등록이 분리되어 있었음.
    - 동일 사용자가 동시에 여러 요청을 보내면 각 요청이 모두 “미투표”로 판단하여 점수가 중복 증가할 수 있는 경쟁 조건이 존재.
- **원인**: 중복 확인과 최초 투표자 등록이 하나의 원자적 연산으로 묶이지 않은 Check-Then-Act 구조.
- **해결**:
    - Redis Set의 `SADD` 반환값을 최초 투표 여부의 기준으로 변경.
    - `SADD = 1`인 최초 요청에서만 ZSet의 랭킹 점수를 증가.
    - `SADD = 0`이면 이미 투표한 사용자로 처리하고 점수를 변경하지 않음.
    - Redis 응답이 `null`인 비정상 상황에서는 점수를 증가시키지 않고 명확한 예외를 반환.
- **검증**:
    - 동일 투표자에 대한 연속·동시 요청을 모킹하여 `SADD`는 여러 번 호출되더라도 `incrementScore`는 최초 한 번만 실행되는 테스트 통과.
- **결과**: 별도 사용자 테이블이나 DB Lock 없이 Redis 단일 명령의 원자성을 활용해 중복 득표 가능성을 차단.
- **적용 파일**: `MenuService.java`, `MenuServiceTest.java`, `RedisKey.java`

### 43. 익명 메뉴 추가·채팅 입력에 대한 서버 측 방어 강화
- **문제**:
    - 익명 사용자가 메뉴 추가 API를 반복 호출하거나 공백·과도하게 긴 문자열·HTML/script 형태의 입력을 보낼 수 있음.
    - 프론트엔드 검증만으로는 API 직접 호출을 차단할 수 없으며, 채팅도 동일 메시지 반복과 단시간 연속 요청에 취약함.
- **해결**:
    - 메뉴 추가 요청자를 `guestId → clientId → IP` 순서로 식별하고 Redis `SETNX + TTL`로 요청자별 30초에 1회만 허용.
    - 메뉴명에 대해 null, blank, trim, 최대 20자, HTML/script 위험 문자 검증을 서버에서 수행.
    - 채팅 메시지에 대해 null, blank, trim, 최대 200자, HTML/script 위험 문자 검증을 서버에서 수행.
    - 기존 금칙어 필터와 채팅 rate limit을 유지하면서 제한 키의 주체를 GuestID 우선으로 변경.
    - 프론트 출력은 `textContent`, `innerText`, HTML escape 처리를 사용하도록 점검.
- **검증**:
    - 메뉴 추가 rate limit, 잘못된 메뉴명 차단, 채팅 공백·길이·위험 입력 차단, 동일 메시지 반복 및 과다 전송 차단 테스트 통과.
- **결과**: 클라이언트를 신뢰하지 않는 서버 중심 입력 검증과 익명 어뷰징 방어 체계를 확보.
- **적용 파일**: `MenuService.java`, `MenuRestController.java`, `ChatService.java`, `main.js`, `MenuServiceTest.java`, `ChatServiceTest.java`

### 44. WebSocket 접속자 수 카운터 불일치와 Redis Set 기반 세션 관리
- **문제**:
    - 전역 접속자 수를 Redis `INCR/DECR` 값으로만 관리하면 중복 connect/disconnect 이벤트, 비정상 종료, 재연결 상황에서 실제 세션 수와 값이 달라질 수 있음.
    - 단순 감소 방식은 음수 보정은 가능하지만 “현재 어떤 세션이 활성 상태인지” 확인할 수 없음.
- **해결**:
    - 활성 WebSocket `sessionId`를 `chat:active:sessions` Redis Set에 저장.
    - connect 시 `SADD`, disconnect 시 `SREM`을 수행하고, 접속자 수는 Set의 `SCARD` 결과로 계산.
    - 중복 이벤트가 발생해도 동일 sessionId는 Set에 한 번만 존재하므로 접속자 수가 중복 증가하거나 감소하지 않음.
    - 기존 `/topic/user-count` 브로드캐스트 경로는 유지하여 프론트 호환성을 보장.
- **검증**:
    - disconnect 이벤트 발생 시 활성 세션 Set 제거와 `/topic/user-count` 전송 여부를 단위 테스트로 확인.
- **결과**: 카운터 값 자체가 아니라 실제 활성 세션 집합을 기준으로 접속자 수를 관리하는 구조로 개선.
- **적용 파일**: `WebSocketEventListener.java`, `RedisKey.java`, `WebSocketEventListenerTest.java`

### 45. 전역 채팅을 유지하면서 채팅방 도메인으로 점진적 확장
- **문제**:
    - 기존 서비스는 `/app/chat/send`, `/topic/bubbles` 기반 전역 채팅만 제공하여 사용자 그룹을 분리할 수 없음.
    - 채팅방 기능을 한 번에 교체하면 기존 채팅·투표·메뉴 기능과 프론트 호환성이 깨질 위험이 큼.
- **해결**:
    - MySQL `chat_room` 테이블에는 방 코드, 이름, 설명, 공개 여부, 최대 인원, 상태, 생성·수정·종료 시각 등 영구 메타데이터만 저장.
    - 참여자 테이블과 채팅 메시지 영구 저장 테이블은 추가하지 않고, 현재 세션과 접속자 상태는 Redis에 저장.
    - 공개방 생성·목록 조회, 비밀방 코드 입장, 상세 조회, 입장 가능 여부 확인, 나가기 API를 추가.
    - 비밀방은 공개 목록에서 제외하되 roomCode를 알고 있으면 입장 가능하도록 설계.
    - 방 기반 STOMP 경로 `/app/rooms/{roomCode}/chat/send`, `/topic/rooms/{roomCode}/bubbles`, `/topic/rooms/{roomCode}/user-count`를 추가.
    - 기존 전역 채팅 경로는 제거하지 않고 roomCode가 없는 메시지는 기존 topic으로 처리.
- **검증**:
    - 공개방·비밀방 생성, 공개방만 목록 노출, roomCode 상세 조회, 코드 입장, 나가기, 존재하지 않는 방 예외를 로컬 MySQL·Redis 환경에서 확인.
    - 기존 전역 경로를 코드와 Security 설정에서 유지하는 것 확인.
- **결과**: 기존 서비스를 중단하거나 대규모로 재작성하지 않고 방 기반 실시간 참여 구조를 점진적으로 추가.
- **적용 파일**: `chatroom/**`, `ChatSocketController.java`, `ChatService.java`, `ChatMessage.java`, `RedisKey.java`, `main.html`, `main.js`

### 46. 채팅방 최대 인원 확인과 세션 등록의 원자성 문제
- **문제**:
    - `SCARD로 현재 인원 확인 → SADD로 세션 추가`를 별도 명령으로 실행하면, 남은 자리가 한 자리일 때 여러 요청이 동시에 검사를 통과하여 최대 인원을 초과할 수 있음.
    - HTTP join 요청에는 WebSocket sessionId가 없으므로 HTTP 입장 검증과 실제 실시간 세션 등록을 동일하게 처리할 수도 없음.
- **해결**:
    - HTTP join은 방 존재 여부, CLOSED 여부, 현재 정원 상태를 확인하고 방 정보를 반환하는 역할로 제한.
    - 실제 참가자 수 증가는 WebSocket join 또는 첫 방 메시지 처리 시 sessionId를 기준으로 수행.
    - Redis Lua script에서 `SISMEMBER`, `SCARD`, 정원 비교, `SADD`를 하나의 원자적 연산으로 실행.
    - 방별 상태를 다음 Redis 키로 분리:
        - `room:{roomCode}:sessions`: 실제 인원 계산용 session Set
        - `room:{roomCode}:guests`: 운영·확장용 익명 요청자 Set
        - `room:{roomCode}:session-actors`: session과 요청자 매핑 Hash
        - `room:session:rooms:{sessionId}`: disconnect 정리용 입장 방 Set
    - disconnect와 명시적 leave 시 session-actor 매핑을 이용해 같은 GuestID의 다른 탭이 남아 있는지 확인한 후 guest Set을 정리.
- **검증**:
    - Redis session Set 크기가 최대 인원과 같을 때 상세 응답이 `FULL`로 계산되는 것 확인.
    - 추가 입장 요청이 기존 BaseResDto 예외로 차단되는 것 확인.
    - 원자 등록 성공, 정원 초과 차단, disconnect 정리, Redis 인원 조회 실패 시 0 fallback 테스트 통과.
- **결과**: 동일 시점의 다중 입장 요청에서도 최대 인원을 초과하지 않는 실시간 세션 관리 구조 확보.
- **적용 파일**: `ChatRoomService.java`, `ChatSocketController.java`, `WebSocketEventListener.java`, `RedisKey.java`, `ChatRoomServiceTest.java`

### 47. `chat_room` 로컬 스키마 부재로 인한 방 API 500 오류
- **문제**:
    - 채팅방 Entity와 API 구현은 완료됐지만 로컬 MySQL `bubble_talk` 데이터베이스에 `chat_room` 테이블이 없어 방 생성·목록 API가 HTTP 500을 반환.
- **원인**:
    - 당시 실제 `application.yml` 기준으로 dev 프로필에는 `spring.jpa.hibernate.ddl-auto` 설정이 없었고, `update`는 prod 프로필에만 설정되어 있었음.
    - 따라서 dev 앱 기동만으로 신규 테이블이 자동 생성되지 않음.
- **해결**:
    - 현재 Entity 매핑에 맞춰 로컬 DB에 `chat_room` 테이블을 생성.
    - `room_code` UNIQUE 인덱스와 공개방 목록 조회용 `(is_private, status, created_date)` 복합 인덱스를 구성.
- **검증**:
    - `SHOW TABLES`, `DESC chat_room`, `SHOW INDEX FROM chat_room`으로 테이블·컬럼·인덱스 확인.
    - 공개방/비밀방 생성 후 DB에 `is_private`, `max_participants`, `status`, 생성·수정 시각이 정상 저장되는 것 확인.
    - 공개방 목록에서 비밀방 제외, 코드 입장, FULL 상태와 정원 초과 차단까지 로컬 환경에서 확인.
- **주의사항**:
    - `BaseEntity`의 실제 컬럼명은 현재 `created_date`, `modified_date`이며 `created_at`, `updated_at`이 아님.
    - dev 프로필의 자동 DDL 정책과 prod 프로필의 `ddl-auto: update` 사용 여부는 배포 전 별도로 정리할 필요가 있음.
- **결과**: 로컬 환경에서 채팅방 도메인의 DB–JPA–REST–Redis 연동 정상 동작 확인.
- **적용 파일**: `ChatRoom.java`, `BaseEntity.java`, `ChatRoomRepository.java`, `application.yml`, `database_schema.txt`

### 48. 메뉴 추가·투표 API가 `/login`으로 302 리다이렉트되는 문제
- **문제**:
    - 로그인 없이 사용해야 하는 `POST /api/menu/add`, `POST /api/menu/vote` 요청이 정상 JSON 응답 대신 `/login`으로 302 리다이렉트됨.
- **영향**:
    - 로그인·회원가입이 없는 BubbleTalk에서 핵심 참여 기능인 메뉴 추가와 투표를 익명 사용자가 이용할 수 없었음.
- **원인**:
    - 두 API는 `authorizeHttpRequests`에서 이미 `permitAll`이므로 인증 required 문제는 아니었음.
    - Spring Security CSRF 예외에는 `/ws-bubble/**`, `/api/rooms/**`만 등록되어 있어 CSRF 토큰 없는 메뉴·투표 POST가 차단됨.
    - 폼 로그인 기반 Security 설정의 인증 진입점 때문에 클라이언트에서는 로그인 리다이렉트로 관찰됨.
- **해결**:
    - CSRF 전체 비활성화는 하지 않고 익명 서비스에 필요한 두 경로만 `ignoringRequestMatchers`에 추가.
    - 관리자 경로 `/admin/**`, `/api/admin/**`, `/api/menu/admin/**`의 `ROLE_ADMIN` 정책과 CSRF 보호는 유지.
- **검증**:
    - 운영 상태가 CLOSED일 때 메뉴 추가 요청이 `/login`이 아닌 기존 운영시간 인터셉터의 JSON 403 응답까지 도달하는 것 확인.
    - 이벤트 상태를 임시 OPEN으로 변경한 뒤 `POST /api/menu/add`와 `POST /api/menu/vote`가 각각 HTTP 200 BaseResDto를 반환하고 랭킹 점수가 0에서 1로 증가하는 것 확인.
    - `GET /api/menu/rankings`가 HTTP 200을 반환하는 것 확인.
    - 익명 `POST /api/menu/admin/status` 요청은 기존과 동일하게 `/login`으로 리다이렉트되고, 관리자 로그인과 CSRF 토큰이 있는 요청만 성공하는 것 확인.
    - 채팅방 API, GuestID 쿠키, `/ws-bubble/info` 응답이 기존처럼 정상 동작하는 것 확인.
    - `compileJava`, `test` 성공.
- **배운 점**:
    - `permitAll`은 인증·인가 규칙이며 CSRF 검증 통과를 의미하지 않는다. 익명 허용 여부와 CSRF 예외 범위를 별도로 설계해야 한다.
    - 로그인 없는 서비스에서도 Spring Security 필터 설정 하나가 일반 사용자 POST API 전체의 회귀를 만들 수 있다.
    - 보안 설정 변경 후에는 수정 대상뿐 아니라 메뉴·투표·채팅방·관리자 보호 등 기존 핵심 경로를 함께 회귀 테스트해야 한다.
- **결과**: 일반 사용자의 익명 메뉴·투표 기능을 복구하면서 관리자 인증과 CSRF 보호 범위는 유지.
- **적용 파일**: `SecurityConfig.java`

### 49. roomCode DB unique 충돌 재시도 처리
- **문제**:
    - 대문자와 숫자로 구성한 8자리 랜덤 roomCode는 충돌 확률이 낮지만 0은 아님.
    - 생성 전 `existsByRoomCode` 확인만으로는 확인 직후 다른 요청이 같은 코드를 저장하는 경쟁 조건을 완전히 막을 수 없음.
- **해결**:
    - `chat_room.room_code`의 DB UNIQUE 제약을 최종 정합성 보장 수단으로 유지.
    - 저장 시 `DataIntegrityViolationException`이 발생하면 새로운 roomCode를 생성해 최대 10회 재시도.
    - 충돌로 실패한 트랜잭션 상태가 다음 저장에 영향을 주지 않도록 room 생성 저장을 바깥 트랜잭션 없이 독립적으로 실행.
- **검증**:
    - 첫 `saveAndFlush`에서 UNIQUE 충돌 예외를 발생시키고 두 번째 저장이 성공하는 단위 테스트 통과.
    - 저장 메서드가 두 번 호출되고 최종 방 생성 응답이 반환되는 것 확인.
- **결과**: 애플리케이션의 사전 중복 확인에 의존하지 않고 DB UNIQUE 제약과 제한된 재시도를 결합해 roomCode 충돌을 안전하게 처리.
- **적용 파일**: `ChatRoom.java`, `ChatRoomRepository.java`, `ChatRoomService.java`, `ChatRoomServiceTest.java`

### 50. 최종 회귀 검증
- **자동 검증**:
    - `./gradlew.bat compileJava` 성공.
    - `./gradlew.bat test` 성공.
    - 총 33개 테스트, 실패 0, 오류 0.
- **수동 검증**:
    - GuestID 쿠키 발급, 메뉴 추가·투표·랭킹 조회, 공개방·비밀방 생성과 목록 정책, roomCode 입장, FULL 상태, WebSocket SockJS endpoint, 관리자 보호 정책 확인.
- **남은 운영 과제**:
    - dev/prod의 DB 이름(`bubble_talk`/`bubbletalk`)과 DDL 정책을 배포 환경에 맞게 명확히 분리.
    - prod에서는 `ddl-auto: update` 대신 Flyway/Liquibase와 `validate` 조합 검토.
    - 실제 브라우저 다중 탭 환경에서 STOMP reconnect와 disconnect 후 Redis stale session 정리 시나리오 추가 검증.
    - 전역 WebSocket 채팅의 실제 브라우저 송수신은 자동화 환경 제한으로 미검증이므로 별도 브라우저 수동 검증 필요.

## 📅 2026-06-24: 어드민 대시보드 1차 고도화

### 51. 사용자용 Top 10 랭킹을 관리자 전체 통계로 사용한 문제
- **문제**:
    - 관리자 화면의 활성 메뉴 수와 총 투표 수가 `GET /api/menu/rankings` 결과로 계산되고 있었음.
    - 해당 API는 사용자 화면을 위한 상위 10개 메뉴만 반환하므로 메뉴가 10개를 초과하면 실제 전체 메뉴 수와 전체 투표 수보다 작게 표시됨.
- **해결**:
    - 관리자 전용 `GET /api/admin/dashboard/summary` API를 추가.
    - `todayMenuCount`는 당일 `lunch:ranking:{yyyyMMdd}` ZSet의 `ZCARD`로 계산.
    - `todayVoteCount`는 동일 ZSet의 전체 member score를 조회해 합산.
    - 사용자용 랭킹 API와 관리자용 운영 통계의 책임을 분리.
- **검증**:
    - ZSet cardinality가 14일 때 `todayMenuCount=14`를 반환하는 단위 테스트 통과.
    - 전체 score 3, 7, 2를 `todayVoteCount=12`로 합산하는 단위 테스트 통과.
- **결과**: 관리자 화면의 “전체” 지표와 실제 Redis 집계 범위를 일치시킴.
- **적용 파일**: `AdminDashboardService.java`, `AdminDashboardSummaryResDto.java`, `AdminDashBoardRestController.java`, `MenuService.java`, `MenuServiceTest.java`

### 52. 최신 채팅방 도메인이 관리자 화면에 보이지 않는 문제
- **문제**:
    - `chat_room`과 방별 Redis session 구조가 도입됐지만 관리자 화면에서는 공개방·비밀방, 상태, 현재 인원을 확인할 수 없었음.
    - 일반 사용자용 `GET /api/rooms`는 비밀방과 CLOSED 방을 제외하므로 관리자 운영 조회에 사용할 수 없음.
- **해결**:
    - `GET /api/admin/rooms`를 추가하여 공개방·비밀방과 OPEN·FULL·CLOSED 방을 최신 생성순으로 모두 반환.
    - 관리자 방 응답에 roomCode, 이름, 공개 여부, 상태, 현재 인원/최대 인원, 생성·종료 시각을 포함.
    - `currentParticipants`는 `room:{roomCode}:sessions` Redis Set의 `SCARD` 기준으로 계산하고 Redis 조회 실패 시 0으로 fallback.
    - 대시보드에 전체·공개·비밀 방 수, OPEN·FULL·CLOSED 상태 요약과 방 목록 테이블을 추가.
- **검증**:
    - 관리자 목록에 비밀방과 CLOSED 방이 포함되는 단위 테스트 통과.
    - 관리자 로그인 후 `/api/admin/rooms`가 비밀방을 포함한 전체 방을 HTTP 200으로 반환하는 것 확인.
- **결과**: 관리자 화면에서 MySQL 영구 방 정보와 Redis 실시간 인원을 함께 확인할 수 있게 됨.
- **적용 파일**: `AdminChatRoomResDto.java`, `ChatRoomRepository.java`, `ChatRoomService.java`, `dashboard.html`, `admin.js`, `ChatRoomServiceTest.java`

### 53. 관리자 WebSocket 중복 연결과 채팅 모니터 정합성 문제
- **문제**:
    - `connectStatsSocket()`과 `connectChatSocket()`이 각각 SockJS/STOMP 연결을 생성하여 관리자 페이지 한 개가 활성 session 수를 두 개 이상 증가시킬 수 있었음.
    - 공통 레이아웃이 `main.js`를 함께 로드하므로 관리자 페이지에서도 메인 WebSocket 연결이 추가 생성될 가능성이 있었음.
    - 관리자 채팅 모니터는 실제 발행되지 않는 `/topic/chat`을 구독하고 `msg.message`를 읽었지만, 전역 채팅의 실제 topic은 `/topic/bubbles`, 메시지 필드는 `content`였음.
- **해결**:
    - 관리자 WebSocket 연결을 하나로 통합하고 동일 연결에서 `/topic/user-count`, `/topic/bubbles`를 구독.
    - `main.js`는 메인 화면 DOM인 `#bamboo-forest`가 존재할 때만 초기화하도록 가드 추가.
    - 채팅 내용은 `msg.content`를 사용하고 식별 정보는 `senderGuestId → senderClientId → senderIp` 순서로 표시.
    - roomCode가 없는 전역 메시지는 `전역`으로 표시.
    - 메시지 출력은 `textContent`를 사용하여 관리자 모니터에서도 사용자 입력을 HTML로 해석하지 않도록 처리.
- **검증**:
    - `admin.js`에서 SockJS 생성이 한 번만 존재하고 두 topic이 동일 STOMP client에서 구독되는 것 확인.
    - `admin.js`, `main.js`의 JavaScript 문법 검사 통과.
    - SockJS `/ws-bubble/info` HTTP 200 확인.
- **결과**: 관리자 접속으로 인한 session 수 왜곡 가능성을 줄이고 전역 채팅 모니터의 topic·필드 불일치를 해결.
- **제한**: 방별 `/topic/rooms/{roomCode}/bubbles` 구독은 이번 단계에서 구현하지 않음.
- **적용 파일**: `admin.js`, `main.js`, `dashboard.html`

### 54. 관리자 Summary의 Redis 가용성 및 활성 Guest 표현
- **구현**:
    - Summary API에서 `activeSessions`는 `chat:active:sessions` Set 크기로 계산.
    - Redis 조회가 실패하면 `redisAvailable=false`로 표시하고 session·메뉴·투표 수는 안전한 기본값 0으로 반환.
    - 방 목록은 방별 Redis 조회가 실패해도 현재 인원을 0으로 fallback하여 관리자 화면 전체가 깨지지 않도록 처리.
- **activeGuests 정책**:
    - 현재 구조에는 모든 방과 전역 접속을 포괄하는 전역 GuestID Set이 없음.
    - 따라서 활성 Guest 수를 정확하게 계산할 수 없으므로 추정값을 만들지 않고 `activeGuests=null`로 반환.
- **검증**:
    - Redis 정상 상태의 session·메뉴·투표 집계 테스트 통과.
    - Redis 예외 발생 시 `redisAvailable=false`와 기본값 반환 테스트 통과.
- **향후 개선**: 전역 GuestID Set을 별도로 설계한 후에만 정확한 활성 Guest 수 제공.
- **적용 파일**: `AdminDashboardService.java`, `AdminDashboardSummaryResDto.java`, `AdminDashboardServiceTest.java`

### 55. 관리자 API 보호와 최종 회귀 검증
- **보안**:
    - 신규 `/api/admin/dashboard/summary`, `/api/admin/rooms`는 기존 `/api/admin/**` 규칙에 포함되어 `ROLE_ADMIN`으로 보호.
    - `/admin/**`, `/api/menu/admin/**`의 기존 보호 정책 유지.
    - 익명 요청은 `/login`으로 리다이렉트되고 관리자 로그인 후에만 HTTP 200 응답 확인.
    - 일반 사용자용 메뉴·투표 CSRF 예외와 익명 채팅방 API 정책은 변경하지 않음.
- **자동 검증**:
    - `./gradlew.bat compileJava` 성공.
    - `./gradlew.bat test` 성공.
    - 총 38개 테스트, 실패 0, 오류 0.
- **수동 검증**:
    - 관리자 Summary와 전체 방 목록 HTTP 200.
    - 공개방·비밀방 수와 방별 현재 인원 응답 확인.
    - 일반 메뉴 랭킹, 메뉴 추가 운영시간 차단 JSON, SockJS endpoint 정상 확인.
- **미구현·향후 개선**:
    - 전역 GuestID Set 기반 `activeGuests` 계산.
    - 운영 이벤트 로그 및 관리자 감사 로그.
    - 방별 topic 채팅 모니터링.
    - 실제 브라우저 다중 탭 reconnect와 stale session 정리 검증.

## 📅 2026-06-24: 어드민 2차 운영 안정화

### 56. 운영자가 비정상 채팅방을 종료할 수 없는 문제
- **문제**:
    - 생성된 방을 운영자가 강제로 종료할 API가 없어 비정상 상태 또는 운영이 끝난 방을 정리할 수 없었음.
    - DB 방 상태와 Redis 실시간 session 상태를 함께 정리할 운영 경로가 필요했음.
- **해결**:
    - `POST /api/admin/rooms/{roomCode}/close` 관리자 API 추가.
    - 방 상태를 `CLOSED`로 변경하고 최초 종료 시 `closedAt` 기록.
    - 이미 CLOSED인 방에 다시 종료 요청해도 기존 `closedAt`을 유지하며 정상 응답하는 멱등 처리.
    - 종료된 방은 기존 공개방 조회 조건에서 제외되고 기존 입장 검증에서 차단.
- **Redis 정리**:
    - `room:{roomCode}:sessions`의 sessionId를 조회.
    - 각 `room:session:rooms:{sessionId}` Set에서 종료한 roomCode만 제거.
    - `room:{roomCode}:sessions`, `room:{roomCode}:guests`, `room:{roomCode}:session-actors` 삭제.
    - 전역 활성 session과 다른 roomCode 정보는 삭제하지 않음.
    - Redis 정리 실패는 경고 로그로 남기며 DB의 CLOSED 상태는 유지.
- **검증**:
    - 상태 CLOSED 변경과 `closedAt` 기록 테스트 통과.
    - 반복 종료 시 `closedAt` 유지 테스트 통과.
    - Redis 정리 실패 시에도 CLOSED 상태를 반환하는 테스트 통과.
    - 수동 검증에서 공개 목록 제외, 재입장 차단, 반복 종료 HTTP 200 확인.
- **적용 파일**: `ChatRoom.java`, `ChatRoomService.java`, `AdminDashboardService.java`, `AdminDashBoardRestController.java`, `ChatRoomServiceTest.java`

### 57. WebSocket disconnect 누락으로 남을 수 있는 stale session 수동 정리
- **문제**:
    - 브라우저 비정상 종료나 disconnect 이벤트 누락 시 Redis의 전역·방별 session Set에 실제로는 연결되지 않은 sessionId가 남을 수 있음.
    - Redis 자체 정보만으로는 session이 현재 서버에 실제 연결되어 있는지 판단할 수 없음.
- **해결**:
    - `ActiveWebSocketSessionRegistry`를 추가하여 connect/disconnect 이벤트에서 현재 서버 메모리의 활성 sessionId를 추적.
    - `POST /api/admin/realtime/cleanup-stale-sessions` 관리자 수동 정리 API 추가.
    - `chat:active:sessions`와 `room:*:sessions`의 session 합집합을 검사하고 메모리 registry에 없는 session만 stale로 판단.
    - stale session은 전역 Set, 방별 Set, session-actor Hash, guest Set, reverse room Set에서 안전하게 제거.
    - reverse room Set에서는 해당 roomCode만 제거하여 다른 방 정보는 유지.
- **응답**:
    - `scannedSessions`
    - `removedSessions`
    - `scannedRooms`
    - `affectedRooms`
    - `message`
- **오류 정책**:
    - Redis 접근 실패 시 HTTP 처리 자체를 깨뜨리지 않고 제거 수 0과 명확한 실패 메시지를 반환.
- **검증**:
    - registry에 존재하는 활성 session은 유지하고 registry에 없는 session만 제거하는 테스트 통과.
    - session이 없는 경우 안전한 빈 결과 반환 테스트 통과.
    - Redis 장애 시 안전한 오류 메시지 반환 테스트 통과.
    - 수동 cleanup API HTTP 200과 집계 응답 확인.
- **제한**:
    - 현재 simple broker와 단일 애플리케이션 인스턴스를 기준으로 한 registry임.
    - 다중 인스턴스 환경에서는 인스턴스별 소유권 또는 공유 session registry가 필요.
- **적용 파일**: `ActiveWebSocketSessionRegistry.java`, `WebSocketEventListener.java`, `RealtimeSessionCleanupService.java`, `StaleSessionCleanupResDto.java`, `RealtimeSessionCleanupServiceTest.java`, `WebSocketEventListenerTest.java`

### 58. 관리자 운영 UI와 최종 검증
- **UI 반영**:
    - 관리자 방 목록에서 OPEN/FULL 방에만 종료 버튼 표시.
    - CLOSED 방은 종료 버튼 대신 비활성 표시.
    - 종료 전 confirm, 성공 후 Summary와 방 목록 재조회.
    - 상단에 `Stale Session 정리` 버튼 추가.
    - cleanup 결과의 검사 session 수와 영향받은 방 수 표시.
- **보안**:
    - 신규 API는 `/api/admin/**` 아래에 배치되어 기존 `ROLE_ADMIN` 보호와 CSRF 토큰 정책 유지.
    - 익명 접근 시 `/login` 리다이렉트 확인.
    - 일반 사용자 메뉴·투표 CSRF 예외와 채팅방 API 정책은 변경하지 않음.
- **자동 검증**:
    - `compileJava` 성공.
    - `test` 성공.
    - 총 44개 테스트, 실패 0, 오류 0.
    - `admin.js` 문법 검사 통과.
- **수동 검증**:
    - 방 종료, 반복 종료, 공개 목록 제외, CLOSED 방 입장 차단 확인.
    - room Redis key 정리와 reverse mapping의 다른 방 정보 보존 정책 확인.
    - stale cleanup 응답과 관리자 API 보호 확인.

---
*(이후 작업 내용에 따라 지속적으로 업데이트 예정)*
### 채팅방 상태 피드백 부족 문제 개선

- **문제**:
    - 방 생성/입장/퇴장/종료 상태가 화면에 명확히 표시되지 않아 사용자가 현재 상태를 알기 어려웠음.
- **원인**:
    - 주요 상태 변화가 WebSocket 시스템 메시지나 UI 상태 변경으로 충분히 연결되지 않았음.
- **해결**:
    - 방 생성/입장 후 화면 전환과 성공·실패 피드백을 보강함.
    - 입장/퇴장/종료 시스템 메시지를 `messageType=SYSTEM`으로 구분해 방 topic에 전달하도록 개선함.
    - 방 종료 이벤트 수신 시 사용자 화면에 CLOSED 상태를 표시하고 메시지 입력창과 전송 버튼을 비활성화함.
- **결과**:
    - 사용자가 채팅방의 현재 상태를 명확히 인지할 수 있게 되었고, 운영 중 혼란을 줄일 수 있었음.
- **적용 파일**:
    - `ChatMessage.java`, `ChatSocketController.java`, `WebSocketEventListener.java`, `AdminDashboardService.java`, `main.js`

## 사용자 행위 로그 수집 구조 도입

- **문제**:
    - 기존에는 사용자의 방 생성, 입장, 퇴장, 메시지 전송, 투표, 방 종료 흐름이 DB 로그로 남지 않아 운영자가 사용자 행위 흐름을 추적하기 어려웠음.
- **원인**:
    - 실시간 채팅 이벤트는 WebSocket으로 처리되지만, 주요 이벤트를 별도 보안/운영 로그 테이블에 저장하는 구조가 없었음.
- **해결**:
    - `SecurityEventLog` Entity와 공통 로그 저장 서비스를 추가하고, 주요 사용자 행위 및 관리자 조작 이벤트를 `EventType`/`Severity` 기반으로 저장하도록 개선함.
    - dev 프로파일에는 별도 `ddl-auto`가 없으므로 로컬 DB에서는 아래 DDL로 테이블을 생성할 수 있음.
```sql
CREATE TABLE security_event_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    room_code VARCHAR(100),
    guest_id VARCHAR(100),
    ip_address VARCHAR(100),
    user_agent VARCHAR(500),
    request_uri VARCHAR(500),
    reason VARCHAR(1000),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_security_event_created_at (created_at),
    INDEX idx_security_event_type (event_type),
    INDEX idx_security_event_room_code (room_code),
    INDEX idx_security_event_guest_id (guest_id),
    INDEX idx_security_event_ip_address (ip_address)
);
```
- **결과**:
    - 관리자 페이지에서 GuestId, IP, 이벤트 유형, 방 코드 기준으로 사용자 행위 로그를 조회할 수 있게 되었고, 향후 Rate Limit이나 차단 정책 같은 보안 기능을 확장할 수 있는 기반을 마련함.
- **적용 파일**:
    - `securitylog/**`, `ChatRoomRestController.java`, `ChatSocketController.java`, `WebSocketEventListener.java`, `MenuRestController.java`, `AdminDashboardService.java`, `RealtimeSessionCleanupService.java`, `AdminDashBoardRestController.java`, `dashboard.html`, `admin.js`

## 로그 저장 실패가 서비스 로직에 영향을 주지 않도록 처리

- **문제**:
    - 로그 저장 기능을 주요 서비스 흐름에 직접 연결하면, 로그 저장 실패가 채팅/투표 기능 장애로 이어질 수 있음.
- **원인**:
    - 로그 저장 로직이 핵심 서비스 로직과 강하게 결합될 경우 예외 전파 위험이 있음.
- **해결**:
    - `SecurityEventLogService` 내부에서 로그 저장 예외를 처리하고, 실패 시 warn 로그만 남기며 기존 서비스 로직은 계속 진행되도록 구성함.
- **결과**:
    - 보안 이벤트 로그 수집 기능을 추가하면서도 기존 채팅/투표/방 종료 기능의 안정성을 유지함.

## 방 생성 후 입장 실패 문제

- **문제**:
    - 채팅방 생성 후 `방 만들고 입장하기`를 누르면 방 화면으로 정상 전환되지 않거나 API가 500으로 실패하는 문제가 발생함.
- **원인**:
    - `SecurityEventLogService` 클래스 전체에 `@Transactional(readOnly = true)`가 적용되어 있었음.
    - `HttpServletRequest`를 받는 `logEvent()` 오버로드가 read-only 트랜잭션으로 실행된 상태에서 `security_event_log` insert를 시도함.
    - MySQL에서 `Connection is read-only. Queries leading to data modification are not allowed` 예외가 발생했고, 로그 저장 실패가 방 생성/입장 흐름까지 rollback시키는 문제가 있었음.
- **해결**:
    - 클래스 레벨 read-only 트랜잭션을 제거함.
    - 로그 저장 메서드에는 쓰기 트랜잭션을 적용하고, 로그 조회 메서드에만 `@Transactional(readOnly = true)`를 적용함.
    - 로그 저장 실패가 핵심 기능을 막지 않도록 `noRollbackFor = RuntimeException.class`와 내부 예외 처리를 유지함.
- **결과**:
    - 방 생성 후 입장 API가 로그 저장 트랜잭션 문제로 실패하지 않도록 수정함.
    - 보안 이벤트 로그는 계속 저장하되, 로그 저장 실패가 사용자 기능 장애로 번지지 않게 분리함.
- **적용 파일**:
    - `SecurityEventLogService.java`

## 방 생성 후 방 코드 알림 중복 표시

- **문제**:
    - `방 만들고 입장하기` 버튼을 누르면 방 코드가 포함된 안내 메시지가 두 번 이상 표시되어 사용자가 중복 생성으로 오해할 수 있었음.
- **원인**:
    - 방 생성 성공 시 `createRoom()`에서 생성 알림을 띄우고, 이어서 `joinRoom()`에서 입장 알림을 다시 띄움.
    - `enterRoom()`에서도 방금 생성한 방인지 확인한 뒤 추가 안내 메시지를 출력하고 있었음.
- **해결**:
    - 방 생성 직후 자동 입장 흐름에서는 생성 알림과 입장 알림을 하나로 합침.
    - `enterRoom()`의 추가 생성 완료 알림은 제거하고 `justCreatedRoomCode` 상태만 정리하도록 변경함.
- **결과**:
    - 방 생성과 입장이 하나의 사용자 액션으로 보이도록 정리되어, 방 코드 안내가 한 번만 표시됨.
- **적용 파일**:
    - `main.js`

## 공개 채팅방 목록 과다 노출 문제

- **문제**:
    - 공개 채팅방이 많이 생성되면 메인 화면의 공개방 목록에 모든 방이 한 번에 표시되어 화면이 길어지고 탐색성이 떨어졌음.
- **원인**:
    - `/api/rooms` API가 공개방 전체 목록을 `List`로 반환하고, 프론트에서도 전체 결과를 그대로 렌더링하고 있었음.
- **해결**:
    - 공개방 목록 API를 `page`, `size` 기반 페이지 응답으로 변경함.
    - 기본 size는 10개로 제한하고, 최신 생성순(`createdDate desc`)으로 조회함.
    - 프론트에 현재 페이지, 전체 페이지, 이전/다음 버튼을 추가함.
    - 방 생성 후에는 새 방이 보이도록 1페이지로 돌아가 목록을 갱신함.
- **결과**:
    - 공개 채팅방 목록이 10개씩 나뉘어 표시되어 화면 밀도를 유지하면서 최근 생성 방 중심으로 탐색할 수 있게 됨.
- **적용 파일**:
    - `ChatRoomRepository.java`, `ChatRoomService.java`, `ChatRoomRestController.java`, `main.html`, `main.css`, `main.js`, `ChatRoomServiceTest.java`

## API 에러 응답 표준화

- **문제**:
    - 일부 API는 `BusinessException`을 HTTP 200으로 내려주고, 일부 컨트롤러는 직접 `4002` 또는 `5000` 응답을 만들어 반환하고 있었음.
    - 같은 비즈니스 오류라도 API마다 HTTP status와 error code가 달라 프론트엔드 예외 처리와 API 계약이 불명확했음.
- **원인**:
    - `GlobalExceptionHandler`와 컨트롤러별 `try-catch`가 혼재되어 있었음.
    - 공통 응답 DTO는 있었지만 실패 응답의 HTTP status 정책이 통일되어 있지 않았음.
- **해결**:
    - `BusinessException` 기본 코드를 `4000`으로 정리함.
    - `GlobalExceptionHandler`에서 `BusinessException`은 HTTP 400으로 통일함.
    - API 경로의 예상치 못한 예외는 HTTP 500과 `code=5000`으로 통일함.
    - 메뉴/채팅방 컨트롤러의 개별 `try-catch`를 제거하고 전역 예외 핸들러를 타도록 정리함.
    - 투표 운영 시간이 아닐 때 차단하는 인터셉터 응답도 `BaseResDto` 형태로 맞춤.
    - 프론트 공통 AJAX에서 실패 응답의 `code`, `status`, `result`를 Error 객체에 보존하도록 개선함.
- **결과**:
    - REST API 실패 응답이 `BaseResDto(code, message, result)` 형태로 통일됨.
    - 프론트엔드는 HTTP status와 message를 일관되게 처리할 수 있게 됨.
    - 컨트롤러는 정상 흐름만 담당하고, 예외 응답 정책은 전역 핸들러가 담당하도록 책임이 분리됨.
- **적용 파일**:
    - `BusinessException.java`, `GlobalExceptionHandler.java`, `BaseResDto.java`, `LunchEventInterceptor.java`, `MenuRestController.java`, `ChatRoomRestController.java`, `common-ajax.js`, `GlobalExceptionHandlerTest.java`
