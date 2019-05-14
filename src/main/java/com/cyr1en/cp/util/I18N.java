package com.cyr1en.cp.util;

import com.google.common.io.ByteStreams;
import org.apache.commons.lang.StringUtils;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

@SuppressWarnings("UnstableApiUsage")
public class I18N {

  private File propertyFile;
  private File propertiesDir;
  private ResourceBundle bundle;

  private String baseName;
  private String fileName;

  private JavaPlugin plugin;

  public I18N(JavaPlugin plugin, String baseName) {
    this.plugin = plugin;
    this.baseName = baseName;
    fileName = baseName + ".properties";
    propertyFile = new File(plugin.getDataFolder() + "/Properties", fileName);
    propertiesDir = new File(plugin.getDataFolder() + "/Properties");

    prepareFiles();

    try {
      bundle = new PropertyResourceBundle(Files.newInputStream(propertyFile.toPath()));
      if (needsUpdate())
        update();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void prepareFiles() {
    try {
      Path dirPath = propertiesDir.toPath();
      if (!Files.exists(dirPath)) {
        Files.createDirectories(dirPath);
      }
      if (!Files.exists(propertyFile.toPath())) {
        Files.createFile(propertyFile.toPath());
        copyFiles(propertyFile);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void copyFiles(File file) {
    try {
      InputStream in = plugin.getResource(fileName);
      OutputStream out = new FileOutputStream(file);
      ByteStreams.copy(in, out);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void update() {
    try {
      ResourceBundle classPathBundle = new PropertyResourceBundle(plugin.getResource(fileName));
      Enumeration<String> enumeration = classPathBundle.getKeys();
      StringBuilder sb = new StringBuilder();

      while (enumeration.hasMoreElements()) {
        String key = enumeration.nextElement();
        sb.append("\n");
        if (!bundle.containsKey(key))
          sb.append(key).append(" = ").append(classPathBundle.getString(key));
        else
          sb.append(key).append(" = ").append(bundle.getString(key));
      }

      BufferedWriter writer = new BufferedWriter(new FileWriter(propertyFile));
      writer.write(sb.toString());
      writer.flush();
      writer.close();

      bundle = new PropertyResourceBundle(Files.newInputStream(propertyFile.toPath()));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private boolean needsUpdate() {
    boolean need = false;
    try {
      ResourceBundle classPathBundle = new PropertyResourceBundle(plugin.getResource(fileName));
      Enumeration<String> enumeration = classPathBundle.getKeys();
      while (!need && enumeration.hasMoreElements()) {
        String key = enumeration.nextElement();
        if (!bundle.containsKey(key))
          need = true;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return need;
  }

  public String getFormattedProperty(String key, String... arguments) {
    int placeHolderCount = StringUtils.countMatches(getProperty(key), "%s");
    if (placeHolderCount < arguments.length || placeHolderCount == 0)
      return getProperty(key);
    return String.format(getProperty(key), (Object[]) arguments);
  }

  public String getProperty(String key) {
    return getSafeProperty(key);
  }

  private String getSafeProperty(String key) {
    if (bundle.containsKey(key))
      return bundle.getString(key);
    return String.format("MissingProperty[key = %s]", key);
  }

}
