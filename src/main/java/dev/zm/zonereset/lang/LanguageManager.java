package dev.zm.zonereset.lang;

import dev.zm.zonereset.core.logging.ZoneLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LanguageManager {

    private final JavaPlugin plugin;
    private final ZoneLogger logger;

    /**
     * Replaced atomically on reload. Never mutated in place: timers, async
     * tasks and command handling may read messages while /zmr reload runs.
     */
    private volatile Map<String, String> messages = Collections.emptyMap();
    private volatile Map<String, List<String>> listMessages = Collections.emptyMap();
    private volatile String prefix = "&8[&ezMZoneReset&8] &7";

    public LanguageManager(JavaPlugin plugin, ZoneLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void load(String langCode) {
        Map<String, String> newMessages = new HashMap<>();
        Map<String, List<String>> newListMessages = new HashMap<>();
        String newPrefix;

        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        saveDefaultLang("Lang_ES.yml");
        saveDefaultLang("Lang_EN.yml");

        File langFile = new File(langFolder, "Lang_" + langCode + ".yml");
        if (!langFile.exists()) {
            logger.warn("Language {} not found. Using Lang_ES.yml as fallback.", langCode);
            langFile = new File(langFolder, "Lang_ES.yml");
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);
        
        try (java.io.InputStream defLangStream = plugin.getResource("lang/" + langFile.getName())) {
            if (defLangStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(defLangStream, java.nio.charset.StandardCharsets.UTF_8));
                boolean changed = false;
                for (String key : defConfig.getKeys(true)) {
                    if (!config.contains(key)) {
                        config.set(key, defConfig.get(key));
                        changed = true;
                    }
                }
                if (changed) {
                    try {
                        config.save(langFile);
                        logger.info("Updated language file {} with missing keys.", langFile.getName());
                    } catch (Exception e) {
                        logger.error("Could not update language file {}", langFile.getName());
                    }
                }
            }
        } catch (java.io.IOException e) {
            logger.warn("Could not read bundled language file: {}", e.getMessage());
        }

        newPrefix = config.getString("prefix", "&8[&ezMZoneReset&8] &7");

        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                if (config.isList(key)) {
                    newListMessages.put(key, config.getStringList(key));
                } else {
                    newMessages.put(key, config.getString(key));
                }
            }
        }

        if (newMessages.isEmpty()) {
            logger.warn("Language file {} contained no messages; keeping the previous language.", langFile.getName());
            return;
        }

        // Single atomic publication point: readers never observe a half-loaded file.
        this.prefix = newPrefix;
        this.listMessages = Collections.unmodifiableMap(newListMessages);
        this.messages = Collections.unmodifiableMap(newMessages);

        logger.info("Language {} loaded successfully.", langFile.getName());
    }

    private void saveDefaultLang(String fileName) {
        File file = new File(plugin.getDataFolder(), "lang/" + fileName);
        if (!file.exists()) {
            plugin.saveResource("lang/" + fileName, false);
        }
    }

    public String getMessage(String key, String... placeholders) {
        String message = messages.getOrDefault(key, "&cError: Message '" + key + "' not found.");
        
        message = message.replace("{prefix}", prefix);
        
        if (placeholders.length % 2 == 0) {
            for (int i = 0; i < placeholders.length; i += 2) {
                String pKey = "{" + placeholders[i] + "}";
                String pVal = placeholders[i + 1] != null ? placeholders[i + 1] : "null";
                message = message.replace(pKey, pVal);
            }
        }

        return ColorParser.parse(message);
    }
    
    public List<String> getMessageList(String key, String... placeholders) {
        List<String> msgList = listMessages.get(key);
        if (msgList == null) {
            return List.of(ColorParser.parse("&cMissing list key: " + key));
        }
        
        List<String> parsedList = new ArrayList<>();
        for (String msg : msgList) {
            msg = msg.replace("{prefix}", prefix);
            if (placeholders.length % 2 == 0) {
                for (int i = 0; i < placeholders.length; i += 2) {
                    String pKey = "{" + placeholders[i] + "}";
                    String pVal = placeholders[i + 1] != null ? placeholders[i + 1] : "null";
                    msg = msg.replace(pKey, pVal);
                }
            }
            parsedList.add(ColorParser.parse(msg));
        }
        return parsedList;
    }
    
    public String getRawMessage(String key) {
        return messages.get(key);
    }
}