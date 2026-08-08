package me.txmc.lvq;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerQueue {
    private final ConcurrentHashMap<UUID, Integer> position;

    public PlayerQueue() {
        this.position = new ConcurrentHashMap<>();
    }

    public int decrementPosition(UUID uuid) {
        position.computeIfPresent(uuid, (u, pos) -> {
            if (pos > 1) return pos - 1;
            return 1;
        });
        return getQueuePosition(uuid);
    }

    public void addToQueue(UUID uuid) {
        position.computeIfAbsent(uuid, (u) -> position.size() + 1);
    }

    public int getQueuePosition(UUID uuid) {
        return position.getOrDefault(uuid, -1);
    }

    public void removeFromQueue(UUID uuid) {
        int pos = getQueuePosition(uuid);
        if (pos > 0) {
            position.entrySet().stream()
                    .filter(e -> e.getValue() > pos)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(this::decrementPosition);
        }
        position.remove(uuid);
    }

    public boolean isInQueue(UUID uuid) {
        return position.containsKey(uuid);
    }

    public Set<UUID> getUUIDsInQueue() {
        return position.keySet();
    }

    public int queueLength() {
        return position.size();
    }
}