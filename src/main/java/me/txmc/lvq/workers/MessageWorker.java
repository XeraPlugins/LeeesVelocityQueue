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
    private static final Duration TITLE_FADE_IN = Duration.ofMillis(200L);
    private static final Duration TITLE_STAY = Duration.ofSeconds(2L);
    private static final Duration TITLE_FADE_OUT = Duration.ofMillis(400L);
    private final Main plugin;
    private final PlayerQueue prioQueue;
    private final PlayerQueue normalQueue;
    private String queuePositionMessage;
    private boolean queuePositionChatEnabled;
    private String queuePositionHotbar;
    private String queuePositionTitle;

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
                    sendPositionMessages(p, prioQueue.getQueuePosition(uuid));
                }));
        normalQueue.getUUIDsInQueue().forEach(uuid ->
                plugin.getServer().getPlayer(uuid).ifPresent(p -> {
                    if (!plugin.getServerFullSent().contains(uuid)) return;
                    sendPositionMessages(p, normalQueue.getQueuePosition(uuid));
                }));
    }

    private void sendPositionMessages(Player player, int position) {
        if (queuePositionChatEnabled && queuePositionMessage != null && !queuePositionMessage.isEmpty()) {
            sendMessage(player, queuePositionMessage, position);
        }
        if (queuePositionHotbar != null && !queuePositionHotbar.isEmpty()) {
            player.sendActionBar(translateChars(String.format(queuePositionHotbar, position)));
        }
        if (queuePositionTitle != null && !queuePositionTitle.isEmpty()) {
            Title title = Title.title(
                    translateChars(String.format(queuePositionTitle, position)),
                    translateChars(""),
                    Title.Times.times(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT)
            );
            player.showTitle(title);
        }
    }

    @Override
    public void reloadConfig() {
        queuePositionMessage = plugin.getConfig().node("messages", "queue-position").getString();
        queuePositionChatEnabled = plugin.getConfig().node("messages", "queue-position-chat-enabled").getBoolean(true);
        queuePositionHotbar = plugin.getConfig().node("messages", "queue-position-hotbar").getString("");
        queuePositionTitle = plugin.getConfig().node("messages", "queue-position-title").getString("");
    }
}