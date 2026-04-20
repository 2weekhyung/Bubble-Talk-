package com.bubbletalk.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainViewController {

    /**
     * "/" 경로로 접속했을 때 main.html 템플릿을 보여줍니다.
     */
    @GetMapping("/")
    public String mainPage() {
        return "main"; // templates/main.html 호출
    }
}
