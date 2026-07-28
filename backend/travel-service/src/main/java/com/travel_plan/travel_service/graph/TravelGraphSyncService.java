package com.travel_plan.travel_service.graph;

import com.travel_plan.travel_service.domain.Destination;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TravelGraphSyncService {

    private final PlaceRepository placeRepository;

    public void recordRoute(List<Destination> orderedDestinations) {
        adjustRoutes(orderedDestinations, 1);
    }

    public void removeRoute(List<Destination> orderedDestinations) {
        adjustRoutes(orderedDestinations, -1);
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
