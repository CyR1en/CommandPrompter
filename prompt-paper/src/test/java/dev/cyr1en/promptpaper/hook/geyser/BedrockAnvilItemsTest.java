package dev.cyr1en.promptpaper.hook.geyser;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.TreeMultimap;
import com.google.gson.JsonParser;
import dev.cyr1en.promptpaper.hook.geyser.BedrockAnvilAppearance.Kind;
import dev.cyr1en.promptui.AnvilItemPresentation.Slot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.geysermc.api.Geyser;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.event.GeyserEventBus;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.GeyserCustomMappingData;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.platform.spigot.shaded.it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.key.Key;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.text.Component;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.populator.CustomItemRegistryPopulator;
import org.geysermc.geyser.registry.type.GeyserMappingItem;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.HolderSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;

class BedrockAnvilItemsTest {
  @Test
  void blockAppearancesReferenceActualBedrockBlocks() throws Exception {
    try (var stream = getClass().getResourceAsStream("/bedrock/block_palette.26_50.nbt");
        var reader = org.cloudburstmc.nbt.NbtUtils.createGZIPReader(stream, true, true)) {
      var palette = (NbtMap) reader.readTag();
      var names =
          palette.getList("blocks", NbtType.COMPOUND).stream()
              .map(block -> block.getString("name"))
              .collect(java.util.stream.Collectors.toSet());
      BedrockAnvilAppearance.load()
          .forEach(
              (material, appearance) -> {
                if (appearance.kind() == Kind.BLOCK) {
                  assertTrue(
                      names.contains(appearance.value()),
                      material + " references an item ID instead of a block ID");
                }
              });
    }
  }

