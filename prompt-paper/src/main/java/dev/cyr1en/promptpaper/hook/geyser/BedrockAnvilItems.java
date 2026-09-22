package dev.cyr1en.promptpaper.hook.geyser;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.preset.AnvilPrompt;
import dev.cyr1en.promptui.AnvilItemPresentation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserBlockPlacer;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserItemDataComponents;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaItemDataComponents;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaRepairable;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.util.Holders;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.GeyserCustomMappingData;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.custom.GeyserCustomItemDefinition;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.key.Key;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;

/** Startup-only custom definitions; only Bedrock prompt buttons are converted to these items. */
public final class BedrockAnvilItems implements AnvilItemPresentation, AutoCloseable {
  static final String INPUT_MODEL_PREFIX = "commandprompter:anvil_input/";
  static final int MAX_DAMAGE = 64;
  private static final NbtMap REPAIRABLE_COMPONENT =
      NbtMap.builder()
          .putList(
              "repair_items",
              NbtType.COMPOUND,
              NbtMap.builder()
                  .putList(
                      "items", NbtType.COMPOUND, NbtMap.builder().putString("tags", "1").build())
                  .putFloat("repair_amount", 1.0F)
                  .build())
          .build();
  private final Map<Material, BedrockAnvilAppearance> appearances;
  private final EventRegistrar registrar;
  private final GeyserApi geyser;
  private final Path pack;
  private volatile boolean itemsRegistered;
  private volatile boolean definitionsReady;
  private volatile boolean packRegistered;

  public static BedrockAnvilItems subscribe(CommandPrompter plugin) throws IOException {
    var catalog = BedrockAnvilAppearance.load();
    var selected = new EnumMap<Material, BedrockAnvilAppearance>(Material.class);
    for (Material material : configuredMaterials(plugin)) {
      var appearance = catalog.get(material);
      if (appearance == null)
        throw new IllegalArgumentException("No Bedrock anvil appearance for " + material);
      selected.put(material, appearance);
    }
    Path pack =
        BedrockAnvilAppearance.writePack(
            plugin.getDataFolder().toPath().resolve("geyser"), selected);
    return new BedrockAnvilItems(selected, pack, GeyserApi.api());
  }

  BedrockAnvilItems(
      Map<Material, BedrockAnvilAppearance> appearances, Path pack, GeyserApi geyser) {
    this.appearances = Map.copyOf(appearances);
    this.pack = pack;
    this.geyser = geyser;
    registrar = EventRegistrar.of(this);
    geyser.eventBus().subscribe(registrar, GeyserDefineCustomItemsEvent.class, this::registerItems);
    geyser
        .eventBus()
        .subscribe(registrar, GeyserDefineResourcePacksEvent.class, this::registerPack);
  }

  public boolean isReady() {
    return definitionsReady && (pack == null || packRegistered);
  }

  /** Completes Geyser's generated definitions before any prompt can use them. */
  public void completeRegistration() {
    completeRegistration(Registries.ITEMS.get().values());
  }

  void completeRegistration(Collection<ItemMappings> versions) {
    if (!itemsRegistered || versions.isEmpty()) {
      throw new IllegalStateException("Geyser custom anvil items were not registered");
    }
    for (var mappings : versions) {
      repairInputDefinitions(mappings, appearances.keySet());
    }
    definitionsReady = true;
  }

  static void repairInputDefinitions(ItemMappings mappings, Set<Material> materials) {
    var customItems = mappings.getMapping(Items.PAPER).getCustomItemDefinitions();
    if (customItems == null) {
      throw new IllegalStateException("Geyser did not register custom paper definitions");
    }
    for (Material material : materials) {
      var model = Key.key(model(Slot.INPUT, material));
      var entries = customItems.get(model);
      if (entries.size() != 1) {
        throw new IllegalStateException("Missing or ambiguous Bedrock anvil definition: " + model);
      }
      var entry = entries.iterator().next();
      var original = entry.itemDefinition();
      var components = original.getComponentData().getCompound("components");
      if (!original.getIdentifier().equals(model.asString())
          || components.getCompound("minecraft:durability").getInt("max_durability")
              != MAX_DAMAGE) {
        throw new IllegalStateException("Unexpected Bedrock anvil definition: " + model);
      }
      if (REPAIRABLE_COMPONENT.equals(components.getCompound("minecraft:repairable"))) continue;
      // Some Geyser builds drop deferred repair rules for vanilla overrides. The selector and
      // network palette must share the corrected definition so client prediction sees the rule.
      var replacement = withRepairableComponent(original);
      customItems.replaceValues(
          model,
          List.of(new GeyserCustomMappingData(entry.definition(), replacement, entry.integerId())));
      mappings.getItemDefinitions().put(original.getRuntimeId(), replacement);
    }
  }

