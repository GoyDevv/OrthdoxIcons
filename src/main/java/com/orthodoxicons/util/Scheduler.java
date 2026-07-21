package com.orthodoxicons.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Thin abstraction over the Bukkit scheduler that bridges Bukkit tasks with
 * {@link CompletableFuture}s so service code can chain sync/async work cleanly
 * without ever blocking the main thread.
 */
public final class Scheduler {

    private final Plugin plugin;

    /**
     * @param plugin owning plugin
     */
    public Scheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs the given supplier on the main server thread and completes the
     * returned future with its result. Safe to call from any thread.
     *
     * @param supplier work to run on the main thread
     * @param <T>      result type
     * @return a future completed on the main thread
     */
    public <T> CompletableFuture<T> sync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (Bukkit.isPrimaryThread()) {
            completeSafely(future, supplier);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> completeSafely(future, supplier));
        }
        return future;
    }

    /**
     * Runs a main-thread action with no result.
     *
     * @param action work to run
     * @return a future completed when the action finishes
     */
    public CompletableFuture<Void> runSync(Runnable action) {
        return sync(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Runs the given supplier on an async pool thread.
     *
     * @param supplier work to run off the main thread
     * @param <T>      result type
     * @return a future completed asynchronously
     */
    public <T> CompletableFuture<T> async(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> completeSafely(future, supplier));
        return future;
    }

    /**
     * Schedules a repeating asynchronous task.
     *
     * @param action     the action to run
     * @param delayTicks initial delay in ticks
     * @param periodTicks period in ticks
     * @return the scheduled task
     */
    public BukkitTask repeatingAsync(Runnable action, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, action, delayTicks, periodTicks);
    }

    private static <T> void completeSafely(CompletableFuture<T> future, Supplier<T> supplier) {
        try {
            future.complete(supplier.get());
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }
}
