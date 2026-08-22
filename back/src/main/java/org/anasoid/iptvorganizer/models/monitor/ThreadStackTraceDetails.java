package org.anasoid.iptvorganizer.models.monitor;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Detailed thread diagnostics useful for lock contention and performance investigations. */
@Getter
@Builder
public class ThreadStackTraceDetails {
  private final long threadId;
  private final String name;
  private final String state;
  private final boolean daemon;
  private final int priority;
  private final long cpuTimeMs;
  private final long userTimeMs;
  private final long blockedCount;
  private final long blockedTimeMs;
  private final long waitedCount;
  private final long waitedTimeMs;
  private final String lockName;
  private final Long lockOwnerId;
  private final String lockOwnerName;
  private final boolean suspended;
  private final boolean inNative;
  private final boolean deadlocked;
  private final List<String> lockedMonitors;
  private final List<String> lockedSynchronizers;
  private final List<String> stackTrace;
}
