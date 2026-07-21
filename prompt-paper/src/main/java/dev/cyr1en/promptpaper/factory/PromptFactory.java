package dev.cyr1en.promptpaper.factory;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.preset.PromptDefinition;
import dev.cyr1en.promptpaper.screen.AnvilPromptScreen;
import dev.cyr1en.promptpaper.screen.ChatPromptScreen;
import dev.cyr1en.promptpaper.screen.SignPromptScreen;
import dev.cyr1en.promptpaper.screen.TitleWrapperScreen;
import dev.cyr1en.promptpaper.screen.dialog.DialogCompletionContext;
import dev.cyr1en.promptpaper.screen.playerui.PlayerUIScreen;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.bukkit.entity.Player;

/**
 * The single source of truth for instantiating a runtime prompt screen from a
 * {@link PromptDefinition}. Replaces / subsumes the old {@code ScreenRouter} tag-key routing
 * so that preset prompts and legacy inline prompts share one code path.
 *
 * <h2>Two entry points</h2>
 *
 * <ul>
 *   <li>{@link #create(Player, PromptDefinition, DialogCompletionContext)} — the
 *       <b>new</b> path. Takes a JSON-backed {@link PromptDefinition} (preset or transient
 *       inline) and dispatches via a {@code switch} on the sealed hierarchy to the
 *       appropriate screen constructor.
 *   <li>{@link #createFromTag(Player, PromptTag, DialogCompletionContext)} — the
 *       <b>legacy</b> convenience. Maps the {@link PromptTag} to a transient
 *       {@link PromptDefinition} via {@link InlineTagMapper} and delegates. For dialogs
 *       the original {@link PromptTag} is passed straight through to the screen so its
 *       {@code subTags()}, filter syntax, and dialog-specific data are preserved
 *       end-to-end.
 * </ul>
 *
 * <h2>Material resolution</h2>
 *
 * <p>The factory owns a {@link MaterialMapper} used to resolve any {@code button_icon}
 * strings in the {@link PromptDefinition} to a Bukkit {@code Material} (with a {@code PAPER}
 * fallback and a non-fatal warning). Scope 3 only needs the mapper to be available; future
 * scopes will thread the resolved materials into the screen implementations.
 *
 * <h2>Dialog support</h2>
 *
 * <p>Both inline dialogs ({@code <d:…>}) and JSON-sourced
 * {@code DialogPrompt} (preset dialogs) are supported. The factory dispatches the
 * JSON case to {@link dev.cyr1en.promptpaper.screen.DialogPromptScreen}'s
 * post-refactor constructor, which reads the {@code base} and
 * {@code dialog_type} blocks directly from the JSON model. Inline dialogs
 * keep the legacy {@code PromptTag}-based path so the filter syntax and
 * compound-tag structure are preserved end-to-end.
 */
public class PromptFactory {

  private final CommandPrompter plugin;
  private final List<ScreenProvider> providers;
  private final MaterialMapper materialMapper;

  /**
   * Constructs the factory and eagerly loads the available {@link ScreenProvider}s via
   * {@link ServiceLoader}. The same discovery is performed by {@code ScreenRouter}; both
   * code paths coexist so legacy and new flows can each have their own provider list.
   */
  public PromptFactory(CommandPrompter plugin) {
    this.plugin = plugin;
    this.providers = new ArrayList<>();
    
    List<ScreenProvider> loaded = new ArrayList<>();
    try {
      var loader = ServiceLoader.load(ScreenProvider.class, plugin.getClass().getClassLoader());
      for (var provider : loader) {
        loaded.add(provider);
      }
    } catch (Exception e) {
      plugin.getPluginLogger().warn("Failed to load screen providers: " + e.getMessage());
    }

    if (loaded.isEmpty()) {
      plugin.getPluginLogger().info("No GUI screen providers found — GUI prompts will fall back to chat.");
    } else {
      // Sort providers descending to fall back to the newest available NMS module.
      loaded.sort((p1, p2) -> {
        String t1 = p1.getTargetVersion();
        String t2 = p2.getTargetVersion();
        if (t1.equals("unknown")) return 1;
        if (t2.equals("unknown")) return -1;
        
        String[] v1 = t1.split("\\.");
        String[] v2 = t2.split("\\.");
        for (int i = 0; i < Math.max(v1.length, v2.length); i++) {
          try {
            int part1 = i < v1.length ? Integer.parseInt(v1[i]) : 0;
            int part2 = i < v2.length ? Integer.parseInt(v2[i]) : 0;
            if (part1 != part2) return Integer.compare(part2, part1); // Descending
          } catch (NumberFormatException e) {
            return t2.compareTo(t1); // Fallback to string comparison
          }
        }
        return 0;
      });

      String serverVersion = org.bukkit.Bukkit.getMinecraftVersion();
      ScreenProvider bestMatch = null;
      
      for (var provider : loaded) {
        String target = provider.getTargetVersion();
        if (serverVersion.equals(target) || serverVersion.startsWith(target + ".")) {
          bestMatch = provider;
          break;
        }
      }

      if (bestMatch != null) {
        providers.add(bestMatch);
        plugin.getPluginLogger().info("Loaded screen provider for Minecraft " + serverVersion + ": " + bestMatch.getClass().getName());
      } else {
        // Fallback to the newest provider.
        bestMatch = loaded.get(0);
        providers.add(bestMatch);
        plugin.getPluginLogger().warn("No specific NMS module found for Minecraft " + serverVersion + ".");
        plugin.getPluginLogger().warn("Falling back to the NMS module for " + bestMatch.getTargetVersion() + " (" + bestMatch.getClass().getSimpleName() + ").");
        plugin.getPluginLogger().warn("Most features should still work, but some GUI elements might fall back to chat if internal server code has changed.");
      }
    }

    this.materialMapper = new MaterialMapper(plugin.getPluginLogger());
  }

