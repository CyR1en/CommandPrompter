package dev.cyr1en.promptcore.config;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.DuplicateKeyException;
import org.yaml.snakeyaml.representer.Representer;

public class SnakeYamlDocument implements YamlDocument {

  private final File file;
  private final Map<String, Object> data;
  private final Map<String, String[]> commentsMap = new LinkedHashMap<>();
  private final Yaml yaml;

  public SnakeYamlDocument(File file) {
    if (file == null) throw new IllegalArgumentException("Configuration file must not be null");
    this.file = file;
    LoaderOptions loaderOptions = new LoaderOptions();
    loaderOptions.setAllowDuplicateKeys(false);
    DumperOptions dumperOptions = new DumperOptions();
    dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    dumperOptions.setIndent(2);
    this.yaml =
        new Yaml(
            new Constructor(loaderOptions),
            new Representer(dumperOptions),
            dumperOptions,
            loaderOptions);

    if (!file.exists()) {
      this.data = new LinkedHashMap<>();
      return;
    }

    try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      Object loaded = yaml.load(reader);
      if (loaded == null) {
        this.data = new LinkedHashMap<>();
      } else if (loaded instanceof Map<?, ?> map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) map;
        this.data = cast;
      } else {
        throw new ConfigurationException(
            "Configuration file "
                + file.getAbsolutePath()
                + " must contain a YAML mapping at the root, but found "
                + typeName(loaded));
      }
    } catch (ConfigurationException e) {
      throw e;
    } catch (DuplicateKeyException e) {
      throw new ConfigurationException(
          "Duplicate YAML key in configuration file " + file.getAbsolutePath() + ": " + e, e);
    } catch (IOException | RuntimeException e) {
      throw new ConfigurationException(
          "Failed to read or parse configuration file " + file.getAbsolutePath(), e);
    }
  }

  @Override
  public Object get(String nodeName) {
    return getNested(nodeName);
  }

  @Override
  public String getString(String nodeName) {
    Object val = get(nodeName);
    if (val == null) return null;
    if (val instanceof CharSequence || val instanceof Number || val instanceof Boolean) {
      return String.valueOf(val);
    }
    throw valueError(nodeName, "a scalar string-compatible value", val);
  }

  @Override
  public int getInt(String nodeName) {
    Object val = get(nodeName);
    if (val == null) return 0;

    BigDecimal decimal;
    if (val instanceof String string) {
      try {
        // Integer configuration values use an integer spelling. In particular, do not silently
        // accept a fractional string and narrow it to an int.
        return Integer.parseInt(string);
      } catch (NumberFormatException e) {
        throw valueError(nodeName, "an integer", val, e);
      }
    } else if (val instanceof BigDecimal bigDecimal) {
      decimal = bigDecimal;
    } else if (val instanceof BigInteger bigInteger) {
      decimal = new BigDecimal(bigInteger);
    } else if (val instanceof Double || val instanceof Float) {
      double number = ((Number) val).doubleValue();
      if (!Double.isFinite(number)) {
        throw valueError(nodeName, "a finite integer", val);
      }
      decimal = BigDecimal.valueOf(number);
    } else if (val instanceof Number number) {
      try {
        decimal = new BigDecimal(number.toString());
      } catch (NumberFormatException e) {
        throw valueError(nodeName, "an integer", val, e);
      }
    } else {
      throw valueError(nodeName, "an integer", val);
    }

    if (decimal.stripTrailingZeros().scale() > 0
        || decimal.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0
        || decimal.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
      throw valueError(nodeName, "a finite 32-bit integer", val);
    }
    return decimal.intValueExact();
  }

  @Override
  public double getDouble(String nodeName) {
    Object val = get(nodeName);
    if (val == null) return 0.0;

    final double result;
    if (val instanceof Number number) {
      result = number.doubleValue();
    } else if (val instanceof String string) {
      try {
        result = Double.parseDouble(string);
      } catch (NumberFormatException e) {
        throw valueError(nodeName, "a number", val, e);
      }
    } else {
      throw valueError(nodeName, "a number", val);
    }
    if (!Double.isFinite(result)) {
      throw valueError(nodeName, "a finite number", val);
    }
    return result;
  }

  @Override
  public boolean getBoolean(String nodeName) {
    Object val = get(nodeName);
    if (val instanceof Boolean) return (Boolean) val;
    if (val == null) return false;
    if (val instanceof String string
        && (string.equalsIgnoreCase("true") || string.equalsIgnoreCase("false"))) {
      return Boolean.parseBoolean(string);
    }
    throw valueError(nodeName, "the boolean literal true or false", val);
  }

  @Override
  public List<?> getList(String nodeName) {
    Object val = get(nodeName);
    if (val == null) return List.of();
    if (val instanceof List<?> list) {
      for (Object element : list) {
        if (element == null) {
          throw valueError(nodeName, "a list without null elements", list);
        }
      }
      return List.copyOf(list);
    }
    throw valueError(nodeName, "a YAML list", val);
  }

  @Override
  public Set<String> getKeys(String nodeName) {
    Object val = get(nodeName);
    if (val instanceof Map<?, ?> map) {
      var keys = new LinkedHashSet<String>();
      for (Object key : map.keySet()) {
        if (!(key instanceof String stringKey)) {
          throw valueError(nodeName, "a mapping with string keys", key);
        }
        keys.add(stringKey);
      }
      return java.util.Collections.unmodifiableSet(keys);
    }
    return Set.of();
  }

  @Override
  public void set(String nodeName, Object value, String[] comments) {
    setComments(nodeName, comments);

    String[] parts = splitPath(nodeName);
    Map<String, Object> current = data;
    for (int i = 0; i < parts.length - 1; i++) {
      Object next = current.get(parts[i]);
      if (next == null) {
        var child = new LinkedHashMap<String, Object>();
        current.put(parts[i], child);
        current = child;
      } else if (next instanceof Map<?, ?> map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> child = (Map<String, Object>) map;
        current = child;
      } else {
        String parentPath = String.join(".", java.util.Arrays.copyOf(parts, i + 1));
        throw new ConfigurationException(
            "Cannot set configuration path '"
                + nodeName
                + "' in "
                + file.getAbsolutePath()
                + ": parent path '"
                + parentPath
                + "' contains "
                + typeName(next)
                + " instead of a mapping");
      }
    }
    current.put(parts[parts.length - 1], value);
  }

  @Override
  public void setComments(String nodeName, String[] comments) {
    if (comments != null && comments.length > 0) {
      commentsMap.put(nodeName, comments);
    }
  }

  private Object getNested(String nodeName) {
    String[] parts = splitPath(nodeName);
    Map<String, Object> current = data;
    for (int i = 0; i < parts.length - 1; i++) {
      Object next = current.get(parts[i]);
      if (next == null) return null;
      if (!(next instanceof Map<?, ?> map)) {
        String parentPath = String.join(".", java.util.Arrays.copyOf(parts, i + 1));
        throw new ConfigurationException(
            "Cannot read configuration path '"
                + nodeName
                + "' in "
                + file.getAbsolutePath()
                + ": parent path '"
                + parentPath
                + "' contains "
                + typeName(next)
                + " instead of a mapping");
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> child = (Map<String, Object>) map;
      current = child;
    }
    return current.get(parts[parts.length - 1]);
  }

  public void save(String[] header) {
    String rawDump = yaml.dump(data);
    // Space out top-level sections
    rawDump = rawDump.replaceAll("(?m)^([a-zA-Z0-9_-]+:)", "\n$1");
    // Remove leading newlines
    rawDump = rawDump.replaceFirst("^\\n+", "");

    for (Map.Entry<String, String[]> entry : commentsMap.entrySet()) {
      String fullKey = entry.getKey();
      String[] parts = fullKey.split("\\.");
      int searchIndex = 0;
      int matchStart = -1;
      String indent = "";

      boolean found = true;
      for (int i = 0; i < parts.length; i++) {
        // Default indentation is 2 spaces
        String expectedIndent = " ".repeat(i * 2);
        java.util.regex.Pattern p =
            java.util.regex.Pattern.compile(
                "(?m)^(" + expectedIndent + ")" + java.util.regex.Pattern.quote(parts[i]) + ":");
        java.util.regex.Matcher m = p.matcher(rawDump);

        if (m.find(searchIndex)) {
          searchIndex = m.start() + 1;
          if (i == parts.length - 1) {
            matchStart = m.start();
            indent = m.group(1);
          }
        } else {
          found = false;
          break;
        }
      }

      if (found && matchStart != -1) {
        StringBuilder commentsStr = new StringBuilder();
        for (String c : entry.getValue()) {
          if (c != null && !c.isEmpty()) {
            commentsStr.append(indent).append("# ").append(c).append("\n");
          }
        }
        rawDump = rawDump.substring(0, matchStart) + commentsStr + rawDump.substring(matchStart);
      }
    }

    StringBuilder output = new StringBuilder();
    if (header != null) {
      for (String line : header) {
        output.append("# ").append(line).append('\n');
      }
      if (header.length > 0) output.append('\n');
    }
    output.append(rawDump);

    Path temporary = null;
    ConfigurationException saveFailure = null;
    try {
      Path target = file.toPath().toAbsolutePath();
      Path parent = target.getParent();
      if (parent == null) {
        throw new IOException("Configuration file has no parent directory: " + target);
      }
      Files.createDirectories(parent);
      temporary = Files.createTempFile(parent, file.getName() + ".", ".tmp");
      try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
        writer.write(output.toString());
        writer.flush();
      }

      try {
        Files.move(
            temporary,
            target,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException | UnsupportedOperationException e) {
        Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      temporary = null;
    } catch (IOException e) {
      saveFailure =
          new ConfigurationException(
              "Failed to atomically save configuration file " + file.getAbsolutePath(), e);
      throw saveFailure;
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException cleanupFailure) {
          if (saveFailure != null) {
            saveFailure.addSuppressed(cleanupFailure);
          } else {
            throw new ConfigurationException(
                "Failed to remove temporary configuration file " + temporary, cleanupFailure);
          }
        }
      }
    }
  }

  private static String[] splitPath(String nodeName) {
    if (nodeName == null || nodeName.isBlank()) {
      throw new IllegalArgumentException("Configuration path must not be blank");
    }
    String[] parts = nodeName.split("\\.", -1);
    for (String part : parts) {
      if (part.isEmpty()) {
        throw new IllegalArgumentException(
            "Configuration path contains an empty segment: " + nodeName);
      }
    }
    return parts;
  }

  private ConfigurationException valueError(String nodeName, String expected, Object actual) {
    return valueError(nodeName, expected, actual, null);
  }

  private ConfigurationException valueError(
      String nodeName, String expected, Object actual, Throwable cause) {
    String message =
        "Invalid value for configuration path '"
            + nodeName
            + "' in "
            + file.getAbsolutePath()
            + ": expected "
            + expected
            + ", got "
            + typeName(actual)
            + " ("
            + String.valueOf(actual)
            + ")";
    return cause == null
        ? new ConfigurationException(message)
        : new ConfigurationException(message, cause);
  }

  private static String typeName(Object value) {
    return value == null ? "null" : value.getClass().getName();
  }
}
