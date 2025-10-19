package com.example.oidcclient.controller;

// ...existing code...

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // プロパティからルートパスを取得
    @Value("${app.path.root:/}")
    private String rootPath;

    @Test
    public void testHello() throws Exception {
        mockMvc.perform(get(rootPath))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hello World")));
    }
}