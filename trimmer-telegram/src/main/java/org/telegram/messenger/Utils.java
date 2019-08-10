package org.telegram.messenger;

import android.content.Context;

public class Utils {

    public static int dp(Context context,float value) {
        if (value == 0) {
            return 0;
        }

        return (int) Math.ceil(context.getResources().getDisplayMetrics().density * value);
    }
}
