package me.shouheng.camerax.enums;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author WngShhng (shouheng2015@gmail.com)
 * @version 2019/4/14 11:57
 */
public final class Camera {

    public static final int FACE_REAR = 0;
    public static final int FACE_FRONT = 1;

    @IntDef(value = {FACE_REAR, FACE_FRONT})
    @Retention(value = RetentionPolicy.SOURCE)
    public @interface Face {
    }
}