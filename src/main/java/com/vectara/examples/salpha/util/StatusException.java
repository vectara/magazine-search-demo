package com.vectara.examples.salpha.util;

import com.vectara.StatusProtos.Status;

/**
 * An exception that wraps a status.
 */
public class StatusException extends RuntimeException {
  private static final long serialVersionUID = 7301449192076643866L;
  private Status status;

  public StatusException(Status status) {
    super(status.getStatusDetail());
    this.status = status;
  }

  public Status getStatus() {
    return status;
  }
}
