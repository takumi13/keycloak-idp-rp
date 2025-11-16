package com.example.oidcclient.controller;

import com.example.oidcclient.config.properties.AppPathProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class CallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppPathProperties appPathProperties;

    @Test
    void callbackViewHasModel() throws Exception {
        mockMvc.perform(get(appPathProperties.getCallback()).param("code", "auth-code").param("state", "abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("callback"))
                .andExpect(model().attributeExists("callback"));
    }
}
