/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.schliweb.makeacopy.R;

/**
 * Lightweight Material 3 workflow stepper showing the position inside the scan pipeline (Camera →
 * Crop → OCR → Export) as a row of dots. The active step uses {@code ?attr/colorPrimary}, inactive
 * steps use {@code ?attr/colorOutlineVariant}, so dynamic color and dark mode are handled
 * automatically.
 *
 * <p>The view is purely informational: it is not clickable and exposes a localized content
 * description such as "Step 2 of 4: Crop" for TalkBack.
 */
public class WorkflowStepperView extends LinearLayout {

  private static final int STEP_COUNT = 4;
  private static final int[] STEP_LABELS = {
    R.string.title_camera, R.string.title_crop, R.string.title_ocr, R.string.title_export
  };

  private int currentStep = 1;

  public WorkflowStepperView(@NonNull Context context) {
    this(context, null);
  }

  public WorkflowStepperView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public WorkflowStepperView(
      @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER);
    int step = 1;
    if (attrs != null) {
      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WorkflowStepperView);
      step = a.getInt(R.styleable.WorkflowStepperView_currentStep, 1);
      a.recycle();
    }
    buildDots();
    setCurrentStep(step);
  }

  private void buildDots() {
    removeAllViews();
    int size = dp(10);
    int margin = dp(6);
    for (int i = 0; i < STEP_COUNT; i++) {
      View dot = new View(getContext());
      LayoutParams lp = new LayoutParams(size, size);
      lp.setMarginStart(margin);
      lp.setMarginEnd(margin);
      dot.setLayoutParams(lp);
      dot.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      addView(dot);
    }
  }

  /**
   * Sets the current workflow step.
   *
   * @param step 1-based step index (1=Camera, 2=Crop, 3=OCR, 4=Export); values are clamped
   */
  public void setCurrentStep(int step) {
    currentStep = Math.max(1, Math.min(STEP_COUNT, step));
    for (int i = 0; i < getChildCount(); i++) {
      getChildAt(i)
          .setBackgroundResource(
              i < currentStep ? R.drawable.stepper_dot_active : R.drawable.stepper_dot_inactive);
    }
    setContentDescription(
        getContext()
            .getString(
                R.string.workflow_step_description,
                currentStep,
                STEP_COUNT,
                getContext().getString(STEP_LABELS[currentStep - 1])));
  }

  /** Returns the 1-based index of the current workflow step. */
  public int getCurrentStep() {
    return currentStep;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
