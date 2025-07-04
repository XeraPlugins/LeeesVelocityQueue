package me.txmc.lvq.workers;

import me.txmc.lvq.Main;
import me.txmc.lvq.PlayerQueue;
import me.txmc.lvq.Reloadable;

import static me.txmc.lvq.util.MessageUtil.sendMessage;

public class MessageWorker implements Runnable, Reloadable {
    private final Main plugin;
    private final PlayerQueue prioQueue;
    private final PlayerQueue normalQueue;
    private String queuePositionMessage;

    public MessageWorker(Main plugin) {
        this.plugin = plugin;
        prioQueue = plugin.getPrioQueue();
        normalQueue = plugin.getNormalQueue();
        reloadConfig();
    }

    @Override
    public void run() {
        prioQueue.getPlayersInQueue().forEach(p -> sendMessage(p, queuePositionMessage, prioQueue.getQueuePosition(p)));
        normalQueue.getPlayersInQueue().forEach(p -> sendMessage(p, queuePositionMessage, normalQueue.getQueuePosition(p)));
    }

    @Override
    public void reloadConfig() {
        queuePositionMessage = plugin.getConfig().node("messages", "queue-position").getString();
    }
}
