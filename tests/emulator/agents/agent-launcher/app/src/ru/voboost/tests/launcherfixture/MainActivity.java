package ru.voboost.tests.launcherfixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Product-test fixture only: one launcher icon and a visibly identifiable activity. */
public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView label = new TextView(this);
        label.setText("Voboost launcher test fixture");
        label.setTextSize(28);
        label.setGravity(android.view.Gravity.CENTER);
        setContentView(label);
    }
}
