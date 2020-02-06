/*
 * MIT License
 *
 * Copyright (c) 2020 Ethan Bacurio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
    InputStream is = getResourceAsStream("plugin.yml");
    PluginDescriptionFile pdl = null;
    try {
      pdl = new PluginDescriptionFile(is);
    } catch (InvalidDescriptionException e) {
      e.printStackTrace();
    }
    return pdl;
  }
}