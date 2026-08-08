package me.txmc.lvq.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import me.txmc.lvq.Main;
import me.txmc.lvq.Reloadable;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static me.txmc.lvq.util.MessageUtil.legacyTranslate;

public class ProxyPingListener implements Reloadable {
    private final Main plugin;
    private List<String> playerList;
    private String versionName;
    private Integer confMaxPlayers;
    Map<String, Supplier<Integer>> mappings;
    Pattern pattern = Pattern.compile("%(.*?)%");

    public ProxyPingListener(Main plugin) {
        this.plugin = plugin;
        mappings = new HashMap<>();
        reloadConfig();
        mappings.put("%priority%", plugin.getPrioQueue()::queueLength);
        mappings.put("%regular%", plugin.getNormalQueue()::queueLength);
        mappings.put("%totalinqueue%", this::getQueueTotal);
        mappings.put("%maxplayers%", plugin::getMaxSlots);
    }

    @Subscribe(priority = 500)
    public void onProxyPing(ProxyPingEvent event) {
        ServerPing og = event.getPing();
        int playerCount = plugin.getServer().getPlayerCount();
        ServerPing.Version version = (versionName == null || versionName.isEmpty())
                ? og.getVersion()
                : new ServerPing.Version(og.getVersion().getProtocol(), legacyTranslate(versionName));
        int maxPlayers;
        if (confMaxPlayers == null) {
            maxPlayers = og.getPlayers().map(ServerPing.Players::getMax).orElse(playerCount + 1);
        } else {
            maxPlayers = (confMaxPlayers == -1) ? playerCount + 1 : confMaxPlayers;
        }
        ServerPing newPing = new ServerPing(
                version,
                new ServerPing.Players(playerCount, maxPlayers, genSampleList()),
                og.getDescriptionComponent(),
                og.getFavicon().orElse(null),
                og.getModinfo().orElse(null));

        event.setPing(newPing);
    }

    @Override
    public void reloadConfig() {
        try {
            playerList = plugin.getConfig().node("custom-query", "query").getList(String.class);
            versionName = plugin.getConfig().node("custom-query", "protocol-message").getString();
            confMaxPlayers = plugin.getConfig().node("custom-query", "max-players").get(Integer.class);
        } catch (Throwable t) {
            plugin.getLogger().atError().setCause(t).log("Failed to load config. Please check stacktrace for more info");
        }
    }
    public int getQueueTotal() {
        return plugin.getPrioQueue().queueLength() + plugin.getNormalQueue().queueLength();
    }
    private List<ServerPing.SamplePlayer> genSampleList() {
        List<ServerPing.SamplePlayer> buf = new ArrayList<>();
        for (String raw : playerList) {
            Matcher matcher = pattern.matcher(raw);
            while (matcher.find()) {
                String group = matcher.group().toLowerCase();
                raw = raw.replace(group, mappings.getOrDefault(group, () -> -Short.MAX_VALUE).get().toString());
            }
            ServerPing.SamplePlayer sp = new ServerPing.SamplePlayer(legacyTranslate(raw), UUID.randomUUID());
            buf.add(sp);
        }
        return buf;
    }
}