  /** The material mapper owned by this factory. Exposed for screens and tests. */
  public MaterialMapper getMaterialMapper() {
    return materialMapper;
  }

  /** Number of loaded providers (exposed for tests and diagnostics). */
  public int providerCount() {
    return providers.size();
  }

  // ------------------------------------------------------------------
  // New entry point: PromptDefinition → screen
  // ------------------------------------------------------------------

  /** Convenience overload that defaults the completion context to {@code null}. */
  public InputScreen create(Player player, PromptDefinition def) {
    return create(player, def, null);
  }

  /**
   * Instantiates the appropriate {@link InputScreen} for the given {@link PromptDefinition}.
   *
   * @param player the player who will see the prompt
   * @param def the JSON-backed prompt definition (preset or transient inline)
   * @param context the dialog completion context for {@code d:tab} dialogs; {@code null}
   *     for non-dialog prompts
   * @return a non-null, unopened screen — call {@code .open()} on the result
   */
  public InputScreen create(Player player, PromptDefinition def, DialogCompletionContext context) {
    if (def == null) throw new IllegalArgumentException("PromptDefinition must not be null");
    plugin.getPluginLogger().debug(
        "PromptFactory.create: player=" + player.getName()
                + " type=" + def.type()
                + " id=" + def.id());
    var screen = switch (def) {
      case dev.cyr1en.promptpaper.preset.ChatPrompt chat -> createChat(player, chat);
      case dev.cyr1en.promptpaper.preset.AnvilPrompt anvil -> createAnvil(player, anvil);
      case dev.cyr1en.promptpaper.preset.SignPrompt sign -> createSign(player, sign);
      case dev.cyr1en.promptpaper.preset.PlayerUiPrompt pui -> createPlayerUi(player, pui);
      case dev.cyr1en.promptpaper.preset.DialogPrompt dialog -> createDialog(player, dialog, context);
    };
    return wrapWithTitle(player, def, screen);
  }

  // ------------------------------------------------------------------
  // Legacy entry point: PromptTag → screen
  // ------------------------------------------------------------------

  /** Convenience overload that defaults the completion context to {@code null}. */
  public InputScreen createFromTag(Player player, PromptTag tag) {
    return createFromTag(player, tag, null);
  }

  /**
   * Convenience entry point for legacy callers that still hold a {@link PromptTag}. Maps
   * the tag to a transient {@link PromptDefinition} and delegates to
   * {@link #create(Player, PromptDefinition, DialogCompletionContext)}.
   *
   * <p>Dialogs (key {@code "d"} or compound tags) bypass the JSON mapping: the original
   * {@link PromptTag} is passed straight to the dialog screen so its filter syntax and
   * sub-tag structure are preserved.
   */
  public InputScreen createFromTag(Player player, PromptTag tag, DialogCompletionContext context) {
    if (tag == null) throw new IllegalArgumentException("PromptTag must not be null");
    if (tag.isPreset()) {
      var def = plugin.getPresetRegistry().getPrompt(tag.displayText())
          .orElseThrow(() -> new IllegalStateException("Preset prompt not found: " + tag.displayText()));
      return create(player, def, context);
    }
    if ("d".equals(tag.key()) || tag.isCompound()) {
      // Pass the tag directly to preserve filter syntax and constraints.
      var promptConfig = plugin.getConfigLoader().getPromptConfig();
      var dialogScreen = new dev.cyr1en.promptpaper.screen.DialogPromptScreen(
          plugin, player, tag, promptConfig, context);
      return wrapWithTagTitle(player, tag, dialogScreen);
    }
    var def = InlineTagMapper.toPromptDefinition(tag);
    return create(player, def, context);
  }

  // ------------------------------------------------------------------
  // Per-variant screen builders
  // ------------------------------------------------------------------

  private ChatPromptScreen createChat(Player player, dev.cyr1en.promptpaper.preset.ChatPrompt chat) {
    return new ChatPromptScreen(plugin, player, chat);
  }

