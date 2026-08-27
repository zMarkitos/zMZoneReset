package dev.zm.zonereset.lang;

import dev.zm.zonereset.core.logging.ZoneLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public final class LanguageManager {

    private final JavaPlugin plugin;
    private final ZoneLogger logger;
    
    private final Map<String, String> messages = new HashMap<>();
    private final Map<String, List<String>> listMessages = new HashMap<>();
    private String prefix = "&8[&ezMZoneReset&8] &7";

    public LanguageManager(JavaPlugin plugin, ZoneLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void load(String langCode) {
        messages.clear();
        listMessages.clear();
        
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
        
        this.prefix = config.getString("prefix", "&8[&ezMZoneReset&8] &7");
        
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                if (config.isList(key)) {
                    listMessages.put(key, config.getStringList(key));
                } else {
                    messages.put(key, config.getString(key));
                }
            }
        }
        
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