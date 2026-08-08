package me.txmc.lvq;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerQueue {
    private final ConcurrentHashMap<UUID, Integer> position;
    private final Object lock = new Object();

    public PlayerQueue() {
        this.position = new ConcurrentHashMap<>();
    }

    public int decrementPosition(UUID uuid) {
        synchronized (lock) {
            position.computeIfPresent(uuid, (u, pos) -> {
                if (pos > 1) return pos - 1;
                return 1;
            });
            return getQueuePositionUnsafe(uuid);
        }
    }

    public void addToQueue(UUID uuid) {
        synchronized (lock) {
            if (!position.containsKey(uuid)) {
                position.put(uuid, position.size() + 1);
            }
        }
    }

    public int getQueuePosition(UUID uuid) {
        Integer pos = position.get(uuid);
        return pos == null ? -1 : pos;
    }

    public void removeFromQueue(UUID uuid) {
        synchronized (lock) {
            Integer pos = position.get(uuid);
            if (pos != null && pos > 0) {
                position.replaceAll((u, p) -> p > pos ? p - 1 : p);
            }
            position.remove(uuid);
        }
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

    public void rebuild() {
        synchronized (lock) {
            List<UUID> sorted = position.entrySet().stream()
                    .sorted(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .toList();
            int i = 1;
            for (UUID uuid : sorted) {
                position.put(uuid, i);
            }
        }
    }

    private int getQueuePositionUnsafe(UUID uuid) {
        Integer pos = position.get(uuid);
        return pos == null ? -1 : pos;
    }
}