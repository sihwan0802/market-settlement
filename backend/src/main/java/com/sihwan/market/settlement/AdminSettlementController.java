package com.sihwan.market.settlement;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settlements")
public class AdminSettlementController {

    public record RunResponse(LocalDate date, int created, long elapsedMs) {}

    public record InconsistentResponse(List<Long> settlementIds, long elapsedMs) {}

    private final SettlementService service;

    public AdminSettlementController(SettlementService service) {
        this.service = service;
    }

    @PostMapping
    public RunResponse run(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        long start = System.nanoTime();
        int created = service.settle(date);
        return new RunResponse(date, created, (System.nanoTime() - start) / 1_000_000);
    }

    @GetMapping("/inconsistent")
    public InconsistentResponse inconsistent() {
        long start = System.nanoTime();
        List<Long> ids = service.findInconsistent();
        return new InconsistentResponse(ids, (System.nanoTime() - start) / 1_000_000);
    }
}
