package org.anasoid.iptvorganizer.models.monitor;

import lombok.Builder;
import lombok.Getter;

/**
 * Snapshot of a single JVM thread at the time of the request.
 *
 * <p>{@code state} is the string representation of {@link java.lang.Thread.State} (e.g. {@code
 * "RUNNABLE"}, {@code "BLOCKED"}, {@code "WAITING"}, {@code "TIMED_WAITING"}).
 */
@Getter
@Builder
public class ThreadInfo {

  /** JVM-assigned thread identifier. Unique within a JVM lifetime. */
  private final long id;

  /** Thread name as assigned by the creator (e.g. {@code "executor-thread-1"}). */
  private final String name;

  /**
   * Current thread state as a string ({@code NEW}, {@code RUNNABLE}, {@code BLOCKED}, {@code
   * WAITING}, {@code TIMED_WAITING}, {@code TERMINATED}).
   */
  private final String state;

  /** {@code true} when this is a daemon thread (background JVM service thread). */
  private final boolean daemon;

  /** Thread scheduling priority (1–10, normal is 5). */
  private final int priority;
}
