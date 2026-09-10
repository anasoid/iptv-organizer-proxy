package org.anasoid.iptvorganizer.services.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.anasoid.iptvorganizer.models.monitor.ThreadInfo;
import org.anasoid.iptvorganizer.models.monitor.ThreadStackTraceDetails;
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

  @Test
  void getThreadDumpShouldContainCurrentThreadNameAndStackFrames() {
    JvmMonitorService service = new JvmMonitorService();

    String dump = service.getThreadDump();

    assertNotNull(dump);
    assertTrue(dump.contains("Generated at:"), "Thread dump should include header timestamp");
    assertTrue(
        dump.contains(Thread.currentThread().getName()),
        "Thread dump should include current thread name");
    assertTrue(dump.contains("\tat "), "Thread dump should include stack frames");
  }

  @Test
  void getThreadStackTraceShouldReturnCurrentThreadFrames() {
    JvmMonitorService service = new JvmMonitorService();

    List<String> stackTrace = service.getThreadStackTrace(Thread.currentThread().threadId());

    assertFalse(stackTrace.isEmpty(), "Current thread stack trace should not be empty");
    assertTrue(stackTrace.get(0).startsWith("at "), "Each frame should use the expected prefix");
  }

  @Test
  void getThreadStackTraceDetailsShouldContainCoreDiagnostics() {
    JvmMonitorService service = new JvmMonitorService();

    var maybeDetails = service.getThreadStackTraceDetails(Thread.currentThread().threadId());

    assertTrue(maybeDetails.isPresent(), "Current thread diagnostics should exist");
    ThreadStackTraceDetails details = maybeDetails.get();
    assertEquals(Thread.currentThread().threadId(), details.getThreadId());
    assertNotNull(details.getName());
    assertNotNull(details.getState());
    assertNotNull(details.getStackTrace());
    assertFalse(details.getStackTrace().isEmpty(), "Stack trace should not be empty");
  }
}
