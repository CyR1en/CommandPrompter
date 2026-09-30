package dev.cyr1en.promptpaper.hook.geyser;

import java.util.function.Consumer;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;

/** Announces each backend login, including logins after a form or a server transfer. */
final class StandaloneAnvilLoginTranslator extends PacketTranslator<ClientboundLoginPacket> {
  private final PacketTranslator<ClientboundLoginPacket> original;
  private final Consumer<GeyserSession> announce;

  StandaloneAnvilLoginTranslator(
      PacketTranslator<ClientboundLoginPacket> original, Consumer<GeyserSession> announce) {
    this.original = original;
    this.announce = announce;
  }

  static Runnable install(Consumer<GeyserSession> announce) {
    var registry = Registries.JAVA_PACKET_TRANSLATORS;
    @SuppressWarnings("unchecked")
    var original =
        (PacketTranslator<ClientboundLoginPacket>) registry.get(ClientboundLoginPacket.class);
    if (original == null)
      throw new IllegalStateException("Geyser Java login translator is unavailable");
    var replacement = new StandaloneAnvilLoginTranslator(original, announce);
    registry.register(ClientboundLoginPacket.class, replacement);
    return () -> {
      if (registry.get(ClientboundLoginPacket.class) == replacement)
        registry.register(ClientboundLoginPacket.class, original);
    };
  }

  @Override
  public void translate(GeyserSession session, ClientboundLoginPacket packet) {
    original.translate(session, packet);
    announce.accept(session);
  }
}
