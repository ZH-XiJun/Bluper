package com.wtbruh.blurrr;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.UsingFieldMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 目标应用的 hook 入口。
 */
final class BlurrrHooker {

    private static final String TAG = ModuleMain.TAG;

    private static final String TARGET_ACTIVITY = ModuleMain.TARGET_PACKAGE + ".MainActivity";

    private static final String TOAST_TEXT = "Module loaded";

    /**
     * VIP 状态枚举，未被混淆，作为 DexKit 定位的锚点。
     */
    private static final String VIP_STATUS_CLASS = "ai.blurrr.video.user.model.VipStatus";

    /**
     * 当前版本的兜底位置：s3.m#j()Z。混淆名随版本变化，仅当 DexKit 定位失败时使用。
     */
    private static final String VIP_CHECK_FALLBACK_CLASS = "s3.m";
    private static final String VIP_CHECK_FALLBACK_METHOD = "j";

    private static boolean sInstalled = false;

    static {
        // DexKit 的 native 库随 APK 一起打包，由框架的模块 ClassLoader 负责加载
        System.loadLibrary("dexkit");
    }

    private BlurrrHooker() {
    }

    static void install(XposedModule module, XposedModuleInterface.PackageReadyParam param) {
        if (sInstalled) {
            return;
        }
        sInstalled = true;

        ClassLoader classLoader = param.getClassLoader();
        String apkPath = param.getApplicationInfo().sourceDir;
        module.log(Log.INFO, TAG, "apk = " + apkPath);
        module.log(Log.INFO, TAG, "classLoader = " + classLoader);

        Method vipCheck = null;
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            module.log(Log.INFO, TAG, "dex count = " + bridge.getDexNum());
            vipCheck = findVipCheckMethod(module, bridge, classLoader);
        } catch (Throwable tr) {
            module.log(Log.ERROR, TAG, "DexKit lookup failed", tr);
        }

        if (vipCheck == null) {
            vipCheck = fallbackVipCheck(module, classLoader);
        }
        if (vipCheck != null) {
            hookVipCheck(module, vipCheck);
        } else {
            module.log(Log.ERROR, TAG, "vip check method not found, skip");
        }

        hookMainActivity(module, classLoader);
    }

    /**
     * 用 DexKit 按结构定位 VIP 判断方法：
     * <pre>
     * public static boolean j() {
     *     d f2 = f();
     *     return f2.a != VipStatus.DEFAULT &amp;&amp; b.z(f2);
     * }
     * </pre>
     * 特征：public static、返回 boolean、无参数、读取 {@link #VIP_STATUS_CLASS} 的 DEFAULT 常量。
     */
    private static Method findVipCheckMethod(XposedModule module, DexKitBridge bridge, ClassLoader classLoader) {
        UsingFieldMatcher readVipDefault = UsingFieldMatcher.create()
                .declaredClass(VIP_STATUS_CLASS)
                .name("DEFAULT")
                .type(VIP_STATUS_CLASS);

        MethodMatcher matcher = MethodMatcher.create()
                .modifiers(Modifier.PUBLIC | Modifier.STATIC)
                .returnType("boolean")
                .paramCount(0)
                .addUsingField(readVipDefault);

        MethodDataList candidates = bridge.findMethod(FindMethod.create().matcher(matcher));
        module.log(Log.INFO, TAG, "vip check candidates = " + candidates.size());
        for (int i = 0; i < Math.min(candidates.size(), 5); i++) {
            MethodData data = candidates.get(i);
            module.log(Log.INFO, TAG, "  candidate: " + data.getClassName() + "#" + data.getName());
        }
        if (candidates.isEmpty()) {
            return null;
        }

        MethodData target = candidates.get(0);
        module.log(Log.INFO, TAG, "DexKit located " + target.getClassName() + "#" + target.getName());
        try {
            return target.getMethodInstance(classLoader);
        } catch (Throwable tr) {
            module.log(Log.ERROR, TAG, "failed to resolve " + target.getClassName() + "#" + target.getName(), tr);
            return null;
        }
    }

    private static Method fallbackVipCheck(XposedModule module, ClassLoader classLoader) {
        try {
            Class<?> clazz = Class.forName(VIP_CHECK_FALLBACK_CLASS, false, classLoader);
            Method method = clazz.getDeclaredMethod(VIP_CHECK_FALLBACK_METHOD);
            module.log(Log.WARN, TAG, "fallback to " + VIP_CHECK_FALLBACK_CLASS + "#" + VIP_CHECK_FALLBACK_METHOD);
            return method;
        } catch (Throwable tr) {
            module.log(Log.ERROR, TAG, "fallback lookup failed", tr);
            return null;
        }
    }

    /**
     * 直接返回 true，跳过原方法（VIP 判断恒为真）。
     */
    private static void hookVipCheck(XposedModule module, Method method) {
        try {
            module.hook(method).intercept(chain -> {
                module.log(Log.INFO, TAG, "force " + method.getName() + "() -> true");
                return Boolean.TRUE;
            });
            module.log(Log.INFO, TAG, "hooked " + method + " -> true");
        } catch (Throwable tr) {
            module.log(Log.ERROR, TAG, "failed to hook " + method, tr);
        }
    }

    /**
     * hook 目标 MainActivity 的 onCreate，原方法执行完后弹出 Toast。
     */
    private static void hookMainActivity(XposedModule module, ClassLoader classLoader) {
        try {
            Class<?> activityClass = Class.forName(TARGET_ACTIVITY, false, classLoader);
            Method onCreate = activityClass.getDeclaredMethod("onCreate", Bundle.class);

            module.hook(onCreate).intercept(chain -> {
                // 先让原方法执行完
                Object result = chain.proceed();
                module.log(Log.INFO, TAG, TARGET_ACTIVITY + "#onCreate finished");
                showToast(chain.getThisObject());
                return result;
            });

            module.log(Log.INFO, TAG, "hooked " + TARGET_ACTIVITY + "#onCreate");
        } catch (Throwable tr) {
            module.log(Log.ERROR, TAG, "failed to hook " + TARGET_ACTIVITY + "#onCreate", tr);
        }
    }

    private static void showToast(Object thisObject) {
        if (!(thisObject instanceof Activity)) {
            return;
        }
        Activity activity = (Activity) thisObject;
        Runnable show = () -> Toast.makeText(activity, TOAST_TEXT, Toast.LENGTH_SHORT).show();
        if (Looper.myLooper() == Looper.getMainLooper()) {
            show.run();
        } else {
            new Handler(Looper.getMainLooper()).post(show);
        }
    }
}
