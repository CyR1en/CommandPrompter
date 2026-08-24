package dev.cyr1en.promptpaper.execution.dispatch;

import java.util.List;

/**
 * Immutable context containing a permission attachment key and permission snapshot.
 */
public record PermissionAttachmentContext(
        String permissionKey,
        List<String> permissionSnapshot
) {
    public PermissionAttachmentContext {
        permissionSnapshot = permissionSnapshot == null ? List.of() : List.copyOf(permissionSnapshot);
    }

    public static PermissionAttachmentContext of(String permissionKey, List<String> permissionSnapshot) {
        return new PermissionAttachmentContext(permissionKey, permissionSnapshot);
    }

    public static PermissionAttachmentContext empty() {
        return new PermissionAttachmentContext(null, List.of());
    }

    public boolean isValid() {
        return permissionKey != null && !permissionKey.isBlank() && !permissionSnapshot.isEmpty();
    }
}
