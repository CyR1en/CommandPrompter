package dev.cyr1en.promptpaper.hook.geyser;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;
import java.util.zip.ZipException;

/** Builds a patched copy without loading or executing any classes from the selected JAR. */
public final class StandaloneJarPatcher {
  static final String GEYSER_CLASS = "org/geysermc/geyser/GeyserImpl.class";
  static final String MARKER = AnvilPatchProtocol.RESOURCE_ROOT + "patch.properties";
  private static final String MAIN_CLASS =
      "org.geysermc.geyser.platform.standalone.GeyserStandaloneBootstrap";
  private static final String PAYLOAD = "/compat/geyser-standalone-patch.jar";
  private static final String OUTPUT_SUFFIX = "-commandprompter.jar";
  private static final String VERSION_KEY = "Geyser-Version";
  private static final String GIT_PROPERTIES = "git.properties";
  private static final Pattern SEMANTIC_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");
  private static final ClassDesc CD_THROWABLE = ClassDesc.of("java.lang.Throwable");
  private static final ClassDesc CD_JUL_LOGGER = ClassDesc.of("java.util.logging.Logger");
  private static final ClassDesc CD_JUL_LEVEL = ClassDesc.of("java.util.logging.Level");
  private static final long MAX_JAR_BYTES = 512L * 1024 * 1024;
  private static final long MAX_EXPANDED_BYTES = 1024L * 1024 * 1024;
  private static final Set<Path> ACTIVE = ConcurrentHashMap.newKeySet();
  private final Path directory;

  public record Assets(Map<String, String> materials, byte[] pack, byte[] announcement) {
    public Assets {
      materials = Map.copyOf(materials);
      pack = pack.clone();
      announcement = announcement.clone();
    }
  }

  public static final class Failure extends IOException {
    private final String reason;
    private final Map<String, String> details;

    Failure(String reason) {
      this(reason, Map.of(), null);
    }

    public Failure(String reason, Map<String, String> details) {
      this(reason, details, null);
    }

    Failure(String reason, Throwable cause) {
      this(reason, Map.of(), cause);
    }

    private Failure(String reason, Map<String, String> details, Throwable cause) {
      super(details.isEmpty() ? reason : reason + " " + details, cause);
      this.reason = reason;
      this.details = Map.copyOf(details);
    }

    public String reason() {
      return reason;
    }

    public Map<String, String> details() {
      return details;
    }
  }

  public StandaloneJarPatcher(Path dataFolder) {
    directory = dataFolder.resolve("compat").toAbsolutePath().normalize();
  }

  public Path directory() {
    return directory;
  }

  public List<String> suggestions(String prefix) throws IOException {
    prepareDirectory();
    String remaining = unquote(prefix).toLowerCase(Locale.ROOT);
    if (remaining.contains("/") || remaining.contains("\\")) return List.of();
    try (var files = Files.list(directory)) {
      return files
          .limit(4096)
          .filter(path -> Files.isRegularFile(path, NOFOLLOW_LINKS))
          .map(path -> path.getFileName().toString())
          .filter(StandaloneJarPatcher::inputName)
          .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
          .sorted(String.CASE_INSENSITIVE_ORDER)
          .limit(100)
          .toList();
    }
  }

  public Path patch(String argument, Assets assets) throws IOException {
    return patch(argument, assets, false);
  }

  public Path patch(String argument, Assets assets, boolean force) throws IOException {
    prepareDirectory();
    String name = unquote(argument);
    if (!inputName(name) || name.contains("/") || name.contains("\\") || name.indexOf('\0') >= 0) {
      throw new Failure("invalid_path");
    }
    var input = directory.resolve(name);
    if (!Files.isRegularFile(input, NOFOLLOW_LINKS)) throw new Failure("not_found");
    if (!ACTIVE.add(input)) throw new Failure("busy");
    try {
      return patchFile(input, assets, force);
    } finally {
      ACTIVE.remove(input);
    }
  }

