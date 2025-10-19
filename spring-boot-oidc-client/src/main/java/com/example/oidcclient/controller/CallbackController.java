package com.example.oidcclient.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CallbackController {

    @GetMapping("/callback")
    public String callback() {
        // templates/callback.html を返す
        return "callback";
    }
}