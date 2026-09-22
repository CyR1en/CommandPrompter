/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package dev.cyr1en.promptpaper.hook.geyser;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.CommandPrompterConfig;
import dev.cyr1en.promptpaper.config.PaperConfigLoader;
import dev.cyr1en.promptpaper.hook.hooks.GeyserHook;
import dev.cyr1en.promptpaper.util.PluginLogger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.event.server.ServerLoadEvent;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.geysermc.api.Geyser;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.event.GeyserEventBus;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.InventoryHolder;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.text.Component;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.text.format.NamedTextColor;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.text.format.TextDecoration;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundRenameItemPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.invocation.InvocationOnMock;

class GeyserAnvilPatchTest {
  @Test
  void synchronizesServerOutputsAndRetainsAuthoritativeCosts() throws Exception {
    try (var locale = mockStatic(GeyserLocale.class)) {
      locale.when(() -> GeyserLocale.getLocaleStringLog(anyString())).thenReturn("Unavailable");
      var geyser = mock(GeyserImpl.class);
      var config = mock(GeyserConfig.class);
      when(geyser.config()).thenReturn(config);
      when(geyser.getLogger()).thenReturn(mock(GeyserLogger.class));
      when(geyser.configDirectory()).thenReturn(Path.of(System.getProperty("user.dir")));
      when(geyser.eventBus()).thenReturn(new GeyserEventBus());
      doAnswer(InvocationOnMock::callRealMethod)
          .when(geyser)
          .provider(any(Class.class), any(), any());
      try (var impl = mockStatic(GeyserImpl.class);
          var api = mockStatic(Geyser.class)) {
        impl.when(GeyserImpl::getInstance).thenReturn(geyser);
        api.when(() -> Geyser.api(GeyserApi.class)).thenReturn(geyser);
        GeyserBootstrap bootstrap = mock(GeyserBootstrap.class);
        when(geyser.getBootstrap()).thenReturn(bootstrap);
        when(bootstrap.getResourceOrThrow(anyString()))
            .thenAnswer(
                call -> getClass().getClassLoader().getResourceAsStream(call.getArgument(0)));
        Registries.BLOCK_ENTITIES.load();
        GeyserConfig.GameplayConfig gameplay = mock(GeyserConfig.GameplayConfig.class);
        when(config.gameplay()).thenReturn(gameplay);
        GeyserSession session = mock(GeyserSession.class);
        when(session.locale()).thenReturn("en_us");
        PatchedAnvilContainer container =
            spy(new PatchedAnvilContainer(session, "Prompt", 1, 3, ContainerType.ANVIL));
        doReturn(GeyserItemStack.EMPTY).when(container).getItem(anyInt());
        GeyserItemStack input = item("Prompt");
        GeyserItemStack output = item("Submit");
        doReturn(input).when(container).getItem(0);
        doReturn(output).when(container).getItem(2);
        var original = InventoryTranslator.inventoryTranslator(ContainerType.ANVIL);
        var restore = GeyserAnvilPatch.install();
        PatchedAnvilTranslator translator;
        try {
          translator =
              assertInstanceOf(
                  PatchedAnvilTranslator.class,
                  InventoryTranslator.inventoryTranslator(ContainerType.ANVIL));
          assertThrows(IllegalStateException.class, GeyserAnvilPatch::install);
        } finally {
          restore.run();
        }
        assertSame(original, InventoryTranslator.inventoryTranslator(ContainerType.ANVIL));
        List<InventorySlotPacket> packets = new ArrayList<>();
        doAnswer(
                call -> {
                  if (call.getArgument(0) instanceof InventorySlotPacket packet) {
                    packets.add(packet);
                  }
                  return null;
                })
            .when(session)
            .sendUpstreamPacket(any());

        translator.updateInventory(session, container);
        translator.updateProperty(session, container, 0, 0);
        translator.updateSlot(session, container, 0);
        assertTrue(packets.isEmpty(), "Do not populate UI slots before the virtual anvil opens");
        assertTrue(container.isUseJavaLevelCost(), "Keep properties received while opening");
        container.setUseJavaLevelCost(false);
        var openAcknowledgement = new AtomicReference<Runnable>();
        doAnswer(
                call -> {
                  openAcknowledgement.set(call.getArgument(2));
                  return null;
                })
            .when(session)
            .sendNetworkLatencyStackPacket(anyLong(), eq(true), any());
        var holder = new InventoryHolder<>(session, container, translator);
        doReturn(holder).when(session).getInventoryHolder();
        translator.openInventory(session, container);
        container.setDisplayed(true);
        translator.updateInventory(session, container);
        assertTrue(
            packets.isEmpty(), "Wait for Bedrock's open acknowledgement before sending input");
        assertNotNull(openAcknowledgement.get());
        openAcknowledgement.get().run();
        assertTrue(
            packets.stream().anyMatch(packet -> packet.getSlot() == 1),
            "An initially different output name is not a user edit; the input must be sent");
        assertEquals(
            "Prompt",
            slot(packets, 1).getItem().getTag().getCompound("display").getString("Name"),
            "Do not expose the item translator's synthetic reset in the editable field");
        assertEquals("Prompt", slot(packets, 1).getItem().getTag().getString("testName"));
        assertEquals(
            "§rPrompt",
            input.getItemData(session).getTag().getCompound("display").getString("Name"),
            "Anvil rendering must not mutate the cached item used outside this screen");
        assertEquals(
            output.getItemData(session),
            slot(packets, 50).getItem(),
            "A full refresh must include the server's result item");
        assertEquals(50, packets.getLast().getSlot(), "Send the result after the inputs");
        assertFalse(container.isUseJavaLevelCost(), "Do not invent an authoritative cost");

        translator.updateProperty(session, container, 0, 0);
        assertEquals(-1, slot(packets, 1).getItem().getTag().getInt("RepairCost"));
        doReturn(item(null)).when(container).getItem(0);
        doReturn(item(null)).when(container).getItem(2);
        translator.updateInventory(session, container);
        assertEquals(
            -1,
            slot(packets, 1).getItem().getTag().getInt("RepairCost"),
            "The default unnamed paper prompt must also reserve the rename surcharge");
        container.checkForRename(session, "Answer");
        assertTrue(
            container.isUseJavaLevelCost(), "Unchanged Java properties are not resent on rename");
        verify(session).sendDownstreamGamePacket(new ServerboundRenameItemPacket("Answer"));
        packets.clear();
        translator.updateSlot(session, container, 2);
        assertTrue(
            packets.stream().noneMatch(packet -> packet.getSlot() == 1),
            "Do not reset an actively edited name field");

        // A subsequent server cost must replace zero, including across input changes.
        translator.updateProperty(session, container, 0, 7);
        doReturn(item("Replacement")).when(container).getItem(0);
        doReturn(item("Replacement")).when(container).getItem(2);
        translator.updateInventory(session, container);
        assertTrue(container.isUseJavaLevelCost());
        assertEquals(7, slot(packets, 1).getItem().getTag().getInt("RepairCost"));
        assertEquals(7, container.getJavaLevelCost());

        // Pre-account for typing's one-level surcharge even when names initially match.
        translator.updateProperty(session, container, 0, 0);
        assertEquals(-1, slot(packets, 1).getItem().getTag().getInt("RepairCost"));
        doReturn(item("Cancel")).when(container).getItem(1);
        translator.updateInventory(session, container);
        assertTrue(container.isUseJavaLevelCost());
        assertEquals(
            -1,
            PatchedAnvilUpdater.INSTANCE.calcLevelCost(session, container, true),
            "An invalid material is still invalid; do not fabricate a recipe");

        container.setNewName("Editing");
        packets.clear();
        translator.updateInventory(session, container);
        translator.updateSlot(session, container, 0);
        assertTrue(
            packets.stream().noneMatch(packet -> packet.getSlot() == 1),
            "Preserve active text even when the repair-cost target is the material slot");
        container.setNewName(null);

        doReturn(GeyserItemStack.EMPTY).when(container).getItem(1);
        doReturn(GeyserItemStack.EMPTY).when(container).getItem(2);
        translator.updateInventory(session, container);
        assertEquals(ItemData.AIR, slot(packets, 50).getItem(), "Clear stale results too");
        assertEquals(
            0,
            slot(packets, 1).getItem().getTag().getInt("RepairCost"),
            "An empty output is not a free operation");

        GeyserItemStack styledInput = item("test");
        when(styledInput.getComponent(DataComponentTypes.CUSTOM_NAME))
            .thenReturn(Component.text("test").decoration(TextDecoration.ITALIC, false));
        doReturn(styledInput).when(container).getItem(0);
        doReturn(item("test")).when(container).getItem(2);
        container.setNewName(null);
        assertEquals(
            0,
            PatchedAnvilUpdater.INSTANCE.calcLevelCost(session, container, true),
            "Identical Bedrock names with different Java decoration defaults are not a rename");
        container.setNewName("test");
        assertEquals(
            0,
            PatchedAnvilUpdater.INSTANCE.calcLevelCost(session, container, true),
            "Submitting the prefilled name without the synthetic reset is not a rename");
        container.setNewName(null);

        GeyserItemStack coloredOutput = item("test");
        when(coloredOutput.getComponent(DataComponentTypes.CUSTOM_NAME))
            .thenReturn(Component.text("test", NamedTextColor.RED));
        doReturn(coloredOutput).when(container).getItem(2);
        assertEquals(
            1,
            PatchedAnvilUpdater.INSTANCE.calcLevelCost(session, container, true),
            "A visible change to the Bedrock name still counts as a rename");

        // An incremental first update has no previous repair-cost target to restore.
        container.setLastTargetSlot(-1);
        assertDoesNotThrow(() -> translator.updateSlot(session, container, 0));
        verify(container, never()).getItem(-1);

        var position = Vector3i.from(1, 2, 3);
        container.setHolderPosition(position);
        var next = translator.createInventory(session, "Prompt", 2, ContainerType.ANVIL);
        try (var worldSpace = mockStatic(InventoryUtils.class)) {
          worldSpace
              .when(() -> InventoryUtils.findAvailableWorldSpace(session))
              .thenReturn(position);
          assertTrue(translator.canReuseInventory(session, next, container));
          assertTrue(next.isReadyForInput(), "Reusing an open window must retain its readiness");
          container.setReadyForInput(false);
          assertFalse(
              translator.canReuseInventory(session, next, container),
              "Do not reuse a window whose open acknowledgement is still pending");
        }
        packets.clear();
        container.setReadyForInput(false);
        doReturn(null).when(session).getInventoryHolder();
        openAcknowledgement.get().run();
        assertFalse(
            container.isReadyForInput(),
            "Ignore an acknowledgement for a closed or replaced anvil");
        assertTrue(packets.isEmpty());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void hookCanLoadWithoutOptionalGeyserClasses(boolean optedIn) throws Exception {
    var isolated =
        new ClassLoader(getClass().getClassLoader()) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.geysermc.")) throw new ClassNotFoundException(name);
            if (!name.equals(GeyserHook.class.getName())
                && !name.startsWith("dev.cyr1en.promptpaper.hook.geyser.")) {
              return super.loadClass(name, resolve);
            }
            var loaded = findLoadedClass(name);
            if (loaded != null) return loaded;
            try (var stream = getResourceAsStream(name.replace('.', '/') + ".class")) {
              if (stream == null) throw new ClassNotFoundException(name);
              byte[] bytes = stream.readAllBytes();
              var type = defineClass(name, bytes, 0, bytes.length);
              if (resolve) resolveClass(type);
              return type;
            } catch (java.io.IOException failure) {
              throw new ClassNotFoundException(name, failure);
            }
          }
        };
    var plugin = mock(CommandPrompter.class);
    var loader = mock(PaperConfigLoader.class);
    var config = mock(CommandPrompterConfig.class);
    when(plugin.getConfigLoader()).thenReturn(loader);
    when(plugin.getLogger()).thenReturn(mock(Logger.class));
    when(loader.getConfig()).thenReturn(config);
    when(config.geyserAnvilPatch()).thenReturn(optedIn);
    var type = isolated.loadClass(GeyserHook.class.getName());
    var hook = type.getConstructor(CommandPrompter.class).newInstance(plugin);
    type.getMethod("onEnable").invoke(hook);
    assertEquals(false, type.getMethod("isAnvilPatchEnabled").invoke(hook));
    type.getMethod("onServerLoaded", ServerLoadEvent.class)
        .invoke(hook, mock(ServerLoadEvent.class));
    type.getMethod("onDisable").invoke(hook);
  }

  @Test
  void disabledHookNeverInstallsPatch() {
    var plugin = mock(CommandPrompter.class);
    var loader = mock(PaperConfigLoader.class);
    var config = mock(CommandPrompterConfig.class);
    when(plugin.getConfigLoader()).thenReturn(loader);
    when(loader.getConfig()).thenReturn(config);
    var hook = new GeyserHook(plugin);
    try (var patch = mockStatic(GeyserAnvilPatch.class);
        var registration = mockStatic(BedrockAnvilItems.class)) {
      hook.onEnable();
      hook.onServerLoaded(mock(ServerLoadEvent.class));
      hook.onDisable();
      patch.verifyNoInteractions();
      registration.verifyNoInteractions();
    }
  }

  @Test
  void incompatibleTranslatorApiKeepsFallbackAndReleasesSubscriptions() {
    var plugin = mock(CommandPrompter.class);
    var loader = mock(PaperConfigLoader.class);
    var config = mock(CommandPrompterConfig.class);
    when(plugin.getConfigLoader()).thenReturn(loader);
    when(plugin.getLogger()).thenReturn(mock(Logger.class));
    when(loader.getConfig()).thenReturn(config);
    when(config.geyserAnvilPatch()).thenReturn(true);
    var items = mock(BedrockAnvilItems.class);
    when(items.isReady()).thenReturn(true);
    var hook = new GeyserHook(plugin);
    try (var registration = mockStatic(BedrockAnvilItems.class);
        var patch = mockStatic(GeyserAnvilPatch.class)) {
      registration.when(() -> BedrockAnvilItems.subscribe(plugin)).thenReturn(items);
      patch.when(GeyserAnvilPatch::install).thenThrow(new NoSuchMethodError("Changed Geyser API"));
      hook.onEnable();
      assertDoesNotThrow(() -> hook.onServerLoaded(mock(ServerLoadEvent.class)));
      assertFalse(hook.isAnvilPatchEnabled());
      patch.verify(GeyserAnvilPatch::install);
      hook.onDisable();
      hook.onDisable();
      verify(items).close();
    }
  }

  @Test
  void enabledHookWaitsForServerLoadAndRestoresOnce() {
    var plugin = mock(CommandPrompter.class);
    var loader = mock(PaperConfigLoader.class);
    var config = mock(CommandPrompterConfig.class);
    when(plugin.getConfigLoader()).thenReturn(loader);
    when(plugin.getPluginLogger()).thenReturn(mock(PluginLogger.class));
    when(loader.getConfig()).thenReturn(config);
    when(config.geyserAnvilPatch()).thenReturn(true);
    var hook = new GeyserHook(plugin);
    var restore = mock(Runnable.class);
    var items = mock(BedrockAnvilItems.class);
    when(items.isReady()).thenReturn(true);
    var event = mock(ServerLoadEvent.class);
    try (var patch = mockStatic(GeyserAnvilPatch.class);
        var registration = mockStatic(BedrockAnvilItems.class)) {
      registration.when(() -> BedrockAnvilItems.subscribe(plugin)).thenReturn(items);
      patch.when(GeyserAnvilPatch::install).thenReturn(restore);
      hook.onEnable();
      registration.verify(() -> BedrockAnvilItems.subscribe(plugin));
      patch.verifyNoInteractions();
      hook.onServerLoaded(event);
      hook.onServerLoaded(event);
      verify(items, times(1)).completeRegistration();
      patch.verify(GeyserAnvilPatch::install, times(1));
      hook.onDisable();
      hook.onDisable();
      verify(restore, times(1)).run();
      verify(items, times(1)).close();
    }
  }

  @Test
  void missingCustomDefinitionsKeepsSafeFallback() {
    var plugin = mock(CommandPrompter.class);
    var loader = mock(PaperConfigLoader.class);
    var config = mock(CommandPrompterConfig.class);
    when(plugin.getConfigLoader()).thenReturn(loader);
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    when(loader.getConfig()).thenReturn(config);
    when(config.geyserAnvilPatch()).thenReturn(true);
    var items = mock(BedrockAnvilItems.class);
    var hook = new GeyserHook(plugin);
    try (var registration = mockStatic(BedrockAnvilItems.class);
        var patch = mockStatic(GeyserAnvilPatch.class)) {
      registration.when(() -> BedrockAnvilItems.subscribe(plugin)).thenReturn(items);
      hook.onEnable();
      hook.onServerLoaded(mock(ServerLoadEvent.class));
      assertFalse(hook.isAnvilPatchEnabled());
      patch.verifyNoInteractions();
      hook.onDisable();
      verify(items).close();
    }
  }

  @Test
  void failedDefinitionCompletionDoesNotInstallTranslator() {
    var plugin = mock(CommandPrompter.class);
    var loader = mock(PaperConfigLoader.class);
    var config = mock(CommandPrompterConfig.class);
    when(plugin.getConfigLoader()).thenReturn(loader);
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    when(loader.getConfig()).thenReturn(config);
    when(config.geyserAnvilPatch()).thenReturn(true);
    var items = mock(BedrockAnvilItems.class);
    doThrow(new IllegalStateException("Missing generated repair definition"))
        .when(items)
        .completeRegistration();
    var hook = new GeyserHook(plugin);
    try (var registration = mockStatic(BedrockAnvilItems.class);
        var patch = mockStatic(GeyserAnvilPatch.class)) {
      registration.when(() -> BedrockAnvilItems.subscribe(plugin)).thenReturn(items);
      hook.onEnable();
      hook.onServerLoaded(mock(ServerLoadEvent.class));
      assertFalse(hook.isAnvilPatchEnabled());
      patch.verifyNoInteractions();
      hook.onDisable();
      verify(items).close();
    }
  }

  private static InventorySlotPacket slot(List<InventorySlotPacket> packets, int slot) {
    return packets.stream()
        .filter(packet -> packet.getSlot() == slot)
        .reduce((first, last) -> last)
        .orElseThrow();
  }

  private static GeyserItemStack item(String name) {
    GeyserItemStack item = mock(GeyserItemStack.class);
    when(item.getComponent(DataComponentTypes.CUSTOM_NAME))
        .thenReturn(name == null ? null : Component.text(name));
    when(item.getComponentElseGet(eq(DataComponentTypes.REPAIR_COST), any())).thenReturn(0);
    when(item.copy()).thenReturn(item);
    when(item.getItemData(any()))
        .thenReturn(
            ItemData.builder()
                .definition(new SimpleItemDefinition("minecraft:paper", 339, false))
                .count(1)
                .tag(
                    NbtMap.builder()
                        .putString("testName", name == null ? "" : name)
                        .putCompound(
                            "display",
                            NbtMap.builder()
                                .putString("Name", name == null ? "" : "§r" + name)
                                .build())
                        .build())
                .build());
    return item;
  }
}
