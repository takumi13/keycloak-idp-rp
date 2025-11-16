package com.example.oidcclient.controller;

import com.example.oidcclient.controller.model.CallbackViewModel;
import com.example.oidcclient.service.SessionStateService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CallbackController {

    private final SessionStateService sessionStateService;

    public CallbackController(SessionStateService sessionStateService) {
        this.sessionStateService = sessionStateService;
    }

    @GetMapping("${app.path.callback:/callback}")
    public String callback(@RequestParam MultiValueMap<String, String> requestParams,
                           HttpSession session,
                           Model model) {
        Map<String, List<String>> orderedParams = toLinkedMap(requestParams);
        CallbackViewModel viewModel = new CallbackViewModel(
                firstValue(requestParams, "code"),
                firstValue(requestParams, "state"),
                firstValue(requestParams, "redirect_uri"),
                firstValue(requestParams, "error"),
                firstValue(requestParams, "error_description"),
                orderedParams,
                sessionStateService.loadPkceContext(session)
        );
        model.addAttribute("callback", viewModel);
        return "callback";
    }

    private Map<String, List<String>> toLinkedMap(MultiValueMap<String, String> params) {
        Map<String, List<String>> ordered = new LinkedHashMap<>();
        params.forEach((key, values) -> ordered.put(key, List.copyOf(values)));
        return ordered;
    }

    private String firstValue(MultiValueMap<String, String> params, String key) {
        List<String> values = params.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}