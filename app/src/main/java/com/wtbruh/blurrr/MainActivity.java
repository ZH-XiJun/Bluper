package com.wtbruh.blurrr;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import io.github.libxposed.api.XposedInterface;

/**
 * 仅用于展示模块信息，方便确认模块已正确安装。
 */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView textView = new TextView(this);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        textView.setPadding(padding, padding, padding, padding);
        textView.setText("BlurrrHooker\n\n"
                + "目标应用：" + ModuleMain.TARGET_PACKAGE + "\n"
                + "libxposed API：" + XposedInterface.LIB_API + "\n"
                + "状态：模块已安装，请在 LSPosed 中启用并勾选目标应用");
        setContentView(textView);
    }
}
