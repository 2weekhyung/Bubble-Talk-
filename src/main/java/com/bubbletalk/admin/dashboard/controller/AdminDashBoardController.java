package com.bubbletalk.admin.dashboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * [관리자 전용 뷰 컨트롤러]
 * 서비스 운영 및 모니터링을 위한 관리자 페이지를 서빙합니다.
 */
@Controller
@RequestMapping("/admin")
public class AdminDashBoardController {

    /**
     * [GET] /admin/dashboard
     * 관리자 메인 대시보드 화면을 반환합니다.
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "admin/dashboard";
    }
}
