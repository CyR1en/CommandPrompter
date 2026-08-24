package dev.cyr1en.promptpaper.execution.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Dispatch Sanitizer, Error, Outcome, and Provenance Unit Tests")
class DispatchTypesTest {

    @Test
    @DisplayName("DispatchSanitizer strips C0 control characters and caps length")
    void testSanitizer() {
        String inputWithC0 = "Hello\u0000World\u0007!\nLine";
        String sanitized = DispatchSanitizer.sanitizeDetail(inputWithC0);
        assertFalse(sanitized.contains("\u0000"));
        assertFalse(sanitized.contains("\u0007"));
        assertFalse(sanitized.contains("\n"));

        String longMsg = "A".repeat(300);
        String capped = DispatchSanitizer.sanitizeDetail(longMsg);
        assertEquals(256, capped.length());

        assertEquals("unknown error", DispatchSanitizer.sanitizeDetail("   "));
        assertEquals("unknown error", DispatchSanitizer.sanitizeDetail(null));
    }

    @Test
    @DisplayName("DispatchSanitizer strips leading slash")
    void testStripLeadingSlash() {
        assertEquals("say hi", DispatchSanitizer.stripLeadingSlash("/say hi"));
        assertEquals("say hi", DispatchSanitizer.stripLeadingSlash("say hi"));
        assertEquals("say /hello", DispatchSanitizer.stripLeadingSlash("/say /hello"));
        assertEquals("", DispatchSanitizer.stripLeadingSlash(""));
        assertEquals("", DispatchSanitizer.stripLeadingSlash(null));
    }

    @Test
    @DisplayName("DispatchError sanitizes detail string")
    void testDispatchError() {
        var error = DispatchError.of(DispatchErrorKind.INVALID_REQUEST, "Bad\u0000Input\u001F");
        assertEquals("BadInput", error.detail());
        assertNull(error.cause());

        var ex = new RuntimeException("Something failed");
        var errorWithEx = DispatchError.of(DispatchErrorKind.EXCEPTION_THROWN, ex);
        assertTrue(errorWithEx.detail().contains("RuntimeException"));
        assertEquals(ex, errorWithEx.cause());
    }

    @Test
    @DisplayName("DispatchOutcome correctly represents success and failure")
    void testDispatchOutcome() {
        var success = DispatchOutcome.success();
        assertTrue(success.isSuccess());
        assertFalse(success.isFailure());
        assertTrue(success.optionalError().isEmpty());

        var failure = DispatchOutcome.failure(DispatchErrorKind.DISPATCH_RETURNED_FALSE, "returned false");
        assertFalse(failure.isSuccess());
        assertTrue(failure.isFailure());
        assertTrue(failure.optionalError().isPresent());
        assertEquals(DispatchErrorKind.DISPATCH_RETURNED_FALSE, failure.error().kind());
    }

    @Test
    @DisplayName("PermissionAttachmentContext normalizes a null snapshot")
    void testPermissionAttachmentContext() {
        var ctx = PermissionAttachmentContext.of("KEY", null);
        assertTrue(ctx.permissionSnapshot().isEmpty());
        assertFalse(ctx.isValid());

        var validCtx = PermissionAttachmentContext.of("KEY", java.util.List.of("perm.one"));
        assertTrue(validCtx.isValid());
    }
}