  private AnvilPromptScreen createAnvil(Player player, dev.cyr1en.promptpaper.preset.AnvilPrompt anvil) {
    // Resolve icons through material mapper to warn about invalid names.
    materialMapper.resolveOrDefault(anvil.leftButton().buttonIcon(),
        "anvil prompt '" + anvil.id() + "' left_button");
    materialMapper.resolveOrDefault(anvil.rightButton().buttonIcon(),
        "anvil prompt '" + anvil.id() + "' right_button");
    return new AnvilPromptScreen(plugin, player, anvil, providers);
  }

  private SignPromptScreen createSign(Player player, dev.cyr1en.promptpaper.preset.SignPrompt sign) {
    return new SignPromptScreen(plugin, player, sign, providers);
  }

  private PlayerUIScreen createPlayerUi(Player player, dev.cyr1en.promptpaper.preset.PlayerUiPrompt pui) {
    var tag = new PromptTag(
        "<p:" + pui.promptText() + ">",
        "p",
        pui.filter(),
        pui.promptText(),
        pui.sanitize(),
        null);
    // Resolve navigation button icons to warn about invalid names.
    resolveUiButton(pui.cancelButton(), pui.id(), "cancel_button");
    resolveUiButton(pui.previousButton(), pui.id(), "previous_button");
    resolveUiButton(pui.nextButton(), pui.id(), "next_button");
    return new PlayerUIScreen(plugin, player, tag, pui);
  }

  /**
   * Builds a {@link dev.cyr1en.promptpaper.screen.DialogPromptScreen} for a
   * JSON-sourced {@code DialogPrompt}. The screen reads the {@code base}
   * and {@code dialog_type} blocks directly from the JSON model — no
   * {@link PromptTag} is involved. The {@link DialogCompletionContext} is
   * forwarded so tab-completion dialogs can look up completions at
   * build time.
   */
  private dev.cyr1en.promptpaper.screen.DialogPromptScreen createDialog(
      Player player,
      dev.cyr1en.promptpaper.preset.DialogPrompt dialog,
      DialogCompletionContext context) {
    var promptConfig = plugin.getConfigLoader().getPromptConfig();
    return new dev.cyr1en.promptpaper.screen.DialogPromptScreen(
        plugin, player, dialog, promptConfig, context);
  }

  private void resolveUiButton(dev.cyr1en.promptpaper.preset.UIButton button, String promptId, String which) {
    if (button == null) return;
    materialMapper.resolveOrDefault(button.buttonIcon(),
        "player_ui prompt '" + promptId + "' " + which);
  }

  // ------------------------------------------------------------------
  // Title wrapper
  // ------------------------------------------------------------------

  /**
   * Wraps the given screen in a {@link TitleWrapperScreen} if the {@link PromptDefinition}
   * carries a non-null {@code titleDisplay} config. If the config's {@code main} is empty, the
   * definition's prompt/display text is injected as the main title.
   *
   * @param player the target player
   * @param def the prompt definition that produced the screen
   * @param screen the freshly built screen (not yet opened)
   * @return the original screen, or a {@link TitleWrapperScreen} wrapping it
   */
  private InputScreen wrapWithTitle(Player player, PromptDefinition def, InputScreen screen) {
    var raw = def.titleDisplay();
    if (raw == null) return screen;
    var resolved = resolveTitleMain(raw, displayTextFor(def));
    return new TitleWrapperScreen(screen, resolved, player, plugin.getScheduler(), plugin);
  }

  /**
   * Wraps the given screen in a {@link TitleWrapperScreen} if the inline {@link PromptTag}
   * carries a non-null {@code title} config. Used for the dialog path in {@link #createFromTag}
   * which bypasses {@link #create}.
   */
  private InputScreen wrapWithTagTitle(Player player, PromptTag tag, InputScreen screen) {
    var raw = tag.title();
    if (raw == null) return screen;
    var resolved = resolveTitleMain(raw, tag.displayText());
    return new TitleWrapperScreen(screen, resolved, player, plugin.getScheduler(), plugin);
  }

  /**
   * If {@code raw.main()} is empty, returns a new {@link TitleConfig} with {@code main} set to
   * {@code fallbackText}; otherwise returns {@code raw} unchanged.
   */
  private static TitleConfig resolveTitleMain(TitleConfig raw, String fallbackText) {
    if (raw.main() == null || raw.main().isEmpty()) {
      var text = fallbackText == null ? "" : fallbackText;
      return new TitleConfig(text, raw.sub(), raw.ticks());
    }
    return raw;
  }

  /**
   * Extracts the display/prompt text from a {@link PromptDefinition} for use as the title main
   * fallback when the title config's main is empty.
   */
  private static String displayTextFor(PromptDefinition def) {
    return switch (def) {
      case dev.cyr1en.promptpaper.preset.ChatPrompt chat -> chat.promptText();
      case dev.cyr1en.promptpaper.preset.AnvilPrompt anvil -> anvil.promptText();
      case dev.cyr1en.promptpaper.preset.SignPrompt sign -> sign.promptText();
      case dev.cyr1en.promptpaper.preset.PlayerUiPrompt pui -> pui.promptText();
      case dev.cyr1en.promptpaper.preset.DialogPrompt dialog -> dialog.title();
    };
  }
}
