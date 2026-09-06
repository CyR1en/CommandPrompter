package dev.cyr1en.promptpaper.testutil;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PlayerMock with the Paper entity scheduler methods implemented. */
public class TestPlayerMock extends PlayerMock {

  private final EntityScheduler scheduler = new MockEntityScheduler();

  public TestPlayerMock(ServerMock server, String name) {
    super(server, name);
  }

  @Override
  public EntityScheduler getScheduler() {
    return scheduler;
  }
}
