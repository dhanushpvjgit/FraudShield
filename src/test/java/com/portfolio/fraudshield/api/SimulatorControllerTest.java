package com.portfolio.fraudshield.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.fraudshield.simulator.SimulationResult;
import com.portfolio.fraudshield.simulator.TransactionSimulator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SimulatorController.class)
class SimulatorControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TransactionSimulator simulator;

    @Test
    void runReturnsSummary() throws Exception {
        when(simulator.generate(200)).thenReturn(new SimulationResult("abc", 200, 190, 10, 2));

        mvc.perform(post("/api/v1/simulator/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"count\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generated").value(200))
                .andExpect(jsonPath("$.labelledFraudClusters").value(2));
    }

    @Test
    void countAboveMaximumIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/v1/simulator/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"count\":10001}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void countBelowMinimumIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/v1/simulator/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"count\":0}"))
                .andExpect(status().isBadRequest());
    }
}
