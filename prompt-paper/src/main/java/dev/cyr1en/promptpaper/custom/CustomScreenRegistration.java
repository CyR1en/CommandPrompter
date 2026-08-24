package dev.cyr1en.promptpaper.custom;

import dev.cyr1en.promptui.api.PromptScreenFactory;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

/**
 * Internal container representing an active screen provider registration.
 *
 * <p>Retains exact {@link Plugin} identity for registry unregistration matching while delegating
 * handle methods to a shared live {@link CustomScreenToken}.</p>
 */
public final class CustomScreenRegistration implements CustomScreenHandle {

    private final Plugin ownerPlugin;
    private final CustomScreenToken token;

    public CustomScreenRegistration(
            long providerId,
            String key,
            String ownerName,
            Plugin ownerPlugin,
            PromptScreenFactory factory
    ) {
        this.ownerPlugin = Objects.requireNonNull(ownerPlugin, "ownerPlugin");
        this.token = new CustomScreenToken(
                providerId,
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(ownerName, "ownerName"),
                Objects.requireNonNull(factory, "factory")
        );
    }

    public CustomScreenRegistration(Plugin ownerPlugin, CustomScreenToken token) {
        this.ownerPlugin = Objects.requireNonNull(ownerPlugin, "ownerPlugin");
        this.token = Objects.requireNonNull(token, "token");
    }

    /**
     * Retained internally for identity-based unregistration matching.
     */
    public Plugin ownerPlugin() {
        return ownerPlugin;
    }

    /**
     * Returns the shared live provider token.
     */
    public CustomScreenToken token() {
        return token;
    }

    @Override
    public long providerId() {
        return token.providerId();
    }

    @Override
    public String key() {
        return token.key();
    }

    @Override
    public String ownerName() {
        return token.ownerName();
    }

    @Override
    public ProviderState state() {
        return token.state();
    }

    @Override
    public boolean isActive() {
        return token.isActive();
    }

    @Deprecated
    @Override
    public PromptScreenFactory factory() {
        return token.factory();
    }

    @Override
    public boolean runIfActive(Runnable action) {
        return token.runIfActive(action);
    }

    @Override
    public <T> java.util.Optional<T> callIfActive(java.util.function.Supplier<T> action) {
        return token.callIfActive(action);
    }

    @Override
    public <T> java.util.Optional<T> invokeFactory(java.util.function.Function<PromptScreenFactory, T> action) {
        return token.invokeFactory(action);
    }

    /**
     * Atomically detaches the factory reference and transitions the shared token to {@link ProviderState#INACTIVE}.
     */
    public void teardown() {
        token.teardown();
    }
}
