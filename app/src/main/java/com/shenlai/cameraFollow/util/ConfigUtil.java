package com.shenlai.cameraFollow.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.arcsoft.face.FaceEngine;

public class ConfigUtil {
    private static final String FIRST_USE = "FirstUse";
    private static final String TOTAL_X = "TotalX";
    private static final String TOTAL_Y = "TotalY";

    private static final String APP_NAME = "CameraFollow";
    private static final String TRACK_ID = "trackID";
    private static final String FT_ORIENT = "ftOrient";
    private static final String EN_ACTIVED = "actived";

    public static void setTrackId(Context context, int trackId) {
        if (context == null) {
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences(APP_NAME, Context.MODE_PRIVATE);
        sharedPreferences.edit()
                .putInt(TRACK_ID, trackId)
                .apply();
    }

    public static int getTrackId(Context context) {
        if (context == null) {
            return 0;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences(APP_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getInt(TRACK_ID, 0);
    }

    public static void setFtOrient(Context context, int ftOrient) {
        if (context == null) {
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences(APP_NAME, Context.MODE_PRIVATE);
        sharedPreferences.edit()
                .putInt(FT_ORIENT, ftOrient)
                .apply();
    }

    public static int getFtOrient(Context context) {
        if (context == null) {
            return FaceEngine.ASF_OP_270_ONLY;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences(APP_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getInt(FT_ORIENT, FaceEngine.ASF_OP_270_ONLY);
    }

    public static void setActived(Context context, Boolean actived) {
        if (context == null) {
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences(APP_NAME, Context.MODE_PRIVATE);
        sharedPreferences.edit()
                .putBoolean(EN_ACTIVED, actived)
                .apply();
    }

    public static Boolean getActived(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences(APP_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(EN_ACTIVED, false);
    }

    public static Boolean firstUse(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(APP_NAME, Context.MODE_PRIVATE);
        Boolean first = sharedPreferences.getBoolean(FIRST_USE, true);
        if (first) {
            sharedPreferences.edit()
                    .putBoolean(FIRST_USE, false)
                    .putInt(TOTAL_Y, 0)
                    .putInt(TOTAL_Y, 0).apply();
        }
        return first;
    }

    public static int[] getTotalXY(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(APP_NAME, Context.MODE_PRIVATE);
        int tx = sharedPreferences.getInt(TOTAL_X, 0);
        int ty = sharedPreferences.getInt(TOTAL_Y, 0);
        return new int[]{tx, ty};
    }
}
