package com.sihwan.market.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

// 테스트에서는 지표 내보내기가 기본으로 꺼져 있어 prometheus 쪽만 켠다
@SpringBootTest(properties = "management.prometheus.metrics.export.enabled=true")
@Import(com.sihwan.market.TestcontainersConfig.class)
@AutoConfigureMockMvc
class MetricsTest {

    @Autowired MockMvc mvc;
    @Autowired com.sihwan.market.settlement.SettlementService settlement;

    @Test
    void prometheus_endpoint_exposes_jvm_pool_http_and_settlement_metrics() throws Exception {
        settlement.settle(LocalDate.of(2000, 1, 1)); // 정산 타이머에 한 번 기록
        mvc.perform(get("/actuator/health"));         // 요청 지연 지표에 한 번 기록

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("hikaricp_connections_active")))
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket")))
                .andExpect(content().string(containsString("settlement_run_seconds_count")));
    }
}
