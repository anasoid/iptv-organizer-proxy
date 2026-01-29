package org.anasoid.iptvorganizer.exceptions;

/** Exception thrown when access is forbidden (client inactive, insufficient permissions, etc.) */
public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) {
    super(message);
  }

  public ForbiddenException(String message, Throwable cause) {
    super(message, cause);
  }
}
