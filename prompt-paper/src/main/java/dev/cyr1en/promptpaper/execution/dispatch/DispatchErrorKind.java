package dev.cyr1en.promptpaper.execution.dispatch;

/** Categorized failure reasons for command dispatch operations. */
public enum DispatchErrorKind {
  /** Request parameters or arguments were invalid, blank, or null. */
  INVALID_REQUEST,
  /** Action requested console execution without necessary provenance or player permission. */
  UNAUTHORIZED_CONSOLE,
  /** Permission attachment definition was null, blank, or invalid. */
  INVALID_ATTACHMENT,
  /** Platform failed to create or allocate permission attachment on player. */
  ATTACHMENT_CREATION_FAILED,
  /** Bukkit dispatch returned boolean false. */
  DISPATCH_RETURNED_FALSE,
  /** Bukkit dispatch threw an unhandled exception. */
  EXCEPTION_THROWN,
  /** Target or initiator player entity scheduler was retired or unavailable. */
  SCHEDULER_RETIRED
}
