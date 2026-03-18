package org.anasoid.iptvorganizer.exceptions;

/** Exception thrown when a resource is not found */
public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }

  public NotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
