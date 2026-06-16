package dev.cyr1en.promptcore.config;

import java.util.List;
import java.util.Set;

public interface YamlDocument {
  Object get(String nodeName);

  String getString(String nodeName);

  int getInt(String nodeName);

  double getDouble(String nodeName);

  boolean getBoolean(String nodeName);

  List<?> getList(String nodeName);

  Set<String> getKeys(String nodeName);

  void set(String nodeName, Object value, String[] comments);

  void setComments(String nodeName, String[] comments);
}
