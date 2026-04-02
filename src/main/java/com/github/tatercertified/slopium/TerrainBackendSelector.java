package com.github.tatercertified.slopium;

public final class TerrainBackendSelector {
    private static final TerrainBackendState STATE = detect();

    private TerrainBackendSelector() {
    }

    public static TerrainBackendState state() {
        return STATE;
    }

    private static TerrainBackendState detect() {
        String preferred = System.getProperty("slopium.terrain.backend", "auto").trim().toLowerCase();

        if ("cpu".equals(preferred)) {
            return new TerrainBackendState(TerrainBackend.CPU, true, "CPU backend forced by system property");
        }

        if ("opencl".equals(preferred)) {
            return detectOpenCl(true);
        }

        if ("vulkan".equals(preferred)) {
            return detectVulkan(true);
        }

        TerrainBackendState openCl = detectOpenCl(false);
        if (openCl.available()) {
            return openCl;
        }

        TerrainBackendState vulkan = detectVulkan(false);
        if (vulkan.available()) {
            return vulkan;
        }

        return cpuFallback(openCl.reason() + "; " + vulkan.reason());
    }

    private static TerrainBackendState detectOpenCl(boolean explicitRequest) {
        if (!Boolean.getBoolean("slopium.experimental_gpu_worldgen")) {
            return fallback(TerrainBackend.OPENCL, explicitRequest, "GPU worldgen disabled unless -Dslopium.experimental_gpu_worldgen=true");
        }

        if (!isClassPresent("org.lwjgl.opencl.CL10")) {
            return fallback(TerrainBackend.OPENCL, explicitRequest, "LWJGL OpenCL bindings not present on runtime classpath");
        }

        return fallback(TerrainBackend.OPENCL, explicitRequest, "OpenCL compute backend not initialized in this build");
    }

    private static TerrainBackendState detectVulkan(boolean explicitRequest) {
        if (!Boolean.getBoolean("slopium.experimental_gpu_worldgen")) {
            return fallback(TerrainBackend.VULKAN, explicitRequest, "GPU worldgen disabled unless -Dslopium.experimental_gpu_worldgen=true");
        }

        if (!isClassPresent("org.lwjgl.vulkan.VK10")) {
            return fallback(TerrainBackend.VULKAN, explicitRequest, "LWJGL Vulkan bindings not present on runtime classpath");
        }

        return fallback(TerrainBackend.VULKAN, explicitRequest, "Vulkan compute backend not initialized in this build");
    }

    private static TerrainBackendState fallback(TerrainBackend backend, boolean explicitRequest, String reason) {
        String prefix = explicitRequest ? backend.name() + " requested but unavailable" : backend.name() + " unavailable";
        return cpuFallback(prefix + ": " + reason);
    }

    private static TerrainBackendState cpuFallback(String reason) {
        return new TerrainBackendState(TerrainBackend.CPU, true, reason);
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, TerrainBackendSelector.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public enum TerrainBackend {
        CPU,
        OPENCL,
        VULKAN
    }

    public record TerrainBackendState(TerrainBackend backend, boolean available, String reason) {
    }
}
