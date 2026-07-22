package com.travel_plan.travel_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
		+ "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration,"
		+ "org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration")
class TravelServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
