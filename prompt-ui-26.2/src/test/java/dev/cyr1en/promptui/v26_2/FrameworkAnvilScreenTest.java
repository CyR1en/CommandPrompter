package dev.cyr1en.promptui.v26_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptui.ScreenResult;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class FrameworkAnvilScreenTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void clickedResultSurvivesAClientCloseBeforeTheNextTick(boolean manualCancel) throws Exception {
    var plugin = mock(JavaPlugin.class);
    var player = mock(Player.class);
    var scheduler = mock(EntityScheduler.class);
    var task = mock(ScheduledTask.class);
    when(player.getScheduler()).thenReturn(scheduler);
    when(scheduler.run(eq(plugin), any(), isNull())).thenReturn(task);
    var screen = new FrameworkAnvilScreen(plugin, player, "Prompt");
    List<ScreenResult> results = new ArrayList<>();
    screen.onResult(results::add);

    // Exercise the real completion methods without constructing an NMS menu and player.
    var state = FrameworkAnvilScreen.class.getDeclaredField("state");
    state.setAccessible(true);
    var open = state.getType().getField("OPEN");
    open.setAccessible(true);
    state.set(screen, open.get(null));
    var click =
        FrameworkAnvilScreen.class.getDeclaredMethod("completeAfterClick", ScreenResult.class);
    click.setAccessible(true);
    var complete =
        FrameworkAnvilScreen.class.getDeclaredMethod("complete", ScreenResult.class, boolean.class);
    complete.setAccessible(true);

    var expected = manualCancel ? ScreenResult.manualCancel() : ScreenResult.answer("accepted");
    click.invoke(screen, expected);
    complete.invoke(screen, ScreenResult.guiExit(), false);

    assertTrue(results.isEmpty());
    ArgumentCaptor<Consumer<ScheduledTask>> nextTick = ArgumentCaptor.captor();
    verify(scheduler).run(eq(plugin), nextTick.capture(), isNull());
    nextTick.getValue().accept(task);

    assertEquals(List.of(expected), results);
  }
}
