/**
 * 공통 AJAX 통신 모듈
 * 모든 비동기 요청은 이 객체를 통해 수행합니다.
 */
const COMMON_AJAX = {
    /**
     * 기본 fetch 래퍼
     * @param {string} url - 요청 API URL
     * @param {object} options - fetch 옵션 (method, headers, body 등)
     */
    request: async function(url, options = {}) {
        const defaultOptions = {
            headers: {
                'Content-Type': 'application/json',
                // 필요 시 CSRF 토큰 등을 여기서 공통으로 처리할 수 있습니다.
            },
        };

        const mergedOptions = {
            ...defaultOptions,
            ...options,
            headers: {
                ...defaultOptions.headers,
                ...options.headers,
            },
        };

        try {
            const response = await fetch(url, mergedOptions);
            
            // 응답이 성공적이지 않을 경우 에러 처리
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
            }

            // 본문이 없는 경우(204 No Content 등) 처리
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.includes("application/json")) {
                return await response.json();
            }
            return await response.text();

        } catch (error) {
            console.error('[AJAX ERROR]', error);
            // 여기서 공통 에러 알림(Alert 등)을 띄울 수 있습니다.
            throw error;
        }
    },

    /**
     * GET 요청
     */
    get: function(url, headers = {}) {
        return this.request(url, {
            method: 'GET',
            headers: headers
        });
    },

    /**
     * POST 요청
     */
    post: function(url, body, headers = {}) {
        return this.request(url, {
            method: 'POST',
            body: JSON.stringify(body),
            headers: headers
        });
    },

    /**
     * PUT 요청
     */
    put: function(url, body, headers = {}) {
        return this.request(url, {
            method: 'PUT',
            body: JSON.stringify(body),
            headers: headers
        });
    },

    /**
     * DELETE 요청
     */
    delete: function(url, headers = {}) {
        return this.request(url, {
            method: 'DELETE',
            headers: headers
        });
    }
};
