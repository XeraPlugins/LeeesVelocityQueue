package me.txmc.lvq;

import com.velocitypowered.api.proxy.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerQueue {
    private final ConcurrentHashMap<Player, Integer> position;
    public PlayerQueue() {
        this.position = new ConcurrentHashMap<>();
    }
    public int decrementPosition(Player player) {
        position.computeIfPresent(player, (p, pos) -> {
          if (pos > 1) return pos - 1;
          return 1;
        });
        return getQueuePosition(player);
    }
    public void addToQueue(Player player) {
        position.computeIfAbsent(player, (pos) -> position.size() + 1);
    }
    public int getQueuePosition(Player player) {
        return position.getOrDefault(player, -1);
    }
    public void removeFromQueue(Player player) {
        int pos = getQueuePosition(player);
        if (pos > 0) position.entrySet().stream().filter(e -> e.getValue() > pos).map(Map.Entry::getKey).toList().forEach(this::decrementPosition);
        position.remove(player);
    }
    public boolean isInQueue(Player player) {
        return position.containsKey(player);
    }
    public Set<Player> getPlayersInQueue() {
        return position.keySet();
    }
    public int queueLength() {
        return position.size();
    }
}
