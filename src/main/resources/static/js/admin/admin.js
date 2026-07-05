/**
 * [Admin JS] 관리자 대시보드 제어 로직
 */
const ADMIN = {
    stompClient: null,
    roomPage: 0,
    roomPageSize: 20,
    roomTotalPages: 1,
    roomTotalElements: 0,
    securityEventPage: 0,
    securityEventPageSize: 20,
    securityEventTotalPages: 1,
    securityEventTotalElements: 0,

    init: function() {
        console.log("관리자 대시보드 초기화...");
        this.fetchForbiddenWords();
        this.fetchSummary();
        this.fetchRooms();
        this.fetchEventStatus();
        this.fetchLunchTimes(); // 추가
        this.fetchHistory();
        this.fetchSecurityEvents();
        this.connectWebSocket();
    },

    /**
     * 운영 시간 설정 조회
     */
    fetchLunchTimes: async function() {
        try {
            const response = await COMMON_AJAX.get('/api/admin/lunch/times');
            if (response.code === "0000") {
                document.getElementById('lunch-start-time').value = response.result.startTime;
                document.getElementById('lunch-end-time').value = response.result.endTime;
            }
        } catch (e) {
            console.error("운영 시간 로딩 실패", e);
        }
    },

    /**
     * 운영 시간 설정 저장
     */
    updateLunchTimes: async function() {
        const startTime = document.getElementById('lunch-start-time').value;
        const endTime = document.getElementById('lunch-end-time').value;

        if (!startTime || !endTime) {
            alert('시간을 모두 입력해주세요.');
            return;
        }

        try {
            const response = await COMMON_AJAX.post('/api/admin/lunch/times', {
                startTime: startTime,
                endTime: endTime
            });
            if (response.code === "0000") {
                alert('운영 시간이 성공적으로 저장되었습니다.');
            }
        } catch (e) {
            alert('저장 실패: ' + e.message);
        }
    },

    /**
     * 시스템 전역 공지 발송
     */
    sendAnnouncement: async function() {
        const input = document.getElementById('admin-announcement-msg');
        const message = input.value.trim();
        if (!message) return;

        try {
            const response = await COMMON_AJAX.post('/api/admin/announcement', { message: message });
            if (response.code === "0000") {
                input.value = '';
                alert('공지가 전송되었습니다.');
            }
        } catch (e) {
            alert('전송 실패: ' + e.message);
        }
    },

    /**
     * 금칙어 목록 가져오기
     */
    fetchForbiddenWords: async function() {
        try {
            const response = await COMMON_AJAX.get('/api/admin/forbidden-words');
            if (response.code === "0000") {
                this.renderForbiddenWords(response.result);
            }
        } catch (e) {
            console.error("금칙어 로딩 실패", e);
        }
    },

    /**
     * 금칙어 리스트 렌더링
     */
    renderForbiddenWords: function(words) {
        const list = document.getElementById('forbidden-word-list');
        if (!list) return;
        list.innerHTML = '';

        words.forEach(word => {
            const div = document.createElement('div');
            div.className = "flex justify-between items-center p-3 bg-slate-800/50 rounded-xl border border-slate-700/50 group hover:border-orange-500/50 transition-all";
            div.innerHTML = `
                <span class="text-sm font-medium text-slate-200">${word.word}</span>
                <button onclick="ADMIN.deleteForbiddenWord(${word.id})" class="text-slate-500 hover:text-red-500 transition-colors opacity-0 group-hover:opacity-100">
                    <i class="fa-solid fa-trash-can text-xs"></i>
                </button>
            `;
            list.appendChild(div);
        });
    },

    /**
     * 금칙어 추가
     */
    addForbiddenWord: async function() {
        const input = document.getElementById('new-forbidden-word');
        const word = input.value.trim();
        if (!word) return;

        try {
            const response = await COMMON_AJAX.post('/api/admin/forbidden-words', { word: word });
            if (response.code === "0000") {
                input.value = '';
                this.fetchForbiddenWords();
            }
        } catch (e) {
            alert(e.message);
        }
    },

    /**
     * 금칙어 삭제
     */
    deleteForbiddenWord: async function(id) {
        if (!confirm('정말 삭제하시겠습니까?')) return;
        try {
            const response = await COMMON_AJAX.delete(`/api/admin/forbidden-words/${id}`);
            if (response.code === "0000") {
                this.fetchForbiddenWords();
            }
        } catch (e) {
            alert(e.message);
        }
    },

    /**
     * 전체 캐시 갱신
     */
    refreshAllCache: async function() {
        try {
            const response = await COMMON_AJAX.post('/api/admin/forbidden-words/refresh');
            if (response.code === "0000") {
                alert('전체 캐시가 갱신되었습니다.');
            }
        } catch (e) {
            alert('갱신 실패: ' + e.message);
        }
    },

    /**
     * 통계 데이터 가져오기
     */
    fetchSummary: async function() {
        try {
            const response = await COMMON_AJAX.get('/api/admin/dashboard/summary');
            if (response.code === "0000") {
                const summary = response.result;
                document.getElementById('admin-user-count').innerText = summary.activeSessions.toLocaleString();
                document.getElementById('admin-menu-count').innerText = summary.todayMenuCount.toLocaleString();
                document.getElementById('admin-total-votes').innerText = summary.todayVoteCount.toLocaleString();
                document.getElementById('admin-total-rooms').innerText = summary.totalRooms.toLocaleString();
                document.getElementById('admin-public-rooms').innerText = summary.publicRooms.toLocaleString();
                document.getElementById('admin-private-rooms').innerText = summary.privateRooms.toLocaleString();
                document.getElementById('admin-open-rooms').innerText = summary.openRooms.toLocaleString();
                document.getElementById('admin-full-rooms').innerText = summary.fullRooms.toLocaleString();
                document.getElementById('admin-closed-rooms').innerText = summary.closedRooms.toLocaleString();
                this.updateRedisStatus(summary.redisAvailable);
            }
        } catch (e) {
            console.error("관리자 요약 조회 실패", e);
            this.updateRedisStatus(false);
        }
    },

    updateRedisStatus: function(available) {
        const badge = document.getElementById('admin-redis-status');
        if (!badge) return;
        badge.textContent = available ? 'Redis 정상' : 'Redis 오류';
        badge.className = available
            ? 'text-[10px] px-2 py-1 rounded border font-bold uppercase text-green-500 border-green-500/30 bg-green-500/10'
            : 'text-[10px] px-2 py-1 rounded border font-bold uppercase text-red-500 border-red-500/30 bg-red-500/10';
    },

    fetchRooms: async function(page = this.roomPage) {
        try {
            const targetPage = Math.max(Number(page) || 0, 0);
            const params = new URLSearchParams();
            params.set('page', String(targetPage));
            params.set('size', String(this.roomPageSize));
            params.set('sort', 'createdDate,desc');
            this.appendParam(params, 'visibility', document.getElementById('admin-room-visibility')?.value);
            this.appendParam(params, 'status', document.getElementById('admin-room-status')?.value);

            const response = await COMMON_AJAX.get(`/api/admin/rooms?${params.toString()}`);
            if (response.code === "0000") {
                const result = response.result || {};
                const rooms = Array.isArray(result) ? result : (result.content || []);
                this.roomPage = Array.isArray(result) ? 0 : (result.number || 0);
                this.roomTotalPages = Array.isArray(result) ? 1 : Math.max(result.totalPages || 1, 1);
                this.roomTotalElements = Array.isArray(result) ? rooms.length : (result.totalElements || rooms.length);
                this.renderRooms(rooms);
                this.renderRoomPagination();
            }
        } catch (e) {
            console.error("관리자 채팅방 목록 조회 실패", e);
        }
    },

    renderRooms: function(rooms) {
        const list = document.getElementById('admin-room-list');
        if (!list) return;
        list.innerHTML = '';

        rooms.forEach(room => {
            const tr = document.createElement('tr');
            tr.className = 'hover:bg-slate-800/30 transition-colors';
            [
                room.roomCode,
                room.name,
                room.privateRoom ? '비밀' : '공개',
                room.status,
                `${room.currentParticipants}/${room.maxParticipants}`,
                this.formatDateTime(room.createdAt),
                this.formatDateTime(room.closedAt)
            ].forEach(value => {
                const td = document.createElement('td');
                td.className = 'py-3 px-2 text-slate-300';
                td.textContent = value ?? '-';
                tr.appendChild(td);
            });

            const actionCell = document.createElement('td');
            actionCell.className = 'py-3 px-2';
            if (room.status !== 'CLOSED') {
                const closeButton = document.createElement('button');
                closeButton.type = 'button';
                closeButton.className = 'px-2 py-1 text-xs font-bold rounded border border-red-500/40 text-red-400 hover:bg-red-500 hover:text-white';
                closeButton.textContent = '종료';
                closeButton.addEventListener('click', () => this.closeRoom(room.roomCode));
                actionCell.appendChild(closeButton);
            } else {
                actionCell.textContent = '-';
                actionCell.classList.add('text-slate-600');
            }
            tr.appendChild(actionCell);
            list.appendChild(tr);
        });
    },

    closeRoom: async function(roomCode) {
        if (!confirm(`${roomCode} 채팅방을 종료하시겠습니까?`)) return;
        try {
            const response = await COMMON_AJAX.post(`/api/admin/rooms/${roomCode}/close`, {});
            if (response.code === '0000') {
                await Promise.all([this.fetchSummary(), this.fetchRooms(this.roomPage)]);
                alert('채팅방이 종료되었습니다.');
            }
        } catch (e) {
            alert('채팅방 종료 실패: ' + e.message);
        }
    },

    cleanupStaleSessions: async function() {
        if (!confirm('현재 서버에서 활성 상태가 아닌 stale session을 정리하시겠습니까?')) return;
        try {
            const response = await COMMON_AJAX.post('/api/admin/realtime/cleanup-stale-sessions', {});
            if (response.code === '0000') {
                const result = response.result;
                await Promise.all([this.fetchSummary(), this.fetchRooms(this.roomPage)]);
                alert(`${result.message}\n검사 세션: ${result.scannedSessions}\n영향받은 방: ${result.affectedRooms}`);
            }
        } catch (e) {
            alert('Stale session 정리 실패: ' + e.message);
        }
    },

    renderRoomPagination: function() {
        const label = document.getElementById('admin-room-page-label');
        if (label) {
            label.textContent = `${this.roomPage + 1} / ${this.roomTotalPages} · 총 ${this.roomTotalElements.toLocaleString()}개`;
        }

        const prev = document.getElementById('admin-room-prev');
        if (prev) prev.disabled = this.roomPage <= 0;

        const next = document.getElementById('admin-room-next');
        if (next) next.disabled = this.roomPage >= this.roomTotalPages - 1;
    },

    prevRoomPage: function() {
        if (this.roomPage > 0) {
            this.fetchRooms(this.roomPage - 1);
        }
    },

    nextRoomPage: function() {
        if (this.roomPage < this.roomTotalPages - 1) {
            this.fetchRooms(this.roomPage + 1);
        }
    },

    fetchSecurityEvents: async function(page = this.securityEventPage) {
        const params = new URLSearchParams();
        this.appendParam(params, 'eventType', document.getElementById('security-event-type')?.value);
        this.appendParam(params, 'severity', document.getElementById('security-severity')?.value);
        this.appendParam(params, 'roomCode', document.getElementById('security-room-code')?.value);
        this.appendParam(params, 'guestId', document.getElementById('security-guest-id')?.value);
        this.appendParam(params, 'ipAddress', document.getElementById('security-ip-address')?.value);
        this.appendDateTimeParam(params, 'startDate', document.getElementById('security-start-date')?.value);
        this.appendDateTimeParam(params, 'endDate', document.getElementById('security-end-date')?.value);
        params.set('page', String(Math.max(Number(page) || 0, 0)));
        params.set('size', String(this.securityEventPageSize));
        params.set('sort', 'createdAt,desc');

        try {
            const response = await COMMON_AJAX.get(`/api/admin/security-events?${params.toString()}`);
            if (response.code === '0000') {
                const result = response.result || {};
                const events = result.content || [];
                this.securityEventPage = result.number || 0;
                this.securityEventTotalPages = Math.max(result.totalPages || 1, 1);
                this.securityEventTotalElements = result.totalElements || events.length;
                this.renderSecurityEvents(events);
                this.renderSecurityEventPagination();
            } else {
                alert(response.message || '보안 이벤트 로그 조회 실패');
            }
        } catch (e) {
            alert('보안 이벤트 로그 조회 실패: ' + e.message);
        }
    },

    appendParam: function(params, key, value) {
        if (value && String(value).trim()) {
            params.set(key, String(value).trim());
        }
    },

    appendDateTimeParam: function(params, key, value) {
        if (value && String(value).trim()) {
            params.set(key, String(value).trim());
        }
    },

    renderSecurityEvents: function(events) {
        const list = document.getElementById('security-event-list');
        if (!list) return;
        list.innerHTML = '';

        if (!events.length) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 7;
            td.textContent = '표시할 보안 이벤트 로그가 없습니다.';
            tr.appendChild(td);
            list.appendChild(tr);
            return;
        }

        events.forEach(event => {
            const tr = document.createElement('tr');
            [
                this.formatDateTime(event.createdAt),
                event.eventType,
                event.severity,
                event.roomCode || '-',
                event.guestId || '-',
                event.ipAddress || '-',
                event.reason || '-'
            ].forEach(value => {
                const td = document.createElement('td');
                td.textContent = value;
                tr.appendChild(td);
            });
            list.appendChild(tr);
        });
    },

    renderSecurityEventPagination: function() {
        const label = document.getElementById('security-event-page-label');
        if (label) {
            label.textContent = `${this.securityEventPage + 1} / ${this.securityEventTotalPages} · 총 ${this.securityEventTotalElements.toLocaleString()}개`;
        }

        const prev = document.getElementById('security-event-prev');
        if (prev) prev.disabled = this.securityEventPage <= 0;

        const next = document.getElementById('security-event-next');
        if (next) next.disabled = this.securityEventPage >= this.securityEventTotalPages - 1;
    },

    prevSecurityEventPage: function() {
        if (this.securityEventPage > 0) {
            this.fetchSecurityEvents(this.securityEventPage - 1);
        }
    },

    nextSecurityEventPage: function() {
        if (this.securityEventPage < this.securityEventTotalPages - 1) {
            this.fetchSecurityEvents(this.securityEventPage + 1);
        }
    },

    formatDateTime: function(value) {
        if (!value) return '-';
        return new Date(value).toLocaleString('ko-KR', { hour12: false });
    },

    /**
     * 이벤트 상태 가져오기
     */
    fetchEventStatus: async function() {
        try {
            const response = await COMMON_AJAX.get('/api/menu/status');
            if (response.code === "0000") {
                this.updateStatusUI(response.result.status);
            }
        } catch (e) {}
    },

    /**
     * 상태 변경 UI 업데이트
     */
    updateStatusUI: function(status) {
        const btnOpen = document.getElementById('btn-status-open');
        const btnClosed = document.getElementById('btn-status-closed');
        
        if (status === 'OPEN') {
            btnOpen.className = "px-3 py-1.5 rounded-lg text-xs font-black bg-green-500 text-slate-900 border border-green-500 shadow-[0_0_15px_rgba(34,197,94,0.4)]";
            btnClosed.className = "px-3 py-1.5 rounded-lg text-xs font-bold border border-slate-600 hover:bg-red-500/10 transition-all";
        } else {
            btnOpen.className = "px-3 py-1.5 rounded-lg text-xs font-bold border border-slate-600 hover:bg-green-500/10 transition-all";
            btnClosed.className = "px-3 py-1.5 rounded-lg text-xs font-black bg-red-500 text-white border border-red-500 shadow-[0_0_15px_rgba(239,68,68,0.4)]";
        }
    },

    /**
     * 이벤트 상태 강제 변경
     */
    toggleEventStatus: async function(status) {
        if (!confirm(`이벤트 상태를 ${status}로 변경하시겠습니까?`)) return;
        try {
            const response = await COMMON_AJAX.post('/api/menu/admin/status', { status: status });
            if (response.code === "0000") {
                this.updateStatusUI(status);
                alert(`이벤트가 ${status} 상태로 변경되었습니다.`);
            }
        } catch (e) {
            alert('상태 변경 실패: ' + e.message);
        }
    },

    /**
     * 오늘의 데이터 초기화
     */
    resetDailyData: async function() {
        if (!confirm('정말 오늘의 모든 데이터를 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.')) return;
        try {
            const response = await COMMON_AJAX.post('/api/menu/admin/reset');
            if (response.code === "0000") {
                this.fetchSummary();
                alert('오늘의 데이터가 초기화되었습니다.');
            }
        } catch (e) {
            alert('초기화 실패: ' + e.message);
        }
    },

    /**
     * 투표 이력 가져오기
     */
    fetchHistory: async function() {
        try {
            const response = await COMMON_AJAX.get('/api/menu/admin/history');
            if (response.code === "0000") {
                this.renderHistory(response.result);
            }
        } catch (e) {
            console.error("이력 로딩 실패", e);
        }
    },

    /**
     * 이력 렌더링
     */
    renderHistory: function(histories) {
        const list = document.getElementById('lunch-history-list');
        if (!list) return;
        list.innerHTML = '';

        // 날짜별로 1위만 필터링해서 표시
        const winners = histories.filter(h => h.ranking === 1);

        winners.forEach(h => {
            const tr = document.createElement('tr');
            tr.className = "hover:bg-slate-800/30 transition-colors";
            tr.innerHTML = `
                <td class="py-3 px-2 text-slate-400 font-medium">${h.targetDate}</td>
                <td class="py-3 px-2 font-bold text-white">${h.menuName}</td>
                <td class="py-3 px-2 text-right text-green-400 font-black">${h.voteCount}표</td>
            `;
            list.appendChild(tr);
        });
    },

    /**
     * 실시간 접속자 수 수신을 위한 소켓 연결
     */
    connectWebSocket: function() {
        const socket = new SockJS('/ws-bubble');
        this.stompClient = Stomp.over(socket);
        this.stompClient.debug = null;

        this.stompClient.connect({}, () => {
            this.stompClient.subscribe('/topic/user-count', (response) => {
                const count = JSON.parse(response.body);
                document.getElementById('admin-user-count').innerText = count.toLocaleString();
            });
            this.stompClient.subscribe('/topic/bubbles', (response) => {
                const msg = JSON.parse(response.body);
                this.renderChatMessage(msg);
            });
        });
    },

    /**
     * 채팅 메시지 렌더링 (모니터링용)
     */
    renderChatMessage: function(msg) {
        const list = document.getElementById('admin-chat-list');
        if (!list) return;

        const div = document.createElement('div');
        div.className = "flex gap-3 animate-slide-in";
        
        const time = new Date().toLocaleTimeString('ko-KR', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
        const actor = msg.senderGuestId
            ? `guest:${msg.senderGuestId}`
            : msg.senderClientId
                ? `client:${msg.senderClientId}`
                : msg.senderIp || msg.sender || 'unknown';
        const room = msg.roomCode || '전역';
        const color = this.getIpColor(actor);

        const timeElement = document.createElement('span');
        timeElement.className = 'text-[10px] text-slate-600 font-mono mt-0.5';
        timeElement.textContent = time;

        const body = document.createElement('div');
        body.className = 'flex-1';

        const meta = document.createElement('div');
        meta.className = 'flex items-center gap-2 mb-1';

        const actorElement = document.createElement('span');
        actorElement.className = 'font-bold';
        actorElement.style.color = color;
        actorElement.textContent = actor;

        const roomElement = document.createElement('span');
        roomElement.className = 'text-[10px] text-cyan-500';
        roomElement.textContent = `[${room}]`;

        const content = document.createElement('div');
        content.className = 'bg-slate-800 border border-slate-700 p-2 rounded-lg text-slate-200 break-all';
        content.textContent = msg.content || '';

        meta.append(actorElement, roomElement);
        body.append(meta, content);
        div.append(timeElement, body);

        list.prepend(div); // 최신 메시지가 위로

        // 최대 50개까지만 유지
        if (list.children.length > 50) {
            list.removeChild(list.lastChild);
        }
    },

    /**
     * IP 기반 고정 색상 생성
     */
    getIpColor: function(ip) {
        if (!ip) return '#94a3b8';
        let hash = 0;
        for (let i = 0; i < ip.length; i++) {
            hash = ip.charCodeAt(i) + ((hash << 5) - hash);
        }
        const h = Math.abs(hash % 360);
        return `hsl(${h}, 70%, 70%)`;
    }
};

