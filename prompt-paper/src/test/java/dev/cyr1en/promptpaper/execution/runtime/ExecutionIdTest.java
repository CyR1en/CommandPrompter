package dev.cyr1en.promptpaper.execution.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutionIdTest {

  @Test
  @DisplayName("ExecutionId.create() produces unique instances with increasing sequence")
  void createProducesUniqueAndMonotonicIds() {
    ExecutionId id1 = ExecutionId.create();
    ExecutionId id2 = ExecutionId.create();

    assertNotNull(id1.uuid());
    assertNotNull(id2.uuid());
    assertNotEquals(id1, id2);
    assertTrue(id2.sequence() > id1.sequence());
    assertTrue(id1.compareTo(id2) < 0);
  }

  @Test
  @DisplayName("ExecutionId.of(UUID) wraps given UUID with monotonic sequence")
  void ofUuid() {
    UUID uuid = UUID.randomUUID();
    ExecutionId id = ExecutionId.of(uuid);

    assertEquals(uuid, id.uuid());
    assertTrue(id.sequence() > 0);
  }

  @Test
  @DisplayName("ExecutionId.of(long, UUID) retains exact sequence and UUID")
  void ofSequenceAndUuid() {
    UUID uuid = UUID.randomUUID();
    ExecutionId id = ExecutionId.of(42L, uuid);

    assertEquals(42L, id.sequence());
    assertEquals(uuid, id.uuid());
    assertEquals("42:" + uuid, id.toString());
  }

  @Test
  @DisplayName("fromString correctly parses valid string and rejects malformed")
  void fromStringParsing() {
    UUID uuid = UUID.randomUUID();
    ExecutionId original = ExecutionId.of(100L, uuid);
    ExecutionId parsed = ExecutionId.fromString(original.toString());

    assertEquals(original, parsed);
    assertEquals(100L, parsed.sequence());
    assertEquals(uuid, parsed.uuid());

    assertThrows(IllegalArgumentException.class, () -> ExecutionId.fromString("invalid"));
    assertThrows(IllegalArgumentException.class, () -> ExecutionId.fromString("123:"));
    assertThrows(IllegalArgumentException.class, () -> ExecutionId.fromString(":uuid"));
    assertThrows(IllegalArgumentException.class, () -> ExecutionId.fromString("abc:not-a-uuid"));
    assertThrows(NullPointerException.class, () -> ExecutionId.fromString(null));
  }

  @Test
  @DisplayName("ExecutionId comparison orders primarily by sequence then by UUID")
  void comparisonOrdering() {
    UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    ExecutionId id1 = ExecutionId.of(1L, u1);
    ExecutionId id2 = ExecutionId.of(2L, u1);
    ExecutionId id3 = ExecutionId.of(1L, u2);

    assertTrue(id1.compareTo(id2) < 0);
    assertTrue(id2.compareTo(id1) > 0);
    assertTrue(id1.compareTo(id3) < 0);
    assertEquals(0, id1.compareTo(ExecutionId.of(1L, u1)));
  }

  @Test
  @DisplayName("Null UUID throws NullPointerException")
  void nullUuidThrows() {
    assertThrows(NullPointerException.class, () -> new ExecutionId(1L, null));
    assertThrows(NullPointerException.class, () -> ExecutionId.of(null));
    assertThrows(NullPointerException.class, () -> ExecutionId.of(1L, null));
  }

  @Test
  @DisplayName("1000 generated ExecutionIds are distinct in hash set")
  void generateManyDistinct() {
    Set<ExecutionId> set = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      set.add(ExecutionId.create());
    }
    assertEquals(1000, set.size());
  }
}
