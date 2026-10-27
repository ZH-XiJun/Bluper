package com.wtbruh.blurrr;

import android.util.Log;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.result.ClassDataList;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 目标应用的 hook 入口。
 *
 * <p>这里先用 DexKit 解析目标 APK，确认原生库加载 / dex 解析链路是通的，
 * 真实的 hook 逻辑在 {@link #install} 里继续补充。</p>
 */
final class BlurrrHooker {

    private static final String TAG = ModuleMain.TAG;

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

        // TODO: 参考下面的写法，用 DexKit 定位到具体类 / 方法后再下 hook
        // Class<?> clazz = Class.forName(className, false, classLoader);
        // Method method = clazz.getDeclaredMethod("methodName", String.class);
        // module.hook(method).intercept(chain -> {
        //     Object result = chain.proceed();
        //     // 修改返回值...
        //     return result;
        // });
    }
}
