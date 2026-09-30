package dev.cyr1en.promptpaper.hook.geyser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** Shared by the Paper backend and the standalone payload, without platform dependencies. */
public final class AnvilPatchProtocol {
  public static final String CHANNEL = "commandprompter:anvil";
  public static final String RESOURCE_ROOT = "META-INF/commandprompter/";
  public static final String INPUT_MODEL_PREFIX = "commandprompter:anvil_input/";
  public static final int MAX_DAMAGE = 64;
  public static final int TOKEN_BYTES = 32;
  private static final int VERSION = 1;
  private static final int MAX_MESSAGE_BYTES = 32766;

  private AnvilPatchProtocol() {}

  public static String model(boolean input, String material) {
    return (input ? INPUT_MODEL_PREFIX : "commandprompter:anvil_cancel/") + material;
  }

  public static byte[] encode(byte[] token, Collection<String> materials) throws IOException {
    if (token.length != TOKEN_BYTES) throw new IOException("Invalid backend token");
    var bytes = new ByteArrayOutputStream();
    try (var output = new DataOutputStream(bytes)) {
      output.writeInt(VERSION);
      output.write(token);
      var sorted = new TreeSet<>(materials);
      output.writeShort(sorted.size());
      for (String material : sorted) output.writeUTF(material);
    }
    byte[] result = bytes.toByteArray();
    decode(result, token);
    return result;
  }

  public static Set<String> decode(byte[] message, byte[] token) throws IOException {
    if (token.length != TOKEN_BYTES || message.length > MAX_MESSAGE_BYTES) {
      throw new IOException("Invalid patch announcement");
    }
    try (var input = new DataInputStream(new ByteArrayInputStream(message))) {
      if (input.readInt() != VERSION
          || !MessageDigest.isEqual(input.readNBytes(TOKEN_BYTES), token)) {
        throw new IOException("Patch announcement belongs to another backend or protocol");
      }
      int count = input.readUnsignedShort();
      if (count == 0 || count > 2048) throw new IOException("Invalid material count");
      var materials = new HashSet<String>();
      for (int i = 0; i < count; i++) {
        String material = input.readUTF();
        if (material.length() > 128
            || !material.matches("[a-z0-9_]+")
            || !materials.add(material)) {
          throw new IOException("Invalid material in patch announcement");
        }
      }
      if (input.available() != 0) throw new IOException("Trailing patch announcement data");
      return Set.copyOf(materials);
    }
  }
}
