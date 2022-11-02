package com.vectara.examples.salpha.util;

import com.google.common.flogger.FluentLogger;
import org.checkerframework.checker.nullness.qual.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class ExpiringMemoizingSupplier<T> implements Supplier<T> {
  /**
   * A supplier that provides its own expiration value. For instance,
   * if supplying JWT tokens, the token itself includes its expiration
   * time.
   */
  public interface ExpiringSupplier<T> extends Supplier<ValueAndExpiration<T>> {
  }

  /**
   * Represents a value along with its expiration.
   */
  public static final class ValueAndExpiration<T> {
    private final T value;
    private long duration;
    private TimeUnit unit;

    public ValueAndExpiration(T value, long duration, TimeUnit unit) {
      checkArgument(unit != null, "unit cannot be null");
      checkArgument(duration > 0, "duration (%s %s) must be > 0", duration, unit);
      this.value = value;
      this.duration = duration;
      this.unit = unit;
    }

    public T getValue() {
      return value;
    }

    public long getDuration() {
      return duration;
    }

    public TimeUnit getUnit() {
      return unit;
    }
  }

  private static final FluentLogger LOG = FluentLogger.forEnclosingClass();
  final ExpiringSupplier<T> delegate;
  transient volatile @Nullable T value;
  // The special value 0 means "not yet initialized".
  transient volatile long expirationNanos;

  public ExpiringMemoizingSupplier(ExpiringSupplier<T> delegate) {
    this.delegate = checkNotNull(delegate);
  }

  @Override
  public T get() {
    // Another variant of Double Checked Locking.
    //
    // We use two volatile reads. We could reduce this to one by
    // putting our fields into a holder class, but (at least on x86)
    // the extra memory consumption and indirection are more
    // expensive than the extra volatile reads.
    long nanos = expirationNanos;
    long now = System.nanoTime();
    if (nanos == 0 || now - nanos >= 0) {
      synchronized (this) {
        if (nanos == expirationNanos) { // recheck for lost race
          ValueAndExpiration<T> t = delegate.get();
          value = t.getValue();
          nanos = now + t.getUnit().toNanos(t.getDuration());
          LOG.atInfo().log("Next expiration will occur in %d %s.", t.getDuration(), t.getUnit());
          // In the very unlikely event that nanos is 0, set it to 1;
          // no one will notice 1 ns of tardiness.
          expirationNanos = (nanos == 0) ? 1 : nanos;
          return t.getValue();
        }
      }
    }
    return value;
  }

  @Override
  public String toString() {
    // This is a little strange if the unit the user provided was not NANOS,
    // but we don't want to store the unit just for toString
    return "ExpiringMemoizingSupplier(" + delegate + ")";
  }
}
