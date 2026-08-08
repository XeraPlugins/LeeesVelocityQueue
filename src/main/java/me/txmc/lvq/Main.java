package me.txmc.lvq;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.Getter;
import me.txmc.lvq.commands.LvqCommand;
import me.txmc.lvq.listeners.DisconnectListener;
import me.txmc.lvq.listeners.PreConnectListener;
import me.txmc.lvq.listeners.ProxyPingListener;
import me.txmc.lvq.workers.MessageWorker;
import me.txmc.lvq.workers.QueueWorker;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Plugin(id = "leeesvelocityqueue",
        name = "LeeesVelocityQueue",
        version = "1.1.1-RELEASE", description = "A 2b2t like queue plugin for Velocity",
        authors = {"254n_m", "Leeewith3Es"})
public class Main implements Reloadable{
    @Getter private final ProxyServer server;
    @Getter private final Logger logger;
    @Getter private PlayerQueue normalQueue;
    @Getter private PlayerQueue prioQueue;
    @Getter private CommentedConfigurationNode config;
    @Getter private RegisteredServer mainServer;
    @Getter private RegisteredServer queueServer;
    @Getter private int maxSlots;
    @Getter private boolean alwaysQueue;
    @Getter private QueueWorker queueWorker;
    @Getter private final List<Reloadable> reloadables;

    private ScheduledTask queueNotifyTask;
    private ScheduledTask messageTask;
    private File configFile;
    private int messageInterval;

    @Inject
    public Main(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;

        reloadables = new ArrayList<>();
        registerReloadable(this);
        try {
            loadConfig();
        } catch (Throwable t) {
            logger.atError().setCause(t).log("Failed to load config");
        }
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        reloadConfig();
        normalQueue = new PlayerQueue();
        prioQueue = new PlayerQueue();
        server.getEventManager().register(this, new PreConnectListener(this));
        server.getEventManager().register(this, new DisconnectListener(this));
        server.getEventManager().register(this, registerReloadable(new ProxyPingListener(this)));
        server.getCommandManager().register(server.getCommandManager().metaBuilder("lvq").plugin(this).build(), new LvqCommand(this));
        queueWorker = new QueueWorker(this);
        queueNotifyTask = server.getScheduler().buildTask(this, queueWorker).repeat(Duration.ofSeconds(1)).schedule();
        messageTask = server.getScheduler().buildTask(this, (Runnable) registerReloadable(new MessageWorker(this))).repeat(Duration.ofSeconds(messageInterval)).schedule();
    }
    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        queueNotifyTask.cancel();
        messageTask.cancel();
        reloadables.clear();
    }
    private Object registerReloadable(Reloadable r) {
        reloadables.add(r);
        return r;
    }
    public void loadConfig() throws Throwable {
        configFile = new File(getPluginDataFolder(), "config.yml");
        if (!configFile.exists()) {
            InputStream is = getClass().getClassLoader().getResourceAsStream("config.yml");
            if (is == null) throw new NullPointerException("Missing resource config.yml");
            Files.copy(is, configFile.toPath());
        }
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder().file(configFile).build();
        config = loader.load();
    }

    private File getPluginDataFolder() {
        File dataFolder = new File("plugins", getClass().getAnnotation(Plugin.class).id());
        if (!dataFolder.exists()) dataFolder.mkdirs();
        return dataFolder;
    }

    @Override
    public void reloadConfig() {
        String mainServerName = getConfig().node("main-server").getString();
        String queueServerName = getConfig().node("queue-server").getString();

        mainServer = getServer().getServer(mainServerName).orElse(null);
        queueServer = getServer().getServer(queueServerName).orElse(null);
        if (mainServer == null) getLogger().atError().log("{} is not a valid server, please ensure that the server name in the lvq configuration file matches the one in velocity.toml", mainServerName);
        if (queueServer == null) getLogger().atError().log("{} is not a valid server, please ensure that the server name in the lvq configuration file matches the one in velocity.toml", queueServerName);
        maxSlots = getConfig().node("main-server-slots").getInt();
        alwaysQueue = getConfig().node("always-queue").getBoolean(false);
        messageInterval = getConfig().node("messages", "interval").getInt();
    }
    public boolean doesServerHaveSlot() {
        try {
            ServerPing ping = getMainServer().ping().join();
            ServerPing.Players players = ping.getPlayers().orElse(null);
            if (players == null) return false;
            return players.getOnline() < maxSlots;
        } catch (Throwable t) {
            getLogger().atWarn().setCause(t).log("Failed to ping the main server to check for an available slot");
            return false;
        }
    }
}
