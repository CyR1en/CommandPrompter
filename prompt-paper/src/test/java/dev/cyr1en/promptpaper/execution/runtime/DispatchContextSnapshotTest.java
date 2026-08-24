package dev.cyr1en.promptpaper.execution.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.preset.ExecuteAs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DispatchContextSnapshotTest {

  @Test
  @DisplayName("Default player snapshot has standard player privileges")
  void playerFactoryDefaults() {
    DispatchContextSnapshot context = DispatchContextSnapshot.player();

    assertEquals(ExecuteAs.PLAYER, context.executeAs());
    assertNull(context.permissionKey());
    assertFalse(context.attachmentRequired());
    assertTrue(context.permissionSnapshot().isEmpty());
    assertFalse(context.isConsoleDelegated());
    assertFalse(context.isDelegated());
  }

  @Test
  @DisplayName("Default console snapshot has console privileges")
  void consoleFactoryDefaults() {
    DispatchContextSnapshot context = DispatchContextSnapshot.console();

    assertEquals(ExecuteAs.CONSOLE, context.executeAs());
    assertNull(context.permissionKey());
    assertFalse(context.attachmentRequired());
    assertTrue(context.permissionSnapshot().isEmpty());
    assertTrue(context.isConsoleDelegated());
    assertTrue(context.isDelegated());
  }

  @Test
  @DisplayName("Permission snapshot is deeply immutable and defensively copied")
  void permissionSnapshotDeepImmutability() {
    List<String> mutableList = new ArrayList<>();
    mutableList.add("prompt.use");
    mutableList.add("minecraft.command.say");

    DispatchContextSnapshot snapshot =
        DispatchContextSnapshot.of(ExecuteAs.PLAYER, "prompt.temp", true, mutableList);

    // Modify original list
    mutableList.add("minecraft.command.op");

    assertEquals(2, snapshot.permissionSnapshot().size());
    assertTrue(snapshot.hasPermission("prompt.use"));
    assertTrue(snapshot.hasPermission("minecraft.command.say"));
    assertFalse(snapshot.hasPermission("minecraft.command.op"));

    // Attempting to modify returned list must throw UnsupportedOperationException
    assertThrows(
        UnsupportedOperationException.class,
        () -> snapshot.permissionSnapshot().add("unsupported"));
  }

  @Test
  @DisplayName("Null values in constructor fall back gracefully")
  void nullHandlingInConstructor() {
    DispatchContextSnapshot snapshot = new DispatchContextSnapshot(null, null, false, null);

    assertEquals(ExecuteAs.PLAYER, snapshot.executeAs());
    assertNull(snapshot.permissionKey());
    assertFalse(snapshot.attachmentRequired());
    assertNotNull(snapshot.permissionSnapshot());
    assertTrue(snapshot.permissionSnapshot().isEmpty());
    assertFalse(snapshot.hasPermission(null));
  }

  @Test
  @DisplayName("Delegation state correctly detected")
  void delegationChecks() {
    DispatchContextSnapshot playerWithAttachment =
        new DispatchContextSnapshot(ExecuteAs.PLAYER, "some.perm", true, List.of("some.perm"));
    assertFalse(playerWithAttachment.isConsoleDelegated());
    assertTrue(playerWithAttachment.isDelegated());

    DispatchContextSnapshot playerNoAttachment =
        new DispatchContextSnapshot(ExecuteAs.PLAYER, null, false, List.of());
    assertFalse(playerNoAttachment.isConsoleDelegated());
    assertFalse(playerNoAttachment.isDelegated());

    DispatchContextSnapshot consoleCtx =
        new DispatchContextSnapshot(ExecuteAs.CONSOLE, null, false, List.of());
    assertTrue(consoleCtx.isConsoleDelegated());
    assertTrue(consoleCtx.isDelegated());
  }
}