  @Test
  void aliasesAreSmallDeterministicAndDoNotReplaceVanillaEntries(@TempDir Path directory)
      throws Exception {
    var catalog = BedrockAnvilAppearance.load();
    assertEquals(new BedrockAnvilAppearance(Kind.ICON, "paper"), catalog.get(Material.PAPER));
    assertEquals(
        new BedrockAnvilAppearance(Kind.TEXTURE, "textures/blocks/barrier"),
        catalog.get(Material.BARRIER));
    assertEquals(
        new BedrockAnvilAppearance(Kind.TEXTURE, "textures/items/diamond_sword"),
        catalog.get(Material.DIAMOND_SWORD));
    assertEquals(
        new BedrockAnvilAppearance(Kind.BLOCK, "minecraft:stone"), catalog.get(Material.STONE));
    assertNull(
        BedrockAnvilAppearance.writePack(
            directory, Map.of(Material.PAPER, catalog.get(Material.PAPER))));
    var selected =
        Map.of(
            Material.PAPER,
            catalog.get(Material.PAPER),
            Material.BARRIER,
            catalog.get(Material.BARRIER),
            Material.DIAMOND_SWORD,
            catalog.get(Material.DIAMOND_SWORD));
    Path pack = BedrockAnvilAppearance.writePack(directory, selected);
    assertEquals(pack, BedrockAnvilAppearance.writePack(directory, selected));
    try (var zip = new ZipFile(pack.toFile())) {
      assertEquals(2, zip.size());
      var atlas =
          JsonParser.parseString(
                  new String(
                      zip.getInputStream(zip.getEntry("textures/item_texture.json")).readAllBytes(),
                      java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject();
      var textures = atlas.getAsJsonObject("texture_data");
      assertEquals(2, textures.size());
      assertTrue(
          textures.keySet().stream().allMatch(key -> key.startsWith("commandprompter.anvil.")));
      assertEquals(
          "textures/blocks/barrier",
          textures.getAsJsonObject("commandprompter.anvil.barrier").get("textures").getAsString());
    }
  }

  @Test
  void registersRepairableRolesAndKeepsTypingOnMaterialCostUpdates() throws Exception {
    try (var locale = mockStatic(GeyserLocale.class)) {
      locale.when(() -> GeyserLocale.getLocaleStringLog(anyString())).thenReturn("Test");
      var geyser = mock(GeyserImpl.class);
      var config = mock(GeyserConfig.class);
      when(geyser.config()).thenReturn(config);
      when(config.gameplay()).thenReturn(mock(GeyserConfig.GameplayConfig.class));
      when(geyser.getLogger()).thenReturn(mock(GeyserLogger.class));
      when(geyser.configDirectory()).thenReturn(Path.of(System.getProperty("user.dir")));
      when(geyser.eventBus()).thenReturn(new GeyserEventBus());
      doAnswer(InvocationOnMock::callRealMethod)
          .when(geyser)
          .provider(any(Class.class), any(Object[].class));
      var bootstrap = mock(GeyserBootstrap.class);
      when(geyser.getBootstrap()).thenReturn(bootstrap);
      when(bootstrap.getResourceOrThrow(anyString()))
          .thenAnswer(call -> getClass().getClassLoader().getResourceAsStream(call.getArgument(0)));
      try (var impl = mockStatic(GeyserImpl.class);
          var api = mockStatic(Geyser.class)) {
        impl.when(GeyserImpl::getInstance).thenReturn(geyser);
        api.when(() -> Geyser.api(GeyserApi.class)).thenReturn(geyser);
        Registries.BLOCK_ENTITIES.load();
        var catalog = BedrockAnvilAppearance.load();
        var mappings =
            registerMappings(
                Map.of(
                    Material.PAPER,
                    catalog.get(Material.PAPER),
                    Material.BARRIER,
                    catalog.get(Material.BARRIER)));
        var original = inputDefinition(mappings, Material.PAPER);
        assertTrue(
            original
                .getComponentData()
                .getCompound("components")
                .getCompound("minecraft:repairable")
                .isEmpty(),
            "Pinned Geyser drops the deferred repair component during vanilla-override registration");
        var cancel =
            mappings
                .getMapping(Items.STICK)
                .getCustomItemDefinitions()
                .values()
                .iterator()
                .next()
                .itemDefinition();
        try (var items =
            new BedrockAnvilItems(
                Map.of(
                    Material.PAPER,
                    catalog.get(Material.PAPER),
                    Material.BARRIER,
                    catalog.get(Material.BARRIER)),
                null,
                geyser)) {
          assertFalse(items.isReady());
          var registration = mock(GeyserDefineCustomItemsEvent.class);
          geyser.eventBus().fire(registration);
          assertFalse(
              items.isReady(),
              "Registration callbacks do not prove the wire definitions are ready");
          assertThrows(
              IllegalStateException.class, () -> items.present(Slot.INPUT, typed(Material.PAPER)));
          items.completeRegistration(List.of(mappings));
          assertTrue(items.isReady());
          verify(registration, times(4))
              .register(
                  any(), any(org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition.class));
          assertThrows(
              IllegalStateException.class,
              () -> items.present(Slot.INPUT, typed(Material.DIAMOND)));
          var logical = typed(Material.BARRIER);
          assertSame(logical, items.present(Slot.RESULT, logical));
        }
        assertTrue(geyser.eventBus().subscribers(GeyserDefineCustomItemsEvent.class).isEmpty());

        var registered = inputDefinition(mappings, Material.PAPER);
        assertSame(
            registered,
            mappings.getItemDefinitions().get(registered.getRuntimeId()),
            "The stack selector and network palette must share the corrected definition");
        assertSame(
            cancel,
            mappings.getItemDefinitions().get(cancel.getRuntimeId()),
            "Cancel definitions must remain untouched");
        BedrockAnvilItems.repairInputDefinitions(
            mappings, Set.of(Material.PAPER, Material.BARRIER));
        assertSame(
            registered, inputDefinition(mappings, Material.PAPER), "Completion must be idempotent");
        assertTrue(
            original
                .getComponentData()
                .getCompound("components")
                .getCompound("minecraft:repairable")
                .isEmpty(),
            "Do not mutate immutable definitions already referenced elsewhere");
        NbtMap content = registered.getComponentData().getCompound("components");
        assertFalse(
            content.getCompound("minecraft:repairable").isEmpty(),
            "The definition delivered to Bedrock must include the repair rule after real registration");
        assertEquals(64, content.getCompound("minecraft:durability").getInt("max_durability"));
        var repair =
            content
                .getCompound("minecraft:repairable")
                .getList("repair_items", NbtType.COMPOUND)
                .getFirst();
        assertEquals("1", repair.getList("items", NbtType.COMPOUND).getFirst().getString("tags"));
        assertEquals(1.0F, repair.getFloat("repair_amount"));
        assertEquals(
            "paper",
            content
                .getCompound("item_properties")
                .getCompound("minecraft:icon")
                .getCompound("textures")
                .getString("default"));
        checkPackets();
      }
    }
  }

  private static ItemMappings registerMappings(Map<Material, BedrockAnvilAppearance> appearances)
      throws Exception {
    var defaults = new DataComponents(new HashMap<>());
    defaults.put(DataComponentTypes.MAX_STACK_SIZE, 64);
    var definitions = new Int2ObjectOpenHashMap<ItemDefinition>();
    var mappings = mock(ItemMappings.class);
    when(mappings.getItemDefinitions()).thenReturn(definitions);
    int runtimeId = 30000;
    for (Slot slot : new Slot[] {Slot.INPUT, Slot.CANCEL}) {
      String base = slot == Slot.INPUT ? "minecraft:paper" : "minecraft:stick";
      var javaItem =
          new Item(base, Item.builder().components(defaults).resolvableComponents(List.of()));
      var vanilla = new GeyserMappingItem().withBedrockIdentifier(base);
      var custom =
          TreeMultimap.<Key, GeyserCustomMappingData>create(
              Comparator.comparing(Key::asString),
              Comparator.comparingInt(GeyserCustomMappingData::integerId));
      for (var appearance : appearances.entrySet()) {
        var definition =
            BedrockAnvilItems.definition(appearance.getKey(), appearance.getValue(), slot);
        var registered =
            CustomItemRegistryPopulator.registerCustomItem(
                javaItem, vanilla, definition, runtimeId++, 2193, true);
        custom.put(Key.key(definition.model().toString()), registered);
        definitions.put(registered.integerId(), registered.itemDefinition());
      }
      var mapping = ItemMapping.builder().javaItem(javaItem).customItemDefinitions(custom).build();
      when(mappings.getMapping(slot == Slot.INPUT ? Items.PAPER : Items.STICK)).thenReturn(mapping);
    }
    return mappings;
  }

  private static ItemDefinition inputDefinition(ItemMappings mappings, Material material) {
    return mappings
        .getMapping(Items.PAPER)
        .getCustomItemDefinitions()
        .get(Key.key(BedrockAnvilItems.model(Slot.INPUT, material)))
        .iterator()
        .next()
        .itemDefinition();
  }

  private static ItemStack typed(Material material) {
    var item = mock(ItemStack.class);
    when(item.getType()).thenReturn(material);
    return item;
  }

  private static void checkPackets() {
    var session = mock(GeyserSession.class);
    when(session.locale()).thenReturn("en_us");
    var container = spy(new PatchedAnvilContainer(session, "Prompt", 1, 3, ContainerType.ANVIL));
    doReturn(GeyserItemStack.EMPTY).when(container).getItem(anyInt());
    var input = stack("commandprompter:anvil_input/paper", "Prompt", 31);
    var material = stack("commandprompter:anvil_cancel/barrier", "Cancel", 32);
    var result = stack("minecraft:emerald", "Prompt", 33);
    when(input.getJavaId()).thenReturn(Items.PAPER.javaId());
    when(input.getMaxDamage()).thenReturn(64);
    when(input.getComponent(DataComponentTypes.ITEM_MODEL))
        .thenReturn(Key.key(BedrockAnvilItems.INPUT_MODEL_PREFIX + "paper"));
    when(input.isDamageable()).thenReturn(true);
    when(input.getComponentElseGet(eq(DataComponentTypes.DAMAGE), any())).thenReturn(1);
    var repairable =
        new HolderSet(
            new org.geysermc.geyser.platform.spigot.shaded.it.unimi.dsi.fastutil.ints.IntArrayList(
                new int[] {Items.STICK.javaId()}));
    when(input.getComponent(DataComponentTypes.REPAIRABLE)).thenReturn(repairable);
    when(material.is(session, repairable)).thenReturn(true);
    doReturn(input).when(container).getItem(0);
    doReturn(material).when(container).getItem(1);
    doReturn(result).when(container).getItem(2);
    container.setDisplayed(true);
    container.setReadyForInput(true);
    container.setUseJavaLevelCost(true);
    container.setJavaLevelCost(0);
    var packets = new ArrayList<InventorySlotPacket>();
    doAnswer(
            call -> {
              if (call.getArgument(0) instanceof InventorySlotPacket packet) packets.add(packet);
              return null;
            })
        .when(session)
        .sendUpstreamPacket(any());
    PatchedAnvilUpdater.INSTANCE.updateInventory(new PatchedAnvilTranslator(), session, container);
    assertEquals(1, PatchedAnvilUpdater.INSTANCE.calcLevelCost(session, container, true));
    assertEquals(-1, lastSlot(packets, 2).getTag().getInt("RepairCost"));
    assertEquals(32, lastSlot(packets, 2).getNetId());
    assertEquals("minecraft:emerald", lastSlot(packets, 50).getDefinition().getIdentifier());
    container.setNewName("Answer");
    packets.clear();
    PatchedAnvilUpdater.INSTANCE.updateSlot(new PatchedAnvilTranslator(), session, container, 2);
    assertEquals(-2, lastSlot(packets, 2).getTag().getInt("RepairCost"));
    assertTrue(packets.stream().noneMatch(packet -> packet.getSlot() == 1));
    // An ordinary component-repairable paper item still uses vanilla repair-material stacking.
    when(input.getComponent(DataComponentTypes.ITEM_MODEL)).thenReturn(Key.key("minecraft:paper"));
    assertFalse(BedrockAnvilItems.isPromptInput(input));
    container.setNewName(null);
    packets.clear();
    PatchedAnvilUpdater.INSTANCE.updateInventory(new PatchedAnvilTranslator(), session, container);
    assertEquals(-1, lastSlot(packets, 1).getTag().getInt("RepairCost"));
    assertEquals(0, lastSlot(packets, 2).getTag().getInt("RepairCost"));
  }

  private static ItemData lastSlot(List<InventorySlotPacket> packets, int slot) {
    return packets.stream()
        .filter(packet -> packet.getSlot() == slot)
        .reduce((a, b) -> b)
        .orElseThrow()
        .getItem();
  }

  private static GeyserItemStack stack(String id, String name, int networkId) {
    var item = mock(GeyserItemStack.class);
    when(item.getAmount()).thenReturn(1);
    when(item.copy()).thenReturn(item);
    when(item.getComponentElseGet(eq(DataComponentTypes.REPAIR_COST), any())).thenReturn(0);
    when(item.getComponent(DataComponentTypes.CUSTOM_NAME)).thenReturn(Component.text(name));
    when(item.getItemData(any()))
        .thenReturn(
            ItemData.builder()
                .definition(new SimpleItemDefinition(id, networkId, true))
                .count(1)
                .netId(networkId)
                .usingNetId(true)
                .tag(
                    NbtMap.builder()
                        .putCompound("display", NbtMap.builder().putString("Name", name).build())
                        .build())
                .build());
    return item;
  }
}