ADMIN.updateRedisStatus = function(available) {
    const badge = document.getElementById('admin-redis-status');
    if (!badge) return;
    badge.textContent = available ? 'Redis 정상' : 'Redis 오류';
    badge.className = available ? 'status-badge status-ok' : 'status-badge status-error';
};

ADMIN.renderRooms = function(rooms) {
    const list = document.getElementById('admin-room-list');
    if (!list) return;
    list.innerHTML = '';

    if (!rooms.length) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 8;
        td.textContent = '표시할 채팅방이 없습니다.';
        tr.appendChild(td);
        list.appendChild(tr);
        return;
    }

    rooms.forEach(room => {
        const tr = document.createElement('tr');
        [
            room.roomCode,
            room.name,
            room.privateRoom ? '비공개' : '공개',
            room.status,
            `${room.currentParticipants}/${room.maxParticipants}`,
            this.formatDateTime(room.createdAt),
            this.formatDateTime(room.closedAt)
        ].forEach(value => {
            const td = document.createElement('td');
            td.textContent = value ?? '-';
            tr.appendChild(td);
        });

        const actionCell = document.createElement('td');
        if (room.status !== 'CLOSED') {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'admin-room-close';
            button.textContent = '종료';
            button.addEventListener('click', () => this.closeRoom(room.roomCode));
            actionCell.appendChild(button);
        } else {
            actionCell.textContent = '-';
        }
        tr.appendChild(actionCell);
        list.appendChild(tr);
    });
};

ADMIN.updateStatusUI = function(status) {
    const open = document.getElementById('btn-status-open');
    const closed = document.getElementById('btn-status-closed');
    if (!open || !closed) return;
    open.className = status === 'OPEN' ? 'is-open' : '';
    closed.className = status === 'CLOSED' ? 'is-closed' : '';
};

ADMIN.renderHistory = function(histories) {
    const list = document.getElementById('lunch-history-list');
    if (!list) return;
    list.innerHTML = '';
    histories.filter(item => item.ranking === 1).forEach(item => {
        const tr = document.createElement('tr');
        [item.targetDate, item.menuName, `${item.voteCount}표`].forEach(value => {
            const td = document.createElement('td');
            td.textContent = value;
            tr.appendChild(td);
        });
        list.appendChild(tr);
    });
};

document.addEventListener('DOMContentLoaded', () => ADMIN.init());
