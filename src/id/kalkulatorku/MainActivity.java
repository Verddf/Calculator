package id.kalkulatorku;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;

/** Layar utama Kalkulatorku. */
public class MainActivity extends Activity implements View.OnClickListener {

    private static final String PREFS = "kalkulatorku";
    private static final int HISTORY_MAX = 50;

    private static final int[] CLICKABLE_IDS = {
            R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4,
            R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9,
            R.id.key_000, R.id.key_point, R.id.key_add, R.id.key_sub, R.id.key_mul,
            R.id.key_div, R.id.key_percent, R.id.key_paren, R.id.key_clear, R.id.key_eq,
            R.id.btn_backspace, R.id.btn_history, R.id.history_clear
    };

    private final Calculator calc = new Calculator();
    /** Riwayat, terbaru di indeks 0. Tiap entri: {ekspresi, hasil} dalam format internal. */
    private final ArrayList<String[]> history = new ArrayList<String[]>();
    private SharedPreferences prefs;

    private ScrollView displayScroll;
    private TextView previousView;
    private TextView exprView;
    private TextView resultView;
    private View keypad;
    private View historyPanel;
    private ScrollView historyScroll;
    private LinearLayout historyList;
    private TextView historyEmpty;
    private TextView historyClear;
    private ImageButton historyButton;
    private Toast toast;

    private char decimalSep;
    private char groupSep;
    private int colorAccent;
    private int colorText;
    private int colorTextSecondary;
    private int colorDanger;
    private float exprMax;
    private float exprMin;
    private float resultMax;
    private float resultMin;
    /** Jenis error yang sedang ditampilkan (0 = tidak ada). */
    private int errorKind;

    private final Runnable scrollToBottom = new Runnable() {
        @Override
        public void run() {
            View content = displayScroll.getChildAt(0);
            if (content != null) {
                displayScroll.scrollTo(0, Math.max(0, content.getHeight() - displayScroll.getHeight()));
            }
        }
    };

    private final Runnable refit = new Runnable() {
        @Override
        public void run() {
            fitAll();
        }
    };

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // Pemisah angka mengikuti bahasa HP: Indonesia = 1.234,5 ; Inggris = 1,234.5
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
        decimalSep = symbols.getDecimalSeparator();
        groupSep = symbols.getGroupingSeparator();
        if (groupSep == decimalSep) {
            groupSep = decimalSep == ',' ? '.' : ',';
        }

        Resources res = getResources();
        colorAccent = getColor(R.color.accent);
        colorText = getColor(R.color.text_primary);
        colorTextSecondary = getColor(R.color.text_secondary);
        colorDanger = getColor(R.color.danger);
        exprMax = res.getDimension(R.dimen.expr_text_max);
        exprMin = res.getDimension(R.dimen.expr_text_min);
        resultMax = res.getDimension(R.dimen.result_text_max);
        resultMin = res.getDimension(R.dimen.result_text_min);

        setupSystemBars(findViewById(R.id.root));

        displayScroll = (ScrollView) findViewById(R.id.display_scroll);
        previousView = (TextView) findViewById(R.id.previous);
        exprView = (TextView) findViewById(R.id.expression);
        resultView = (TextView) findViewById(R.id.result);
        keypad = findViewById(R.id.keypad);
        historyPanel = findViewById(R.id.history_panel);
        historyScroll = (ScrollView) findViewById(R.id.history_scroll);
        historyList = (LinearLayout) findViewById(R.id.history_list);
        historyEmpty = (TextView) findViewById(R.id.history_empty);
        historyClear = (TextView) findViewById(R.id.history_clear);
        historyButton = (ImageButton) findViewById(R.id.btn_history);

        for (int id : CLICKABLE_IDS) {
            findViewById(id).setOnClickListener(this);
        }
        ((Button) findViewById(R.id.key_point)).setText(String.valueOf(decimalSep));

