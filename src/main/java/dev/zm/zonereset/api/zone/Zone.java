package dev.zm.zonereset.api.zone;

import dev.zm.zonereset.api.reset.ResetStrategy;
import dev.zm.zonereset.zone.PlayerResetAction;
import dev.zm.zonereset.zone.ZoneBounds;
import dev.zm.zonereset.zone.ZoneStatus;

import java.util.UUID;

public interface Zone {

    String getId();

    UUID getWorldUID();

    ZoneBounds getBounds();

    ZoneStatus getStatus();

    boolean isEnabled();

    ResetStrategy getResetStrategy();

    void setResetStrategy(ResetStrategy strategy);

    long getResetIntervalTicks();

    void setResetIntervalTicks(long ticks);

    long getRemainingTicks();

    void resetTimer();

    boolean isInteractionBlocked();

    void setInteractionBlocked(boolean blocked);

    org.bukkit.Location getSpawn();

    void setSpawn(org.bukkit.Location spawn);

    PlayerResetAction getPlayerResetAction();

    void setPlayerResetAction(PlayerResetAction action);

    boolean isShowTitles();

    void setShowTitles(boolean show);

    boolean isShowMessages();

    void setShowMessages(boolean show);

    boolean contains(int x, int y, int z);

    boolean contains(long packedPos);

    boolean hasValidSnapshot();
}
