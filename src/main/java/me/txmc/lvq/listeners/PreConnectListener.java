package me.txmc.lvq.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
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
    private String serverFullMessage;
    private String commandsBlockedMessage;
    private String serverSwitchBlockedMessage;

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
        RegisteredServer server = event.getOriginalServer();
        if (player.hasPermission("lvq.bypass")) return;
        boolean inQueue = prioQueue.isInQueue(player) || normalQueue.isInQueue(player);
        String targetName = server.getServerInfo().getName();
        String queueName = plugin.getQueueServer().getServerInfo().getName();
        if (inQueue) {
            boolean advancingToMain = targetName.equals(mainServer.getServerInfo().getName())
                    && plugin.getQueueWorker().isPendingTransfer(player);
            if (!targetName.equals(queueName) && !advancingToMain) {
                sendMessage(player, serverSwitchBlockedMessage);
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
            }
            return;
        }
        if (targetName.equals(mainServer.getServerInfo().getName()) && (plugin.isAlwaysQueue() || !plugin.doesServerHaveSlot())) {
            if (isOnQueueServer(player)) return;
            sendMessage(player, serverFullMessage);
            if (player.hasPermission("lvq.priority")) {
                prioQueue.addToQueue(player);
            } else normalQueue.addToQueue(player);
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(plugin.getQueueServer()));
        }
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) return;
        if (player.hasPermission("lvq.bypass")) return;
        if (prioQueue.isInQueue(player) || normalQueue.isInQueue(player)) {
            sendMessage(player, commandsBlockedMessage);
            event.setResult(CommandExecuteEvent.CommandResult.denied());
        }
    }

    private boolean isOnQueueServer(Player player) {
        return player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName()
                        .equals(plugin.getQueueServer().getServerInfo().getName()))
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