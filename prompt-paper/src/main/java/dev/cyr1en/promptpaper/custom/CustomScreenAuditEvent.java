package dev.cyr1en.promptpaper.custom;

import java.util.Objects;

/** Structured audit record emitted during custom screen registration and lifecycle events. */
public record CustomScreenAuditEvent(
    Type type, String key, String ownerName, long providerId, String detail) {
  public enum Type {
    REGISTERED,
    UNREGISTERED,
    REJECTED,
    FROZEN,
    TEARDOWN
  }

  public CustomScreenAuditEvent {
    Objects.requireNonNull(type, "type");
    key = key != null ? key : "";
    ownerName = ownerName != null ? ownerName : "";
    detail = detail != null ? detail : "";
  }

  public static CustomScreenAuditEvent registered(String key, String ownerName, long providerId) {
    return new CustomScreenAuditEvent(
        Type.REGISTERED, key, ownerName, providerId, "Screen registered");
  }

  public static CustomScreenAuditEvent unregistered(String key, String ownerName, long providerId) {
    return new CustomScreenAuditEvent(
        Type.UNREGISTERED, key, ownerName, providerId, "Screen unregistered");
  }

  public static CustomScreenAuditEvent rejected(String key, String ownerName, String reason) {
    return new CustomScreenAuditEvent(Type.REJECTED, key, ownerName, -1, reason);
  }

  public static CustomScreenAuditEvent frozen() {
    return new CustomScreenAuditEvent(Type.FROZEN, "", "", -1, "Registry frozen");
  }

  public static CustomScreenAuditEvent teardown(String key, String ownerName, long providerId) {
    return new CustomScreenAuditEvent(
        Type.TEARDOWN, key, ownerName, providerId, "Screen torn down");
  }
}
