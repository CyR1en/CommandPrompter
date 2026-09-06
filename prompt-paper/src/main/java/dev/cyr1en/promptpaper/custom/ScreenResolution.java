package dev.cyr1en.promptpaper.custom;

import dev.cyr1en.promptpaper.config.ScreenType;
import java.util.Objects;

/** Typed outcome of resolving a prompt screen key. */
public sealed interface ScreenResolution {

  /** Resolves to a built-in or configured {@link ScreenType}. */
  record BuiltIn(ScreenType screenType) implements ScreenResolution {
    public BuiltIn {
      Objects.requireNonNull(screenType, "screenType");
    }
  }

  /** Resolves to an active custom screen provider handle. */
  record Custom(CustomScreenHandle handle) implements ScreenResolution {
    public Custom {
      Objects.requireNonNull(handle, "handle");
    }
  }

  /** Resolves to a preset identifier marked with the {@code @} prefix. */
  record Preset(String presetId) implements ScreenResolution {
    public Preset {
      Objects.requireNonNull(presetId, "presetId");
    }
  }

  /** Key could not be resolved to any built-in, preset, or active custom screen. */
  record Unresolved(String rawKey) implements ScreenResolution {
    public Unresolved {
      rawKey = rawKey != null ? rawKey : "";
    }
  }

  default boolean isBuiltIn() {
    return this instanceof BuiltIn;
  }

  default boolean isCustom() {
    return this instanceof Custom;
  }

  default boolean isPreset() {
    return this instanceof Preset;
  }

  default boolean isUnresolved() {
    return this instanceof Unresolved;
  }
}
