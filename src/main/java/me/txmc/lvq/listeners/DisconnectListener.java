package me.txmc.lvq.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
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

    @Subscribe
    public void onKick(KickedFromServerEvent event) {
        if (!plugin.isRequeueOnMainKick()) return;
        Player player = event.getPlayer();
        if (player.hasPermission("lvq.bypass")) return;
        RegisteredServer mainServer = plugin.getMainServer();
        if (mainServer == null) return;
        if (!event.getServer().getServerInfo().getName().equals(mainServer.getServerInfo().getName())) return;
        if (event.kickedDuringServerConnect()) return;
        UUID uuid = player.getUniqueId();
        if (prioQueue.isInQueue(uuid) || normalQueue.isInQueue(uuid)) return;
        plugin.getQueueWorker().requeuePlayer(player);
        plugin.getLogger().atInfo().log("{} was kicked from the main server, re-adding them to the queue", player.getUsername());
    }
}