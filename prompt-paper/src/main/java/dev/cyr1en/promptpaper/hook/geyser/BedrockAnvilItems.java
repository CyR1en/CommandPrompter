package dev.cyr1en.promptpaper.hook.geyser;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptui.AnvilItemPresentation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.ItemMappings;

/** Startup-only custom definitions; only Bedrock prompt buttons are converted to these items. */
public final class BedrockAnvilItems implements AnvilItemPresentation, AutoCloseable {
  static final String INPUT_MODEL_PREFIX = AnvilPatchProtocol.INPUT_MODEL_PREFIX;
  static final int MAX_DAMAGE = AnvilPatchProtocol.MAX_DAMAGE;
  private final Map<Material, BedrockAnvilAppearance> appearances;
  private final AnvilItemPresentation presentation;
  private final EventRegistrar registrar;
  private final GeyserApi geyser;
  private final Path pack;
  private volatile boolean itemsRegistered;
  private volatile boolean definitionsReady;
  private volatile boolean packRegistered;

  public static BedrockAnvilItems subscribe(CommandPrompter plugin) throws IOException {
    var selected = BedrockAnvilAppearance.selected(plugin);
    Path pack =
        BedrockAnvilAppearance.writePack(
            plugin.getDataFolder().toPath().resolve("geyser"), selected);
    return new BedrockAnvilItems(selected, pack, GeyserApi.api());
  }

  BedrockAnvilItems(
      Map<Material, BedrockAnvilAppearance> appearances, Path pack, GeyserApi geyser) {
    this.appearances = Map.copyOf(appearances);
    presentation = new BedrockAnvilPresentation(appearances.keySet());
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
    GeyserAnvilDefinitions.repairInputDefinitions(
        mappings, materials.stream().map(material -> material.getKey().getKey()).toList());
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
    return GeyserAnvilDefinitions.definition(
        material.getKey().getKey(),
        appearance.serialized() + "|" + material.translationKey(),
        slot == Slot.INPUT);
  }

  @Override
  public ItemStack present(Slot slot, ItemStack item) {
    if (!isReady())
      throw new IllegalStateException(
          "Bedrock anvil definitions are unavailable; restart with Geyser custom content enabled");
    return presentation.present(slot, item);
  }

  static boolean isPromptInput(GeyserItemStack input) {
    return GeyserAnvilDefinitions.isPromptInput(input);
  }

  static String model(Slot slot, Material material) {
    return AnvilPatchProtocol.model(slot == Slot.INPUT, material.getKey().getKey());
  }

  @Override
  public void close() {
    itemsRegistered = false;
    definitionsReady = false;
    geyser.eventBus().unregisterAll(registrar);
  }
}
