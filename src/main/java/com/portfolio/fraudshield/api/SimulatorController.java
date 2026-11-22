package com.portfolio.fraudshield.api;

import com.portfolio.fraudshield.simulator.SimulationResult;
import com.portfolio.fraudshield.simulator.TransactionSimulator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulator")
public class SimulatorController {

    private final TransactionSimulator simulator;

    public SimulatorController(TransactionSimulator simulator) {
        this.simulator = simulator;
    }

    public record RunRequest(
            @Min(TransactionSimulator.MIN_COUNT) @Max(TransactionSimulator.MAX_COUNT) int count) {
    }

    @PostMapping("/transactions")
    public SimulationResult run(@Valid @RequestBody RunRequest request) {
        return simulator.generate(request.count());
    }
}
