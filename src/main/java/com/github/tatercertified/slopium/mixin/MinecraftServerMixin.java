package com.github.tatercertified.slopium.mixin;

import net.minecraft.CrashReport;
import net.minecraft.SystemReport;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.nio.file.Path;
import java.util.Optional;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Unique private static final int SLOPIUM$TARGET_PRECOMPUTED_TICKS = 6;
    @Unique private static final int SLOPIUM$MAX_PRECOMPUTED_TICKS_PER_LOOP = 8;
    @Unique private static final float SLOPIUM$PRECOMPUTE_MSPT_THRESHOLD = 50.0F;
    @Unique private static final long SLOPIUM$PRECOMPUTE_HEADROOM_MARGIN_NANOS = 1_000_000L;

    @Shadow @Final private static Logger LOGGER;
    @Shadow @Final private static long OVERLOADED_THRESHOLD_NANOS;
    @Shadow @Final private static long OVERLOADED_WARNING_INTERVAL_NANOS;

    @Shadow private volatile boolean running;
    @Shadow private boolean stopped;
    @Shadow private int tickCount;
    @Shadow private long lastOverloadWarningNanos;
    @Shadow private long nextTickTimeNanos;
    @Shadow private boolean mayHaveDelayedTasks;
    @Shadow private long delayedTasksMaxNextTickTimeNanos;
    @Shadow private boolean debugCommandProfilerDelayStart;
    @Shadow private volatile boolean isReady;
    @Shadow private float smoothedTickTimeMillis;
    @Shadow private ServerStatus status;
    @Shadow private ServerStatus.Favicon statusIcon;
    @Shadow @Final private ServerTickRateManager tickRateManager;

    @Shadow protected abstract boolean initServer();
    @Shadow private Optional<ServerStatus.Favicon> loadStatusIcon() { throw new AssertionError(); }
    @Shadow private ServerStatus buildServerStatus() { throw new AssertionError(); }
    @Shadow public abstract boolean isPaused();
    @Shadow protected abstract void processPacketsAndTick(boolean sprinting);
    @Shadow private ProfilerFiller createProfiler() { throw new AssertionError(); }
    @Shadow protected abstract void waitUntilNextTick();
    @Shadow private void startMeasuringTaskExecutionTime() { throw new AssertionError(); }
    @Shadow private void finishMeasuringTaskExecutionTime() { throw new AssertionError(); }
    @Shadow private void logFullTickTime() { throw new AssertionError(); }
    @Shadow private void endMetricsRecordingTick() { throw new AssertionError(); }
    @Shadow protected abstract void stopServer();
    @Shadow protected abstract void onServerExit();
    @Shadow protected abstract void onServerCrash(CrashReport crashReport);
    @Shadow private static CrashReport constructOrExtractCrashReport(Throwable throwable) { throw new AssertionError(); }
    @Shadow protected abstract SystemReport fillSystemReport(SystemReport report);
    @Shadow public abstract Path getServerDirectory();
    @Shadow public abstract void startTimeProfiler();

    @Unique
    private void slopium$shutdownServer() {
        this.stopped = true;
        try {
            this.stopServer();
        } catch (Throwable throwable) {
            LOGGER.error("Exception stopping the server", throwable);
        } finally {
            this.onServerExit();
        }
    }

    @Unique
    private int slopium$getDesiredBufferedTicks(long nanosPerTick) {
        if (nanosPerTick <= 0L || this.smoothedTickTimeMillis >= SLOPIUM$PRECOMPUTE_MSPT_THRESHOLD) {
            return 0;
        }

        final long now = Util.getNanos();
        final long headroomNanos = this.nextTickTimeNanos - now;
        final long usableHeadroomNanos = headroomNanos - SLOPIUM$PRECOMPUTE_HEADROOM_MARGIN_NANOS;
        if (usableHeadroomNanos < nanosPerTick) {
            return 0;
        }

        return Math.min(SLOPIUM$TARGET_PRECOMPUTED_TICKS, (int) (usableHeadroomNanos / nanosPerTick));
    }

    /**
     * @author tatercertified
     * @reason Rewrites the main server loop to reduce repeated field walks and duplicate shutdown handling.
     */
    @Overwrite
    protected void runServer() {
        try {
            if (!this.initServer()) {
                throw new IllegalStateException("Failed to initialize server");
            }

            this.nextTickTimeNanos = Util.getNanos();
            this.statusIcon = this.loadStatusIcon().orElse(null);
            this.status = this.buildServerStatus();

            while (this.running) {
                final ServerTickRateManager tickRateManager = this.tickRateManager;
                final boolean paused = this.isPaused();
                final boolean sprinting = !paused && tickRateManager.isSprinting() && tickRateManager.checkShouldSprintThisTick();

                final long nanosPerTick;
                if (sprinting) {
                    nanosPerTick = 0L;
                    final long now = Util.getNanos();
                    this.nextTickTimeNanos = now;
                    this.lastOverloadWarningNanos = now;
                } else {
                    nanosPerTick = tickRateManager.nanosecondsPerTick();
                    final long now = Util.getNanos();
                    final long behindNanos = now - this.nextTickTimeNanos;

                    if (behindNanos > OVERLOADED_THRESHOLD_NANOS + 20L * nanosPerTick
                            && this.nextTickTimeNanos - this.lastOverloadWarningNanos >= OVERLOADED_WARNING_INTERVAL_NANOS + 100L * nanosPerTick) {
                        final long behindTicks = behindNanos / nanosPerTick;
                        LOGGER.warn(
                                "Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind",
                                behindNanos / TimeUtil.NANOSECONDS_PER_MILLISECOND,
                                behindTicks
                        );
                        this.nextTickTimeNanos += behindTicks * nanosPerTick;
                        this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                    }
                }

                final boolean zeroTickTime = nanosPerTick == 0L;

                if (this.debugCommandProfilerDelayStart) {
                    this.debugCommandProfilerDelayStart = false;
                    this.startTimeProfiler();
                }

                this.nextTickTimeNanos += nanosPerTick;

                try {
                    final ProfilerFiller profiler = this.createProfiler();
                    try (Profiler.Scope ignored = Profiler.use(profiler)) {
                        this.processPacketsAndTick(zeroTickTime);
                        if (!zeroTickTime) {
                            int precomputedTicks = 0;
                            int desiredBufferedTicks = this.slopium$getDesiredBufferedTicks(nanosPerTick);
                            while (this.running
                                    && precomputedTicks < desiredBufferedTicks
                                    && precomputedTicks < SLOPIUM$MAX_PRECOMPUTED_TICKS_PER_LOOP) {
                                this.nextTickTimeNanos += nanosPerTick;
                                this.processPacketsAndTick(false);
                                precomputedTicks++;
                            }
                        }

                        profiler.push("nextTickWait");
                        this.mayHaveDelayedTasks = true;
                        this.delayedTasksMaxNextTickTimeNanos = Math.max(Util.getNanos() + nanosPerTick, this.nextTickTimeNanos);
                        this.startMeasuringTaskExecutionTime();
                        try {
                            this.waitUntilNextTick();
                        } finally {
                            this.finishMeasuringTaskExecutionTime();
                        }

                        if (zeroTickTime) {
                            tickRateManager.endTickWork();
                        }

                        profiler.pop();
                        this.logFullTickTime();
                    } finally {
                        this.endMetricsRecordingTick();
                    }
                } finally {
                    this.isReady = true;
                    JvmProfiler.INSTANCE.onServerTick(this.smoothedTickTimeMillis);
                }
            }

            this.slopium$shutdownServer();
        } catch (Throwable throwable) {
            LOGGER.error("Encountered an unexpected exception", throwable);
            final CrashReport crashReport = constructOrExtractCrashReport(throwable);
            this.fillSystemReport(crashReport.getSystemReport());

            final Path crashReportPath = this.getServerDirectory()
                    .resolve("crash-reports")
                    .resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");

            if (crashReport.saveToFile(crashReportPath, net.minecraft.ReportType.CRASH)) {
                LOGGER.error("This crash report has been saved to: {}", crashReportPath.toAbsolutePath());
            } else {
                LOGGER.error("We were unable to save this crash report to disk.");
            }

            this.onServerCrash(crashReport);
            this.slopium$shutdownServer();
        }
    }
}
