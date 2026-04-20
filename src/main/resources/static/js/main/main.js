/**
 * [Main JS] 실시간 점심 전쟁 메인 로직
 * WebSocket과 REST API를 결합하여 실시간 데이터 환경을 구축합니다.
 */
const MAINJS = {
    // 서버에서 받은 실시간 메뉴 리스트를 보관하는 변수
    battleItems: [],
    
    // 현재 화면 상태 (투표중, 결과발표 등)
    state: "VOTING",

    // WebSocket 연결 객체
    stompClient: null,

    /**
     * 페이지 로드 시 가장 먼저 실행되는 함수
     */
    init: function() {
        console.log("MAINJS 초기화 시작...");
        this.connectWebSocket(); // 1. 실시간 통신 연결
        this.fetchInitialData(); // 2. 초기 데이터 한 번 가져오기
        this.bindEvents();       // 3. 버튼 등 이벤트 연결
        this.startTimers();      // 4. 타이머 시작
    },

    /**
     * [개념] WebSocket 연결 및 구독
     * 서버(Spring)의 WebSocket 엔드포인트에 접속하고, 
     * 특정 주제(/topic/menus)를 '구독'하여 데이터가 올 때까지 기다립니다.
     */
    connectWebSocket: function() {
        const socket = new SockJS('/ws-bubble'); // 서버의 WebSocket 설정 경로
        this.stompClient = Stomp.over(socket);

        // 콘솔 로그가 너무 많아지는 것을 방지 (개발 시 필요하면 주석 해제)
        // this.stompClient.debug = null;

        this.stompClient.connect({}, (frame) => {
            console.log('Connected: ' + frame);

            /**
             * [중요] 구독(Subscribe)
             * 서버가 /topic/menus 경로로 데이터를 쏘면(Broadcast),
             * 이 콜백 함수가 즉시 실행됩니다.
             */
            this.stompClient.subscribe('/topic/menus', (response) => {
                // 서버가 보낸 JSON 데이터를 자바스크립트 객체로 변환
                const updatedMenus = JSON.parse(response.body);
                console.log("실시간 업데이트 수신:", updatedMenus);
                
                // 전역 변수에 저장하고 화면을 다시 그립니다.
                this.battleItems = updatedMenus;
                this.renderVoting();
                this.renderResults();
            });
        }, (error) => {
            console.error('WebSocket 접속 에러:', error);
            // 5초 후 재연결 시도
            setTimeout(() => this.connectWebSocket(), 5000);
        });
    },

    /**
     * [개념] 초기 데이터 로드 (REST API)
     * 소켓이 연결되기 전이나, 처음 접속했을 때 현재 상태를 한 번 가져옵니다.
     */
    fetchInitialData: async function() {
        try {
            const response = await COMMON_AJAX.get('/api/menu/rankings');
            if (response.code === "0000") {
                this.battleItems = response.result;
                this.changeState(this.state); // 현재 상태에 맞춰 렌더링
            }
        } catch (error) {
            console.error("초기 데이터 로딩 실패:", error);
        }
    },

    /**
     * [개념] 메뉴 추가 (REST API 호출)
     */
    addNewMenu: async function() {
        const input = document.getElementById('add-menu-input');
        const name = input.value.trim();
        if (!name) return;

        try {
            // FormData 형태로 보낼 수도 있고, 쿼리 파라미터로 보낼 수도 있습니다.
            // 여기서는 서버의 @RequestParam을 위해 URL 파라미터 방식으로 보냅니다.
            const response = await COMMON_AJAX.post(`/api/menu/add?menuName=${encodeURIComponent(name)}`);
            
            if (response.code === "0000") {
                input.value = '';
                this.createBullet(`🚀 [${name}] 전장 투입 성공!`, true);
                // 화면 갱신은 소켓에서 오는 데이터를 기다립니다 (Broadcasting).
            } else {
                alert(response.message);
            }
        } catch (error) {
            console.error("메뉴 추가 에러:", error);
        }
    },

    /**
     * [개념] 투표하기 (REST API 호출)
     */
    vote: async function(menuId, menuName) {
        try {
            const response = await COMMON_AJAX.post(`/api/menu/vote?menuId=${menuId}`);
            if (response.code === "0000") {
                this.createBullet(`${menuName} +1 화력 지원!`, true);
                // 화면 갱신은 소켓을 통해 자동으로 이루어집니다.
            }
        } catch (error) {
            console.error("투표 에러:", error);
        }
    },

    /**
     * 화면의 탭/상태를 바꿉니다.
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
     * 투표 리스트 화면 그리기
     */
    renderVoting: function() {
        const grid = document.getElementById('voting-grid');
        if (!grid) return;
        grid.innerHTML = '';

        const totalVotes = this.battleItems.reduce((acc, cur) => acc + (cur.finalScore || 0), 0) || 1;
        
        // 점수 높은 순으로 정렬
        const sorted = [...this.battleItems].sort((a, b) => b.finalScore - a.finalScore);

        sorted.forEach((item, idx) => {
            const pct = Math.round(((item.finalScore || 0) / totalVotes) * 100);
            const card = document.createElement('div');
            card.className = "bg-slate-900/80 border border-slate-800 rounded-2xl p-4 flex items-center justify-between group active:scale-[0.98] transition-all cursor-pointer hover:border-green-500/50";
            
            // 클릭 시 투표 API 호출
            card.onclick = () => this.vote(item.id, item.menuName);

            card.innerHTML = `
                <div class="flex-1">
                    <div class="flex items-center gap-2 mb-2">
                        <span class="text-[9px] font-bold ${idx < 3 ? 'text-yellow-500' : 'text-slate-600'}">#0${idx+1}</span>
                        <h4 class="font-bold text-sm text-slate-200">${item.menuName}</h4>
                    </div>
                    <div class="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden">
                        <div class="progress-bar h-full bg-green-500" style="width: ${pct}%"></div>
                    </div>
                </div>
                <div class="ml-4 text-right">
                    <div class="text-lg font-black text-white leading-none">${pct}%</div>
                    <div class="text-[9px] text-slate-500 font-mono">${item.finalScore}V</div>
                </div>
            `;
            grid.appendChild(card);
        });
    },

    /**
     * 결과 화면 (시상대 등) 그리기
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
            row.className = "flex justify-between items-center p-3 bg-slate-900 rounded-xl border border-slate-800/50";
            row.innerHTML = `
                <div class="flex items-center gap-3">
                    <span class="text-xs font-mono text-slate-500">#${idx+1}</span>
                    <span class="text-sm font-bold text-slate-300">${item.menuName}</span>
                </div>
                <span class="text-xs font-mono text-green-500">${item.finalScore}표</span>
            `;
            list.appendChild(row);
        });
    },

    bindEvents: function() {
        const msgForm = document.getElementById('msg-form');
        if (msgForm) {
            msgForm.addEventListener('submit', (e) => {
                e.preventDefault();
                const input = document.getElementById('msg-input');
                if (input.value.trim()) {
                    this.createBullet(input.value, true);
                    input.value = '';
                }
            });
        }
    },

    startTimers: function() {
        setInterval(() => {
            const now = new Date();
            const timerEl = document.getElementById('live-timer');
            if (timerEl) {
                timerEl.innerText = now.toLocaleTimeString('ko-KR', { hour12: false });
            }
        }, 1000);
    },

    showFullRanking: function() {
        const fullRanking = document.getElementById('full-ranking');
        const btnShowMore = document.getElementById('btn-show-more');
        if (fullRanking) fullRanking.classList.remove('hidden');
        if (btnShowMore) btnShowMore.classList.add('hidden');
    },

    createBullet: function(text, isSpecial = false) {
        const container = document.getElementById('bullet-container');
        if (!container) return;
        const el = document.createElement('div');
        el.className = 'bullet-msg';
        if (isSpecial) {
            el.style.border = '1px solid #22c55e';
            el.style.color = '#22c55e';
            el.style.background = 'rgba(34, 197, 94, 0.15)';
            el.style.zIndex = '20';
        }
        el.innerText = text;
        const left = Math.random() * 70 + 5;
        const top = Math.random() * 60 + 10;
        const duration = isSpecial ? 5 : 3 + Math.random() * 2;
        el.style.left = `${left}%`;
        el.style.top = `${top}%`;
        el.style.animation = `popAndStay ${duration}s ease-in-out forwards`;
        container.appendChild(el);
        el.addEventListener('animationend', () => el.remove());
    }
};

document.addEventListener('DOMContentLoaded', () => {
    MAINJS.init();
});
