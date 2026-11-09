package com.sihwan.market;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(com.sihwan.market.TestcontainersConfig.class)
class MarketApplicationTests {

	@Test
	void contextLoads() {
	}

}
