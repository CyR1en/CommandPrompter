package com.cyr1en.cp.util;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SignGUI {

  protected ProtocolManager protocolManager;
  protected PacketAdapter packetListener;
  protected Map<String, SignGUIListener> listeners;
  protected Map<String, Vector> signLocations;

  public SignGUI(Plugin plugin) {
    protocolManager = ProtocolLibrary.getProtocolManager();
    packetListener = new PacketListener(plugin);
    protocolManager.addPacketListener(packetListener);
    listeners = new ConcurrentHashMap<>();
    signLocations = new ConcurrentHashMap<>();
  }

  public void open(Player player, SignGUIListener response) {
    open(player, (Location) null, response);
  }

  public void open(Player player, Location signLocation, SignGUIListener response) {
    int x = 0, y = 0, z = 0;
    if (signLocation != null) {
      x = signLocation.getBlockX();
      y = signLocation.getBlockY();
      z = signLocation.getBlockZ();
    }

    PacketContainer packet = protocolManager.createPacket(133);
    packet.getIntegers().write(0, 0).write(1, x).write(2, y).write(3, z);

    try {
      protocolManager.sendServerPacket(player, packet);
      signLocations.put(player.getName(), new Vector(x, y, z));
      listeners.put(player.getName(), response);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  public void open(Player player, String[] defaultText, SignGUIListener response) {
    List<PacketContainer> packets = new ArrayList<>();

    int x = 0, y = 0, z = 0;
    if (defaultText != null) {
      x = player.getLocation().getBlockX();
      z = player.getLocation().getBlockZ();

      PacketContainer packet53 = protocolManager.createPacket(53);
      packet53.getIntegers().write(0, x).write(1, y).write(2, z).write(3, 63).write(4, 0);
      packets.add(packet53);

      PacketContainer packet130 = protocolManager.createPacket(130);
      packet130.getIntegers().write(0, x).write(1, y).write(2, z);
      packet130.getStringArrays().write(0, defaultText);
      packets.add(packet130);
    }

    PacketContainer packet133 = protocolManager.createPacket(133);
    packet133.getIntegers().write(0, 0).write(1, x).write(2, y).write(3, z);
    packets.add(packet133);

    if (defaultText != null) {
      PacketContainer packet53 = protocolManager.createPacket(53);
      packet53.getIntegers().write(0, x).write(1, y).write(2, z).write(3, 7).write(4, 0);
      packets.add(packet53);
    }

    try {
      for (PacketContainer packet : packets) {
        protocolManager.sendServerPacket(player, packet);
      }
      signLocations.put(player.getName(), new Vector(x, y, z));
      listeners.put(player.getName(), response);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  public void destroy() {
    protocolManager.removePacketListener(packetListener);
    listeners.clear();
    signLocations.clear();
  }

  public interface SignGUIListener {
    void onSignDone(Player player, String[] lines);
  }

  class PacketListener extends PacketAdapter {

    Plugin plugin;

    public PacketListener(Plugin plugin) {
      super(new PacketAdapter.AdapterParameteters().plugin(plugin).clientSide().listenerPriority(ListenerPriority.NORMAL).packets(0x82));
      this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
      final Player player = event.getPlayer();
      Vector v = signLocations.remove(player.getName());
      if (v == null) return;
      List<Integer> list = event.getPacket().getIntegers().getValues();
      if (list.get(0) != v.getBlockX()) return;
      if (list.get(1) != v.getBlockY()) return;
      if (list.get(2) != v.getBlockZ()) return;

      final String[] lines = event.getPacket().getStringArrays().getValues().get(0);
      final SignGUIListener response = listeners.remove(event.getPlayer().getName());
      if (response != null) {
        event.setCancelled(true);
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> response.onSignDone(player, lines));
      }
    }

  }

}
