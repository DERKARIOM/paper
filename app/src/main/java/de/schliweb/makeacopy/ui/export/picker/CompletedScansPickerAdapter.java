/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.export.picker;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.ui.export.session.CompletedScan;
import de.schliweb.makeacopy.utils.image.BitmapUtils;
import de.schliweb.makeacopy.utils.image.ImageDecodeUtils;
import de.schliweb.makeacopy.utils.image.RotationPolicy;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Adapter class to manage and bind a list of completed scans in a RecyclerView. Used for picking
 * and displaying completed scan items with support for selection, enabling/disabling items, and
 * handling item interactions such as clicks and long-presses.
 */
class CompletedScansPickerAdapter extends RecyclerView.Adapter<CompletedScansPickerAdapter.VH> {

  /**
   * Interface representing callbacks used for handling user interactions with items in a
   * RecyclerView or similar list-based UI component. These callbacks are commonly used in adapter
   * implementations to delegate selection, state change, and interaction logic to the hosting
   * component or fragment.
   */
  interface Callbacks {
    boolean isSelected(@NonNull String id);

    void onItemSelectionChanged(@NonNull String id, boolean selected);

    boolean isDisabled(@NonNull String id);

    void onItemLongPress(@NonNull String id);
  }

  private final List<CompletedScan> items = new ArrayList<>();
  private final Callbacks callbacks;

  // Small thread pool for background decoding
  private static final Executor EXEC = Executors.newFixedThreadPool(2);
  private static final Handler MAIN = new Handler(Looper.getMainLooper());

  CompletedScansPickerAdapter(Callbacks callbacks) {
    this.callbacks = callbacks;
    setHasStableIds(false);
  }

  /**
   * Updates the adapter with a new list of completed scans. Clears the current items and adds all
   * elements from the provided list, then notifies the adapter that the data set has changed.
   *
   * @param list The new list of completed scans to be displayed. If null, the current list will be
   *     cleared.
   */
  public void submitList(List<CompletedScan> list) {
    items.clear();
    if (list != null) items.addAll(list);
    notifyDataSetChanged();
  }

