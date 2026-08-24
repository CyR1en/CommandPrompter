package dev.cyr1en.promptpaper.custom;

import dev.cyr1en.promptui.api.CommandPrompterAPI;
import dev.cyr1en.promptui.api.PromptScreenFactory;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

/**
 * Public service implementation of {@link CommandPrompterAPI} registered in Bukkit's ServicesManager.
 *
 * <p>Delegates registration to {@link CustomScreenRegistry} and unregistration to
 * {@link ProviderLifecycleCoordinator}, ensuring that explicit unregistration executes the exact
 * same lifecycle cleanup, session cancellation, platform close, and audit logging as provider disable events.</p>
 */
public final class CommandPrompterAPIFacade implements CommandPrompterAPI {

    private final CustomScreenRegistry registry;
    private final ProviderLifecycleCoordinator coordinator;

    public CommandPrompterAPIFacade(
            CustomScreenRegistry registry,
            ProviderLifecycleCoordinator coordinator
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public void registerScreen(Plugin plugin, String key, PromptScreenFactory factory) {
        registry.registerScreen(plugin, key, factory);
    }

    @Override
    public void unregisterScreens(Plugin plugin) {
        Objects.requireNonNull(plugin, "Owner plugin must not be null");
        coordinator.onProviderDisable(plugin);
    }
}
