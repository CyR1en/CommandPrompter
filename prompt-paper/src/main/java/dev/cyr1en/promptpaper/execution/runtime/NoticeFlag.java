package dev.cyr1en.promptpaper.execution.runtime;

/**
 * Flags for logging or warning events that should only be recorded or emitted once per execution
 * plan instance.
 */
public enum NoticeFlag {
  PERMISSION_MISSING,
  DELEGATION_WARNING,
  GATE_TIMEOUT,
  GATE_REJECTED,
  PRIMARY_DISPATCH_FAILED,
  POST_ACTION_SKIPPED,
  POST_ACTION_FAILED,
  CANCEL_REQUESTED,
  STALE_CALLER_DETECTED,
  CUSTOM_NOTICE
}
