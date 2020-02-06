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

package com.cyr1en.cp.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.Charset;

public class SimpleConfigManager {

  private JavaPlugin plugin;

  public SimpleConfigManager(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public SimpleConfig getNewConfig(String filePath, String[] header) {

    File file = this.getConfigFile(filePath);

    if (!file.exists()) {
      this.prepareFile(filePath);
      if (header != null && header.length != 0) {
        this.setHeader(file, header);
      }

    }

    SimpleConfig config = new SimpleConfig(this.getConfigContent(filePath), file, this.getCommentsNum(file), plugin);
    return config;

  }

  public SimpleConfig getNewConfig(String filePath) {
    return this.getNewConfig(filePath, null);
  }

  private File getConfigFile(String file) {

    if (file.isEmpty() || file == null) {
      return null;
    }

    File configFile;

    if (file.contains("/")) {

      if (file.startsWith("/")) {
        configFile = new File(plugin.getDataFolder() + file.replace("/", File.separator));
      } else {
        configFile = new File(plugin.getDataFolder() + File.separator + file.replace("/", File.separator));
      }

    } else {
      configFile = new File(plugin.getDataFolder(), file);
    }

    return configFile;

  }

  public void prepareFile(String filePath, String resource) {

    File file = this.getConfigFile(filePath);

    if (file.exists()) {
      return;
    }

    try {
      file.getParentFile().mkdirs();
      file.createNewFile();

      if (resource != null)
        if (!resource.isEmpty()) {
          this.copyResource(plugin.getResource(resource), file);
        }

    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  public void prepareFile(String filePath) {
    this.prepareFile(filePath, null);
  }

  public void setHeader(File file, String[] header) {

    if (!file.exists()) {
      return;
    }

    try {
      String currentLine;
      StringBuilder config = new StringBuilder("");
      BufferedReader reader = new BufferedReader(new FileReader(file));

      while ((currentLine = reader.readLine()) != null) {
        config.append(currentLine + "\n");
      }

      reader.close();
      config.append("# +----------------------------------------------------+ #\n");

      for (String line : header) {

        if (line.length() > 50) {
          continue;
        }

        int lenght = (50 - line.length()) / 2;
        StringBuilder finalLine = new StringBuilder(line);

        for (int i = 0; i < lenght; i++) {
          finalLine.append(" ");
          finalLine.reverse();
          finalLine.append(" ");
          finalLine.reverse();
        }

        if (line.length() % 2 != 0) {
          finalLine.append(" ");
        }

        config.append("# < " + finalLine.toString() + " > #\n");

      }

      config.append("# +----------------------------------------------------+ #");

      BufferedWriter writer = new BufferedWriter(new FileWriter(file));
      writer.write(this.prepareConfigString(config.toString()));
      writer.flush();
      writer.close();

    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  public InputStream getConfigContent(File file) {

    if (!file.exists()) {
      return null;
    }

    try {
      int commentNum = 0;

      String addLine;
      String currentLine;
      String pluginName = this.getPluginName();

      StringBuilder whole = new StringBuilder("");
      BufferedReader reader = new BufferedReader(new FileReader(file));

      while ((currentLine = reader.readLine()) != null) {

        if (currentLine.startsWith("#")) {
          addLine = currentLine.replaceFirst("#", pluginName + "_COMMENT_" + commentNum + ":");
          whole.append(addLine + "\n");
          commentNum++;

        } else {
          whole.append(currentLine + "\n");
        }

      }

      String config = whole.toString();
      InputStream configStream = new ByteArrayInputStream(config.getBytes(Charset.forName("UTF-8")));

      reader.close();
      return configStream;

    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }

  }

  private int getCommentsNum(File file) {

    if (!file.exists()) {
      return 0;
    }

    try {
      int comments = 0;
      String currentLine;

      BufferedReader reader = new BufferedReader(new FileReader(file));

      while ((currentLine = reader.readLine()) != null) {

        if (currentLine.startsWith("#")) {
          comments++;
        }

      }

      reader.close();
      return comments;

    } catch (IOException e) {
      e.printStackTrace();
      return 0;
    }

  }

  public InputStream getConfigContent(String filePath) {
    return this.getConfigContent(this.getConfigFile(filePath));
  }


  private String prepareConfigString(String configString) {

    int lastLine = 0;
    int headerLine = 0;

    String[] lines = configString.split("\n");
    StringBuilder config = new StringBuilder("");

    for (String line : lines) {

      if (line.startsWith(this.getPluginName() + "_COMMENT")) {
        String comment = "#" + line.trim().substring(line.indexOf(":") + 1);

        if (comment.startsWith("# +-")) {

          /*
           * If header line = 0 then it is
           * header start, if it's equal
           * to 1 it's the end of header
           */

          if (headerLine == 0) {
            config.append(comment + "\n");

            lastLine = 0;
            headerLine = 1;

          } else if (headerLine == 1) {
            config.append(comment + "\n\n");

            lastLine = 0;
            headerLine = 0;

          }

        } else {


          String normalComment;

          if (comment.startsWith("# ' ")) {
            normalComment = comment.substring(0, comment.length() - 1).replaceFirst("# ' ", "# ");
          } else {
            normalComment = comment;
          }

          if (lastLine == 0) {
            config.append(normalComment + "\n");
          } else if (lastLine == 1) {
            config.append("\n" + normalComment + "\n");
          }

          lastLine = 0;

        }

      } else {
        config.append(line + "\n");
        lastLine = 1;
      }

    }

    return config.toString();

  }

  public void saveConfig(String configString, File file) {
    String configuration = this.prepareConfigString(configString);
    try {
      BufferedWriter writer = new BufferedWriter(new FileWriter(file));
      writer.write(configuration);
      writer.flush();
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  public String getPluginName() {
    return plugin.getDescription().getName();
  }

  private void copyResource(InputStream resource, File file) {
    try {
      OutputStream out = new FileOutputStream(file);
      int lenght;
      byte[] buf = new byte[1024];
      while ((lenght = resource.read(buf)) > 0) {
        out.write(buf, 0, lenght);
      }
      out.close();
      resource.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}