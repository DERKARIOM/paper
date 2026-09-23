package de.schliweb.makeacopy.a11y.util;

import de.schliweb.makeacopy.utils.ui.HapticsUtils;

/** Test helper to capture one-shot haptic vibrations emitted via HapticsUtils. */
public final class HapticsCapture {
  private static final java.util.concurrent.BlockingQueue<Long> Q =
      new java.util.concurrent.LinkedBlockingQueue<>();

  private HapticsCapture() {}

  public static void install() {
    HapticsUtils.setTestListener(Q::add);
  }

  public static void uninstall() {
    HapticsUtils.setTestListener(null);
    Q.clear();
  }

  public static void clear() {
    Q.clear();
  }

  public static Long await(long timeoutMs) throws InterruptedException {
    return Q.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
  }

  public static int drainedCount() {
    int n = 0;
    Long v;
    while ((v = Q.poll()) != null) {
      n++;
    }
    return n;
  }
}
