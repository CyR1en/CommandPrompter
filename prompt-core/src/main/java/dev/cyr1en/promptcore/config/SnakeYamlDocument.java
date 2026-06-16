package dev.cyr1en.promptcore.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class SnakeYamlDocument implements YamlDocument {

  private final File file;
  private final Map<String, Object> data;
  private final Map<String, String[]> commentsMap = new LinkedHashMap<>();
  private final Yaml yaml;

  @SuppressWarnings("unchecked")
  public SnakeYamlDocument(File file) {
    this.file = file;
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setIndent(2);
    this.yaml = new Yaml(options);

    Map<String, Object> loaded = null;
    if (file.exists()) {
      try (FileInputStream is = new FileInputStream(file)) {
        loaded = yaml.load(is);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    this.data = loaded != null ? loaded : new LinkedHashMap<>();
  }

  @Override
  public Object get(String nodeName) {
    return getNested(nodeName);
  }

  @Override
  public String getString(String nodeName) {
    Object val = get(nodeName);
    return val != null ? String.valueOf(val) : null;
  }

  @Override
  public int getInt(String nodeName) {
    Object val = get(nodeName);
    if (val instanceof Number) return ((Number) val).intValue();
    if (val instanceof String) return Integer.parseInt((String) val);
    return 0;
  }

  @Override
  public double getDouble(String nodeName) {
    Object val = get(nodeName);
    if (val instanceof Number) return ((Number) val).doubleValue();
    if (val instanceof String) return Double.parseDouble((String) val);
    return 0.0;
  }

  @Override
  public boolean getBoolean(String nodeName) {
    Object val = get(nodeName);
    if (val instanceof Boolean) return (Boolean) val;
    if (val instanceof String) return Boolean.parseBoolean((String) val);
    return false;
  }

  @Override
  public List<?> getList(String nodeName) {
    Object val = get(nodeName);
    if (val instanceof List) return (List<?>) val;
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<String> getKeys(String nodeName) {
    Object val = get(nodeName);
    if (val instanceof Map) {
      return ((Map<String, Object>) val).keySet();
    }
    return Set.of();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void set(String nodeName, Object value, String[] comments) {
    setComments(nodeName, comments);

    String[] parts = nodeName.split("\\.");
    Map<String, Object> current = data;
    for (int i = 0; i < parts.length - 1; i++) {
      current =
          (Map<String, Object>)
              current.computeIfAbsent(parts[i], k -> new LinkedHashMap<String, Object>());
    }
    current.put(parts[parts.length - 1], value);
  }

  @Override
  public void setComments(String nodeName, String[] comments) {
    if (comments != null && comments.length > 0) {
      commentsMap.put(nodeName, comments);
    }
  }

  @SuppressWarnings("unchecked")
  private Object getNested(String nodeName) {
    String[] parts = nodeName.split("\\.");
    Map<String, Object> current = data;
    for (int i = 0; i < parts.length - 1; i++) {
      Object next = current.get(parts[i]);
      if (!(next instanceof Map)) return null;
      current = (Map<String, Object>) next;
    }
    return current.get(parts[parts.length - 1]);
  }

  public void save(String[] header) {
    try {
      if (!file.getParentFile().exists()) {
        file.getParentFile().mkdirs();
      }
      try (FileWriter writer = new FileWriter(file)) {
        if (header != null) {
          for (String line : header) {
            writer.write("# " + line + "\n");
          }
          if (header.length > 0) writer.write("\n");
        }

        String rawDump = yaml.dump(data);

        // Add a newline before each top-level key to space out sections
        rawDump = rawDump.replaceAll("(?m)^([a-zA-Z0-9_-]+:)", "\n$1");
        // Remove any leading newlines that might have been added to the very first item
        rawDump = rawDump.replaceFirst("^\\n+", "");

        for (Map.Entry<String, String[]> entry : commentsMap.entrySet()) {
          String fullKey = entry.getKey();
          String[] parts = fullKey.split("\\.");
          int searchIndex = 0;
          int matchStart = -1;
          String indent = "";

          boolean found = true;
          for (int i = 0; i < parts.length; i++) {
            // SnakeYAML default block style indents each level by 2 spaces.
            String expectedIndent = " ".repeat(i * 2);
            java.util.regex.Pattern p =
                java.util.regex.Pattern.compile(
                    "(?m)^("
                        + expectedIndent
                        + ")"
                        + java.util.regex.Pattern.quote(parts[i])
                        + ":");
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
            rawDump =
                rawDump.substring(0, matchStart)
                    + commentsStr.toString()
                    + rawDump.substring(matchStart);
          }
        }

        writer.write(rawDump);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
