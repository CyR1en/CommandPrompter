package dev.cyr1en.promptpaper.hook.geyser;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.function.Consumer;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.junit.jupiter.api.Test;

class StandaloneAnvilLoginTranslatorTest {
  @Test
  @SuppressWarnings("unchecked")
  void announcesAfterEveryJavaLoginIncludingAnAlreadyInitializedBedrockSession() {
    var original = (PacketTranslator<ClientboundLoginPacket>) mock(PacketTranslator.class);
    var announce = (Consumer<GeyserSession>) mock(Consumer.class);
    var session = mock(GeyserSession.class);
    var packet = mock(ClientboundLoginPacket.class);
    var translator = new StandaloneAnvilLoginTranslator(original, announce);
    translator.translate(session, packet);
    translator.translate(session, packet);

    var order = inOrder(original, announce);
    order.verify(original).translate(session, packet);
    order.verify(announce).accept(session);
    order.verify(original).translate(session, packet);
    order.verify(announce).accept(session);
    order.verifyNoMoreInteractions();
    verifyNoInteractions(session);
  }

  @Test
  @SuppressWarnings("unchecked")
  void failedLoginDoesNotAnnouncePatchReadiness() {
    var original = (PacketTranslator<ClientboundLoginPacket>) mock(PacketTranslator.class);
    var announce = (Consumer<GeyserSession>) mock(Consumer.class);
    var session = mock(GeyserSession.class);
    var packet = mock(ClientboundLoginPacket.class);
    doThrow(new IllegalStateException("Login failed")).when(original).translate(session, packet);
    var translator = new StandaloneAnvilLoginTranslator(original, announce);
    assertThrows(IllegalStateException.class, () -> translator.translate(session, packet));
    verifyNoInteractions(announce);
  }
}
