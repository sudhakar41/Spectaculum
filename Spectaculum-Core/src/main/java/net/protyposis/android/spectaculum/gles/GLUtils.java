/*
 * Copyright 2014 Mario Guggenberger <mg@protyposis.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.protyposis.android.spectaculum.gles;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Mario on 14.06.2014.
 */
public class GLUtils {

    private static final String TAG = GLUtils.class.getSimpleName();

    public static boolean HAS_GLES30;
    public static boolean HAS_GL_OES_texture_half_float;
    public static boolean HAS_GL_OES_texture_float;
    public static boolean HAS_FLOAT_FRAMEBUFFER_SUPPORT;
    public static boolean HAS_GPU_TEGRA;

    /**
     * Sets the static feature flags. Needs to be called from a GLES context.
     */
    public static void init() {
        HAS_GLES30 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
        HAS_GL_OES_texture_half_float = checkExtension("GL_OES_texture_half_float");
        HAS_GL_OES_texture_float = checkExtension("GL_OES_texture_float");
        HAS_GPU_TEGRA = GLES20.glGetString(GLES20.GL_RENDERER).toLowerCase().contains("tegra");

        /* Try to create a framebuffer with an attached floating point texture. If this fails,
         * the device does not support floating point FB attachments and needs to fall back to
         * byte textures ... and possibly deactivate features that demand FP textures.
         */
        if(HAS_GL_OES_texture_half_float || HAS_GL_OES_texture_float) {
            try {
                // must be set to true before the check, otherwise the fallback kicks in
                HAS_FLOAT_FRAMEBUFFER_SUPPORT = true;
                new Framebuffer(8, 8);
            } catch (RuntimeException e) {
                Log.w(TAG, "float framebuffer test failed");
                HAS_FLOAT_FRAMEBUFFER_SUPPORT = false;
                GLUtils.clearError();
            }
        }
    }

    /**
     * Checks if the system supports OpenGL ES 2.0.
     */
    public static boolean isGlEs2Supported(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
        return configurationInfo != null && configurationInfo.reqGlEsVersion >= 0x20000;
    }

    private static void checkError(String operation, boolean throwException) {
        int errorCount = 0;
        int error;
        String msg = null;

        while((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            msg = "GL ERROR " + String.format("0x%X", error) + " @ " + operation;
            Log.e(TAG, msg);
            errorCount++;
        }

        if(throwException && errorCount > 0) {
            throw new RuntimeException(msg);
        }
    }

    public static void checkError(String operation) {
        checkError(operation, true);
    }

    public static void clearError() {
        checkError("error clearance", false);
    }

    public static String[] getExtensions() {
        String extensionsString = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        if(extensionsString != null) {
            return extensionsString.split(" ");
        }
        return new String[0];
    }

    /**
     * Checks if an extension is supported.
     */
    public static boolean checkExtension(String query) {
        for(String ext : getExtensions()) {
            if(ext.equals(query)) {
                return true;
            }
        }
        return false;
    }

    public static void printSysConfig() {
        for(String ext : GLUtils.getExtensions()) {
            Log.d(TAG, ext);
        }
        Log.d(TAG, GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION));
        Log.d(TAG, GLES20.glGetString(GLES20.GL_VENDOR));
        Log.d(TAG, GLES20.glGetString(GLES20.GL_RENDERER));
        Log.d(TAG, GLES20.glGetString(GLES20.GL_VERSION));
    }

    public static Bitmap getFrameBuffer(int width, int height) {
        // read pixels from GLES context
        long startTime = SystemClock.elapsedRealtime();
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
        GLUtils.checkError("glReadPixels");
        buffer.rewind();
        Log.d(TAG, "glReadPixels " + (SystemClock.elapsedRealtime() - startTime) + "ms");

        // transfer pixels to bitmap
        Bitmap bmp1 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp1.copyPixelsFromBuffer(buffer);

        // horizontally flip bitmap to make it upright, since GL origin is at bottom left
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setRotate(180);
        matrix.postScale(-1, 1);
        Bitmap bmp2 = Bitmap.createBitmap(bmp1, 0, 0, bmp1.getWidth(), bmp1.getHeight(), matrix, true);
        bmp1.recycle();
        Log.d(TAG, "glReadPixels+rotate " + (SystemClock.elapsedRealtime() - startTime) + "ms");

        return bmp2;
    }

    public static boolean saveBitmapToFile(Bitmap bmp, File file) {
        try {
            BufferedOutputStream bos = null;
            try {
                bos = new BufferedOutputStream(new FileOutputStream(file));

                // compress to file
                bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
            } finally {
                if (bos != null) bos.close();
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "failed to save frame", e);
        }
        return false;
    }

    public static void saveFramebufferToFile(int width, int height, File file) {
        Bitmap bmp = getFrameBuffer(width, height);
        if(saveBitmapToFile(bmp, file)) {
            Log.d(TAG, "frame saved to " + file.getName());
        }
        bmp.recycle();
    }

    public static class Matrix {
        /**
         * Android's {@link android.opengl.Matrix#setRotateEulerM(float[], int, float, float, float)}
         * is broken and returns an invalid matrix when rotating around the Y axis.
         *
         * See comment #3 in https://code.google.com/p/android/issues/detail?id=12964 for fix
         * @see android.opengl.Matrix#setRotateEulerM(float[], int, float, float, float)
         */
        public static void setRotateEulerM(float[] rm, int rmOffset, float x, float y, float z) {
            x = x * 0.01745329f;
            y = y * 0.01745329f;
            z = z * 0.01745329f;
            float sx = (float) Math.sin(x);
            float sy = (float) Math.sin(y);
            float sz = (float) Math.sin(z);
            float cx = (float) Math.cos(x);
            float cy = (float) Math.cos(y);
            float cz = (float) Math.cos(z);
            float cxsy = cx * sy;
            float sxsy = sx * sy;

            rm[rmOffset + 0] = cy * cz;
            rm[rmOffset + 1] = -cy * sz;
            rm[rmOffset + 2] = sy;
            rm[rmOffset + 3] = 0.0f;

            rm[rmOffset + 4] = sxsy * cz + cx * sz;
            rm[rmOffset + 5] = -sxsy * sz + cx * cz;
            rm[rmOffset + 6] = -sx * cy;
            rm[rmOffset + 7] = 0.0f;

            rm[rmOffset + 8] = -cxsy * cz + sx * sz;
            rm[rmOffset + 9] = cxsy * sz + sx * cz;
            rm[rmOffset + 10] = cx * cy;
            rm[rmOffset + 11] = 0.0f;

            rm[rmOffset + 12] = 0.0f;
            rm[rmOffset + 13] = 0.0f;
            rm[rmOffset + 14] = 0.0f;
            rm[rmOffset + 15] = 1.0f;
        }
    }
}
