package dev.cyr1en.promptpaper.custom;

/**
 * Lifecycle state of a registered custom screen provider.
 */
public enum ProviderState {
    /**
     * Provider is actively registered and eligible to materialize screens.
     */
    ACTIVE,

    /**
     * Provider is in the process of unregistering / tearing down; factory references are detached.
     */
    TEARING_DOWN,

    /**
     * Provider is completely unregistered or disabled and no longer active.
     */
    INACTIVE
}