        findViewById(R.id.btn_backspace).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                calc.clear();
                errorKind = 0;
                render(false);
                return true;
            }
        });
        View display = findViewById(R.id.display);
        display.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return copyCurrent();
            }
        });
        display.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (bottom - top != oldBottom - oldTop) {
                    displayScroll.post(scrollToBottom);
                }
            }
        });
        exprView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (right - left != oldRight - oldLeft) {
                    v.post(refit);
                }
            }
        });

        loadState();
        if (savedInstanceState != null) {
            errorKind = savedInstanceState.getInt("error", 0);
            if (savedInstanceState.getBoolean("historyOpen", false)) {
                openHistory(false);
            }
        }
        render(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("error", errorKind);
        outState.putBoolean("historyOpen", historyPanel.getVisibility() == View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (historyPanel.getVisibility() == View.VISIBLE) {
            closeHistory();
            return;
        }
        super.onBackPressed();
    }

    // --------------------------------------------------------------- input

    @Override
    public void onClick(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        int status;
        switch (v.getId()) {
            case R.id.key_0: status = calc.digit('0'); break;
            case R.id.key_1: status = calc.digit('1'); break;
            case R.id.key_2: status = calc.digit('2'); break;
            case R.id.key_3: status = calc.digit('3'); break;
            case R.id.key_4: status = calc.digit('4'); break;
            case R.id.key_5: status = calc.digit('5'); break;
            case R.id.key_6: status = calc.digit('6'); break;
            case R.id.key_7: status = calc.digit('7'); break;
            case R.id.key_8: status = calc.digit('8'); break;
            case R.id.key_9: status = calc.digit('9'); break;
            case R.id.key_000: status = calc.tripleZero(); break;
            case R.id.key_point: status = calc.point(); break;
            case R.id.key_add: status = calc.operator(Engine.PLUS); break;
            case R.id.key_sub: status = calc.operator(Engine.MINUS); break;
            case R.id.key_mul: status = calc.operator(Engine.TIMES); break;
            case R.id.key_div: status = calc.operator(Engine.DIVIDE); break;
            case R.id.key_percent: status = calc.percent(); break;
            case R.id.key_paren: status = calc.parens(); break;
            case R.id.btn_backspace: status = calc.backspace(); break;
            case R.id.key_clear:
                calc.clear();
                status = Calculator.OK;
                break;
            case R.id.key_eq:
                onEquals();
                return;
            case R.id.btn_history:
                toggleHistory();
                return;
            case R.id.history_clear:
                confirmClearHistory();
                return;
            default:
                return;
        }
        errorKind = 0;
        report(status);
        render(false);
    }

    private void onEquals() {
        Calculator.Result r = calc.equalsKey();
        if (r != null && r.error != 0) {
            errorKind = r.error;
            render(false);
            return;
        }
        errorKind = 0;
        if (r != null) {
            addHistory(r.expression, r.value);
        }
        render(r != null);
    }

    private void report(int status) {
        if (status == Calculator.MAX_DIGITS) {
            showToast(getString(R.string.max_digits));
        } else if (status == Calculator.TOO_LONG) {
            showToast(getString(R.string.too_long));
        }
    }

    // ------------------------------------------------------------- display

    private void render(boolean animate) {
        String expr = calc.expression();
        if (expr.length() == 0) {
            exprView.setText("0");
            exprView.setTextColor(colorTextSecondary);
        } else {
            exprView.setText(colorize(Engine.format(expr, decimalSep, groupSep)));
            exprView.setTextColor(colorText);
        }

        String last = calc.lastExpression();
        if (calc.isJustEvaluated() && last != null) {
            previousView.setText(colorize(Engine.format(last, decimalSep, groupSep) + " ="));
            previousView.setVisibility(View.VISIBLE);
        } else {
            previousView.setVisibility(View.GONE);
        }

        if (errorKind != 0) {
            resultView.setText(errorText(errorKind));
            resultView.setTextColor(colorDanger);
        } else {
            String preview = calc.preview();
            resultView.setText(preview == null ? "" : Engine.format(preview, decimalSep, groupSep));
            resultView.setTextColor(colorTextSecondary);
        }

        fitAll();
        if (animate) {
            exprView.setAlpha(0f);
            exprView.setTranslationY(exprView.getTextSize() * 0.35f);
            exprView.animate().alpha(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        displayScroll.post(scrollToBottom);
    }

    private String errorText(int kind) {
        if (kind == Engine.ERR_DIV_ZERO) {
            return getString(R.string.err_div_zero);
        }
        if (kind == Engine.ERR_OVERFLOW) {
            return getString(R.string.err_overflow);
        }
        return getString(R.string.err_syntax);
    }

    /** Mewarnai operator dengan warna aksen. */
    private CharSequence colorize(String s) {
        SpannableStringBuilder sb = new SpannableStringBuilder(s);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean symbol = Engine.isOperator(c) || c == Engine.PERCENT
                    || c == Engine.OPEN || c == Engine.CLOSE;
            boolean exponentSign = i > 0 && s.charAt(i - 1) == Engine.EXP;
            if (symbol && !exponentSign) {
                sb.setSpan(new ForegroundColorSpan(colorAccent), i, i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    private void fitAll() {
        fitText(exprView, exprMax, exprMin);
        fitText(resultView, resultMax, resultMin);
    }

    /** Mengecilkan huruf agar angka panjang tetap muat satu baris (sampai batas minimum). */
    private void fitText(TextView view, float maxPx, float minPx) {
        int available = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
        float size = maxPx;
        if (available > 0) {
            CharSequence text = view.getText();
            Paint paint = new Paint(view.getPaint());
            float step = Math.max(1f, (maxPx - minPx) / 24f);
            while (size > minPx) {
                paint.setTextSize(size);
                if (paint.measureText(text, 0, text.length()) <= available) {
                    break;
                }
                size -= step;
            }
            if (size < minPx) {
                size = minPx;
            }
        }
        if (Math.abs(view.getTextSize() - size) > 0.5f) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        }
    }

    // ------------------------------------------------------------- clipboard

    private boolean copyCurrent() {
        String value = calc.preview();
        if (value == null) {
            value = calc.expression();
        }
        if (value.length() == 0) {
            return false;
        }
        return copyText(Engine.format(value, decimalSep, groupSep));
    }

    private boolean copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            return false;
        }
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text));
        if (Build.VERSION.SDK_INT < 33) {
            // Android 13+ sudah menampilkan pemberitahuan salin sendiri.
            showToast(getString(R.string.copied));
        }
        return true;
    }

    private void showToast(String message) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }

    // --------------------------------------------------------------- history

    private void toggleHistory() {
        if (historyPanel.getVisibility() == View.VISIBLE) {
            closeHistory();
        } else {
            openHistory(true);
        }
    }

    private void openHistory(boolean animate) {
        rebuildHistory();
        historyPanel.setVisibility(View.VISIBLE);
        keypad.setVisibility(View.INVISIBLE);
        if (animate) {
            historyPanel.setAlpha(0f);
            historyPanel.animate().alpha(1f).setDuration(160).start();
        }
        historyButton.setColorFilter(colorAccent);
        historyButton.setContentDescription(getString(R.string.cd_close_history));
    }

    private void closeHistory() {
        historyPanel.animate().cancel();
        historyPanel.setAlpha(1f);
        historyPanel.setVisibility(View.GONE);
        keypad.setVisibility(View.VISIBLE);
        historyButton.setColorFilter(colorTextSecondary);
        historyButton.setContentDescription(getString(R.string.cd_history));
    }

    private void rebuildHistory() {
        historyList.removeAllViews();
        boolean empty = history.isEmpty();
        historyEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        historyScroll.setVisibility(empty ? View.GONE : View.VISIBLE);
        historyClear.setEnabled(!empty);
        historyClear.setAlpha(empty ? 0.35f : 1f);

        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < history.size(); i++) {
            final String[] entry = history.get(i);
            View row = inflater.inflate(R.layout.item_history, historyList, false);
            ((TextView) row.findViewById(R.id.h_expr))
                    .setText(colorize(Engine.format(entry[0], decimalSep, groupSep)));
            ((TextView) row.findViewById(R.id.h_result))
                    .setText("= " + Engine.format(entry[1], decimalSep, groupSep));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    int status = calc.insertValue(entry[1]);
                    errorKind = 0;
                    report(status);
                    closeHistory();
                    render(false);
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return copyText(Engine.format(entry[1], decimalSep, groupSep));
                }
            });
            historyList.addView(row);
        }
        historyScroll.scrollTo(0, 0);
    }

    private void confirmClearHistory() {
        if (history.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(R.string.history_clear_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.history_clear, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        history.clear();
                        saveHistory();
                        rebuildHistory();
                    }
                })
                .show();
    }

    private void addHistory(String expression, String value) {
        history.add(0, new String[]{expression, value});
        while (history.size() > HISTORY_MAX) {
            history.remove(history.size() - 1);
        }
        saveHistory();
    }

    // ------------------------------------------------------------ persistence

    private void saveState() {
        BigDecimal carry = calc.carryValue();
        prefs.edit()
                .putString("expr", calc.expression())
                .putString("carry", carry == null ? null : carry.toString())
                .putInt("carryLen", calc.carryLength())
                .putBoolean("evaluated", calc.isJustEvaluated())
                .putString("last", calc.lastExpression())
                .apply();
    }

    private void loadState() {
        BigDecimal carry = null;
        String carryText = prefs.getString("carry", null);
        if (carryText != null) {
            try {
                carry = new BigDecimal(carryText);
            } catch (NumberFormatException ignored) {
                carry = null;
            }
        }
        calc.restore(prefs.getString("expr", ""), carry, prefs.getInt("carryLen", 0),
                prefs.getBoolean("evaluated", false), prefs.getString("last", null));

        history.clear();
        String raw = prefs.getString("history", "");
        if (raw.length() > 0) {
            String[] lines = raw.split("\n");
            for (int i = 0; i < lines.length && history.size() < HISTORY_MAX; i++) {
                int tab = lines[i].indexOf('\t');
                if (tab > 0 && tab < lines[i].length() - 1) {
                    history.add(new String[]{lines[i].substring(0, tab), lines[i].substring(tab + 1)});
                }
            }
        }
    }

    private void saveHistory() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(history.get(i)[0]).append('\t').append(history.get(i)[1]);
        }
        prefs.edit().putString("history", sb.toString()).apply();
    }

    // ------------------------------------------------------------ system bars

    private void setupSystemBars(View root) {
        Window window = getWindow();
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int bg = getColor(R.color.bg);

        if (Build.VERSION.SDK_INT < 35) {
            window.setStatusBarColor(bg);
            if (Build.VERSION.SDK_INT >= 26) {
                window.setNavigationBarColor(bg);
            }
        }

        if (Build.VERSION.SDK_INT >= 30) {
            // WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS (8) | APPEARANCE_LIGHT_NAVIGATION_BARS (16)
            int mask = 8 | 16;
            try {
                Object controller = Window.class.getMethod("getInsetsController").invoke(window);
                if (controller != null) {
                    Method set = Class.forName("android.view.WindowInsetsController")
                            .getMethod("setSystemBarsAppearance", int.class, int.class);
                    set.invoke(controller, night ? 0 : mask, mask);
                }
            } catch (Throwable ignored) {
                // Biarkan tampilan bawaan.
            }
        } else {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (!night) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) {
                    flags |= 0x10; // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
            decor.setSystemUiVisibility(flags);
        }

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                Window.class.getMethod("setNavigationBarContrastEnforced", boolean.class)
                        .invoke(window, false);
            } catch (Throwable ignored) {
                // Tidak penting.
            }
        }

        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15+ menggambar aplikasi sampai ke balik status bar & navigasi,
            // jadi beri jarak sesuai ukuran bar (dan poni layar).
            root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    int left = insets.getSystemWindowInsetLeft();
                    int top = insets.getSystemWindowInsetTop();
                    int right = insets.getSystemWindowInsetRight();
                    int bottom = insets.getSystemWindowInsetBottom();
                    try {
                        Object cutout = WindowInsets.class.getMethod("getDisplayCutout").invoke(insets);
                        if (cutout != null) {
                            Class<?> k = Class.forName("android.view.DisplayCutout");
                            left = Math.max(left, (Integer) k.getMethod("getSafeInsetLeft").invoke(cutout));
                            top = Math.max(top, (Integer) k.getMethod("getSafeInsetTop").invoke(cutout));
                            right = Math.max(right, (Integer) k.getMethod("getSafeInsetRight").invoke(cutout));
                            bottom = Math.max(bottom, (Integer) k.getMethod("getSafeInsetBottom").invoke(cutout));
                        }
                    } catch (Throwable ignored) {
                        // Pakai inset bar saja.
                    }
                    v.setPadding(left, top, right, bottom);
                    return insets.consumeSystemWindowInsets();
                }
            });
        }
    }
}
