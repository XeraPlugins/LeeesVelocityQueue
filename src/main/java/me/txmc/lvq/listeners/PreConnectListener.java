package me.txmc.lvq.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
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
    private String serverFullMessage;
    private String commandsBlockedMessage;
    private String serverSwitchBlockedMessage;
    private boolean unconfiguredLogged;

    public PreConnectListener(Main plugin) {
        this.plugin = plugin;
        normalQueue = plugin.getNormalQueue();
        prioQueue = plugin.getPrioQueue();
        reloadConfig();
    }

    @Subscribe
    public void onPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer server = event.getOriginalServer();
        if (player.hasPermission("lvq.bypass")) return;
        RegisteredServer mainServer = plugin.getMainServer();
        RegisteredServer queueServer = plugin.getQueueServer();
        if (!isConfigured(mainServer, queueServer)) return;
        boolean inQueue = prioQueue.isInQueue(player.getUniqueId()) || normalQueue.isInQueue(player.getUniqueId());
        String targetName = server.getServerInfo().getName();
        String queueName = queueServer.getServerInfo().getName();
        if (inQueue) {
            boolean advancingToMain = targetName.equals(mainServer.getServerInfo().getName())
                    && plugin.getQueueWorker().isPendingTransfer(player.getUniqueId());
            if (!targetName.equals(queueName) && !advancingToMain) {
                sendMessage(player, serverSwitchBlockedMessage);
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
            }
            return;
        }
        if (targetName.equals(mainServer.getServerInfo().getName()) && (plugin.isAlwaysQueue() || !plugin.doesServerHaveSlot())) {
            if (isOnQueueServer(player, queueServer)) return;
            if (player.hasPermission("lvq.priority")) {
                prioQueue.addToQueue(player.getUniqueId());
            } else normalQueue.addToQueue(player.getUniqueId());
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(queueServer));
        }
    }

    @Subscribe
    public void onConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("lvq.bypass")) return;
        RegisteredServer queueServer = plugin.getQueueServer();
        if (queueServer == null) return;
        if (!event.getServer().getServerInfo().getName().equals(queueServer.getServerInfo().getName())) return;
        boolean inQueue = prioQueue.isInQueue(player.getUniqueId()) || normalQueue.isInQueue(player.getUniqueId());
        if (inQueue) sendMessage(player, serverFullMessage);
    }

    private boolean isConfigured(RegisteredServer mainServer, RegisteredServer queueServer) {
        if (mainServer != null && queueServer != null) return true;
        if (!unconfiguredLogged) {
            plugin.getLogger().atWarn().log("Queue handling disabled: ensure the main-server and queue-server names in config.yml match servers registered in velocity.toml");
            unconfiguredLogged = true;
        }
        return false;
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) return;
        if (player.hasPermission("lvq.bypass")) return;
        if (prioQueue.isInQueue(player.getUniqueId()) || normalQueue.isInQueue(player.getUniqueId())) {
            sendMessage(player, commandsBlockedMessage);
            event.setResult(CommandExecuteEvent.CommandResult.denied());
        }
    }

    private boolean isOnQueueServer(Player player, RegisteredServer queueServer) {
        return player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName()
                        .equals(queueServer.getServerInfo().getName()))
                .orElse(false);
    }

    @Override
    public void reloadConfig() {
        try {
            serverFullMessage = plugin.getConfig().node("messages", "server-full").getString();
            commandsBlockedMessage = plugin.getConfig().node("messages", "commands-blocked").getString("&cYou cannot use commands while in the queue");
            serverSwitchBlockedMessage = plugin.getConfig().node("messages", "server-switch-blocked").getString("&cYou can only be on the queue server while in the queue");
        } catch (Throwable t) {
            plugin.getLogger().atError().setCause(t).log("Failed to load config. Please check stacktrace for more info");
        }
    }
}