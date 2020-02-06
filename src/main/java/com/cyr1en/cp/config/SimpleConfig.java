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

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Set;

public class SimpleConfig {
  private int comments;
  private SimpleConfigManager manager;

  private File file;
  private FileConfiguration config;

  public SimpleConfig(InputStream configStream, File configFile, int comments, JavaPlugin plugin) {
    this.comments = comments;
    this.manager = new SimpleConfigManager(plugin);

    this.file = configFile;
    Reader reader = new InputStreamReader(configStream);
    this.config = YamlConfiguration.loadConfiguration(reader);
  }

  public Object get(String path) {
    return this.config.get(path);
  }

  public Object get(String path, Object def) {
    return this.config.get(path, def);
  }

  public String getString(String path) {
    return this.config.getString(path);
  }

  public String getString(String path, String def) {
    return this.config.getString(path, def);
  }

  public int getInt(String path) {
    return this.config.getInt(path);
  }

  public int getInt(String path, int def) {
    return this.config.getInt(path, def);
  }

  public boolean getBoolean(String path) {
    return this.config.getBoolean(path);
  }

  public boolean getBoolean(String path, boolean def) {
    return this.config.getBoolean(path, def);
  }

  public void createSection(String path) {
    this.config.createSection(path);
  }

  public ConfigurationSection getConfigurationSection(String path) {
    return this.config.getConfigurationSection(path);
  }

  public double getDouble(String path) {
    return this.config.getDouble(path);
  }

  public double getDouble(String path, double def) {
    return this.config.getDouble(path, def);
  }

  public List<?> getList(String path) {
    return this.config.getList(path);
  }

  public List<?> getList(String path, List<?> def) {
    return this.config.getList(path, def);
  }

  public boolean contains(String path) {
    return this.config.contains(path);
  }

  public void removeKey(String path) {
    this.config.set(path, null);
  }

  public void set(String path, Object value) {
    this.config.set(path, value);
  }

  public void set(String path, Object value, String comment) {
    if (!this.config.contains(path)) {
      this.config.set(manager.getPluginName() + "_COMMENT_" + comments, " " + comment);
      comments++;
    }

    this.config.set(path, value);
  }

  public void set(String path, Object value, String[] comment) {

    for (String comm : comment) {

      if (!this.config.contains(path)) {
        this.config.set(manager.getPluginName() + "_COMMENT_" + comments, " " + comm);
        comments++;
      }

    }

    this.config.set(path, value);

  }

  public void setHeader(String[] header) {
    manager.setHeader(this.file, header);
    this.comments = header.length + 2;
    this.reloadConfig();
  }

  public void reloadConfig() {
    Reader reader = new InputStreamReader(manager.getConfigContent(file));
    this.config = YamlConfiguration.loadConfiguration(reader);
  }

  public void saveConfig() {
    String config = this.config.saveToString();
    manager.saveConfig(config, this.file);

  }

  public Set<String> getKeys() {
    return this.config.getKeys(false);
  }

}
