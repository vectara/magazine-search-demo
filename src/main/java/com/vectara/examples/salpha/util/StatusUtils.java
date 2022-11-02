package com.vectara.examples.salpha.util;

import com.google.protobuf.TextFormat;
import com.vectara.StatusProtos.Status;
import com.vectara.StatusProtos.StatusCode;
import javax.annotation.Nullable;

public class StatusUtils {
  /**
   * Return an OK status.
   */
  public static Status ok() {
    return Status.newBuilder().setCode(StatusCode.OK).build();
  }

  public static boolean ok(@Nullable Status status) {
    return status == null || status.getCode() == StatusCode.OK;
  }

  public static boolean ok(StatusCode code) {
    return code == StatusCode.OK;
  }

  /**
   * Return a generic status.
   */
  public static Status status(StatusCode code, @Nullable String statusDetail) {
    return Status.newBuilder()
        .setCode(code)
        .setStatusDetail(statusDetail)
        .build();
  }

  /**
   * Return a generic status.
   */
  public static Status status(StatusCode code) {
    return Status.newBuilder()
        .setCode(code)
        .build();
  }

  /**
   * Construct a generic failure status from an arbitrary exception.
   */
  public static Status status(Throwable t) {
    return status(StatusCode.FAILURE, t);
  }

  /**
   * Construct a failure status from an arbitrary exception.
   */
  public static Status status(StatusCode code, Throwable t) {
    return Status.newBuilder()
        .setCode(code)
        .setStatusDetail(String.valueOf(t))
        .build();
  }

  public static String toString(Status s) {
    // Default toString() for proto messages prints a new line after each field, which messes up
    // log lines. So we use TextFormat.shortDebugString.
    // TextFormat.shortDebugString expects non-null protos messages.
    return s == null ? "(null)" : TextFormat.shortDebugString(s);
  }
}
