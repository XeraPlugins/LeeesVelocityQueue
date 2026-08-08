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
        boolean serverHasSlot = plugin.doesServerHaveSlot();
        for (Player player : prioQueue.getPlayersInQueue()) {
            int queuePos = prioQueue.getQueuePosition(player);
            player.sendPlayerListHeaderAndFooter(parseHeader(queuePos, prioQueue), prioFooter);
            if (serverHasSlot && queuePos == 1) {
                sendMessage(player, queueEndMessage);
                player.createConnectionRequest(plugin.getMainServer()).connect().join();
                prioQueue.removeFromQueue(player);
                serverHasSlot = plugin.doesServerHaveSlot();
                break;
            }
        }
        for (Player player : normalQueue.getPlayersInQueue()) {
            int queuePos = normalQueue.getQueuePosition(player);
            player.sendPlayerListHeaderAndFooter(parseHeader(queuePos, normalQueue), normalFooter);
            if (serverHasSlot && queuePos == 1) {
                sendMessage(player, queueEndMessage);
                player.createConnectionRequest(plugin.getMainServer()).connect().join();
                normalQueue.removeFromQueue(player);
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
        raw = raw.replace("%wait%", Utils.getFormattedInterval(Math.max(0L, ((posInQueue*5L - plugin.getMaxSlots()) * 60L) * 1000)));
        return translateChars(raw);
    }
    private TextComponent parseFooter(List<String> input) {
        String raw = String.join("\n", input);
        return translateChars(raw);
    }
}
