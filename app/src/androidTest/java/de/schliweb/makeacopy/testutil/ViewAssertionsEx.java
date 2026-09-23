package de.schliweb.makeacopy.testutil;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.util.DisplayMetrics;
import androidx.test.espresso.ViewAssertion;

/** Extra Espresso ViewAssertions used in a11y tests. */
public final class ViewAssertionsEx {

  private ViewAssertionsEx() {}

  /**
   * Asserts that the view has a non-null contentDescription and its touch target (measured
   * width/height) is at least {@code minDp}.
   */
  public static ViewAssertion minTouchTargetAndContentDescription(int minDp) {
    return (view, noViewFoundException) -> {
      if (noViewFoundException != null) {
        throw noViewFoundException;
      }
      assertNotNull("view must not be null", view);
      CharSequence cd = view.getContentDescription();
      assertNotNull("contentDescription must be set", cd);
      DisplayMetrics dm = view.getResources().getDisplayMetrics();
      int wDp = (int) (view.getWidth() / dm.density + 0.5f);
      int hDp = (int) (view.getHeight() / dm.density + 0.5f);
      assertTrue("width should be >=" + minDp + "dp but is " + wDp + "dp", wDp >= minDp);
      assertTrue("height should be >=" + minDp + "dp but is " + hDp + "dp", hDp >= minDp);
    };
  }
}
