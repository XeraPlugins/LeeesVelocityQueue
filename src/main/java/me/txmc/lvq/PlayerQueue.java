package me.txmc.lvq;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerQueue {
    private final List<UUID> order = new ArrayList<>();
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Object lock = new Object();

    public void addToQueue(UUID uuid) {
        synchronized (lock) {
            if (members.add(uuid)) {
                order.add(uuid);
            }
        }
    }

    public int getQueuePosition(UUID uuid) {
        synchronized (lock) {
            int index = order.indexOf(uuid);
            return index < 0 ? -1 : index + 1;
        }
    }

    public void removeFromQueue(UUID uuid) {
        synchronized (lock) {
            if (members.remove(uuid)) {
                order.remove(uuid);
            }
        }
    }

    public boolean isInQueue(UUID uuid) {
        return members.contains(uuid);
    }

    public List<UUID> getUUIDsInQueue() {
        synchronized (lock) {
            return new ArrayList<>(order);
        }
    }

    public int queueLength() {
        synchronized (lock) {
            return order.size();
        }
    }

    public void rebuild() {
    }
}