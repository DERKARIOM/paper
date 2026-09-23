package de.schliweb.makeacopy.testutil;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.test.espresso.IdlingResource;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * An IdlingResource that idles when a given LiveData satisfies a predicate. Useful to await
 * ViewModel state changes without depending on view binding timing.
 */
public class LiveDataIdlingResource<T> implements IdlingResource {
  private final LiveData<T> liveData;
  private final LifecycleOwner lifecycleOwner;
  private final Predicate<T> predicate;
  private volatile ResourceCallback callback;
  private final AtomicBoolean isIdle = new AtomicBoolean(false);

  public LiveDataIdlingResource(
      @NonNull LiveData<T> liveData,
      @NonNull LifecycleOwner owner,
      @NonNull Predicate<T> predicate) {
    this.liveData = liveData;
    this.lifecycleOwner = owner;
    this.predicate = predicate;
  }

  @Override
  public String getName() {
    return "LiveDataIdlingResource(" + liveData + ")";
  }

  @Override
  public boolean isIdleNow() {
    T v = liveData.getValue();
    boolean ok = v != null && predicate.test(v);
    if (ok) {
      isIdle.set(true);
      if (callback != null) callback.onTransitionToIdle();
    }
    return ok;
  }

  @Override
  public void registerIdleTransitionCallback(ResourceCallback resourceCallback) {
    this.callback = resourceCallback;
    liveData.observe(
        lifecycleOwner,
        new Observer<T>() {
          @Override
          public void onChanged(T t) {
            if (!isIdle.get() && isIdleNow()) {
              // callback fired in isIdleNow
            }
          }
        });
  }
}
