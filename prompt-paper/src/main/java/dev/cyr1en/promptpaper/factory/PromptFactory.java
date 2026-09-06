package dev.cyr1en.promptpaper.factory;

import dev.cyr1en.promptcore.ItemSource;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.item.catalog.CatalogSnapshot;
import dev.cyr1en.promptpaper.preset.AnvilPrompt;
import dev.cyr1en.promptpaper.preset.ChatPrompt;
import dev.cyr1en.promptpaper.preset.ConfirmationPrompt;
import dev.cyr1en.promptpaper.preset.DialogPrompt;
import dev.cyr1en.promptpaper.preset.ItemPrompt;
import dev.cyr1en.promptpaper.preset.PlayerUiPrompt;
import dev.cyr1en.promptpaper.preset.PromptDefinition;
import dev.cyr1en.promptpaper.preset.SignPrompt;
import dev.cyr1en.promptpaper.preset.UIButton;
import dev.cyr1en.promptpaper.screen.AnvilPromptScreen;
import dev.cyr1en.promptpaper.screen.ChatPromptScreen;
import dev.cyr1en.promptpaper.screen.DialogPromptScreen;
import dev.cyr1en.promptpaper.screen.SignPromptScreen;
import dev.cyr1en.promptpaper.screen.TitleWrapperScreen;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationScreenFactory;
import dev.cyr1en.promptpaper.screen.dialog.DialogCompletionContext;
import dev.cyr1en.promptpaper.screen.item.ItemPromptScreen;
import dev.cyr1en.promptpaper.screen.playerui.PlayerUIScreen;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Builds runtime screens from preset definitions and inline tags. Presentation placeholders are
 * expanded once before construction. Inline dialogs retain their tag structure so the screen can
 * interpret compound rows and filter syntax.
 *
 * <p>GUI providers are discovered with {@link ServiceLoader} and selected for the current server
 * version. The material mapper validates configured button icons.
 */
public class PromptFactory {

  private static final List<String> SUPPORTED_TARGETS = List.of("26.1", "26.2");

  private final CommandPrompter plugin;
  private final List<ScreenProvider> providers;
  private final MaterialMapper materialMapper;
  private final PromptPresentationExpander presentationExpander;

  /** Loads the GUI providers available to this plugin. */
  public PromptFactory(CommandPrompter plugin) {
    this(plugin, null);
  }

