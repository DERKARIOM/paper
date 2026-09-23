/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ui;

import androidx.fragment.app.Fragment;
import com.google.android.material.transition.MaterialSharedAxis;

/**
 * Small helper applying the Material 3 shared-axis (X) transition pattern to the workflow fragments
 * Crop → OCR → Export: forward navigation slides along the X axis ("forward = progress"), back
 * navigation reverses it.
 *
 * <p>The CameraFragment is deliberately excluded: its CameraX {@code PreviewView} uses a
 * SurfaceView which cannot be animated by view transitions — a previous attempt made back
 * navigation from Crop appear broken. {@link #applySharedAxisXForwardOnly(Fragment)} exists for
 * fragments bordering the camera screen so only the transitions away from / back to the next
 * pipeline step are animated.
 *
 * <p>The framework automatically honors the system animator duration scale, so reduced-motion
 * settings are respected without extra code.
 */
public final class TransitionUtils {

  private TransitionUtils() {}

  /**
   * Applies MaterialSharedAxis.X enter/exit/return/reenter transitions to the given fragment. Call
   * from {@code Fragment#onCreate(Bundle)}. Use only for fragments whose both navigation neighbors
   * tolerate view transitions (no SurfaceView screens).
   *
   * @param fragment the workflow fragment to apply the transitions to
   */
  public static void applySharedAxisX(Fragment fragment) {
    fragment.setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
    fragment.setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
    fragment.setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
    fragment.setReenterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
  }

  /**
   * Applies only exit/reenter MaterialSharedAxis.X transitions (towards the next pipeline step and
   * back from it). Enter/return stay unanimated — used for fragments that border the camera screen,
   * where animating against the SurfaceView preview breaks perceived back navigation.
   *
   * @param fragment the workflow fragment to apply the transitions to
   */
  public static void applySharedAxisXForwardOnly(Fragment fragment) {
    fragment.setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
    fragment.setReenterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
  }

  /**
   * Applies only enter/return MaterialSharedAxis.X transitions (arriving from the previous pipeline
   * step and going back to it). Exit/reenter stay unanimated — used for fragments that can navigate
   * forward to the camera screen (e.g. Export's "New"/"Add page"), where animating against the
   * SurfaceView preview breaks perceived navigation.
   *
   * @param fragment the workflow fragment to apply the transitions to
   */
  public static void applySharedAxisXEnterReturnOnly(Fragment fragment) {
    fragment.setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
    fragment.setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
  }
}
