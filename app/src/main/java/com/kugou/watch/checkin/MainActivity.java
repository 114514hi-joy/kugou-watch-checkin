package com.kugou.watch.checkin;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 极简手表端酷狗概念版签到界面（适配 378×496 圆屏，纯原生 View，无多余后台）。
 */
public class MainActivity extends Activity {

    private KugouApi api;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout loginPane;
    private ImageView qrImage;
    private TextView qrStatus;
    private Button refreshQrBtn;
    private Button toSmsBtn;

    private LinearLayout smsPane;
    private EditText phoneEdit;
    private EditText codeEdit;
    private Button sendCodeBtn;
    private Button smsLoginBtn;
    private TextView smsStatus;
    private Button toQrBtn;

    private LinearLayout mainPane;
    private TextView userInfoText;
    private Button checkInBtn;
    private TextView resultText;
    private Button logoutBtn;

    private String currentQrKey;
    private volatile boolean polling = false;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!polling || currentQrKey == null) return;
            final String key = currentQrKey;
            executor.execute(() -> {
                KugouApi.QrCheckResult res = api.checkQrStatus(key);
                mainHandler.post(() -> {
                    if (!polling || !key.equals(currentQrKey)) return;
                    if (res.status == 4 && api.isLoggedIn()) {
                        polling = false;
                        showMainPane();
                        runCheckIn();
                    } else if (res.status == 0) {
                        polling = false;
                        qrStatus.setText("二维码已过期，请点击刷新");
                    } else if (res.status == 2) {
                        qrStatus.setText("已扫码，请在手机上确认登录");
                        mainHandler.postDelayed(this, 1800);
                    } else {
                        qrStatus.setText("请用手机酷狗 App 扫码");
                        mainHandler.postDelayed(this, 2200);
                    }
                });
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new KugouApi(this);
        buildUi();
        if (api.isLoggedIn()) {
            showMainPane();
        } else {
            showLoginPane();
        }
    }

    @Override
    protected void onDestroy() {
        polling = false;
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0E1116"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int padH = dp(18);
        int padV = dp(18);
        root.setPadding(padH, padV, padH, padV);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("酷狗概念版签到");
        title.setTextColor(Color.parseColor("#F0F4F8"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // ---- 未登录：二维码扫码面板 ----
        loginPane = new LinearLayout(this);
        loginPane.setOrientation(LinearLayout.VERTICAL);
        loginPane.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lpParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpParams.topMargin = dp(8);
        root.addView(loginPane, lpParams);

        qrImage = new ImageView(this);
        qrImage.setBackgroundColor(Color.WHITE);
        qrImage.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(dp(116), dp(116));
        qrLp.topMargin = dp(6);
        loginPane.addView(qrImage, qrLp);

        qrStatus = new TextView(this);
        qrStatus.setText("正在获取二维码…");
        qrStatus.setTextColor(Color.parseColor("#9BA6B2"));
        qrStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        qrStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = dp(8);
        loginPane.addView(qrStatus, stLp);

        refreshQrBtn = makeButton("刷新二维码", "#222933", "#E6EDF3");
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
        rbLp.topMargin = dp(8);
        refreshQrBtn.setOnClickListener(v -> loadQrCode());
        loginPane.addView(refreshQrBtn, rbLp);

        toSmsBtn = makeButton("改用手机号验证码登录", "#161B22", "#2BD999");
        LinearLayout.LayoutParams tsLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
        tsLp.topMargin = dp(6);
        toSmsBtn.setOnClickListener(v -> showSmsPane());
        loginPane.addView(toSmsBtn, tsLp);

        // ---- 未登录：手机号验证码面板 ----
        smsPane = new LinearLayout(this);
        smsPane.setOrientation(LinearLayout.VERTICAL);
        smsPane.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams spParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spParams.topMargin = dp(10);
        root.addView(smsPane, spParams);

        phoneEdit = makeInput("输入手机号", InputType.TYPE_CLASS_PHONE);
        smsPane.addView(phoneEdit);

        LinearLayout codeRow = new LinearLayout(this);
        codeRow.setOrientation(LinearLayout.HORIZONTAL);
        codeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        crLp.topMargin = dp(8);
        smsPane.addView(codeRow, crLp);

        codeEdit = makeInput("验证码", InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams ceLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        codeRow.addView(codeEdit, ceLp);

        sendCodeBtn = makeButton("获取", "#222933", "#E6EDF3");
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
        scLp.leftMargin = dp(6);
        sendCodeBtn.setOnClickListener(v -> doSendCode());
        codeRow.addView(sendCodeBtn, scLp);

        smsLoginBtn = makeButton("登录并签到", "#2BD999", "#0E1116");
        LinearLayout.LayoutParams slLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(38));
        slLp.topMargin = dp(10);
        smsLoginBtn.setOnClickListener(v -> doSmsLogin());
        smsPane.addView(smsLoginBtn, slLp);

        smsStatus = new TextView(this);
        smsStatus.setTextColor(Color.parseColor("#9BA6B2"));
        smsStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        smsStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ssLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ssLp.topMargin = dp(6);
        smsPane.addView(smsStatus, ssLp);

        toQrBtn = makeButton("返回扫码登录", "#161B22", "#9BA6B2");
        LinearLayout.LayoutParams tqLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
        tqLp.topMargin = dp(6);
        toQrBtn.setOnClickListener(v -> showLoginPane());
        smsPane.addView(toQrBtn, tqLp);

        // ---- 已登录：一键签到面板 ----
        mainPane = new LinearLayout(this);
        mainPane.setOrientation(LinearLayout.VERTICAL);
        mainPane.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams mpParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mpParams.topMargin = dp(8);
        root.addView(mainPane, mpParams);

        userInfoText = new TextView(this);
        userInfoText.setTextColor(Color.parseColor("#9BA6B2"));
        userInfoText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        userInfoText.setGravity(Gravity.CENTER);
        mainPane.addView(userInfoText);

        checkInBtn = makeButton("一键签到领 VIP", "#2BD999", "#0E1116");
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        cbLp.topMargin = dp(10);
        checkInBtn.setOnClickListener(v -> runCheckIn());
        mainPane.addView(checkInBtn, cbLp);

        resultText = new TextView(this);
        resultText.setTextColor(Color.parseColor("#D5DCE4"));
        resultText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        resultText.setLineSpacing(dp(2), 1.0f);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#161B22"));
        cardBg.setCornerRadius(dp(10));
        resultText.setBackground(cardBg);
        resultText.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams rtLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rtLp.topMargin = dp(10);
        mainPane.addView(resultText, rtLp);

        logoutBtn = makeButton("切换账号", "#222933", "#9BA6B2");
        LinearLayout.LayoutParams loLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
        loLp.topMargin = dp(10);
        logoutBtn.setOnClickListener(v -> {
            api.logout();
            showLoginPane();
        });
        mainPane.addView(logoutBtn, loLp);

        setContentView(scroll);
    }

    private Button makeButton(String text, String bgHex, String fgHex) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor(fgHex));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setAllCaps(false);
        b.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(dp(20));
        b.setBackground(bg);
        return b;
    }

    private EditText makeInput(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.parseColor("#6B7682"));
        e.setTextColor(Color.parseColor("#F0F4F8"));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#161B22"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#2A323D"));
        e.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(36));
        e.setLayoutParams(lp);
        return e;
    }

    private void showLoginPane() {
        mainPane.setVisibility(View.GONE);
        smsPane.setVisibility(View.GONE);
        loginPane.setVisibility(View.VISIBLE);
        loadQrCode();
    }

    private void showSmsPane() {
        polling = false;
        mainHandler.removeCallbacks(pollTask);
        mainPane.setVisibility(View.GONE);
        loginPane.setVisibility(View.GONE);
        smsPane.setVisibility(View.VISIBLE);
    }

    private void showMainPane() {
        polling = false;
        mainHandler.removeCallbacks(pollTask);
        loginPane.setVisibility(View.GONE);
        smsPane.setVisibility(View.GONE);
        mainPane.setVisibility(View.VISIBLE);
        String nick = api.getNickname();
        String uid = api.getUserId();
        userInfoText.setText(nick.isEmpty() ? "UID: " + uid : nick + " (" + uid + ")");
        String last = api.getLastResult();
        resultText.setText(last.isEmpty() ? "点击上方按钮领取今日概念版 VIP" : last);
    }

    private void loadQrCode() {
        polling = false;
        mainHandler.removeCallbacks(pollTask);
        qrStatus.setText("正在获取二维码…");
        qrImage.setImageBitmap(null);
        executor.execute(() -> {
            KugouApi.QrCodeResult res = api.fetchQrCode();
            mainHandler.post(() -> {
                if (res.error != null) {
                    qrStatus.setText(res.error);
                    return;
                }
                currentQrKey = res.key;
                qrImage.setImageBitmap(res.bitmap);
                qrStatus.setText("请用手机酷狗 App 扫码");
                polling = true;
                mainHandler.postDelayed(pollTask, 2000);
            });
        });
    }

    private void doSendCode() {
        String phone = phoneEdit.getText().toString().trim();
        if (phone.length() < 11) {
            smsStatus.setText("请输入完整手机号");
            return;
        }
        sendCodeBtn.setEnabled(false);
        smsStatus.setText("正在发送验证码…");
        executor.execute(() -> {
            String err = api.sendSmsCode(phone);
            mainHandler.post(() -> {
                sendCodeBtn.setEnabled(true);
                smsStatus.setText(err == null ? "验证码已发送，请查收短信" : err);
            });
        });
    }

    private void doSmsLogin() {
        String phone = phoneEdit.getText().toString().trim();
        String code = codeEdit.getText().toString().trim();
        if (phone.length() < 11 || code.isEmpty()) {
            smsStatus.setText("请输入手机号和验证码");
            return;
        }
        smsLoginBtn.setEnabled(false);
        smsStatus.setText("正在登录…");
        executor.execute(() -> {
            String err = api.loginBySms(phone, code);
            mainHandler.post(() -> {
                smsLoginBtn.setEnabled(true);
                if (err == null && api.isLoggedIn()) {
                    smsStatus.setText("");
                    showMainPane();
                    runCheckIn();
                } else {
                    smsStatus.setText(err == null ? "登录失败" : err);
                }
            });
        });
    }

    private void runCheckIn() {
        checkInBtn.setEnabled(false);
        checkInBtn.setText("签到中…");
        resultText.setText("正在请求酷狗概念版接口…");
        executor.execute(() -> {
            String res = api.runCheckIn();
            mainHandler.post(() -> {
                checkInBtn.setEnabled(true);
                checkInBtn.setText("一键签到领 VIP");
                resultText.setText(res);
            });
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
