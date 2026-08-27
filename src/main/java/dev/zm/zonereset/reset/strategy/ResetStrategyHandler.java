package dev.zm.zonereset.reset.strategy;

import dev.zm.zonereset.reset.ResetJobImpl;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

public interface ResetStrategyHandler {

    CompletableFuture<Object> prepareAsync(ResetJobImpl job);

    CompletableFuture<Void> executeReset(ResetJobImpl job, World world, Object context);

    CompletableFuture<Void> verifyAndCleanup(ResetJobImpl job, World world, Object context);
}
