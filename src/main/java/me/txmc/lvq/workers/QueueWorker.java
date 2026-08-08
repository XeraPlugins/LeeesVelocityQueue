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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static me.txmc.lvq.util.MessageUtil.sendMessage;
import static me.txmc.lvq.util.MessageUtil.translateChars;

public class QueueWorker implements Runnable, Reloadable {
    private static final long RETRY_DELAY_MS = 3000L;
    private static final int MAX_RETRIES = 5;
    private final Main plugin;
    private final PlayerQueue normalQueue;
    private final PlayerQueue prioQueue;
    private final Set<UUID> pendingTransfers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastAttempt = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> retryCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinQueue = new ConcurrentHashMap<>();
    private long queueGraceMs;
    private String queueEndMessage;
    private String serverFullMessage;
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
            if (plugin.getMainServer() == null || plugin.getQueueServer() == null) return;
            processQueue(prioQueue, prioFooter);
            processQueue(normalQueue, normalFooter);
            resendPlayersInQueueServer();
        } catch (Throwable t) {
            plugin.getLogger().atWarn().setCause(t).log("Queue worker encountered an unexpected error, continuing next tick");
        }
    }

    public boolean isPendingTransfer(UUID uuid) {
        return pendingTransfers.contains(uuid);
    }

    public void requeuePlayer(Player player) {
        queuePlayer(player);
    }

    public void cleanupPlayer(UUID uuid) {
        pendingTransfers.remove(uuid);
        lastAttempt.remove(uuid);
        retryCount.remove(uuid);
        joinQueue.remove(uuid);
    }

    private void processQueue(PlayerQueue queue, TextComponent footer) {
        boolean serverHasSlot = plugin.doesServerHaveSlot();
        boolean advancedThisTick = false;
        for (UUID uuid : queue.getUUIDsInQueue()) {
            try {
                Player player = plugin.getServer().getPlayer(uuid).orElse(null);
                if (player == null || !player.isActive()) {
                    queue.removeFromQueue(uuid);
                    cleanupPlayer(uuid);
                    continue;
                }
                int queuePos = queue.getQueuePosition(uuid);
                player.sendPlayerListHeaderAndFooter(parseHeader(queuePos, queue), footer);
                if (isOnJoinGrace(uuid)) continue;
                if (queuePos != 1 || !serverHasSlot || pendingTransfers.contains(uuid) || isOnRetryCooldown(uuid)) continue;
                if (advancedThisTick) continue;
                advancedThisTick = true;
                serverHasSlot = advanceToMainServer(player, queue, serverHasSlot);
            } catch (Throwable t) {
                queue.removeFromQueue(uuid);
                cleanupPlayer(uuid);
            }
        }
        queue.rebuild();
    }

    private boolean isOnJoinGrace(UUID uuid) {
        joinQueue.computeIfAbsent(uuid, u -> System.currentTimeMillis());
        return System.currentTimeMillis() - joinQueue.get(uuid) < queueGraceMs;
    }

    private boolean isOnRetryCooldown(UUID uuid) {
        Long last = lastAttempt.get(uuid);
        return last != null && System.currentTimeMillis() - last < RETRY_DELAY_MS;
    }

    private void resendPlayersInQueueServer() {
        boolean serverHasSlot = plugin.doesServerHaveSlot();
        boolean advancedThisTick = false;
        for (Player player : plugin.getQueueServer().getPlayersConnected()) {
            try {
                if (player.hasPermission("lvq.bypass")) continue;
                UUID uuid = player.getUniqueId();
                if (pendingTransfers.contains(uuid)) continue;
                if (prioQueue.isInQueue(uuid) || normalQueue.isInQueue(uuid)) continue;
                if (player.getCurrentServer().map(con -> con.getServerInfo().getName().equals(plugin.getMainServer().getServerInfo().getName())).orElse(false)) continue;
                if (plugin.isAlwaysQueue() || !serverHasSlot) {
                    sendMessage(player, serverFullMessage);
                    queuePlayer(player);
                    continue;
                }
                if (isOnRetryCooldown(uuid)) continue;
                if (isOnJoinGrace(uuid)) continue;
                if (advancedThisTick) continue;
                advancedThisTick = true;
                serverHasSlot = advanceToMainServer(player, null, serverHasSlot);
            } catch (Throwable t) {
                cleanupPlayer(player.getUniqueId());
            }
        }
    }

    private boolean advanceToMainServer(Player player, PlayerQueue queue, boolean serverHasSlot) {
        UUID uuid = player.getUniqueId();
        if (!player.isActive()) {
            if (queue != null) queue.removeFromQueue(uuid);
            cleanupPlayer(uuid);
            return serverHasSlot;
        }
        lastAttempt.put(uuid, System.currentTimeMillis());
        sendMessage(player, queueEndMessage);
        pendingTransfers.add(uuid);
        ConnectionRequestBuilder.Result result;
        try {
            result = player.createConnectionRequest(plugin.getMainServer()).connect().join();
        } catch (Throwable t) {
            plugin.getLogger().atWarn().setCause(t).log("Failed to connect {} to the main server, keeping them in the queue", player.getUsername());
            result = null;
        } finally {
            pendingTransfers.remove(uuid);
        }
        if (result == null) {
            handleFailedTransfer(player, queue);
            return plugin.doesServerHaveSlot();
        }
        switch (result.getStatus()) {
            case SUCCESS:
            case ALREADY_CONNECTED:
                retryCount.remove(uuid);
                joinQueue.remove(uuid);
                if (queue != null) queue.removeFromQueue(uuid);
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
        UUID uuid = player.getUniqueId();
        if (!player.isActive()) {
            if (queue != null) queue.removeFromQueue(uuid);
            cleanupPlayer(uuid);
            return;
        }
        int attempts = retryCount.merge(uuid, 1, Integer::sum);
        if (attempts < MAX_RETRIES) return;
        retryCount.remove(uuid);
        if (!plugin.doesServerHaveSlot()) {
            plugin.getLogger().atInfo().log("{} could not be moved, main server unreachable, keeping {} at the front", player.getUsername(), player.getUsername());
            return;
        }
        joinQueue.remove(uuid);
        if (queue != null) {
            queue.removeFromQueue(uuid);
            queue.addToQueue(uuid);
        } else {
            queuePlayer(player);
        }
        plugin.getLogger().atWarn().log("Giving up after {} attempts, requeued {}", MAX_RETRIES, player.getUsername());
    }

    private void queuePlayer(Player player) {
        if (player.hasPermission("lvq.priority")) {
            prioQueue.addToQueue(player.getUniqueId());
        } else {
            normalQueue.addToQueue(player.getUniqueId());
        }
    }

    @Override
    public void reloadConfig() {
        try {
            queueGraceMs = plugin.getConfig().node("queue-grace-ms").getLong(5000L);
            queueEndMessage = plugin.getConfig().node("messages", "queue-end").getString();
            serverFullMessage = plugin.getConfig().node("messages", "server-full").getString();
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
