package com.vectara.examples.salpha.util;

import com.vectara.StatusProtos.Status;
import com.vectara.StatusProtos.StatusCode;

public class StatusOr<T> {
  private T result;
  private Status status;

  public StatusOr(T result) {
    status = null;
    this.result = result;
  }

  public StatusOr(Status status) {
    result = null;
    this.status = status;
  }

  public StatusOr(Throwable t) {
    result = null;
    this.status = StatusUtils.status(t);
  }

  public StatusOr(StatusCode code) {
    result = null;
    this.status = StatusUtils.status(code);
  }

  public StatusOr(StatusCode code, Throwable t) {
    result = null;
    this.status = StatusUtils.status(code, t);
  }

  public StatusOr(StatusCode code, String detail) {
    result = null;
    this.status = StatusUtils.status(code, detail);
  }

  public T get() {
    assert ok();
    return result;
  }

  public boolean ok() {
    return status == null || status.getCode() == StatusCode.OK;
  }

  public T getOrDie() {
    if (!ok()) {
      throw new StatusException(status);
    }
    return result;
  }

  public Status status() {
    return status;
  }

  @Override
  public String toString() {
    return String.format("(status=%s; result=%s)", StatusUtils.toString(status), result);
  }
}
