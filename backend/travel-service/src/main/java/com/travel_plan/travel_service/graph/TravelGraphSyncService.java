package com.travel_plan.travel_service.graph;

import com.travel_plan.travel_service.domain.Destination;
import java.util.List;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TravelGraphSyncService {

    private final PlaceRepository placeRepository;
    private final TransactionTemplate neo4jTransactionTemplate;

    // Transaction Neo4j explicite et independante de celle (JPA/Postgres) de l'appelant :
    // Postgres et Neo4j sont deux bases distinctes sans atomicite reelle entre elles, donc
    // partager la transaction ambiante n'apporte rien et faisait echouer les requetes
    // derivees de Spring Data Neo4j (executees hors de tout contexte transactionnel Neo4j).
    // Injection par TYPE (pas par nom de bean) : un seul Neo4jTransactionManager existe dans
    // le contexte, donc Spring le resout sans ambiguite quel que soit son nom interne.
    public TravelGraphSyncService(PlaceRepository placeRepository, Neo4jTransactionManager neo4jTransactionManager) {
        this.placeRepository = placeRepository;
        this.neo4jTransactionTemplate = new TransactionTemplate(neo4jTransactionManager);
    }

    public void recordRoute(List<Destination> orderedDestinations) {
        neo4jTransactionTemplate.executeWithoutResult(status -> adjustRoutes(orderedDestinations, 1));
    }

    public void removeRoute(List<Destination> orderedDestinations) {
        neo4jTransactionTemplate.executeWithoutResult(status -> adjustRoutes(orderedDestinations, -1));
    }

    private void adjustRoutes(List<Destination> orderedDestinations, int delta) {
        for (int i = 0; i < orderedDestinations.size() - 1; i++) {
            Destination from = orderedDestinations.get(i);
            Destination to = orderedDestinations.get(i + 1);
            adjustRoute(from.getCity(), from.getCountry(), to.getCity(), to.getCountry(), delta);
        }
    }

    private void adjustRoute(String fromCity, String fromCountry, String toCity, String toCountry, int delta) {
        PlaceNode fromNode = findOrCreate(fromCity, fromCountry);
        PlaceNode toNode = findOrCreate(toCity, toCountry);

        RouteRelationship existing = fromNode.getRoutes().stream()
                .filter(route -> route.getTo().getId().equals(toNode.getId()))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            if (delta > 0) {
                fromNode.getRoutes().add(new RouteRelationship(toNode, delta));
                placeRepository.save(fromNode);
            }
            return;
        }

        int newCount = existing.getTripCount() + delta;
        if (newCount <= 0) {
            fromNode.getRoutes().remove(existing);
        } else {
            existing.setTripCount(newCount);
        }
        placeRepository.save(fromNode);
    }

    private PlaceNode findOrCreate(String city, String country) {
        return placeRepository
                .findByCityAndCountry(city, country)
                .orElseGet(() -> placeRepository.save(new PlaceNode(city, country)));
    }
}
