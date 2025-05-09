package me.txmc.lvq;

import me.txmc.lvq.listeners.AuthenticateListener;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new AuthenticateListener(), this);

    }
    @Override
    public void onDisable() {

    }
}
