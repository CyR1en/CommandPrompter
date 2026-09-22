package dev.cyr1en.promptui.v26_1;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class AnvilInventoryImplTest {

  private static Unsafe unsafe;
  private static Field handleField;
  private static Field containerField;
  private static Field openedField;
  private static Field experienceFakedField;
  private static Field parentField;

  public static class TestPacketListener extends ServerGamePacketListenerImpl {
    public List<Packet<?>> sentPackets;
    public boolean shouldThrowOnSend = false;

    public TestPacketListener() {
      super(null, null, null, null);
    }

    @Override
    public void send(Packet<?> packet) {
      if (shouldThrowOnSend) {
        throw new RuntimeException("Simulated packet network failure");
      }
      if (sentPackets != null) {
        sentPackets.add(packet);
      }
    }
  }

  public static class TestServerPlayer extends ServerPlayer {
    public boolean closeContainerCalled = false;
    public boolean initMenuCalled = false;
    public boolean throwOnInitMenu = false;

    public TestServerPlayer() {
      super(null, null, null, null);
    }

    @Override
    public void doCloseContainer() {
      closeContainerCalled = true;
    }

    @Override
    public void initMenu(net.minecraft.world.inventory.AbstractContainerMenu menu) {
      initMenuCalled = true;
      if (throwOnInitMenu) {
        throw new IllegalStateException("Simulated initMenu failure");
      }
    }
  }

  private TestServerPlayer serverPlayer;
  private TestPacketListener packetListener;
  private CraftPlayer craftPlayer;
  private AnvilInventoryImpl anvilInventory;
  private AnvilInventoryImpl.NMSAnvilContainer container;

  @BeforeAll
  static void initNMS() throws Exception {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();

    Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    unsafe = (Unsafe) unsafeField.get(null);

    Class<?> c = CraftPlayer.class;
    while (c != null) {
      for (Field f : c.getDeclaredFields()) {
        if (f.getType().isAssignableFrom(ServerPlayer.class)
            || f.getName().toLowerCase().contains("entity")
            || f.getName().toLowerCase().contains("handle")) {
          f.setAccessible(true);
          handleField = f;
          break;
        }
      }
      if (handleField != null) break;
      c = c.getSuperclass();
    }

    containerField = AnvilInventoryImpl.class.getDeclaredField("container");
    containerField.setAccessible(true);

    openedField = AnvilInventoryImpl.class.getDeclaredField("opened");
    openedField.setAccessible(true);

    experienceFakedField = AnvilInventoryImpl.class.getDeclaredField("experienceFaked");
    experienceFakedField.setAccessible(true);

    parentField = AnvilInventoryImpl.NMSAnvilContainer.class.getDeclaredField("parent");
    parentField.setAccessible(true);
  }

  @BeforeEach
  void setUp() throws Exception {
    serverPlayer = (TestServerPlayer) unsafe.allocateInstance(TestServerPlayer.class);
    packetListener = (TestPacketListener) unsafe.allocateInstance(TestPacketListener.class);
    packetListener.sentPackets = new ArrayList<>();
    serverPlayer.connection = packetListener;

    craftPlayer = (CraftPlayer) unsafe.allocateInstance(CraftPlayer.class);
    handleField.set(craftPlayer, serverPlayer);

    anvilInventory = new AnvilInventoryImpl(craftPlayer);
    container =
        (AnvilInventoryImpl.NMSAnvilContainer)
            unsafe.allocateInstance(AnvilInventoryImpl.NMSAnvilContainer.class);
    containerField.set(anvilInventory, container);
    parentField.set(container, anvilInventory);
  }

  @Test
  void onContainerRemoved_whenCurrentContainer_restoresExperienceAndRetiresFlag() throws Exception {
    serverPlayer.containerMenu = container;
    experienceFakedField.set(anvilInventory, true);
    openedField.set(anvilInventory, true);

    container.removed(serverPlayer);

    assertFalse(anvilInventory.isExperienceFaked());
    assertFalse(anvilInventory.isOpened());
    assertEquals(1, packetListener.sentPackets.size());
    assertInstanceOf(ClientboundSetExperiencePacket.class, packetListener.sentPackets.getFirst());
  }

  @Test
  void onContainerRemoved_whenContainerAlreadyReplaced_doesNotRestoreExperience() throws Exception {
    AnvilInventoryImpl.NMSAnvilContainer newerContainer =
        (AnvilInventoryImpl.NMSAnvilContainer)
            unsafe.allocateInstance(AnvilInventoryImpl.NMSAnvilContainer.class);
    serverPlayer.containerMenu = newerContainer; // Replaced by newer menu
    experienceFakedField.set(anvilInventory, true);
    openedField.set(anvilInventory, true);

    container.removed(serverPlayer);

    assertFalse(anvilInventory.isExperienceFaked());
    assertFalse(anvilInventory.isOpened());
    // Crucial safety check: stale anvil MUST NOT send packet to overwrite newer container's fake
    // XP!
    assertTrue(packetListener.sentPackets.isEmpty());
  }

  @Test
  void onContainerRemoved_whenConnectionNull_doesNotThrow() throws Exception {
    serverPlayer.containerMenu = container;
    serverPlayer.connection = null;
    experienceFakedField.set(anvilInventory, true);

    assertDoesNotThrow(() -> container.removed(serverPlayer));
    assertFalse(anvilInventory.isExperienceFaked());
  }

  @Test
  void onContainerRemoved_whenPacketThrows_doesNotThrowAndRetiresFlag() throws Exception {
    serverPlayer.containerMenu = container;
    packetListener.shouldThrowOnSend = true;
    experienceFakedField.set(anvilInventory, true);

    assertDoesNotThrow(() -> container.removed(serverPlayer));
    assertFalse(anvilInventory.isExperienceFaked());
  }

  @Test
  void close_whenCurrentContainer_sendsClosePacketAndRestoresExperience() throws Exception {
    serverPlayer.containerMenu = container;
    openedField.set(anvilInventory, true);
    experienceFakedField.set(anvilInventory, true);

    anvilInventory.close();

    assertTrue(serverPlayer.closeContainerCalled);
    assertFalse(anvilInventory.isOpened());
    assertFalse(anvilInventory.isExperienceFaked());

    boolean hasClose =
        packetListener.sentPackets.stream()
            .anyMatch(p -> p instanceof ClientboundContainerClosePacket);
    boolean hasExp =
        packetListener.sentPackets.stream()
            .anyMatch(p -> p instanceof ClientboundSetExperiencePacket);
    assertTrue(hasClose, "Expected ClientboundContainerClosePacket to be sent");
    assertTrue(hasExp, "Expected ClientboundSetExperiencePacket to be sent");
  }

  @Test
  void close_whenContainerAlreadyReplaced_doesNotSendPacketsOrRestoreExperience() throws Exception {
    AnvilInventoryImpl.NMSAnvilContainer newerContainer =
        (AnvilInventoryImpl.NMSAnvilContainer)
            unsafe.allocateInstance(AnvilInventoryImpl.NMSAnvilContainer.class);
    serverPlayer.containerMenu = newerContainer; // Replaced!
    openedField.set(anvilInventory, true);
    experienceFakedField.set(anvilInventory, true);

    anvilInventory.close();

    assertFalse(serverPlayer.closeContainerCalled);
    assertFalse(anvilInventory.isOpened());
    assertFalse(anvilInventory.isExperienceFaked());
    // Stale anvil must not send any packets
    assertTrue(packetListener.sentPackets.isEmpty());
  }

  @Test
  void close_whenConnectionNull_doesNotThrow() throws Exception {
    serverPlayer.containerMenu = container;
    serverPlayer.connection = null;
    openedField.set(anvilInventory, true);
    experienceFakedField.set(anvilInventory, true);

    assertDoesNotThrow(() -> anvilInventory.close());
    assertTrue(serverPlayer.closeContainerCalled);
    assertFalse(anvilInventory.isExperienceFaked());
  }

  @Test
  void close_whenClosePacketThrows_stillClosesContainerAndRetiresExperience() throws Exception {
    serverPlayer.containerMenu = container;
    packetListener.shouldThrowOnSend = true;
    openedField.set(anvilInventory, true);
    experienceFakedField.set(anvilInventory, true);

    assertDoesNotThrow(() -> anvilInventory.close());
    assertTrue(serverPlayer.closeContainerCalled);
    assertFalse(anvilInventory.isExperienceFaked());
  }

  @Test
  void patchedBedrockPromptSuppliesClientXpAndRestoresCurrentRealXp() throws Exception {
    serverPlayer.setUUID(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
    anvilInventory = new AnvilInventoryImpl(craftPlayer, true);
    containerField.set(anvilInventory, container);
    parentField.set(container, anvilInventory);
    dev.cyr1en.promptui.util.BedrockUtil.setBedrockChecker(uuid -> true);
    try {
      container.setTitle(net.minecraft.network.chat.Component.literal("Test"));
      anvilInventory.open();
      assertTrue(anvilInventory.isOpened());
      assertTrue(
          anvilInventory.isExperienceFaked(), "Patched prompts must be usable at zero real XP");
      var displayed =
          packetListener.sentPackets.stream()
              .filter(ClientboundSetExperiencePacket.class::isInstance)
              .map(ClientboundSetExperiencePacket.class::cast)
              .findFirst()
              .orElseThrow();
      assertEquals(20, displayed.getExperienceLevel());
      assertEquals(0, serverPlayer.experienceLevel);
      assertEquals(0, serverPlayer.totalExperience);
      assertEquals(0.0f, serverPlayer.experienceProgress);

      // Experience earned while the prompt is open must not be rolled back on close.
      serverPlayer.experienceLevel = 7;
      serverPlayer.totalExperience = 91;
      serverPlayer.experienceProgress = 0.375f;
      packetListener.sentPackets.clear();
      anvilInventory.close();
      assertFalse(anvilInventory.isExperienceFaked());
      var restored =
          packetListener.sentPackets.stream()
              .filter(ClientboundSetExperiencePacket.class::isInstance)
              .map(ClientboundSetExperiencePacket.class::cast)
              .findFirst()
              .orElseThrow();
      assertEquals(7, restored.getExperienceLevel());
      assertEquals(91, restored.getTotalExperience());
      assertEquals(0.375f, restored.getExperienceProgress());
      assertEquals(7, serverPlayer.experienceLevel);
      assertEquals(91, serverPlayer.totalExperience);
    } finally {
      dev.cyr1en.promptui.util.BedrockUtil.reset();
    }
  }

  @Test
  void patchedJavaPromptDoesNotSendClientXp() throws Exception {
    serverPlayer.setUUID(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
    anvilInventory = new AnvilInventoryImpl(craftPlayer, true);
    containerField.set(anvilInventory, container);
    parentField.set(container, anvilInventory);
    dev.cyr1en.promptui.util.BedrockUtil.setBedrockChecker(uuid -> false);
    try {
      container.setTitle(net.minecraft.network.chat.Component.literal("Test"));
      anvilInventory.open();
      assertFalse(anvilInventory.isExperienceFaked());
      assertTrue(
          packetListener.sentPackets.stream()
              .noneMatch(ClientboundSetExperiencePacket.class::isInstance));
    } finally {
      dev.cyr1en.promptui.util.BedrockUtil.reset();
    }
  }

  @Test
  void open_whenExceptionOccurs_safelyCleansUpAndDoesNotMaskFailure() throws Exception {
    serverPlayer.throwOnInitMenu = true;
    packetListener.shouldThrowOnSend = true; // Packet errors must not mask initMenu failure

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> anvilInventory.open());
    assertEquals("Simulated initMenu failure", ex.getMessage());
    assertFalse(anvilInventory.isOpened());
    assertFalse(anvilInventory.isExperienceFaked());
  }
}
