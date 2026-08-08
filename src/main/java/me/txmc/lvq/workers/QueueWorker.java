package me.txmc.lvq.workers;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import me.txmc.lvq.Main;
import me.txmc.lvq.PlayerQueue;
import me.txmc.lvq.Reloadable;
import me.txmc.lvq.util.Utils;
import net.kyori.adventure.text.TextComponent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static me.txmc.lvq.util.MessageUtil.sendMessage;
import static me.txmc.lvq.util.MessageUtil.translateChars;

public class QueueWorker implements Runnable, Reloadable {
    private static final long RETRY_DELAY_MS = 3000L;
    private static final int MAX_RETRIES = 5;
    private final Main plugin;
    private final PlayerQueue normalQueue;
    private final PlayerQueue prioQueue;
    private final Set<Player> pendingTransfers = ConcurrentHashMap.newKeySet();
    private final Map<Player, Long> lastAttempt = new ConcurrentHashMap<>();
    private final Map<Player, Integer> retryCount = new ConcurrentHashMap<>();
    private String queueEndMessage;
    private List<String> tabHeader;
    private TextComponent prioFooter;
    private TextComponent normalFooter;

    public QueueWorker(Main plugin) {
        this.plugin = plugin;
        prioQueue = plugin.getPrioQueue();
        normalQueue = plugin.getNormalQueue();
        reloadConfig();
    }

    @Override
    public void run() {
        try {
            processQueue(prioQueue, prioFooter);
            processQueue(normalQueue, normalFooter);
            resendPlayersInQueueServer();
        } catch (Throwable t) {
            plugin.getLogger().atWarn().setCause(t).log("Queue worker encountered an unexpected error, continuing next tick");
        }
    }

    public boolean isPendingTransfer(Player player) {
        return pendingTransfers.contains(player);
    }

    private void processQueue(PlayerQueue queue, TextComponent footer) {
        boolean serverHasSlot = plugin.doesServerHaveSlot();
        for (Player player : queue.getPlayersInQueue()) {
            int queuePos = queue.getQueuePosition(player);
            player.sendPlayerListHeaderAndFooter(parseHeader(queuePos, queue), footer);
            if (queuePos != 1 || !serverHasSlot || pendingTransfers.contains(player) || isOnRetryCooldown(player)) continue;
            serverHasSlot = advanceToMainServer(player, queue, serverHasSlot);
        }
    }

    private boolean isOnRetryCooldown(Player player) {
        Long last = lastAttempt.get(player);
        return last != null && System.currentTimeMillis() - last < RETRY_DELAY_MS;
    }

    private void resendPlayersInQueueServer() {
        boolean serverHasSlot = plugin.doesServerHaveSlot();
        for (Player player : plugin.getQueueServer().getPlayersConnected()) {
            if (player.hasPermission("lvq.bypass")) continue;
            if (pendingTransfers.contains(player)) continue;
            if (prioQueue.isInQueue(player) || normalQueue.isInQueue(player)) continue;
            if (player.getCurrentServer().map(con -> con.getServerInfo().getName().equals(plugin.getMainServer().getServerInfo().getName())).orElse(false)) continue;
            if (!serverHasSlot) {
                queuePlayer(player);
                continue;
            }
            if (isOnRetryCooldown(player)) continue;
            serverHasSlot = advanceToMainServer(player, null, serverHasSlot);
        }
    }

    private boolean advanceToMainServer(Player player, PlayerQueue queue, boolean serverHasSlot) {
        lastAttempt.put(player, System.currentTimeMillis());
        sendMessage(player, queueEndMessage);
        pendingTransfers.add(player);
        ConnectionRequestBuilder.Result result;
        try {
            result = player.createConnectionRequest(plugin.getMainServer()).connect().join();
        } catch (Throwable t) {
            plugin.getLogger().atWarn().setCause(t).log("Failed to connect {} to the main server, keeping them in the queue", player.getUsername());
            result = null;
        } finally {
            pendingTransfers.remove(player);
        }
        if (result == null) {
            handleFailedTransfer(player, queue);
            return plugin.doesServerHaveSlot();
        }
        switch (result.getStatus()) {
            case SUCCESS:
            case ALREADY_CONNECTED:
                retryCount.remove(player);
                if (queue != null) queue.removeFromQueue(player);
                break;
            case CONNECTION_IN_PROGRESS:
            case CONNECTION_CANCELLED:
                plugin.getLogger().atInfo().log("{} transfer was cancelled, will retry shortly", player.getUsername());
                handleFailedTransfer(player, queue);
                break;
            case SERVER_DISCONNECTED:
            default:
                plugin.getLogger().atWarn().log("Connection to the main server failed with status {}, keeping {} in the queue", result.getStatus(), player.getUsername());
                handleFailedTransfer(player, queue);
                break;
        }
        return plugin.doesServerHaveSlot();
    }

    private void handleFailedTransfer(Player player, PlayerQueue queue) {
        int attempts = retryCount.merge(player, 1, Integer::sum);
        if (attempts >= MAX_RETRIES) {
            retryCount.remove(player);
            if (queue != null) {
                queue.removeFromQueue(player);
                queue.addToQueue(player);
            } else {
                queuePlayer(player);
            }
            plugin.getLogger().atWarn().log("Giving up after {} attempts, requeued {}", MAX_RETRIES, player.getUsername());
        }
    }

    private void queuePlayer(Player player) {
        if (player.hasPermission("lvq.priority")) {
            prioQueue.addToQueue(player);
        } else {
            normalQueue.addToQueue(player);
        }
    }

    @Override
    public void reloadConfig() {
        try {
            queueEndMessage = plugin.getConfig().node("messages", "queue-end").getString();
            tabHeader = plugin.getConfig().node("tablist", "header").getList(String.class);
            prioFooter = parseFooter(plugin.getConfig().node("tablist", "priority-queue-footer").getList(String.class));
            normalFooter = parseFooter(plugin.getConfig().node("tablist", "normal-queue-footer").getList(String.class));
        } catch (Throwable t) {
            plugin.getLogger().atError().setCause(t).log("Failed to load config. Please check stacktrace for more info");
        }
    }

    private TextComponent parseHeader(int posInQueue, PlayerQueue queue) {
        String raw = String.join("\n", tabHeader);
        raw = raw.replace("%position%", String.valueOf(posInQueue));
        raw = raw.replace("%wait%", Utils.getFormattedInterval(posInQueue * 5L * 60L * 1000L));
        return translateChars(raw);
    }
    private TextComponent parseFooter(List<String> input) {
        String raw = String.join("\n", input);
        return translateChars(raw);
    }
}
