package com.travel_plan.travel_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.neo4j.autoconfigure.DataNeo4jAutoConfiguration;
import org.springframework.boot.data.neo4j.autoconfigure.DataNeo4jRepositoriesAutoConfiguration;
import org.springframework.boot.neo4j.autoconfigure.Neo4jAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
		Neo4jAutoConfiguration.class,
		DataNeo4jAutoConfiguration.class,
		DataNeo4jRepositoriesAutoConfiguration.class
})
class TravelServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
