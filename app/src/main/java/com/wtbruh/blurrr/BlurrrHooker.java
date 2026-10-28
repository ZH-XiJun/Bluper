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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

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
     * 以下模型类未被混淆，作为 DexKit 的结构锚点。
     */
    private static final String VIP_STATUS_CLASS = "ai.blurrr.video.user.model.VipStatus";
    private static final String VIP_GRADE_CLASS = "ai.blurrr.video.user.model.VipGrade";
    private static final String VIP_STAGE_CLASS = "ai.blurrr.video.user.model.VipStage";
    private static final String USER_VIP_STATUS_RESPONSE_CLASS =
            "ai.video.blurrr.api.user.data.UserVipStatusResponse";

    /**
     * 伪造的会员到期时间：2099-12-31 23:59:59（设备本地时区）。
     */
    private static final long VIP_EXPIRE_AT = LocalDate.of(2099, 12, 31)
            .atTime(23, 59, 59)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();

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
        Method vipMapper = null;
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            module.log(Log.INFO, TAG, "dex count = " + bridge.getDexNum());
            vipCheck = findVipCheckMethod(module, bridge, classLoader);
            vipMapper = findVipStatusMapper(module, bridge, classLoader);
        } catch (Throwable tr) {
            module.log(Log.ERROR, TAG, "DexKit lookup failed", tr);
        }

        if (vipCheck != null) {
            hookVipCheck(module, vipCheck);
        } else {
            module.log(Log.ERROR, TAG, "vip check method not found, skip");
        }

        if (vipMapper != null) {
            hookVipStatusMapper(module, vipMapper);
        } else {
            module.log(Log.ERROR, TAG, "vip status mapper not found, skip");
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
        module.log(Log.INFO, TAG, "DexKit located vip check: " + target.getClassName() + "#" + target.getName());
        return resolve(module, target, classLoader);
    }

    /**
     * 用 DexKit 定位 VIP 信息映射方法：
     * <pre>
     * public static final t3.d d(UserVipStatusResponse response)
     * </pre>
     * 特征：public static、唯一入参为 {@link #USER_VIP_STATUS_RESPONSE_CLASS}、读取 {@code VipStatus.FOREVER}。
     * 不依赖被混淆的方法名、类名与返回类型。
     */
    private static Method findVipStatusMapper(XposedModule module, DexKitBridge bridge, ClassLoader classLoader) {
        UsingFieldMatcher readForever = UsingFieldMatcher.create()
                .declaredClass(VIP_STATUS_CLASS)
                .name("FOREVER")
                .type(VIP_STATUS_CLASS);

        MethodMatcher matcher = MethodMatcher.create()
                .modifiers(Modifier.PUBLIC | Modifier.STATIC)
                .paramTypes(USER_VIP_STATUS_RESPONSE_CLASS)
                .addUsingField(readForever);

        MethodDataList candidates = bridge.findMethod(FindMethod.create().matcher(matcher));
        module.log(Log.INFO, TAG, "vip status mapper candidates = " + candidates.size());
        for (int i = 0; i < Math.min(candidates.size(), 5); i++) {
            MethodData data = candidates.get(i);
            module.log(Log.INFO, TAG, "  candidate: " + data.getClassName() + "#" + data.getName());
        }
        if (candidates.isEmpty()) {
            return null;
        }

        MethodData target = candidates.get(0);
        module.log(Log.INFO, TAG, "DexKit located vip mapper: " + target.getClassName() + "#" + target.getName());
        return resolve(module, target, classLoader);
    }

    private static Method resolve(XposedModule module, MethodData data, ClassLoader classLoader) {
        try {
            return data.getMethodInstance(classLoader);
        } catch (Throwable tr) {
            module.log(Log.ERROR, TAG, "failed to resolve " + data.getClassName() + "#" + data.getName(), tr);
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
     * 先让原方法生成 VIP 信息，再把字段强制改为永久 SVIP。
     */
    private static void hookVipStatusMapper(XposedModule module, Method method) {
        try {
            module.hook(method).intercept(chain -> {
                Object result = chain.proceed();
                if (result != null) {
                    forceVipValues(module, result);
                }
                return result;
            });
            module.log(Log.INFO, TAG, "hooked " + method + " -> FOREVER/SVIP/VALID_PERIOD/2099-12-31");
        } catch (Throwable tr) {
            module.log(Log.ERROR, TAG, "failed to hook " + method, tr);
        }
    }

    /**
     * 按字段类型改值，不依赖被混淆的字段名：
     * <ul>
     *     <li>{@code VipStatus} -&gt; FOREVER</li>
     *     <li>{@code Date} -&gt; 2099-12-31</li>
     *     <li>{@code VipGrade} -&gt; SVIP</li>
     *     <li>{@code VipStage} -&gt; VALID_PERIOD</li>
     * </ul>
     */
    private static void forceVipValues(XposedModule module, Object vipInfo) {
        for (Field field : vipInfo.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Class<?> type = field.getType();
            String typeName = type.getName();
            try {
                Object value;
                if (VIP_STATUS_CLASS.equals(typeName)) {
                    value = type.getField("FOREVER").get(null);
                } else if (VIP_GRADE_CLASS.equals(typeName)) {
                    value = type.getField("SVIP").get(null);
                } else if (VIP_STAGE_CLASS.equals(typeName)) {
                    value = type.getField("VALID_PERIOD").get(null);
                } else if (type == Date.class) {
                    value = new Date(VIP_EXPIRE_AT);
                } else {
                    continue;
                }
                field.setAccessible(true);
                field.set(vipInfo, value);
                module.log(Log.INFO, TAG, "set " + field.getName() + " = " + value);
            } catch (Throwable tr) {
                module.log(Log.ERROR, TAG, "failed to set field " + field.getName(), tr);
            }
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