  private static ItemDefinition withRepairableComponent(ItemDefinition original) {
    var data = original.getComponentData();
    var components =
        data.getCompound("components").toBuilder()
            .putCompound("minecraft:repairable", REPAIRABLE_COMPONENT)
            .build();
    return new SimpleItemDefinition(
        original.getIdentifier(),
        original.getRuntimeId(),
        original.getVersion(),
        original.isComponentBased(),
        data.toBuilder().putCompound("components", components).build());
  }

  private void registerPack(GeyserDefineResourcePacksEvent event) {
    if (pack == null) return;
    event.register(ResourcePack.create(PackCodec.path(pack)));
    packRegistered = true;
  }

  void registerItems(GeyserDefineCustomItemsEvent event) {
    itemsRegistered = false;
    definitionsReady = false;
    for (var entry : appearances.entrySet()) {
      for (Slot slot : List.of(Slot.INPUT, Slot.CANCEL)) {
        event.register(
            Identifier.of(slot == Slot.INPUT ? "paper" : "stick"),
            definition(entry.getKey(), entry.getValue(), slot));
      }
    }
    itemsRegistered = true;
  }

  static CustomItemDefinition definition(
      Material material, BedrockAnvilAppearance appearance, Slot slot) {
    var identifier = Identifier.of(model(slot, material));
    var builder =
        CustomItemDefinition.builder(identifier, identifier)
            .displayName(material.translationKey())
            .component(JavaItemDataComponents.MAX_STACK_SIZE, 1);
    if (slot == Slot.INPUT) {
      builder
          .component(JavaItemDataComponents.MAX_DAMAGE, MAX_DAMAGE)
          .component(
              JavaItemDataComponents.REPAIRABLE,
              JavaRepairable.of(Holders.of(Identifier.of("stick"))));
    }
    if (appearance.kind() == BedrockAnvilAppearance.Kind.BLOCK) {
      // Geyser's internal block-placer component supplies the vanilla block icon.
      ((GeyserCustomItemDefinition.Builder) builder)
          .geyserComponent(
              GeyserItemDataComponents.BLOCK_PLACER,
              GeyserBlockPlacer.of(Identifier.of(appearance.value()), true));
    } else {
      builder.bedrockOptions(CustomItemBedrockOptions.builder().icon(appearance.icon(material)));
    }
    return builder.build();
  }

  @Override
  public ItemStack present(Slot slot, ItemStack item) {
    if (!isReady())
      throw new IllegalStateException(
          "Bedrock anvil definitions are unavailable; restart with Geyser custom content enabled");
    if (slot == Slot.RESULT) return item;
    if (!appearances.containsKey(item.getType())) {
      throw new IllegalStateException(
          "Bedrock anvil appearance "
              + item.getType()
              + " was added after startup; restart the server to register it");
    }
    return BedrockAnvilCarrier.create(slot, item);
  }

  static boolean isPromptInput(GeyserItemStack input) {
    var model = input.getComponent(DataComponentTypes.ITEM_MODEL);
    return model != null
        && model.asString().startsWith(INPUT_MODEL_PREFIX)
        && input.getJavaId() == Items.PAPER.javaId()
        && input.getMaxDamage() == MAX_DAMAGE;
  }

  static String model(Slot slot, Material material) {
    return (slot == Slot.INPUT ? INPUT_MODEL_PREFIX : "commandprompter:anvil_cancel/")
        + material.getKey().getKey();
  }

  private static Set<Material> configuredMaterials(CommandPrompter plugin) {
    var materials = EnumSet.of(Material.PAPER);
    var config = plugin.getConfigLoader().getPromptConfig();
    addMaterial(materials, config.anvilItem());
    addMaterial(materials, config.anvilCancelItem());
    for (var prompt : plugin.getPresetRegistry().snapshot().prompts().values()) {
      if (prompt instanceof AnvilPrompt anvil) {
        addMaterial(materials, anvil.leftButton().buttonIcon());
        addMaterial(materials, anvil.rightButton().buttonIcon());
      }
    }
    return materials;
  }

  private static void addMaterial(Set<Material> materials, String name) {
    Material material = Material.matchMaterial(name);
    if (material != null && (material.isAir() || !material.isItem())) return;
    materials.add(material == null ? Material.PAPER : material);
  }

  @Override
  public void close() {
    itemsRegistered = false;
    definitionsReady = false;
    geyser.eventBus().unregisterAll(registrar);
  }
}