  private Path patchFile(Path input, Assets assets, boolean force) throws IOException {
    if (Files.size(input) > MAX_JAR_BYTES) throw new Failure("too_large");
    var filename = input.getFileName().toString();
    var output = directory.resolve(filename.substring(0, filename.length() - 4) + OUTPUT_SUFFIX);
    String previousOutput = existingOutput(output);
    String sourceHash = digest(input);
    Path temporary = null;
    try (var original = new JarFile(input.toFile(), false)) {
      validate(original);
      var entries = original.entries();
      var names = new java.util.HashSet<String>();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        if (!names.add(entry.getName())) throw new Failure("unsupported");
        String upper = entry.getName().toUpperCase(Locale.ROOT);
        if (upper.startsWith("META-INF/")
            && (upper.endsWith(".SF")
                || upper.endsWith(".RSA")
                || upper.endsWith(".DSA")
                || upper.endsWith(".EC"))) throw new Failure("signed");
        if (names.size() > 100000) throw new Failure("too_large");
      }
      // Jar-integrity rejections take precedence; version compatibility is checked last.
      if (!force) checkVersion(original);
      byte[] modified;
      try (var stream = original.getInputStream(original.getJarEntry(GEYSER_CLASS))) {
        byte[] bytes = stream.readNBytes(8 * 1024 * 1024 + 1);
        if (bytes.length > 8 * 1024 * 1024) throw new Failure("too_large");
        modified = inject(bytes);
      }
      temporary = Files.createTempFile(directory, ".geyser-patch-", ".tmp");
      try (var target = new JarOutputStream(Files.newOutputStream(temporary))) {
        long total = 0;
        var sourceEntries = original.entries();
        byte[] buffer = new byte[32768];
        while (sourceEntries.hasMoreElements()) {
          var entry = sourceEntries.nextElement();
          if (entry.getName().equals("META-INF/INDEX.LIST")) continue;
          var copy = new JarEntry(entry.getName());
          copy.setTime(entry.getTime() < 0 ? 0 : entry.getTime());
          target.putNextEntry(copy);
          if (entry.getName().equals(GEYSER_CLASS)) {
            target.write(modified);
          } else if (!entry.isDirectory()) {
            try (var stream = original.getInputStream(entry)) {
              int read;
              while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_EXPANDED_BYTES) throw new Failure("too_large");
                target.write(buffer, 0, read);
              }
            }
          }
          target.closeEntry();
        }
        writePayload(target, names);
        var properties = new StringBuilder();
        new TreeMap<>(assets.materials())
            .forEach((key, value) -> properties.append(key).append('=').append(value).append('\n'));
        put(
            target,
            AnvilPatchProtocol.RESOURCE_ROOT + "materials.properties",
            properties.toString().getBytes(StandardCharsets.UTF_8));
        put(target, AnvilPatchProtocol.RESOURCE_ROOT + "anvil-icons.mcpack", assets.pack());
        put(target, AnvilPatchProtocol.RESOURCE_ROOT + "announcement.bin", assets.announcement());
        put(
            target,
            MARKER,
            ("format=1\nsource-sha256=" + sourceHash + "\n").getBytes(StandardCharsets.US_ASCII));
      }
      if (!Files.isRegularFile(input, NOFOLLOW_LINKS)
          || !sourceHash.equals(digest(input))
          || !java.util.Objects.equals(previousOutput, existingOutput(output)))
        throw new Failure("changed");
      Files.move(
          temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return output;
    } catch (ZipException | IllegalArgumentException failure) {
      throw new Failure("unsupported", failure);
    } finally {
      if (temporary != null) Files.deleteIfExists(temporary);
    }
  }

  void prepareDirectory() throws IOException {
    if (Files.isSymbolicLink(directory)) throw new Failure("invalid_path");
    Files.createDirectories(directory);
  }

  private static boolean inputName(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    return !name.startsWith(".") && lower.endsWith(".jar") && !lower.endsWith(OUTPUT_SUFFIX);
  }

  private static String unquote(String value) {
    String result = value.strip();
    if (result.startsWith("\"")) result = result.substring(1);
    if (result.endsWith("\"")) result = result.substring(0, result.length() - 1);
    return result;
  }

  private static void validate(JarFile jar) throws IOException {
    if (jar.getJarEntry(MARKER) != null) throw new Failure("already_patched");
    if (jar.getManifest() == null
        || !MAIN_CLASS.equals(jar.getManifest().getMainAttributes().getValue("Main-Class"))
        || jar.getJarEntry(GEYSER_CLASS) == null
        || jar.getJarEntry(MAIN_CLASS.replace('.', '/') + ".class") == null) {
      throw new Failure("unsupported");
    }
    if (jar.stream()
        .anyMatch(entry -> entry.getName().startsWith(AnvilPatchProtocol.RESOURCE_ROOT)))
      throw new Failure("unsupported");
  }

  private static void checkVersion(JarFile jar) throws IOException {
    String expected = expectedVersion();
    if (expected == null) return;
    String found = versionOf(jar);
    if (!expected.equals(found)) {
      throw new Failure(
          "version", Map.of("expected", expected, "found", found == null ? "unknown" : found));
    }
  }

  /** Version of the Geyser release the bundled payload was compiled against, or null if absent. */
  static String expectedVersion() {
    try (InputStream resource = StandaloneJarPatcher.class.getResourceAsStream(PAYLOAD)) {
      if (resource == null) return null;
      try (var payload = new JarInputStream(resource)) {
        var manifest = payload.getManifest();
        return manifest == null ? null : manifest.getMainAttributes().getValue(VERSION_KEY);
      }
    } catch (IOException failure) {
      // An unreadable payload is reported by writePayload with full context instead.
      return null;
    }
  }

  /** Geyser release of the target JAR (for example "2.11.3"), or null when it cannot be read. */
  static String versionOf(JarFile jar) throws IOException {
    var entry = jar.getJarEntry(GIT_PROPERTIES);
    if (entry == null) return null;
    var properties = new Properties();
    try (var input = jar.getInputStream(entry)) {
      byte[] bytes = input.readNBytes(64 * 1024 + 1);
      if (bytes.length > 64 * 1024) return null;
      properties.load(new ByteArrayInputStream(bytes));
    }
    String version = properties.getProperty("git.build.version");
    if (version == null) return null;
    var matcher = SEMANTIC_VERSION.matcher(version);
    return matcher.find() ? matcher.group() : null;
  }

  private static String existingOutput(Path output) throws IOException {
    if (!Files.exists(output, NOFOLLOW_LINKS)) return null;
    if (!Files.isRegularFile(output, NOFOLLOW_LINKS)) throw new Failure("output_exists");
    try (var jar = new JarFile(output.toFile(), false)) {
      if (jar.getJarEntry(MARKER) == null) throw new Failure("output_exists");
    } catch (ZipException failure) {
      throw new Failure("output_exists", failure);
    }
    return digest(output);
  }

  static byte[] inject(byte[] bytes) throws Failure {
    var classFile = ClassFile.of();
    var model = classFile.parse(bytes);
    var methods =
        model.methods().stream()
            .filter(
                method ->
                    method.methodName().equalsString("initialize")
                        && method.methodType().equalsString("()V")
                        && method.code().isPresent()
                        && !method.flags().has(java.lang.reflect.AccessFlag.STATIC))
            .toList();
    if (methods.size() != 1
        || model.methods().stream()
            .anyMatch(method -> method.methodName().equalsString("commandprompter$initialize"))) {
      throw new Failure("unsupported");
    }
    var geyser = model.thisClass().asSymbol();
    var runtime = ClassDesc.of("dev.cyr1en.promptpaper.hook.geyser.StandaloneAnvilPatch");
    var noArgs = MethodTypeDesc.of(CD_void);
    return classFile.transformClass(
        model,
        (builder, element) -> {
          if (element instanceof MethodModel method && method == methods.getFirst()) {
            // Retain the original code and stack maps. Only the wrapper contains new instructions.
            builder.withMethod(
                "commandprompter$initialize",
                noArgs,
                method.flags().flagsMask(),
                methodBuilder -> method.forEach(methodBuilder::with));
            builder.withMethodBody(
                "initialize",
                noArgs,
                method.flags().flagsMask(),
                code ->
                    // The guard must live in the wrapper: a payload that fails to link never
                    // reaches its own catch block, and the original initialize must still run.
                    // java.util.logging is JDK-only, so the handler itself cannot fail to link.
                    code.trying(
                            body ->
                                body.aload(0)
                                    .invokestatic(
                                        runtime,
                                        "initialize",
                                        MethodTypeDesc.of(CD_void, CD_Object)),
                            catches ->
                                catches.catching(
                                    CD_THROWABLE,
                                    handler ->
                                        handler
                                            .astore(1)
                                            .ldc("CommandPrompter")
                                            .invokestatic(
                                                CD_JUL_LOGGER,
                                                "getLogger",
                                                MethodTypeDesc.of(CD_JUL_LOGGER, CD_String))
                                            .getstatic(CD_JUL_LEVEL, "SEVERE", CD_JUL_LEVEL)
                                            .ldc(
                                                "CommandPrompter standalone anvil patch could not"
                                                    + " load and was skipped")
                                            .aload(1)
                                            .invokevirtual(
                                                CD_JUL_LOGGER,
                                                "log",
                                                MethodTypeDesc.of(
                                                    CD_void,
                                                    CD_JUL_LEVEL,
                                                    CD_String,
                                                    CD_THROWABLE))))
                        .aload(0)
                        .invokevirtual(geyser, "commandprompter$initialize", noArgs)
                        .return_());
          } else {
            builder.with(element);
          }
        });
  }

  private static void writePayload(JarOutputStream target, Set<String> originalNames)
      throws IOException {
    try (InputStream resource = StandaloneJarPatcher.class.getResourceAsStream(PAYLOAD)) {
      if (resource == null) throw new IOException("Bundled standalone patch is missing");
      try (var payload = new JarInputStream(resource)) {
        JarEntry entry;
        while ((entry = payload.getNextJarEntry()) != null) {
          if (entry.isDirectory()) continue;
          if (!entry.getName().startsWith("dev/cyr1en/promptpaper/hook/geyser/")
              || !entry.getName().endsWith(".class")) {
            throw new IOException("Unexpected standalone payload entry");
          }
          if (originalNames.contains(entry.getName())) throw new Failure("unsupported");
          put(target, entry.getName(), payload.readAllBytes());
        }
      }
    }
  }

  private static void put(JarOutputStream output, String name, byte[] data) throws IOException {
    var entry = new JarEntry(name);
    entry.setTime(0);
    output.putNextEntry(entry);
    output.write(data);
    output.closeEntry();
  }

  private static String digest(Path file) throws IOException {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      try (var input = Files.newInputStream(file)) {
        byte[] buffer = new byte[32768];
        int read;
        while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
