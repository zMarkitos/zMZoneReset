package dev.zm.zonereset.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorParser {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})|#([A-Fa-f0-9]{6})");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('\u00A7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ColorParser() {
    }

    public static String parse(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String processed = parseHex(text);
        if (processed.contains("<") && processed.contains(">")) {
            try {
                Component comp = MINI_MESSAGE.deserialize(processed);
                String serialized = LEGACY_SERIALIZER.serialize(comp);
                return ChatColor.translateAlternateColorCodes('&', serialized);
            } catch (Exception ignored) {
            }
        }

        return ChatColor.translateAlternateColorCodes('&', processed);
    }

    private static String parseHex(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hex).toString());
        }
        return matcher.appendTail(buffer).toString();
    }
}
