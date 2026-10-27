package com.wtbruh.blurrr;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.result.ClassDataList;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 目标应用的 hook 入口。
 */
final class BlurrrHooker {

    private static final String TAG = ModuleMain.TAG;

    private static final String TARGET_ACTIVITY = ModuleMain.TARGET_PACKAGE + ".MainActivity";

    private static final String TOAST_TEXT = "Module loaded";

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

        dumpDexKitInfo(module, apkPath);
        hookMainActivity(module, classLoader);
    }

    /**
     * 用 DexKit 解析一遍目标 APK，确认 native 库与 dex 解析链路正常。
     */
    private static void dumpDexKitInfo(XposedModule module, String apkPath) {
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            module.log(Log.INFO, TAG, "dex count = " + bridge.getDexNum());

            ClassDataList classes = bridge.findClass(FindClass.create().searchPackages(ModuleMain.TARGET_PACKAGE));
            module.log(Log.INFO, TAG, "classes in " + ModuleMain.TARGET_PACKAGE + " = " + classes.size());
            for (int i = 0; i < Math.min(classes.size(), 10); i++) {
                module.log(Log.INFO, TAG, "  " + classes.get(i).getName());
            }
        } catch (Throwable tr) {
            module.log(Log.ERROR, TAG, "DexKit init failed", tr);
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
