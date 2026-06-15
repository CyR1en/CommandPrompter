package dev.cyr1en.promptcore;

/**
 * Where a post-command meta should be dispatched.
 *
 * <p>{@link #PASSTHROUGH} — use the same executor as the original command.<br>
 * {@link #CONSOLE} — dispatch as the server console.<br>
 * {@link #PLAYER} — dispatch as the prompting player.
 */
public enum DispatchTarget {
  PASSTHROUGH,
  CONSOLE,
  PLAYER
}
