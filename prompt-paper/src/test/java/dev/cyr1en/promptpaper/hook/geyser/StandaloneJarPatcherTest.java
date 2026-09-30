package dev.cyr1en.promptpaper.hook.geyser;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandaloneJarPatcherTest {
  @TempDir Path data;

  @Test
  void patchesRealStandaloneJarAndPreservesTheOriginal() throws Exception {
    var patcher = new StandaloneJarPatcher(data);
    Files.createDirectories(patcher.directory());
    Path source = Path.of(System.getProperty("geyserStandaloneJar"));
    Path original = Files.copy(source, patcher.directory().resolve("Geyser Standalone.jar"));
    var assets = assets();
    Path output = patcher.patch("\"Geyser Standalone.jar\"", assets);
    assertEquals(-1, Files.mismatch(source, original));
    assertEquals("Geyser Standalone-commandprompter.jar", output.getFileName().toString());
    try (var jar = new JarFile(output.toFile())) {
      assertNotNull(jar.getJarEntry(StandaloneJarPatcher.MARKER));
      assertNotNull(
          jar.getJarEntry("dev/cyr1en/promptpaper/hook/geyser/StandaloneAnvilPatch.class"));
      var model =
          ClassFile.of()
              .parse(
                  jar.getInputStream(jar.getJarEntry(StandaloneJarPatcher.GEYSER_CLASS))
                      .readAllBytes());
      assertEquals(
          1,
          model.methods().stream()
              .filter(method -> method.methodName().equalsString("initialize"))
              .count());
      assertEquals(
          1,
          model.methods().stream()
              .filter(method -> method.methodName().equalsString("commandprompter$initialize"))
              .count());
      var initialize =
          model.methods().stream()
              .filter(method -> method.methodName().equalsString("initialize"))
              .findFirst()
              .orElseThrow();
      var handlers = initialize.code().orElseThrow().exceptionHandlers();
      assertEquals(1, handlers.size());
      assertEquals(
          "java/lang/Throwable", handlers.getFirst().catchType().orElseThrow().asInternalName());
      assertArrayEquals(
          assets.announcement(),
          jar.getInputStream(jar.getJarEntry(AnvilPatchProtocol.RESOURCE_ROOT + "announcement.bin"))
              .readAllBytes());
      assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("org/bukkit/")));
      for (var entry :
          jar.stream()
              .filter(
                  entry ->
                      entry.getName().startsWith("dev/cyr1en/")
                          && entry.getName().endsWith(".class"))
              .toList()) {
        byte[] bytes = jar.getInputStream(entry).readAllBytes();
        assertTrue(ClassFile.of().parse(bytes).majorVersion() <= 65, "Payload must run on Java 21");
        String constants = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(constants.contains("org/bukkit/"), entry.getName());
        assertFalse(constants.contains("platform/spigot/shaded/"), entry.getName());
      }
    }
    var snapshot = Files.copy(output, data.resolve("first-output.jar"));
    assertEquals(output, patcher.patch(original.getFileName().toString(), assets));
    assertEquals(
        -1, Files.mismatch(snapshot, output), "Rebuilding the same patch must not stack wrappers");
  }

  @Test
  void completionAndExecutionStayInsideCompat() throws Exception {
    var patcher = new StandaloneJarPatcher(data);
    Files.createDirectories(patcher.directory());
    Files.writeString(patcher.directory().resolve("Geyser A.jar"), "jar");
    Files.writeString(patcher.directory().resolve("Geyser B.JAR"), "jar");
    Files.writeString(patcher.directory().resolve("note.txt"), "text");
    Files.writeString(patcher.directory().resolve("Geyser-commandprompter.jar"), "output");
    Files.createDirectory(patcher.directory().resolve("directory.jar"));
    Path outside = Files.writeString(data.resolve("outside.jar"), "outside");
    Files.createSymbolicLink(patcher.directory().resolve("link.jar"), outside);
    assertEquals(List.of("Geyser A.jar", "Geyser B.JAR"), patcher.suggestions("geY"));
    assertTrue(patcher.suggestions("../").isEmpty());
    for (String path :
        List.of("../outside.jar", outside.toString(), "nested/Geyser.jar", "nested\\Geyser.jar")) {
      assertEquals(
          "invalid_path",
          assertThrows(StandaloneJarPatcher.Failure.class, () -> patcher.patch(path, assets()))
              .reason());
    }
    assertEquals(
        "not_found",
        assertThrows(StandaloneJarPatcher.Failure.class, () -> patcher.patch("link.jar", assets()))
            .reason());
    assertEquals("outside", Files.readString(outside));
  }

  @Test
  void rejectsUnknownSignedAndPreviouslyPatchedJarsWithoutReplacingFiles() throws Exception {
    var patcher = new StandaloneJarPatcher(data);
    Files.createDirectories(patcher.directory());
    Path valid = fixture(patcher.directory().resolve("Geyser.jar"), null, null);
    Path collision =
        Files.writeString(patcher.directory().resolve("Geyser-commandprompter.jar"), "keep me");
    assertEquals(
        "output_exists",
        assertThrows(
                StandaloneJarPatcher.Failure.class, () -> patcher.patch("Geyser.jar", assets()))
            .reason());
    assertEquals("keep me", Files.readString(collision));
    Files.delete(collision);
    fixture(patcher.directory().resolve("signed.jar"), "META-INF/SIGNATURE.SF", null);
    fixture(patcher.directory().resolve("patched.jar"), StandaloneJarPatcher.MARKER, null);
    Files.writeString(patcher.directory().resolve("invalid.jar"), "not a jar");
    assertEquals(
        "signed",
        assertThrows(
                StandaloneJarPatcher.Failure.class, () -> patcher.patch("signed.jar", assets()))
            .reason());
    assertEquals(
        "already_patched",
        assertThrows(
                StandaloneJarPatcher.Failure.class, () -> patcher.patch("patched.jar", assets()))
            .reason());
    assertEquals(
        "unsupported",
        assertThrows(
                StandaloneJarPatcher.Failure.class, () -> patcher.patch("invalid.jar", assets()))
            .reason());
    try (var paths = Files.list(patcher.directory())) {
      assertTrue(paths.noneMatch(path -> path.toString().endsWith(".tmp")));
    }
    assertTrue(Files.isRegularFile(valid));
  }

  @Test
  void refusesSymlinkedCompatDirectory() throws Exception {
    var elsewhere = Files.createDirectory(data.resolve("elsewhere"));
    Files.createSymbolicLink(data.resolve("compat"), elsewhere);
    var patcher = new StandaloneJarPatcher(data);
    assertThrows(StandaloneJarPatcher.Failure.class, () -> patcher.suggestions(""));
    assertThrows(StandaloneJarPatcher.Failure.class, () -> patcher.patch("Geyser.jar", assets()));
    try (var files = Files.list(elsewhere)) {
      assertEquals(0, files.count());
    }
  }

  @Test
  void backendAnnouncementRejectsWrongKeysVersionsAndMalformedMaterials() throws Exception {
    byte[] token = new byte[AnvilPatchProtocol.TOKEN_BYTES];
    byte[] message = AnvilPatchProtocol.encode(token, Set.of("paper", "barrier"));
    assertEquals(Set.of("paper", "barrier"), AnvilPatchProtocol.decode(message, token));
    token[0] = 1;
    assertThrows(IOException.class, () -> AnvilPatchProtocol.decode(message, token));
    token[0] = 0;
    message[3] = 2;
    assertThrows(IOException.class, () -> AnvilPatchProtocol.decode(message, token));
    assertThrows(IOException.class, () -> AnvilPatchProtocol.encode(token, Set.of("../paper")));
    assertThrows(IOException.class, () -> AnvilPatchProtocol.decode(new byte[33000], token));
    assertThrows(IOException.class, () -> AnvilPatchProtocol.decode(new byte[0], token));
  }

  @Test
  void refusesUntestedVersionsUnlessForcedAndWrapperSurvivesUnlinkablePayload() throws Exception {
    var patcher = new StandaloneJarPatcher(data);
    Files.createDirectories(patcher.directory());
    fixture(
        patcher.directory().resolve("Geyser.jar"),
        null,
        "2.10.0-b1200 (git-master-0000000000000000)");
    var failure =
        assertThrows(
            StandaloneJarPatcher.Failure.class, () -> patcher.patch("Geyser.jar", assets()));
    assertEquals("version", failure.reason());
    assertEquals(StandaloneJarPatcher.expectedVersion(), failure.details().get("expected"));
    assertEquals("2.10.0", failure.details().get("found"));
    assertFalse(Files.exists(patcher.directory().resolve("Geyser-commandprompter.jar")));
    Path output = patcher.patch("Geyser.jar", assets(), true);
    try (var jar = new JarFile(output.toFile())) {
      byte[] bytes =
          jar.getInputStream(jar.getJarEntry(StandaloneJarPatcher.GEYSER_CLASS)).readAllBytes();
      // A loader that cannot see the payload turns the wrapper's patch call into a
      // LinkageError; the guard must log it and still run the original initialize.
      var loader =
          new ClassLoader(null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
              if (!name.equals("org.geysermc.geyser.GeyserImpl"))
                throw new ClassNotFoundException(name);
              return defineClass(name, bytes, 0, bytes.length);
            }
          };
      var geyserClass = Class.forName("org.geysermc.geyser.GeyserImpl", true, loader);
      var geyser = geyserClass.getDeclaredConstructor().newInstance();
      geyserClass.getMethod("initialize").invoke(geyser);
      assertEquals(1, geyserClass.getField("initialized").get(null));
    }
  }

  private static StandaloneJarPatcher.Assets assets() throws IOException {
    return new StandaloneJarPatcher.Assets(
        Map.of("paper", "icon:paper"),
        new byte[0],
        AnvilPatchProtocol.encode(new byte[AnvilPatchProtocol.TOKEN_BYTES], Set.of("paper")));
  }

  private static Path fixture(Path path, String extra, String gitBuildVersion) throws Exception {
    var geyserImpl = ClassDesc.of("org.geysermc.geyser.GeyserImpl");
    var manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest
        .getMainAttributes()
        .put(
            Attributes.Name.MAIN_CLASS,
            "org.geysermc.geyser.platform.standalone.GeyserStandaloneBootstrap");
    byte[] clazz =
        ClassFile.of()
            .build(
                geyserImpl,
                builder -> {
                  builder.withField(
                      "initialized", CD_int, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
                  builder.withMethodBody(
                      "<init>",
                      MethodTypeDesc.of(CD_void),
                      ClassFile.ACC_PUBLIC,
                      code ->
                          code.aload(0)
                              .invokespecial(CD_Object, "<init>", MethodTypeDesc.of(CD_void))
                              .return_());
                  builder.withMethodBody(
                      "initialize",
                      MethodTypeDesc.of(CD_void),
                      ClassFile.ACC_PUBLIC,
                      code ->
                          code.iconst_1().putstatic(geyserImpl, "initialized", CD_int).return_());
                });
    try (var jar = new JarOutputStream(Files.newOutputStream(path), manifest)) {
      jar.putNextEntry(new JarEntry(StandaloneJarPatcher.GEYSER_CLASS));
      jar.write(clazz);
      jar.closeEntry();
      jar.putNextEntry(
          new JarEntry("org/geysermc/geyser/platform/standalone/GeyserStandaloneBootstrap.class"));
      jar.closeEntry();
      if (gitBuildVersion != null) {
        jar.putNextEntry(new JarEntry("git.properties"));
        jar.write(("git.build.version=" + gitBuildVersion + "\n").getBytes());
        jar.closeEntry();
      }
      if (extra != null) {
        jar.putNextEntry(new JarEntry(extra));
        jar.closeEntry();
      }
    }
    return path;
  }
}