  /**
   * Package-private constructor that also accepts a {@link PromptPresentationExpander} so tests can
   * inject a deterministic (non-idempotent) expansion function. {@code null} wires the production
   * PlaceholderAPI-backed expander.
   */
  PromptFactory(CommandPrompter plugin, PromptPresentationExpander presentationExpander) {
    this.plugin = plugin;
    this.providers = new ArrayList<>();
    this.presentationExpander =
        presentationExpander != null
            ? presentationExpander
            : PromptPresentationExpander.forPlugin(plugin);

    List<ScreenProvider> loaded = new ArrayList<>();
    try {
      var loader = ServiceLoader.load(ScreenProvider.class, plugin.getClass().getClassLoader());
      for (var provider : loader) {
        loaded.add(provider);
      }
    } catch (ServiceConfigurationError | LinkageError | RuntimeException e) {
      plugin.getPluginLogger().warn("Failed to load screen providers: " + e.getMessage());
    }

    String serverVersion = Bukkit.getMinecraftVersion();
    if (loaded.isEmpty()) {
      plugin
          .getPluginLogger()
          .info("No GUI screen providers found — GUI prompts will fall back to chat.");
      plugin
          .getPluginLogger()
          .warn(
              "Supported screen targets: "
                  + SUPPORTED_TARGETS
                  + "; current server version: "
                  + serverVersion
                  + ".");
    } else {
      // Prefer the highest matching target when more than one module is on the
      // class path. An unmatched server deliberately leaves this list empty.
      loaded.sort(
          (p1, p2) -> {
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
        plugin
            .getPluginLogger()
            .info(
                "Loaded screen provider for Minecraft "
                    + serverVersion
                    + ": "
                    + bestMatch.getClass().getName());
      } else {
        plugin
            .getPluginLogger()
            .warn("No screen provider matches Minecraft " + serverVersion + ".");
        plugin
            .getPluginLogger()
            .warn(
                "Supported screen targets: "
                    + SUPPORTED_TARGETS
                    + "; current server version: "
                    + serverVersion
                    + ". GUI prompts will fall back to chat.");
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

  /** Convenience overload that defaults the completion context to {@code null}. */
  public InputScreen create(Player player, PromptDefinition def) {
    return create(player, def, null);
  }

  /**
   * Instantiates the appropriate {@link InputScreen} for the given {@link PromptDefinition}.
   *
   * <p>This is the single presentation-materialization boundary: the raw definition is expanded
   * exactly once via {@link PromptPresentationExpander} and the expanded copy is handed to the
   * private {@link #createExpanded} builder. Registry/session models stay raw; every render gets a
   * fresh expanded immutable copy.
   *
   * @param player the player who will see the prompt
   * @param def the JSON-backed prompt definition (preset or transient inline)
   * @param context the dialog completion context for {@code d:tab} dialogs; {@code null} for
   *     non-dialog prompts
   * @return a non-null, unopened screen — call {@code .open()} on the result
   */
  public InputScreen create(Player player, PromptDefinition def, DialogCompletionContext context) {
    if (def == null) throw new IllegalArgumentException("PromptDefinition must not be null");
    plugin
        .getPluginLogger()
        .debug(
            "PromptFactory.create: player="
                + player.getName()
                + " type="
                + def.type()
                + " id="
                + def.id());
    return createExpanded(player, presentationExpander.expand(player, def), context);
  }

  /**
   * Builds the screen from an already-expanded definition. Never expands again — the public {@link
   * #create(Player, PromptDefinition, DialogCompletionContext)} is the only entry point allowed to
   * materialize placeholders.
   */
  private InputScreen createExpanded(
      Player player, PromptDefinition def, DialogCompletionContext context) {
    var screen =
        switch (def) {
          case ChatPrompt chat -> createChat(player, chat);
          case AnvilPrompt anvil -> createAnvil(player, anvil);
          case SignPrompt sign -> createSign(player, sign);
          case PlayerUiPrompt pui -> createPlayerUi(player, pui);
          case DialogPrompt dialog -> createDialog(player, dialog, context);
          case ConfirmationPrompt confirmation -> createConfirmation(player, confirmation);
          case ItemPrompt item -> createItem(player, item);
        };
    return wrapWithTitle(player, def, screen);
  }

  /** Convenience overload that defaults the completion context to {@code null}. */
  public InputScreen createFromTag(Player player, PromptTag tag) {
    return createFromTag(player, tag, null);
  }

  /**
   * Convenience entry point for legacy callers that still hold a {@link PromptTag}. Maps the tag to
   * a transient {@link PromptDefinition} and delegates to {@link #create(Player, PromptDefinition,
   * DialogCompletionContext)}.
   *
   * <p>Dialogs (key {@code "d"} or compound tags) bypass the JSON mapping: the original {@link
   * PromptTag} is passed straight to the dialog screen so its filter syntax and sub-tag structure
   * are preserved.
   *
   * <p>Expansion is applied exactly once, here:
   *
   * <ul>
   *   <li><b>Presets</b> look up the registry with the <b>raw</b> {@code displayText} (the id is a
   *       semantic key, never expanded) and delegate to {@link #create}, which expands the resolved
   *       definition once.
   *   <li><b>Inline dialogs / compound tags</b> expand a presentation-only copy of the tag via
   *       {@link PromptPresentationExpander#expandInlineDialog} and build the dialog screen
   *       directly from that copy.
   *   <li><b>Other inline tags</b> map the raw tag and delegate to {@link #create}, which expands
   *       once.
   * </ul>
   */
  public InputScreen createFromTag(Player player, PromptTag tag, DialogCompletionContext context) {
    if (tag == null) throw new IllegalArgumentException("PromptTag must not be null");
    if (tag.isPreset()) {
      // Lookup uses the raw displayText — a PAPI-looking preset id must never be expanded.
      var def =
          plugin
              .getPresetRegistry()
              .getPrompt(tag.displayText())
              .orElseThrow(
                  () -> new IllegalStateException("Preset prompt not found: " + tag.displayText()));
      return create(player, def, context);
    }
    var promptConfig = plugin.getConfigLoader().getPromptConfig();
    var mappings =
        promptConfig != null && promptConfig.getScreenMappings() != null
            ? promptConfig.getScreenMappings()
            : Map.<String, ScreenType>of();
    var rawKey = tag.key();
    var normalizedKey = rawKey == null ? "" : rawKey.trim().toLowerCase(Locale.ROOT);
    var screenType = mappings.get(normalizedKey);
    boolean isDialog =
        screenType == ScreenType.DIALOG
            || (screenType == null
                && (ScreenType.fromBuiltInKey(normalizedKey) == ScreenType.DIALOG
                    || tag.isCompound()));
    if (isDialog) {
      // Expand the presentation-only copy of the tag, then build the dialog screen directly so
      // the expanded fields are materialized exactly once.
      var expandedTag = presentationExpander.expandInlineDialog(player, tag);
      var dialogScreen = new DialogPromptScreen(plugin, player, expandedTag, promptConfig, context);
      return wrapWithTagTitle(player, expandedTag, dialogScreen);
    }
    var def = InlineTagMapper.toPromptDefinition(tag, mappings);
    return create(player, def, context);
  }

  private ChatPromptScreen createChat(Player player, ChatPrompt chat) {
    return new ChatPromptScreen(plugin, player, chat);
  }

  private AnvilPromptScreen createAnvil(Player player, AnvilPrompt anvil) {
    // Resolve icons through material mapper to warn about invalid names.
    materialMapper.resolveOrDefault(
        anvil.leftButton().buttonIcon(), "anvil prompt '" + anvil.id() + "' left_button");
    materialMapper.resolveOrDefault(
        anvil.rightButton().buttonIcon(), "anvil prompt '" + anvil.id() + "' right_button");
    return new AnvilPromptScreen(plugin, player, anvil, providers);
  }

  private SignPromptScreen createSign(Player player, SignPrompt sign) {
    return new SignPromptScreen(plugin, player, sign, providers);
  }

  private PlayerUIScreen createPlayerUi(Player player, PlayerUiPrompt pui) {
    var tag =
        new PromptTag(
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
    return new PlayerUIScreen(plugin, player, tag, pui, providers);
  }

  /**
   * Builds a {@link DialogPromptScreen} for a JSON-sourced {@code DialogPrompt}. The screen reads
   * the {@code base} and {@code dialog_type} blocks directly from the JSON model — no {@link
   * PromptTag} is involved. The {@link DialogCompletionContext} is forwarded so tab-completion
   * dialogs can look up completions at build time.
   */
  private DialogPromptScreen createDialog(
      Player player, DialogPrompt dialog, DialogCompletionContext context) {
    var promptConfig = plugin.getConfigLoader().getPromptConfig();
    return new DialogPromptScreen(plugin, player, dialog, promptConfig, context);
  }

  private void resolveUiButton(UIButton button, String promptId, String which) {
    if (button == null) return;
    materialMapper.resolveOrDefault(
        button.buttonIcon(), "player_ui prompt '" + promptId + "' " + which);
  }

  /**
   * Wraps the given screen in a {@link TitleWrapperScreen} if the {@link PromptDefinition} carries
   * a non-null {@code titleDisplay} config. If the config's {@code main} is empty, the definition's
   * prompt/display text is injected as the main title.
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
   * Wraps the given screen in a {@link TitleWrapperScreen} if the inline {@link PromptTag} carries
   * a non-null {@code title} config. Used for the dialog path in {@link #createFromTag} which
   * bypasses {@link #create}.
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

  private ConfirmationPromptScreen createConfirmation(
      Player player, ConfirmationPrompt confirmation) {
    return ConfirmationScreenFactory.create(plugin, materialMapper, player, confirmation);
  }

  private ItemPromptScreen createItem(Player player, ItemPrompt item) {
    if (item.source() == ItemSource.CATALOG) {
      var registry = plugin.getItemCatalogRegistry();
      var snapshot = registry != null ? registry.snapshot() : CatalogSnapshot.empty();
      var category =
          item.category() != null && !item.category().isBlank() ? item.category() : "all";
      if (!snapshot.hasCategory(category)) {
        throw new IllegalStateException(
            "Unknown catalog category: '" + category + "' for prompt '" + item.id() + "'");
      }
      return new ItemPromptScreen(plugin, player, item, snapshot);
    }
    return new ItemPromptScreen(plugin, player, item);
  }

  /**
   * Extracts the display/prompt text from a {@link PromptDefinition} for use as the title main
   * fallback when the title config's main is empty.
   */
  private static String displayTextFor(PromptDefinition def) {
    return switch (def) {
      case ChatPrompt chat -> chat.promptText();
      case AnvilPrompt anvil -> anvil.promptText();
      case SignPrompt sign -> sign.promptText();
      case PlayerUiPrompt pui -> pui.promptText();
      case DialogPrompt dialog -> dialog.title();
      case ConfirmationPrompt confirmation -> confirmation.promptText();
      case ItemPrompt item -> item.promptText();
    };
  }
}
