package com.tradetracker.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired MockMvc mvc;

    @Test
    void health_endpoint_is_publicly_accessible() throws Exception {
        mvc.perform(get("/actuator/health"))
           .andExpect(status().isOk());
    }

    @Test
    void swagger_ui_is_publicly_accessible() throws Exception {
        mvc.perform(get("/v3/api-docs"))
           .andExpect(status().isOk());
    }

    @Test
    void portfolio_endpoint_requires_authentication() throws Exception {
        mvc.perform(get("/v1/portfolios"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void tax_endpoint_requires_authentication() throws Exception {
        mvc.perform(get("/v1/portfolios/00000000-0000-0000-0000-000000000001/tax/cgt-summary"))
           .andExpect(status().isUnauthorized());
    }
}
