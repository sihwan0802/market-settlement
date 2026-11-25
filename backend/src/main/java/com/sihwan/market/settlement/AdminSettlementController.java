package com.sihwan.market.settlement;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    /** 매일 01:00 작업(정산, 검사, 알림)을 지정한 날짜로 바로 실행한다. */
    @PostMapping("/daily")
    public Map<String, Object> daily(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.runDaily(date);
    }

    @GetMapping("/missing")
    public List<MissingSettlement> missing(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.findMissing(from, to);
    }

    @GetMapping("/inconsistent")
    public InconsistentResponse inconsistent() {
        long start = System.nanoTime();
        List<Long> ids = service.findInconsistent();
        return new InconsistentResponse(ids, (System.nanoTime() - start) / 1_000_000);
    }
}
