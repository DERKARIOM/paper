package de.schliweb.makeacopy.testutil;

import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

public class ViewDump {
  private static final String TAG = "[A11Y_TEST_VIEW_DUMP]";

  public static void dumpTree(View root) {
    try {
      StringBuilder sb = new StringBuilder();
      dumpNode(sb, root, 0);
      Log.d(TAG, sb.toString());
    } catch (Throwable t) {
      Log.w(TAG, "dumpTree failed: " + t.getMessage());
    }
  }

  private static void dumpNode(StringBuilder sb, View v, int depth) {
    if (v == null) return;
    indent(sb, depth);
    sb.append(nodeInfo(v)).append('\n');
    if (v instanceof ViewGroup vg) {
      for (int i = 0; i < vg.getChildCount(); i++) {
        dumpNode(sb, vg.getChildAt(i), depth + 1);
      }
    }
  }

  private static void indent(StringBuilder sb, int depth) {
    for (int i = 0; i < depth; i++) sb.append("  ");
  }

  private static String nodeInfo(View v) {
    String idName = "no-id";
    try {
      if (v.getId() != View.NO_ID) {
        idName = v.getResources().getResourceEntryName(v.getId());
      }
    } catch (Resources.NotFoundException ignore) {
      // Safe to ignore: view may have a dynamically generated ID with no resource name
    }
    return v.getClass().getSimpleName()
        + "#"
        + idName
        + " vis="
        + visToStr(v.getVisibility())
        + " shown="
        + v.isShown()
        + " cd="
        + String.valueOf(v.getContentDescription());
  }

  private static String visToStr(int v) {
    switch (v) {
      case View.VISIBLE:
        return "VISIBLE";
      case View.INVISIBLE:
        return "INVISIBLE";
      case View.GONE:
        return "GONE";
      default:
        return String.valueOf(v);
    }
  }
}
