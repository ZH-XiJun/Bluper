package com.wtbruh.blurrr;

import android.util.Log;

import io.github.libxposed.api.XposedModule;

/**
 * 模块入口，类名必须与 META-INF/xposed/java_init.list 中声明的一致。
 */
public final class ModuleMain extends XposedModule {

    static final String TAG = "Bluper";

    /**
     * 被 hook 的目标应用，需与 META-INF/xposed/scope.list 保持一致。
     */
    static final String TARGET_PACKAGE = "ai.blurrr.video";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "loaded in " + param.getProcessName()
                + ", framework=" + getFrameworkName() + " " + getFrameworkVersion()
                + ", api=" + getApiVersion());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }
        log(Log.INFO, TAG, "target package ready: " + packageName);
        try {
            BlurrrHooker.install(this, param);
        } catch (Throwable tr) {
            log(Log.ERROR, TAG, "failed to install hooks", tr);
        }
    }
}
