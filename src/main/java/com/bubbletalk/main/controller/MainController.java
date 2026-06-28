package com.bubbletalk.main.controller;

import com.bubbletalk.guest.GuestIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final GuestIdSupport guestIdSupport;

    @GetMapping("/")
    public String mainPage(HttpServletRequest request, HttpServletResponse response, Model model) {
        String clientIp = request.getRemoteAddr();
        String guestId = guestIdSupport.resolveOrCreate(request, response);

        model.addAttribute("clientIp", clientIp);
        model.addAttribute("guestId", guestId);
        return "main";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }
}
