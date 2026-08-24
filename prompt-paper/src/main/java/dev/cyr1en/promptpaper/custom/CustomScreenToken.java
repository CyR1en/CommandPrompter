package dev.cyr1en.promptpaper.custom;

import dev.cyr1en.promptui.api.PromptScreenFactory;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared live token representing an active custom screen provider lease.
 *
 * <p>Contains NO {@link org.bukkit.plugin.Plugin} reference. All resolvers, adapters,
 * and active handles hold a handle backed by this live token. All provider-owned method invocations
 * and lifecycle teardown share a single per-token lifecycle gate.</p>
 */
public final class CustomScreenToken implements CustomScreenHandle {

    private final Object gate = new Object();
    private final long providerId;
    private final String key;
    private final String ownerName;
    private ProviderState state;
    private PromptScreenFactory factory;

    public CustomScreenToken(
            long providerId,
            String key,
            String ownerName,
            PromptScreenFactory factory
    ) {
        this.providerId = providerId;
        this.key = Objects.requireNonNull(key, "key");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
        this.state = ProviderState.ACTIVE;
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    @Override
    public long providerId() {
        return providerId;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String ownerName() {
        return ownerName;
    }

    @Override
    public ProviderState state() {
        synchronized (gate) {
            return state;
        }
    }

    @Override
    public boolean isActive() {
        synchronized (gate) {
            return state == ProviderState.ACTIVE && factory != null;
        }
    }

    @Deprecated
    @Override
    public PromptScreenFactory factory() {
        synchronized (gate) {
            return state == ProviderState.ACTIVE ? factory : null;
        }
    }

    @Override
    public boolean runIfActive(Runnable action) {
        synchronized (gate) {
            if (state != ProviderState.ACTIVE) {
                return false;
            }
            action.run();
            return true;
        }
    }

    @Override
    public <T> Optional<T> callIfActive(Supplier<T> action) {
        synchronized (gate) {
            if (state != ProviderState.ACTIVE) {
                return Optional.empty();
            }
            return Optional.ofNullable(action.get());
        }
    }

    @Override
    public <T> Optional<T> invokeFactory(Function<PromptScreenFactory, T> action) {
        synchronized (gate) {
            if (state != ProviderState.ACTIVE || factory == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(action.apply(factory));
        }
    }

    /**
     * Atomically transitions this token to TEARING_DOWN, clears the factory reference,
     * and transitions to INACTIVE under the shared invocation gate.
     */
    public void teardown() {
        synchronized (gate) {
            this.state = ProviderState.TEARING_DOWN;
            this.factory = null;
            this.state = ProviderState.INACTIVE;
        }
    }
}
