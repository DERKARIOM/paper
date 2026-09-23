package de.schliweb.makeacopy.a11y.util;

import de.schliweb.makeacopy.utils.ui.A11yUtils;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class A11yCapture {
  private static final java.util.concurrent.BlockingQueue<CharSequence> Q =
      new java.util.concurrent.LinkedBlockingQueue<>();

  private A11yCapture() {}

  public static void install() {
    A11yUtils.setAnnounceListener(Q::add);
  }

  public static void uninstall() {
    A11yUtils.setAnnounceListener(null);
    Q.clear();
  }

  public static void clear() {
    Q.clear();
  }

  public static CharSequence await(long timeoutMs) throws InterruptedException {
    return Q.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
  }

  /**
   * Awaits until timeout for an announcement that matches the provided predicate. Non-matching
   * announcements are ignored (dropped). Returns null on timeout.
   */
  public static CharSequence awaitMatching(long timeoutMs, Predicate<String> predicate)
      throws InterruptedException {
    if (predicate == null) return await(timeoutMs);
    final long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      long remaining = Math.max(1L, deadline - System.currentTimeMillis());
      CharSequence s = Q.poll(remaining, TimeUnit.MILLISECONDS);
      if (s == null) return null;
      try {
        if (predicate.test(String.valueOf(s))) return s;
      } catch (Throwable ignored) {
        // Ignore predicate errors and continue
      }
    }
    return null;
  }
}
