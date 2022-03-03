package me.crypnotic.velocitystaffchat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent.ChatResult;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class VelocityStaffChat {

    @Inject
    private ProxyServer proxy;
    @Inject
    private Logger logger;
    @Inject
    @DataDirectory
    private Path configFolder;

    private String messageFormat;
    private String toggleFormat;
    private String enabledFormat;
    private String disabledFormat;
    Set<UUID> toggledPlayers;

    private static final MiniMessage MINIMESSAGE = MiniMessage.miniMessage();

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        Toml toml = loadConfig();
        if (toml == null) {
            logger.warn("Failed to load config.toml. Shutting down.");
            return;
        }

        this.messageFormat = toml.getString("message-format");
        this.toggleFormat = toml.getString("toggle-format");
        this.enabledFormat = toml.getString("enabled-format", "enabled");
        this.disabledFormat = toml.getString("disabled-format", "disabled");
        this.toggledPlayers = new HashSet<>();

        CommandMeta meta = proxy.getCommandManager().metaBuilder("staffchat")
            .aliases("sc")
            .build();

        proxy.getCommandManager().register(meta, new StaffChatCommand(this));
    }

    private Toml loadConfig() {
        if (!Files.exists(configFolder)) {
            try{
                Files.createDirectory(configFolder);
            } catch(IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        Path configPath = configFolder.resolve("config.toml");

        if (!Files.exists(configPath)) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.toml")) {
                Files.copy(input, configPath);
            } catch (IOException exception) {
                exception.printStackTrace();
                return null;
            }
        }

        try {
            return new Toml().read(Files.newInputStream(configPath));
        } catch (IOException e){
            e.printStackTrace();
            return null;
        }
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!toggledPlayers.contains(player.getUniqueId())) {
            return;
        }

        event.setResult(ChatResult.denied());
        sendStaffMessage(player, event.getMessage());
    }

    void sendToggleMessage(Player player, boolean state) {
        player.sendMessage(MINIMESSAGE.deserialize(toggleFormat, Placeholder.unparsed("state", state ? enabledFormat : disabledFormat)));
    }

    void sendStaffMessage(Player player, String message) {
        Component staffMessage = MiniMessage.miniMessage().deserialize(messageFormat, TagResolver.resolver(
            Placeholder.unparsed("player", player.getUsername()),
            Placeholder.unparsed("server", player.getCurrentServer().map(con -> con.getServerInfo().getName()).orElse("N/A")),
            Placeholder.unparsed("message", message)
        ));
        for(Player pl : proxy.getAllPlayers()){
            if(pl.hasPermission("staffchat")){
                pl.sendMessage(staffMessage);
            }
        }
    }
}
