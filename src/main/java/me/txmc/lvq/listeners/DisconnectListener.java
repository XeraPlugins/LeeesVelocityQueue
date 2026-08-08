package me.txmc.lvq.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import me.txmc.lvq.Main;
import me.txmc.lvq.PlayerQueue;

import java.util.UUID;

public class DisconnectListener {
    private final Main plugin;
    private final PlayerQueue normalQueue;
    private final PlayerQueue prioQueue;

    public DisconnectListener(Main plugin) {
        this.plugin = plugin;
        normalQueue = plugin.getNormalQueue();
        prioQueue = plugin.getPrioQueue();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        prioQueue.removeFromQueue(uuid);
        normalQueue.removeFromQueue(uuid);
        plugin.getServerFullSent().remove(uuid);
        plugin.getQueueWorker().cleanupPlayer(uuid);
    }
}