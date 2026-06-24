/**
 * [Main JS] 실시간 점심 전쟁 메인 로직 (Visual Enhanced)
 * WebSocket과 REST API를 결합하여 실시간 데이터 환경을 구축합니다.
 */
const MAINJS = {
    battleItems: [],
    state: "VOTING",
    stompClient: null,
    endTime: "14:00:00",
    prevTopId: null,
    clientId: null,
    currentRoomCode: null,
    roomSubscription: null,
    roomCountSubscription: null,

    /**
     * 페이지 로드 시 초기화
     */
    init: function() {
        console.log("MAINJS 초기화 시작...");
        this.clientId = sessionStorage.getItem('bubbleTalkClientId');
        if (!this.clientId) {
            this.clientId = window.crypto?.randomUUID ? window.crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
            sessionStorage.setItem('bubbleTalkClientId', this.clientId);
        }
        this.connectWebSocket();
        this.fetchInitialData();
        this.fetchExtraData(); // 타이머, 어제 우승자 등 추가 데이터
        this.fetchRooms();
        this.bindEvents();
        this.startTimers();
        this.initResizer();
        // this.initRecommendation(); // [보류] 추천 시스템 초기화
    },

    /**
     * 어제 우승자 및 종료 시간 데이터 로드
     */
    fetchExtraData: async function() {
        try {
            const response = await COMMON_AJAX.get('/api/menu/init-data');
            if (response.code === "0000") {
                const data = response.result;
                this.endTime = data.endTime + ":00";
                
                // 어제 우승자 렌더링
                const winnerEl = document.getElementById('yesterday-winner');
                if (winnerEl) {
                    winnerEl.innerText = `${data.yesterdayWinner} (${data.yesterdayVotes}표)`;
                }
            }
        } catch (e) {
            console.error("추가 데이터 로딩 실패", e);
        }
    },

    /**
     * 상황별 메뉴 추천 시스템 (Weather/Time Mock)
     */
    initRecommendation: function() {
        const recommendations = {
            clear: ["돈가스", "제육볶음", "냉면", "비빔밥"],
            rain: ["짬뽕", "부침개", "칼국수", "수제비"],
            cloud: ["순대국", "뼈해장국", "쌀국수", "부대찌개"],
            hot: ["삼계탕", "모밀", "물회", "치킨"],
            cold: ["우동", "라면", "전골", "곰탕"]
        };

        const weatherMock = ["clear", "rain", "cloud", "hot", "cold"];
        const randomWeather = weatherMock[Math.floor(Math.random() * weatherMock.length)];
        const iconMap = {
            clear: "fa-sun",
            rain: "fa-cloud-showers-heavy",
            cloud: "fa-cloud",
            hot: "fa-fire-orange",
            cold: "fa-snowflake"
        };

        const weatherIconEl = document.getElementById('weather-icon');
        const recommendEl = document.getElementById('menu-recommendation');
        
        if (weatherIconEl) {
            weatherIconEl.innerHTML = `<i class="fa-solid ${iconMap[randomWeather]} text-xl"></i>`;
        }

        if (recommendEl) {
            const list = recommendations[randomWeather];
            const menu = list[Math.floor(Math.random() * list.length)];
            const weatherName = {clear:"맑은 날", rain:"비 오는 날", cloud:"흐린 날", hot:"무더운 날", cold:"추운 날"}[randomWeather];
            recommendEl.innerText = `${weatherName}엔 [${menu}] 어떠세요?`;
        }
    },

    /**
     * 리사이저 초기화 (채팅 영역 높이 조절)
     */
    initResizer: function() {
        const resizer = document.getElementById('h-resizer');
        const bamboo = document.getElementById('bamboo-forest');
        if (!resizer || !bamboo) return;

        let isResizing = false;

        resizer.addEventListener('mousedown', (e) => {
            isResizing = true;
            document.body.classList.add('resizing');
        });

        document.addEventListener('mousemove', (e) => {
            if (!isResizing) return;

            // 최소 100px, 최대 화면의 60%로 제한
            let newHeight = e.clientY;
            if (newHeight < 100) newHeight = 100;
            if (newHeight > window.innerHeight * 0.6) newHeight = window.innerHeight * 0.6;

            bamboo.style.height = `${newHeight}px`;
        });

        document.addEventListener('mouseup', () => {
            isResizing = false;
            document.body.classList.remove('resizing');
        });
    },

    /**
     * WebSocket 연결 및 구독
     */
    connectWebSocket: function() {
        const socket = new SockJS('/ws-bubble');
        this.stompClient = Stomp.over(socket);
        this.stompClient.debug = null; // 로그 간소화

        this.stompClient.connect({}, (frame) => {
            console.log('Connected: ' + frame);

            // [구독] 실시간 메뉴 랭킹 업데이트
            this.stompClient.subscribe('/topic/menus', (response) => {
                const updatedMenus = JSON.parse(response.body);
                this.battleItems = updatedMenus.menuList;
                
                // 이벤트가 종료(데이터가 비어있거나 특정 조건)되면 자동으로 상태 확인 API 호출
                if (!this.battleItems || this.battleItems.length === 0) {
                    this.checkEventStatus();
                }

                if (this.state === 'VOTING') this.renderVoting();
                if (this.state === 'FINISHED') this.renderResults();
            });

            // [구독] 실시간 채팅 메시지 수신
            this.stompClient.subscribe('/topic/bubbles', (response) => {
                const chatMsg = JSON.parse(response.body);
                
                // 특정 시스템 메시지에 따라 강제 상태 전환 (선택 사항)
                if (chatMsg.content.includes("전쟁 시작")) this.changeState('VOTING');
                if (chatMsg.content.includes("전쟁이 종료")) this.changeState('FINISHED');

                // 발신자 IP에 따른 버블 생성 (SYSTEM 여부 확인)
                const isSystem = chatMsg.senderIp === 'SYSTEM';
                this.createBullet(chatMsg.content, chatMsg.senderIp, isSystem, chatMsg.senderClientId);
            });

            // [구독] 실시간 접속자 수 업데이트
            this.stompClient.subscribe('/topic/user-count', (response) => {
                const count = JSON.parse(response.body);
                const userCountEl = document.getElementById('user-count');
                if (userCountEl) {
                    userCountEl.innerText = count.toLocaleString();
                }
            });

            // [구독] 나에게만 오는 에러/알림 메시지 (도배 방지 등)
            this.stompClient.subscribe('/user/queue/errors', (response) => {
                const chatMsg = JSON.parse(response.body);
                this.createBullet(chatMsg.content, chatMsg.senderIp, true);
            });

            if (this.currentRoomCode) {
                this.joinRoom(this.currentRoomCode);
            }

        }, (error) => {
            console.error('WebSocket 접속 에러:', error);
            setTimeout(() => this.connectWebSocket(), 5000);
        });
    },

    /**
     * 초기 데이터 로드 및 상태 체크
     */
    fetchInitialData: async function() {
        try {
            // 1. 이벤트 상태 먼저 확인
            await this.checkEventStatus();
            
            // 2. 랭킹 데이터 로드
            const response = await COMMON_AJAX.get('/api/menu/rankings');
            if (response.code === "0000") {
                this.battleItems = response.result.menuList;
                this.changeState(this.state);
            }
        } catch (error) {
            console.error("초기 데이터 로딩 실패:", error);
        }
    },

    /**
     * 서버에 현재 이벤트 상태 조회
     */
    checkEventStatus: async function() {
        try {
            const response = await COMMON_AJAX.get('/api/menu/status');
            if (response.code === "0000") {
                const status = response.result.status; // OPEN or CLOSED
                this.state = (status === 'OPEN') ? 'VOTING' : 'LOCKED';
            }
        } catch (e) {
            this.state = 'LOCKED';
        }
    },

    fetchRooms: async function() {
        try {
            const response = await COMMON_AJAX.get('/api/rooms');
            if (response.code === "0000") {
                this.renderRooms(response.result || []);
            }
        } catch (e) {
            console.error('채팅방 목록 조회 실패:', e);
        }
    },

    createRoom: async function() {
        const input = document.getElementById('room-name-input');
        const name = input?.value?.trim();
        const privateRoom = Boolean(document.getElementById('room-private-input')?.checked);
        const maxParticipants = Number(document.getElementById('room-max-input')?.value || 10);
        if (!name) return;

        try {
            const response = await COMMON_AJAX.post('/api/rooms', {
                name,
                isPrivate: privateRoom,
                maxParticipants
            }, { 'X-Client-Id': this.clientId });

            if (response.code === "0000") {
                input.value = '';
                await this.fetchRooms();
                await this.joinRoom(response.result.roomCode);
            }
        } catch (error) {
            this.createBullet(`❌ ${error.message}`, CLIENT_IP, false, this.clientId);
        }
    },

    joinRoomByCode: async function() {
        const input = document.getElementById('room-code-input');
        const roomCode = input?.value?.trim();
        if (!roomCode) return;
        await this.joinRoom(roomCode);
    },

    joinRoom: async function(roomCode) {
        try {
            const response = await COMMON_AJAX.post(`/api/rooms/${roomCode}/join`, {}, { 'X-Client-Id': this.clientId });
            if (response.code === "0000") {
                this.enterRoom(response.result);
            }
        } catch (error) {
            this.createBullet(`❌ ${error.message}`, CLIENT_IP, false, this.clientId);
        }
    },

    enterRoom: function(room) {
        const previousRoomCode = this.currentRoomCode;
        if (previousRoomCode && previousRoomCode !== room.roomCode && this.stompClient?.connected) {
            this.stompClient.send(`/app/rooms/${previousRoomCode}/leave`, { clientId: this.clientId }, '');
        }
        this.currentRoomCode = room.roomCode;

        if (this.roomSubscription) this.roomSubscription.unsubscribe();
        if (this.roomCountSubscription) this.roomCountSubscription.unsubscribe();

        if (this.stompClient?.connected) {
            this.roomSubscription = this.stompClient.subscribe(`/topic/rooms/${room.roomCode}/bubbles`, (response) => {
                const chatMsg = JSON.parse(response.body);
                this.createBullet(chatMsg.content, chatMsg.senderIp, chatMsg.senderIp === 'SYSTEM', chatMsg.senderClientId);
            });
            this.roomCountSubscription = this.stompClient.subscribe(`/topic/rooms/${room.roomCode}/user-count`, (response) => {
                const label = document.getElementById('current-room-label');
                if (label) label.textContent = `${room.name} (${response.body}/${room.maxParticipants})`;
            });
            this.stompClient.send(`/app/rooms/${room.roomCode}/join`, { clientId: this.clientId }, '');
        }

        const label = document.getElementById('current-room-label');
        if (label) label.textContent = `${room.name} (${room.currentParticipants}/${room.maxParticipants})`;
    },

    renderRooms: function(rooms) {
        const list = document.getElementById('room-list');
        if (!list) return;
        list.innerHTML = '';

        rooms.forEach(room => {
            const row = document.createElement('button');
            row.type = 'button';
            row.className = 'show-more-btn';
            row.textContent = `${room.name} (${room.currentParticipants}/${room.maxParticipants})`;
            row.addEventListener('click', () => this.joinRoom(room.roomCode));
            list.appendChild(row);
        });
    },

    /**
     * 메뉴 추가 (REST)
     */
    addNewMenu: async function() {
        const input = document.getElementById('add-menu-input');
        const name = input.value.trim();
        if (!name) return;

        try {
            const response = await COMMON_AJAX.post('/api/menu/add', { menuName: name }, { 'X-Client-Id': this.clientId });
            if (response.code === "0000") {
                this.createBullet(`[${name}] 후보에 추가됐어요`, 'SYSTEM', true);
                input.value = '';
            }
        } catch (error) {
            console.error("메뉴 추가/투표 에러:", error);
            // 에러 메시지(중복 투표 등)를 버블로 표시
            this.createBullet(`❌ ${error.message}`, CLIENT_IP, false, this.clientId);
            input.value = '';
        }
    },

    /**
     * 투표하기 (REST)
     */
    vote: async function(menuId, menuName) {
        if (!menuId) return;
        try {
            const response = await COMMON_AJAX.post('/api/menu/vote', { menuId: Number(menuId) }, { 'X-Client-Id': this.clientId });
            if (response.code === "0000") {
                this.createBullet(`${menuName}에 한 표 더했어요`, 'SYSTEM', true);
            }
        } catch (error) {
            console.error("투표 에러:", error);
            // 에러 시 본인에게만 빨간 버블로 표시
            this.createBullet(`❌ ${error.message}`, CLIENT_IP, false, this.clientId);
        }
    },

    /**
     * 상태 변경 및 화면 전환
     */
    changeState: function(newState) {
        this.state = newState;
        const locked = document.getElementById('state-locked');
        const voting = document.getElementById('state-voting');
        const finished = document.getElementById('state-finished');

        if (locked) locked.classList.toggle('hidden', this.state !== 'LOCKED');
        if (voting) voting.classList.toggle('hidden', this.state !== 'VOTING');
        if (finished) finished.classList.toggle('hidden', this.state !== 'FINISHED');

        if (this.state === 'VOTING') this.renderVoting();
        if (this.state === 'FINISHED') this.renderResults();
    },

    /**
     * 투표 리스트 렌더링 (Visual Enhanced)
     */
    renderVoting: function() {
        const grid = document.getElementById('voting-grid');
        if (!grid) return;
        grid.innerHTML = '';

        const totalVotes = this.battleItems.reduce((acc, cur) => acc + (cur.finalScore || 0), 0) || 1;
        const sorted = [...this.battleItems].sort((a, b) => b.finalScore - a.finalScore);

        // [3] 순위 역전 감지 (1위 변경 시)
        if (sorted.length > 0 && sorted[0].finalScore > 0) {
            const currentTop = sorted[0];
            if (this.prevTopId && this.prevTopId !== currentTop.id) {
                this.createBullet(`[${currentTop.menuName}] 1위로 올라왔어요`, 'SYSTEM', true);
            }
            this.prevTopId = currentTop.id;
        }

        sorted.forEach((item, idx) => {
            const pct = Math.round(((item.finalScore || 0) / totalVotes) * 100);
            const card = document.createElement('div');
            const isFirst = idx === 0 && item.finalScore > 0;
            
            card.className = `battle-card ${isFirst ? 'rank-1' : ''}`;
            
            card.onclick = () => {
                // 클릭 피드백: 살짝 흔들림
                card.style.transform = 'translateX(4px)';
                setTimeout(() => card.style.transform = 'translateX(0)', 80);
                this.vote(item.id, item.menuName);
            };

            card.innerHTML = `
                <span class="menu-rank">#${String(idx + 1).padStart(2, '0')}</span>
                <h4 class="menu-title">${this.escapeHtml(item.menuName)}</h4>
                <div class="menu-score">
                    <span>${item.finalScore || 0}표</span>
                    <strong>${pct}%</strong>
                </div>
                <div class="progress-container">
                    <div class="progress-bar" style="width: ${pct}%"></div>
                </div>
            `;
            grid.appendChild(card);
        });
    },

    /**
     * 결과 발표 화면 렌더링
     */
    renderResults: function() {
        const sorted = [...this.battleItems].sort((a, b) => b.finalScore - a.finalScore);
        const rank1 = document.getElementById('rank-1-name');
        const rank2 = document.getElementById('rank-2-name');
        const rank3 = document.getElementById('rank-3-name');

        if (rank1) rank1.innerText = sorted[0]?.menuName || '-';
        if (rank2) rank2.innerText = sorted[1]?.menuName || '-';
        if (rank3) rank3.innerText = sorted[2]?.menuName || '-';

        const list = document.getElementById('ranking-list');
        if (!list) return;
        list.innerHTML = '';
        sorted.forEach((item, idx) => {
            const row = document.createElement('div');
            row.className = "ranking-row";
            row.innerHTML = `
                <span>#${idx + 1} ${this.escapeHtml(item.menuName)}</span>
                <strong>${item.finalScore || 0}표</strong>
            `;
            list.appendChild(row);
        });
    },

    /**
     * 이벤트 바인딩
     */
    bindEvents: function() {
        // [1] 채팅 폼
        const msgForm = document.getElementById('msg-form');
        if (msgForm) {
            msgForm.addEventListener('submit', (e) => {
                e.preventDefault();
                const input = document.getElementById('msg-input');
                const content = input.value.trim();
                if (content && this.stompClient?.connected) {
                    const destination = this.currentRoomCode
                        ? `/app/rooms/${this.currentRoomCode}/chat/send`
                        : "/app/chat/send";
                    this.stompClient.send(destination, { clientId: this.clientId }, content);
                    input.value = '';
                }
            });
        }

        // [2] 메뉴 추가 폼 (엔터 키 지원)
        const addMenuForm = document.getElementById('add-menu-form');
        if (addMenuForm) {
            addMenuForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.addNewMenu();
            });
        }

        const roomCreateForm = document.getElementById('room-create-form');
        if (roomCreateForm) {
            roomCreateForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.createRoom();
            });
        }

        const roomCodeForm = document.getElementById('room-code-form');
        if (roomCodeForm) {
            roomCodeForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.joinRoomByCode();
            });
        }
    },

    /**
     * 타이머 시작 (라이브 시계 + 타임어택 카운트다운)
     */
    startTimers: function() {
        setInterval(() => {
            const now = new Date();
            
            // 1. 라이브 시계 (푸터 등)
            const timerEl = document.getElementById('live-timer');
            if (timerEl) timerEl.innerText = now.toLocaleTimeString('ko-KR', { hour12: false });

            // 2. 전쟁 종료 카운트다운
            const warTimerEl = document.getElementById('war-timer');
            if (warTimerEl) {
                const [h, m, s] = this.endTime.split(':');
                const target = new Date();
                target.setHours(h, m, s, 0);

                let diff = target - now;
                if (diff < 0) {
                    warTimerEl.innerText = "00:00:00";
                    warTimerEl.classList.add('text-red-500', 'animate-pulse');
                } else {
                    const hours = String(Math.floor(diff / (1000 * 60 * 60))).padStart(2, '0');
                    const mins = String(Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))).padStart(2, '0');
                    const secs = String(Math.floor((diff % (1000 * 60)) / 1000)).padStart(2, '0');
                    warTimerEl.innerText = `${hours}:${mins}:${secs}`;
                    
                    // 1시간 미만일 때 색상 변경으로 긴박감 조성
                    if (diff < 3600000) warTimerEl.classList.add('text-orange-500');
                    else warTimerEl.classList.remove('text-orange-500', 'text-red-500');
                }
            }
        }, 1000);
    },

    showFullRanking: function() {
        document.getElementById('full-ranking')?.classList.remove('hidden');
        document.getElementById('btn-show-more')?.classList.add('hidden');
    },

    escapeHtml: function(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#39;');
    },

    /**
     * [개선] 다채로운 색상의 팝업 버블 생성
     * 메시지가 화면 곳곳에서 랜덤하게 튀어나와 '왁자지껄'한 분위기를 연출합니다.
     */
    createBullet: function(text, senderIp, isSpecial = false, senderClientId = null) {
        const container = document.getElementById('bullet-container');
        if (!container) return;
        
        const el = document.createElement('div');
        el.className = 'bullet-msg';
        
        if (isSpecial) {
            // [SYSTEM] 공지 스타일
            el.style.border = '2px solid #ff6b57';
            el.style.color = '#ffffff';
            el.style.background = 'rgba(255, 107, 87, 0.86)';
            el.style.boxShadow = '0 12px 28px rgba(255, 107, 87, 0.28)';
            el.style.zIndex = '100';
        } else if (senderClientId && senderClientId === this.clientId) {
            // [MY] 내 메시지 강조
            el.style.border = '2px solid #2ec4b6';
            el.style.color = '#0f3f3a';
            el.style.background = 'rgba(223, 247, 243, 0.95)';
            el.style.boxShadow = '0 12px 28px rgba(46, 196, 182, 0.24)';
            el.style.fontWeight = 'bold';
            el.style.zIndex = '90';
        } else {
            // [OTHERS] 타인 메시지 (IP 기반 고정 색상)
            const colors = [
                'rgba(255, 209, 102, 0.78)',
                'rgba(255, 246, 238, 0.95)',
                'rgba(223, 247, 243, 0.92)',
                'rgba(255, 221, 211, 0.9)',
                'rgba(232, 230, 255, 0.9)',
                'rgba(255, 255, 255, 0.92)'
            ];
            
            // IP를 이용한 간단한 해시 함수로 색상 결정
            let hash = 0;
            if (senderIp) {
                for (let i = 0; i < senderIp.length; i++) {
                    hash = senderIp.charCodeAt(i) + ((hash << 5) - hash);
                }
            }
            const colorIndex = Math.abs(hash) % colors.length;
            const selectedColor = colors[colorIndex];
            
            el.style.background = selectedColor;
            el.style.borderColor = 'rgba(255, 255, 255, 0.75)';
            el.style.color = '#1f2937';
        }
        
        el.innerText = text;
        // 입력창(하단)에 가려지지 않도록 범위를 상단 10% ~ 55% 정도로 제한
        const left = Math.random() * 80 + 5;
        const top = Math.random() * 45 + 10;
        const duration = isSpecial ? 4.5 : 3 + Math.random() * 1.5;
        
        el.style.left = `${left}%`;
        el.style.top = `${top}%`;
        el.style.zIndex = isSpecial ? '100' : (senderClientId && senderClientId === this.clientId ? '95' : '50'); // 기본 버블도 입력폼(20)보다 높게 설정
        el.style.animation = `popAndStay ${duration}s ease-in-out forwards`;
        
        container.appendChild(el);
        el.addEventListener('animationend', () => el.remove());
    }
};

document.addEventListener('DOMContentLoaded', () => {
    if (document.getElementById('bamboo-forest')) {
        MAINJS.init();
    }
});
