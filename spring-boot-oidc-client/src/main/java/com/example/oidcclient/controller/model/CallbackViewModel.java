package com.example.oidcclient.controller.model;

import com.example.oidcclient.service.SessionStateService;

import java.util.List;
import java.util.Map;

public record CallbackViewModel(
        String code,
        String state,
        String redirectUri,
        String error,
        String errorDescription,
        Map<String, List<String>> rawParameters,
        SessionStateService.PkceContext pkceContext
) {
}
