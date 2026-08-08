package me.txmc.lvq.workers;

import com.velocitypowered.api.proxy.Player;
import me.txmc.lvq.Main;
import me.txmc.lvq.PlayerQueue;
import me.txmc.lvq.Reloadable;
import me.txmc.lvq.util.Utils;
import net.kyori.adventure.text.TextComponent;

import java.util.List;

import static me.txmc.lvq.util.MessageUtil.sendMessage;
import static me.txmc.lvq.util.MessageUtil.translateChars;

public class QueueWorker implements Runnable, Reloadable {
    private final Main plugin;
    private final PlayerQueue normalQueue;
    private final PlayerQueue prioQueue;
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
        } catch (Throwable t) {
            plugin.getLogger().atWarn().setCause(t).log("Queue worker encountered an unexpected error, continuing next tick");
        }
    }

    private void processQueue(PlayerQueue queue, TextComponent footer) {
        boolean serverHasSlot = plugin.doesServerHaveSlot();
        for (Player player : queue.getPlayersInQueue()) {
            int queuePos = queue.getQueuePosition(player);
            player.sendPlayerListHeaderAndFooter(parseHeader(queuePos, queue), footer);
            if (queuePos == 1) {
                if (!serverHasSlot) continue;
                sendMessage(player, queueEndMessage);
                queue.removeFromQueue(player);
                try {
                    player.createConnectionRequest(plugin.getMainServer()).connect().join();
                } catch (Throwable t) {
                    plugin.getLogger().atWarn().setCause(t).log("Failed to connect {} to the main server, requeueing them", player.getUsername());
                    queue.addToQueue(player);
                }
                serverHasSlot = plugin.doesServerHaveSlot();
                break;
            }
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
