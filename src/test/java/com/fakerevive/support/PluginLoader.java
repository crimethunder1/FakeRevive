package com.fakerevive.support;

import com.fakerevive.disguise.MojangIdentityFetcher;
import org.bukkit.plugin.PluginDescriptionFile;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginManagerMock;

import java.io.InputStream;

public final class PluginLoader {
    private PluginLoader() {
    }

    public static StubIdentityFakeRevivePlugin load() {
        return loadWithParameters();
    }

    public static StubIdentityFakeRevivePlugin load(MojangIdentityFetcher identityFetcher) {
        return loadWithParameters(identityFetcher);
    }

    private static StubIdentityFakeRevivePlugin loadWithParameters(Object... parameters) {
        PluginManagerMock pluginManager = MockBukkit.getMock().getPluginManager();
        StubIdentityFakeRevivePlugin plugin = (StubIdentityFakeRevivePlugin) pluginManager.loadPlugin(
                StubIdentityFakeRevivePlugin.class, readDescriptor(), parameters);
        pluginManager.addPermissions(plugin.getDescription().getPermissions());
        pluginManager.enablePlugin(plugin);
        return plugin;
    }

    private static PluginDescriptionFile readDescriptor() {
        try (InputStream stream = PluginLoader.class.getResourceAsStream("/plugin.yml")) {
            return new PluginDescriptionFile(stream);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
