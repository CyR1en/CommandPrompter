package dev.cyr1en.promptpaper.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.factory.InlineTagMapper;
import dev.cyr1en.promptpaper.preset.CancelBehavior;
import dev.cyr1en.promptpaper.preset.ChatPrompt;
import dev.cyr1en.promptpaper.preset.PresetGson;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Converts inline prompts into persistent structured JSON presets. */
public final class PresetCommand extends PromptCommand {
  public PresetCommand(CommandPrompter plugin) {
    super(plugin, "preset", "promptpaper.preset", null, "Manage prompt presets", List.of());
  }

  @Override
  public LiteralCommandNode<CommandSourceStack> build() {
    var root =
        Commands.literal(name())
            .requires(src -> allowed(src.getSender()))
            .executes(
                ctx -> {
                  message(ctx.getSource().getSender(), "command.preset.usage");
                  return 0;
                });
    for (var edit : PresetRegistry.Edit.values()) {
      var id = Commands.argument("id", StringArgumentType.string());
      if (edit != PresetRegistry.Edit.ADD) {
        id.suggests(
            (ctx, builder) -> {
              plugin.getPresetRegistry().getPromptIds().stream()
                  .map(StringArgumentType::escapeIfRequired)
                  .filter(
                      value ->
                          value
                              .toLowerCase(Locale.ROOT)
                              .startsWith(builder.getRemainingLowerCase()))
                  .forEach(builder::suggest);
              return builder.buildFuture();
            });
      }
      if (edit == PresetRegistry.Edit.REMOVE) {
        id.executes(
            ctx ->
                execute(
                    ctx.getSource().getSender(),
                    edit,
                    StringArgumentType.getString(ctx, "id"),
                    null));
      } else {
        id.then(
            Commands.argument("prompt", StringArgumentType.greedyString())
                .executes(
                    ctx ->
                        execute(
                            ctx.getSource().getSender(),
                            edit,
                            StringArgumentType.getString(ctx, "id"),
                            StringArgumentType.getString(ctx, "prompt"))));
      }
      root.then(Commands.literal(edit.name().toLowerCase(Locale.ROOT)).then(id));
    }
    return root.build();
  }

  public int execute(CommandSender sender, PresetRegistry.Edit edit, String id, String inline) {
    if (!allowed(sender)) return 0;
    var registry = plugin.getPresetRegistry();
    try {
      synchronized (registry) {
        var engine = plugin.getEngine();
        if (engine.isReloadInProgress()) {
          message(sender, "command.preset.reloading");
          return 0;
        }
        com.google.gson.JsonObject json = null;
        if (edit != PresetRegistry.Edit.REMOVE) {
          var parser = engine.getParser();
          var delimiters = parser.getConfig();
          var reference = parser.parse(delimiters.opening() + "@" + id + delimiters.closing());
          if (reference.promptTags().size() != 1
              || !reference.promptTags().getFirst().isPreset()
              || !id.equals(reference.promptTags().getFirst().displayText())) {
            throw new IllegalArgumentException("ID must be usable in a preset reference");
          }
          var source = inline == null ? "" : inline.strip();
          var parsed = parser.parse(source);
          if (parsed.promptTags().size() != 1
              || !parsed.postCmds().isEmpty()
              || parsed.hasGates()
              || !source.equals(parsed.promptTags().getFirst().rawTag())
              || parsed.promptTags().getFirst().isPreset()) {
            throw new IllegalArgumentException("Supply exactly one complete inline prompt");
          }
          var tag = parsed.promptTags().getFirst();
          var config = plugin.getConfigLoader().getPromptConfig();
          if (tag.validatorAlias() != null && !config.hasValidator(tag.validatorAlias())) {
            throw new IllegalArgumentException("Unknown validator: " + tag.validatorAlias());
          }
          var definition =
              InlineTagMapper.toPromptDefinition(
                  tag, config.getScreenMappings(), id, config.dialogConfig());
          if (tag.isCompound()
              && !(definition instanceof dev.cyr1en.promptpaper.preset.DialogPrompt)) {
            throw new IllegalArgumentException("Compound presets must use a dialog prompt type");
          }
          var gson = PresetGson.presetGson(plugin.getConfigLoader().getConfig().templateSyntax());
          json = gson.toJsonTree(definition).getAsJsonObject();
          if (definition instanceof ChatPrompt) {
            json.add(
                "cancel",
                gson.toJsonTree(
                    new CancelBehavior(
                        config.sendCancelText(),
                        config.textCancelMessage(),
                        config.sendCancelText(),
                        config.textCancelHoverMessage())));
          }
        }
        registry.editPrompt(edit, id, json);
      }
      message(
          sender,
          "command.preset." + edit.name().toLowerCase(Locale.ROOT),
          Placeholder.of("id", MiniMessage.miniMessage().escapeTags(id)));
      return 1;
    } catch (IllegalArgumentException
        | IllegalStateException
        | PresetRegistry.PresetLoadException e) {
      message(sender, "command.preset.failed");
      sender.sendMessage(
          Component.text(e.getMessage() == null ? "Unable to edit preset" : e.getMessage()));
      return 0;
    }
  }

  private void message(CommandSender sender, String key, Placeholder... placeholders) {
    sender.sendMessage(
        plugin
            .getConfigLoader()
            .getI18n()
            .get(key, sender instanceof Player player ? player : null, placeholders));
  }
}
