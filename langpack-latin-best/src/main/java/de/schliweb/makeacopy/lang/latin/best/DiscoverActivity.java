package de.schliweb.makeacopy.lang.latin.best;

import android.app.Activity;
import android.os.Bundle;

/**
 * DiscoverActivity is a subclass of Activity that is designed to immediately
 * terminate upon creation. This activity is not intended to be visible to the user.
 * <p>
 * The {@code onCreate} method is overridden to call {@code finish()} right after the
 * superclass's {@code onCreate} method, ensuring the activity lifecycle ends immediately
 * after it is started.
 */
public class DiscoverActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Immediately finish; this activity is not meant to be visible.
        finish();
    }
}
