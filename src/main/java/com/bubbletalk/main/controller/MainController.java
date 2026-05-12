package com.bubbletalk.main.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

    /**
     * "/" 경로로 접속했을 때 main.html 템플릿을 보여줍니다.
     */
    @GetMapping("/")
    public String mainPage(HttpServletRequest request, Model model) {
        String clientIp = request.getRemoteAddr();
        model.addAttribute("clientIp", clientIp);
        return "main"; // templates/main.html 호출
    }
}
