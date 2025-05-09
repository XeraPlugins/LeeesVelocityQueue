package me.txmc.lvq.listeners;

import com.nickuc.login.api.nLoginAPI;
import com.nickuc.login.api.types.Identity;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.txmc.lvq.Main;
import me.txmc.lvq.PlayerQueue;
import me.txmc.lvq.Reloadable;

import static me.txmc.lvq.util.MessageUtil.sendMessage;

public class PreConnectListener implements Reloadable {
    private final Main plugin;
    private final PlayerQueue normalQueue;
    private final PlayerQueue prioQueue;
    private final RegisteredServer mainServer;
    private String serverFullMessasge;

    public PreConnectListener(Main plugin) {
        this.plugin = plugin;
        normalQueue = plugin.getNormalQueue();
        prioQueue = plugin.getPrioQueue();
        mainServer = plugin.getMainServer();
        reloadConfig();
    }

    @Subscribe
    public void onPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        Identity nlId = Identity.ofKnownName(player.getUsername());
        RegisteredServer server = event.getOriginalServer();
        if (player.hasPermission("lvq.bypass")) return;
        if (nLoginAPI.getApi().isAuthenticated(nlId) && server.getServerInfo().getName().equals(mainServer.getServerInfo().getName()) && !plugin.doesServerHaveSlot()) {
            sendMessage(player, serverFullMessasge);
            if (player.hasPermission("lvq.priority")) {
                prioQueue.addToQueue(player);
            } else normalQueue.addToQueue(player);
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(plugin.getQueueServer()));
        }
    }

    @Override
    public void reloadConfig() {
        try {
            serverFullMessasge = plugin.getConfig().node("messages", "server-full").getString();
        } catch (Throwable t) {
            plugin.getLogger().atError().setCause(t).log("Failed to load config. Please check stacktrace for more info");
        }
    }
}