  /**
   * Creates a new {@link VH} instance by inflating the layout for an individual item in the
   * RecyclerView.
   *
   * @param parent The parent ViewGroup into which the new view will be added after it is bound to a
   *     position within the adapter. It provides the context for layout inflation.
   * @param viewType The view type of the new view. This parameter can be used to handle multiple
   *     view types, though it is not used in this specific implementation.
   * @return A new {@link VH} instance holding the view for an individual RecyclerView item.
   */
  @NonNull
  @Override
  public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View v =
        LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_completed_scan_entry, parent, false);
    return new VH(v);
  }

  /**
   * Binds the data to the specified ViewHolder, configuring its visual elements and interaction
   * behaviors based on the item data at the given position in the adapter's data set.
   *
   * @param h The ViewHolder that should be updated to represent the content of the item at the
   *     given position. This includes setting its text, image, and interaction handlers based on
   *     the item's state.
   * @param position The position of the item within the adapter's data set. This determines which
   *     item from the data source should be bound to the given ViewHolder.
   */
  @Override
  public void onBindViewHolder(@NonNull VH h, int position) {
    CompletedScan s = items.get(position);
    String date =
        DateFormat.getMediumDateFormat(h.itemView.getContext()).format(s.createdAt())
            + " "
            + DateFormat.getTimeFormat(h.itemView.getContext()).format(s.createdAt());

    boolean hasThumb = s.thumbPath() != null && new File(s.thumbPath()).exists();
    boolean hasFile = s.filePath() != null && new File(s.filePath()).exists();
    boolean missing = !hasThumb && !hasFile;

    if (missing) {
      h.title.setText(date + " • " + h.itemView.getContext().getString(R.string.missing_file));
      h.thumb.setImageResource(R.drawable.ic_image);
      h.thumb.setTag(null);
    } else {
      h.title.setText(date);
      // Efficient thumb loading with sampling and background decode
      String path = hasThumb ? s.thumbPath() : s.filePath();
      int deg = 0;
      try {
        deg = s.rotationDeg();
      } catch (Throwable ignore) {
        // Best-effort; failure is non-critical
      }
      String mode = null;
      try {
        mode = s.orientationMode();
      } catch (Throwable ignore) {
        // Best-effort; failure is non-critical
      }
      boolean rotate;
      try {
        rotate = RotationPolicy.shouldRotateForThumbnail(true, mode, deg);
      } catch (Throwable ignore) {
        rotate = false;
      }
      int effDeg = rotate ? ((deg % 360) + 360) % 360 : 0;
      loadThumbnailAsync(h.thumb, path, dpToPx(h.itemView.getResources(), 56), effDeg);
    }

    boolean disabled = callbacks.isDisabled(s.id()) || missing;
    boolean selected = callbacks.isSelected(s.id());
    // If disabled, force checked and disable interactions
    h.checkBox.setChecked(disabled || selected);
    h.checkBox.setEnabled(!disabled);
    h.itemView.setEnabled(!disabled);
    h.itemView.setAlpha(disabled ? 0.4f : 1.0f);

    if (!disabled) {
      h.itemView.setOnClickListener(v -> toggle(s));
      h.checkBox.setOnClickListener(v -> toggle(s));
      h.itemView.setOnLongClickListener(
          v -> {
            callbacks.onItemLongPress(s.id());
            return true;
          });
    } else {
      h.itemView.setOnClickListener(null);
      h.itemView.setOnLongClickListener(null);
      h.checkBox.setOnClickListener(null);
    }
  }

  private void toggle(CompletedScan s) {
    boolean newState = !callbacks.isSelected(s.id());
    callbacks.onItemSelectionChanged(s.id(), newState);
    notifyDataSetChanged();
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  // ===== Image loading helpers =====
  private static void loadThumbnailAsync(
      @NonNull ImageView target, @NonNull String path, int targetPx, int rotationDeg) {
    target.setImageResource(R.drawable.ic_image);
    target.setTag(path);
    EXEC.execute(
        () -> {
          Bitmap bmp = null;
          try {
            bmp = decodeSampled(path, targetPx, targetPx);
            // Apply rotation if requested (only for metadata entries)
            if (bmp != null && ((rotationDeg % 360) != 0)) {
              bmp = BitmapUtils.maybeRotate(bmp, rotationDeg);
            }
          } catch (Throwable ignore) {
            // Best-effort; failure is non-critical
          }
          final Bitmap result = bmp;
          MAIN.post(
              () -> {
                Object tag = target.getTag();
                if (tag != null && tag.equals(path)) {
                  if (result != null) target.setImageBitmap(result);
                  else target.setImageResource(R.drawable.ic_image);
                }
              });
        });
  }

  private static int dpToPx(Resources res, int dp) {
    DisplayMetrics dm = res.getDisplayMetrics();
    return Math.max(1, (int) (dp * dm.density + 0.5f));
  }

  private static Bitmap decodeSampled(String path, int reqW, int reqH) {
    // Centralized EXIF-neutral decode for baked disk files
    try {
      return ImageDecodeUtils.decodeSampled(path, Math.max(1, reqW), Math.max(1, reqH));
    } catch (Throwable t) {
      return null;
    }
  }

  @SuppressWarnings("UnusedMethod") // kept for future bitmap downsampling optimization
  private static int calculateInSampleSize(
      BitmapFactory.Options options, int reqWidth, int reqHeight) {
    int height = Math.max(1, options.outHeight);
    int width = Math.max(1, options.outWidth);
    int inSampleSize = 1;
    if (height > reqHeight || width > reqWidth) {
      final int halfHeight = height / 2;
      final int halfWidth = width / 2;
      while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
        inSampleSize *= 2;
      }
    }
    return Math.max(1, inSampleSize);
  }

  /**
   * A static inner class of the adapter that represents a ViewHolder for the RecyclerView. This
   * ViewHolder is responsible for holding references to the views within an individual item of the
   * RecyclerView, enabling efficient recycling and updating of list items.
   *
   * <p>Fields: - thumb: Represents an ImageView for displaying a thumbnail associated with the
   * item. - title: Represents a TextView for displaying the title of the item. - checkBox:
   * Represents a CheckBox for managing the selection state of the item.
   *
   * <p>Constructor: The constructor initializes the ViewHolder by binding its view references to
   * the corresponding UI elements in the provided itemView. It uses findViewById to bind views
   * based on their IDs.
   */
  static class VH extends RecyclerView.ViewHolder {
    ImageView thumb;
    TextView title;
    CheckBox checkBox;

    VH(@NonNull View itemView) {
      super(itemView);
      thumb = itemView.findViewById(R.id.thumb);
      title = itemView.findViewById(R.id.title);
      checkBox = itemView.findViewById(R.id.checkbox);
    }
  }
}
