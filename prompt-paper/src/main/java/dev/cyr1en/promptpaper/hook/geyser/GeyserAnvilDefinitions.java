package dev.cyr1en.promptpaper.hook.geyser;

import java.util.Collection;
import java.util.List;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserBlockPlacer;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserItemDataComponents;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaItemDataComponents;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaRepairable;
import org.geysermc.geyser.api.util.Holders;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.GeyserCustomMappingData;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.custom.GeyserCustomItemDefinition;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.key.Key;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;

/** Geyser-side definitions shared with the standalone patch. */
public final class GeyserAnvilDefinitions {
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

  private GeyserAnvilDefinitions() {}

  public static CustomItemDefinition definition(String material, String appearance, boolean input) {
    var identifier = Identifier.of(AnvilPatchProtocol.model(input, material));
    var builder =
        CustomItemDefinition.builder(identifier, identifier)
            .component(JavaItemDataComponents.MAX_STACK_SIZE, 1);
    if (input) {
      builder
          .component(JavaItemDataComponents.MAX_DAMAGE, AnvilPatchProtocol.MAX_DAMAGE)
          .component(
              JavaItemDataComponents.REPAIRABLE,
              JavaRepairable.of(Holders.of(Identifier.of("stick"))));
    }
    String[] display = appearance.split("\\|", 2);
    if (display.length == 2) builder.displayName(display[1]);
    String[] parts = display[0].split(":", 2);
    if (parts.length != 2)
      throw new IllegalArgumentException("Invalid anvil appearance: " + material);
    switch (parts[0]) {
      case "block" ->
          ((GeyserCustomItemDefinition.Builder) builder)
              .geyserComponent(
                  GeyserItemDataComponents.BLOCK_PLACER,
                  GeyserBlockPlacer.of(Identifier.of(parts[1]), true));
      case "icon", "texture" ->
          builder.bedrockOptions(
              CustomItemBedrockOptions.builder()
                  .icon(
                      parts[0].equals("texture") ? "commandprompter.anvil." + material : parts[1]));
      default -> throw new IllegalArgumentException("Invalid anvil appearance kind: " + parts[0]);
    }
    return builder.build();
  }

  public static void repairInputDefinitions(ItemMappings mappings, Collection<String> materials) {
    var customItems = mappings.getMapping(Items.PAPER).getCustomItemDefinitions();
    if (customItems == null)
      throw new IllegalStateException("Geyser did not register custom paper definitions");
    for (String material : materials) {
      var model = Key.key(AnvilPatchProtocol.model(true, material));
      var entries = customItems.get(model);
      if (entries.size() != 1)
        throw new IllegalStateException("Missing or ambiguous Bedrock anvil definition: " + model);
      var entry = entries.iterator().next();
      var original = entry.itemDefinition();
      var components = original.getComponentData().getCompound("components");
      if (!original.getIdentifier().equals(model.asString())
          || components.getCompound("minecraft:durability").getInt("max_durability")
              != AnvilPatchProtocol.MAX_DAMAGE) {
        throw new IllegalStateException("Unexpected Bedrock anvil definition: " + model);
      }
      if (REPAIRABLE_COMPONENT.equals(components.getCompound("minecraft:repairable"))) continue;
      // The selector and network palette must share the repair rule for client prediction.
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

  public static boolean isPromptInput(GeyserItemStack input) {
    var model = input.getComponent(DataComponentTypes.ITEM_MODEL);
    return model != null
        && model.asString().startsWith(AnvilPatchProtocol.INPUT_MODEL_PREFIX)
        && input.getJavaId() == Items.PAPER.javaId()
        && input.getMaxDamage() == AnvilPatchProtocol.MAX_DAMAGE;
  }
}
