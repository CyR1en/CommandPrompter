package com.cyr1en.cp.util;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.*;
import java.util.zip.ZipFile;

public class FileUtil {
  public static boolean isJarFile(File file) throws IOException {
    if (!isZipFile(file)) {
      return false;
    }
    ZipFile zip = new ZipFile(file);
    boolean manifest = zip.getEntry("META-INF/MANIFEST.MF") != null;
    zip.close();
    return manifest;
  }

  public static boolean isZipFile(File file) throws IOException {
    if (file.isDirectory()) {
      return false;
    }
    if (!file.canRead()) {
      throw new IOException("Cannot read file " + file.getAbsolutePath());
    }
    if (file.length() < 4) {
      return false;
    }
    DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
    int test = in.readInt();
    in.close();
    return test == 0x504b0304;
  }

  public static InputStream getResourceAsStream(String path) {
    InputStream inputStream = null;
    try {
      inputStream = FileUtil.class.getResource(path).openStream();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return inputStream;
  }

  public static PluginDescriptionFile getPluginDescriptionFile() {
    InputStream is = getResourceAsStream("plugin.ymk");
    PluginDescriptionFile pdl = null;
    try {
      pdl = new PluginDescriptionFile(is);
    } catch (InvalidDescriptionException e) {
      e.printStackTrace();
    }
    return pdl;
  }
}