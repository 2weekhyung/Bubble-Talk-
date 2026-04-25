/**
 * [Main JS] 실시간 점심 전쟁 메인 로직 (Visual Enhanced)
 * WebSocket과 REST API를 결합하여 실시간 데이터 환경을 구축합니다.
 */
const MAINJS = {
    battleItems: [],
    state: "VOTING",
    stompClient: null,

    /**
     * 페이지 로드 시 초기화
     */
    init: function() {
        console.log("MAINJS 초기화 시작...");
        this.connectWebSocket();
        this.fetchInitialData();
        this.bindEvents();
        this.startTimers();
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
                this.battleItems = updatedMenus;
                if (this.state === 'VOTING') this.renderVoting();
                if (this.state === 'FINISHED') this.renderResults();
            });

            // [구독] 실시간 채팅 메시지 수신
            this.stompClient.subscribe('/topic/bubbles', (response) => {
                const chatMsg = JSON.parse(response.body);
                this.createBullet(chatMsg.content);
            });

        }, (error) => {
            console.error('WebSocket 접속 에러:', error);
            setTimeout(() => this.connectWebSocket(), 5000);
        });
    },

    /**
     * 초기 데이터 로드
     */
    fetchInitialData: async function() {
        try {
            const response = await COMMON_AJAX.get('/api/menu/rankings');
            if (response.code === "0000") {
                this.battleItems = response.result;
                this.changeState(this.state);
            }
        } catch (error) {
            console.error("초기 데이터 로딩 실패:", error);
        }
    },

    /**
     * 메뉴 추가 (REST)
     */
    addNewMenu: async function() {
        const input = document.getElementById('add-menu-input');
        const name = input.value.trim();
        if (!name) return;

        try {
            const response = await COMMON_AJAX.post(`/api/menu/add?menuName=${encodeURIComponent(name)}`);
            if (response.code === "0000") {
                input.value = '';
                this.createBullet(`🚀 [${name}] 전장 투입 성공!`, true);
            } else {
                alert(response.message);
            }
        } catch (error) {
            console.error("메뉴 추가 에러:", error);
        }
    },

    /**
     * 투표하기 (REST)
     */
    vote: async function(menuId, menuName) {
        try {
            const response = await COMMON_AJAX.post(`/api/menu/vote?menuId=${menuId}`);
            if (response.code === "0000") {
                this.createBullet(`${menuName} +1 화력 지원!`, true);
            }
        } catch (error) {
            console.error("투표 에러:", error);
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

        sorted.forEach((item, idx) => {
            const pct = Math.round(((item.finalScore || 0) / totalVotes) * 100);
            const card = document.createElement('div');
            const isFirst = idx === 0 && item.finalScore > 0;
            
            card.className = `battle-card p-5 flex items-center justify-between group active:scale-[0.96] transition-all cursor-pointer ${isFirst ? 'rank-1' : ''}`;
            
            card.onclick = () => {
                // 클릭 피드백: 살짝 흔들림
                card.style.transform = 'translateX(4px)';
                setTimeout(() => card.style.transform = 'translateX(0)', 80);
                this.vote(item.id, item.menuName);
            };

            card.innerHTML = `
                <div class="flex-1">
                    <div class="flex items-center gap-3 mb-3">
                        <span class="text-xs font-black ${idx < 3 ? 'text-yellow-500 text-neon-yellow' : 'text-slate-500'}">#0${idx+1}</span>
                        <h4 class="font-extrabold text-base text-slate-100 tracking-tight">${item.menuName}</h4>
                    </div>
                    <div class="progress-container">
                        <div class="progress-bar" style="width: ${pct}%"></div>
                    </div>
                </div>
                <div class="ml-6 text-right">
                    <div class="text-2xl font-black text-white leading-none mb-1">${pct}<span class="text-xs ml-0.5 opacity-50">%</span></div>
                    <div class="text-[10px] text-slate-500 font-mono tracking-tighter uppercase">${item.finalScore} Support</div>
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
            row.className = "flex justify-between items-center p-3 bg-slate-900/50 rounded-xl border border-slate-800/50 mb-2";
            row.innerHTML = `
                <div class="flex items-center gap-3">
                    <span class="text-xs font-mono text-slate-500">#${idx+1}</span>
                    <span class="text-sm font-bold text-slate-300">${item.menuName}</span>
                </div>
                <span class="text-xs font-mono text-green-500 font-bold">${item.finalScore}표</span>
            `;
            list.appendChild(row);
        });
    },

    /**
     * 이벤트 바인딩
     */
    bindEvents: function() {
        const msgForm = document.getElementById('msg-form');
        if (msgForm) {
            msgForm.addEventListener('submit', (e) => {
                e.preventDefault();
                const input = document.getElementById('msg-input');
                const content = input.value.trim();
                if (content && this.stompClient?.connected) {
                    this.stompClient.send("/app/chat/send", {}, content);
                    input.value = '';
                }
            });
        }
    },

    /**
     * 타이머 시작
     */
    startTimers: function() {
        setInterval(() => {
            const now = new Date();
            const timerEl = document.getElementById('live-timer');
            if (timerEl) timerEl.innerText = now.toLocaleTimeString('ko-KR', { hour12: false });
        }, 1000);
    },

    showFullRanking: function() {
        document.getElementById('full-ranking')?.classList.remove('hidden');
        document.getElementById('btn-show-more')?.classList.add('hidden');
    },

    /**
     * [개선] 다채로운 색상의 팝업 버블 생성
     * 메시지가 화면 곳곳에서 랜덤하게 튀어나와 '왁자지껄'한 분위기를 연출합니다.
     */
    createBullet: function(text, isSpecial = false) {
        const container = document.getElementById('bullet-container');
        if (!container) return;
        
        const el = document.createElement('div');
        el.className = 'bullet-msg';
        
        if (isSpecial) {
            el.style.border = '2px solid #22c55e';
            el.style.color = '#ffffff';
            el.style.background = 'rgba(34, 197, 94, 0.5)';
            el.style.boxShadow = '0 0 20px rgba(34, 197, 94, 0.3)';
            el.style.zIndex = '100';
        } else {
            const colors = [
                'rgba(147, 197, 253, 0.25)', // Blue
                'rgba(196, 181, 253, 0.25)', // Purple
                'rgba(167, 243, 208, 0.25)', // Green
                'rgba(253, 186, 116, 0.25)', // Orange
                'rgba(244, 114, 182, 0.25)'  // Pink
            ];
            const randomColor = colors[Math.floor(Math.random() * colors.length)];
            el.style.background = randomColor;
            el.style.borderColor = 'rgba(255, 255, 255, 0.15)';
            el.style.color = '#f8fafc';
        }
        
        el.innerText = text;
        const left = Math.random() * 80 + 5;
        const top = Math.random() * 65 + 10;
        const duration = isSpecial ? 4.5 : 3 + Math.random() * 1.5;
        
        el.style.left = `${left}%`;
        el.style.top = `${top}%`;
        el.style.animation = `popAndStay ${duration}s ease-in-out forwards`;
        
        container.appendChild(el);
        el.addEventListener('animationend', () => el.remove());
    }
};

document.addEventListener('DOMContentLoaded', () => MAINJS.init());
