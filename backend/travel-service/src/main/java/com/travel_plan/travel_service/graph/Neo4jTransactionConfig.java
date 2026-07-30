package com.travel_plan.travel_service.graph;

import jakarta.persistence.EntityManagerFactory;
import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class Neo4jTransactionConfig {

    // Des qu'un bean de type TransactionManager existe dans le contexte (notre
    // Neo4jTransactionManager ci-dessous), l'auto-configuration Spring Boot du
    // transactionManager JPA est desactivee (@ConditionalOnMissingBean(TransactionManager.class)
    // cote JpaBaseConfiguration). Sans ca, plus aucun bean "transactionManager" n'existe et
    // tous les @Transactional nus (TravelService, UserService, etc.) echouent au runtime avec
    // NoSuchBeanDefinitionException. On reprend donc sa creation nous-memes, en @Primary pour
    // que les @Transactional sans qualifier continuent de cibler Postgres/JPA sans ambiguite.
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    // Transaction Neo4j explicite et independante de celle (JPA/Postgres) ci-dessus :
    // Postgres et Neo4j sont deux bases distinctes sans atomicite reelle entre elles, donc
    // partager la transaction ambiante n'apporte rien et faisait echouer les requetes derivees
    // de Spring Data Neo4j. TravelGraphSyncService l'injecte par TYPE (Neo4jTransactionManager,
    // pas PlatformTransactionManager), donc aucune ambiguite malgre le @Primary ci-dessus.
    @Bean
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver) {
        return new Neo4jTransactionManager(driver);
    }
}
