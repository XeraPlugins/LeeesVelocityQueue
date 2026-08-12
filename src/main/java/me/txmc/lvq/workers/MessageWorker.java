package me.txmc.lvq.workers;

import com.velocitypowered.api.proxy.Player;
import me.txmc.lvq.Main;
import me.txmc.lvq.PlayerQueue;
import me.txmc.lvq.Reloadable;
import net.kyori.adventure.title.Title;

import java.time.Duration;

import static me.txmc.lvq.util.MessageUtil.sendMessage;
import static me.txmc.lvq.util.MessageUtil.translateChars;

public class MessageWorker implements Runnable, Reloadable {
    private static final Duration TITLE_FADE_IN = Duration.ZERO;
    private static final Duration TITLE_STAY = Duration.ofMillis(5000L);
    private static final Duration TITLE_FADE_OUT = Duration.ZERO;
    private final Main plugin;
    private final PlayerQueue prioQueue;
    private final PlayerQueue normalQueue;
    private String queuePositionMessage;
    private boolean queuePositionChatEnabled;
    private String queuePositionHotbar;
    private boolean queuePositionHotbarEnabled;
    private String queuePositionTitle;
    private boolean queuePositionTitleEnabled;

    public MessageWorker(Main plugin) {
        this.plugin = plugin;
        prioQueue = plugin.getPrioQueue();
        normalQueue = plugin.getNormalQueue();
        reloadConfig();
    }

    @Override
    public void run() {
        prioQueue.getUUIDsInQueue().forEach(uuid ->
                plugin.getServer().getPlayer(uuid).ifPresent(p -> {
                    if (!plugin.getServerFullSent().contains(uuid)) return;
                    if (queuePositionChatEnabled && queuePositionMessage != null && !queuePositionMessage.isEmpty()) {
                        sendMessage(p, queuePositionMessage, prioQueue.getQueuePosition(uuid));
                    }
                }));
        normalQueue.getUUIDsInQueue().forEach(uuid ->
                plugin.getServer().getPlayer(uuid).ifPresent(p -> {
                    if (!plugin.getServerFullSent().contains(uuid)) return;
                    if (queuePositionChatEnabled && queuePositionMessage != null && !queuePositionMessage.isEmpty()) {
                        sendMessage(p, queuePositionMessage, normalQueue.getQueuePosition(uuid));
                    }
                }));
    }

    public void showPosition(Player player) {
        int position = -1;
        if (prioQueue.isInQueue(player.getUniqueId())) {
            position = prioQueue.getQueuePosition(player.getUniqueId());
        } else if (normalQueue.isInQueue(player.getUniqueId())) {
            position = normalQueue.getQueuePosition(player.getUniqueId());
        }
        if (position < 0) return;
        if (queuePositionHotbarEnabled && queuePositionHotbar != null && !queuePositionHotbar.isEmpty()) {
            player.sendActionBar(translateChars(String.format(queuePositionHotbar, position)));
        }
        if (queuePositionTitleEnabled && queuePositionTitle != null && !queuePositionTitle.isEmpty()) {
            Title title = Title.title(
                    translateChars(String.format(queuePositionTitle, position)),
                    translateChars(""),
                    Title.Times.times(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT)
            );
            player.showTitle(title);
        }
    }

    public void refreshAll() {
        prioQueue.getUUIDsInQueue().forEach(uuid ->
                plugin.getServer().getPlayer(uuid).ifPresent(p -> {
                    plugin.getQueueWorker().updateTablist(p);
                    showPosition(p);
                }));
        normalQueue.getUUIDsInQueue().forEach(uuid ->
                plugin.getServer().getPlayer(uuid).ifPresent(p -> {
                    plugin.getQueueWorker().updateTablist(p);
                    showPosition(p);
                }));
    }

    @Override
    public void reloadConfig() {
        queuePositionMessage = plugin.getConfig().node("messages", "queue-position").getString();
        queuePositionChatEnabled = plugin.getConfig().node("messages", "queue-position-chat-enabled").getBoolean(true);
        queuePositionHotbar = plugin.getConfig().node("messages", "queue-position-hotbar").getString("");
        queuePositionHotbarEnabled = plugin.getConfig().node("messages", "queue-position-hotbar-enabled").getBoolean(true);
        queuePositionTitle = plugin.getConfig().node("messages", "queue-position-title").getString("");
        queuePositionTitleEnabled = plugin.getConfig().node("messages", "queue-position-title-enabled").getBoolean(false);
    }
}