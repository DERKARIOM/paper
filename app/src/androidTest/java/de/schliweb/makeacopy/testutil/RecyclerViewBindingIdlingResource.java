package de.schliweb.makeacopy.testutil;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.IdlingResource;

/**
 * An IdlingResource that idles when a RecyclerView has an adapter with at least a given item count
 * AND a ViewHolder is bound for a specific adapter position.
 *
 * <p>This helps to deterministically wait for binding/layout without blocking the UI thread.
 */
public class RecyclerViewBindingIdlingResource implements IdlingResource {
  private final RecyclerView recyclerView;
  private final int minItemCount;
  private final int targetAdapterPosition;
  private final boolean waitForBoundViewHolder;
  private volatile ResourceCallback callback;

  private RecyclerView.Adapter<?> lastObservedAdapter;
  private RecyclerView.AdapterDataObserver dataObserver;

  /**
   * Waits until adapter has at least {@code minItemCount} items and (by default) a bound ViewHolder
   * at {@code targetAdapterPosition}. Use the 4-arg constructor to disable waiting for a bound
   * ViewHolder if that causes flakes on slow emulators.
   */
  public RecyclerViewBindingIdlingResource(
      @NonNull RecyclerView rv, int minItemCount, int targetAdapterPosition) {
    this(rv, minItemCount, targetAdapterPosition, true);
  }

  public RecyclerViewBindingIdlingResource(
      @NonNull RecyclerView rv,
      int minItemCount,
      int targetAdapterPosition,
      boolean waitForBoundViewHolder) {
    this.recyclerView = rv;
    this.minItemCount = minItemCount;
    this.targetAdapterPosition = targetAdapterPosition;
    this.waitForBoundViewHolder = waitForBoundViewHolder;
  }

  @Override
  public String getName() {
    return "RecyclerViewBindingIdlingResource(" + recyclerView.getId() + ")";
  }

  @Override
  public boolean isIdleNow() {
    ensureObserverRegistered();
    boolean idle = isBound();
    if (idle && callback != null) {
      callback.onTransitionToIdle();
    }
    return idle;
  }

  private boolean isBound() {
    RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
    if (adapter == null) return false;
    if (adapter.getItemCount() < minItemCount) return false;
    // Ensure the RecyclerView (and its container) is actually visible before declaring idle
    if (!recyclerView.isShown()) return false;
    if (!waitForBoundViewHolder) return true;
    return recyclerView.findViewHolderForAdapterPosition(targetAdapterPosition) != null;
  }

  @Override
  public void registerIdleTransitionCallback(ResourceCallback resourceCallback) {
    this.callback = resourceCallback;
    // Ensure an observer is attached (even if adapter is set later)
    ensureObserverRegistered();
    // Use OnPreDraw for a more reliable signal than OnDraw
    recyclerView
        .getViewTreeObserver()
        .addOnPreDrawListener(
            new android.view.ViewTreeObserver.OnPreDrawListener() {
              @Override
              public boolean onPreDraw() {
                if (callback != null && isBound()) callback.onTransitionToIdle();
                return true;
              }
            });
  }

  private void ensureObserverRegistered() {
    RecyclerView.Adapter<?> current = recyclerView.getAdapter();
    if (current == null) return;

    if (dataObserver == null) {
      dataObserver =
          new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
              if (callback != null && isBound()) callback.onTransitionToIdle();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
              if (callback != null && isBound()) callback.onTransitionToIdle();
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
              if (callback != null && isBound()) callback.onTransitionToIdle();
            }
          };
    }

    if (lastObservedAdapter != current) {
      try {
        if (lastObservedAdapter != null) {
          lastObservedAdapter.unregisterAdapterDataObserver(dataObserver);
        }
      } catch (Throwable ignored) {
        // Safe to ignore: observer may not be registered
      }
      try {
        current.registerAdapterDataObserver(dataObserver);
      } catch (Throwable ignored) {
        // Safe to ignore: observer may already be registered
      }
      lastObservedAdapter = current;
    }
  }
}
