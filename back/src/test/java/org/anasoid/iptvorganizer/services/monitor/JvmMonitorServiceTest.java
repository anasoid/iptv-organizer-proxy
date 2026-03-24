package org.anasoid.iptvorganizer.services.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.anasoid.iptvorganizer.models.monitor.ThreadInfo;
import org.junit.jupiter.api.Test;

class JvmMonitorServiceTest {

  @Test
  void getThreadsShouldReturnCurrentThreadAndBeSortedByName() {
    JvmMonitorService service = new JvmMonitorService();

    List<ThreadInfo> threads = service.getThreads();

    assertFalse(threads.isEmpty(), "Thread snapshot should not be empty");
    assertTrue(
        threads.stream().anyMatch(t -> t.getId() == Thread.currentThread().threadId()),
        "Current thread must be present in the snapshot");

    for (int i = 1; i < threads.size(); i++) {
      String previous = threads.get(i - 1).getName();
      String current = threads.get(i).getName();
      assertTrue(previous.compareTo(current) <= 0, "Threads should be sorted by name");
    }
  }
}
