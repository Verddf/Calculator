package id.kalkulatorku;

import java.math.BigDecimal;

/**
 * Logika tombol kalkulator. Kelas ini tidak bergantung pada Android,
 * sehingga perilakunya bisa diuji otomatis di komputer.
 */
final class Calculator {

    static final int OK = 0;
    static final int IGNORED = 1;
    static final int MAX_DIGITS = 2;
    static final int TOO_LONG = 3;

    static final int MAX_DIGIT_COUNT = 15;
    static final int MAX_LENGTH = 120;

    /** Hasil menekan "=". */
    static final class Result {
        final String expression;
        final String value;
        final int error;

        Result(String expression, String value, int error) {
            this.expression = expression;
            this.value = value;
            this.error = error;
        }
    }

    private final StringBuilder e = new StringBuilder();
    /** Nilai persis hasil sebelumnya, dipakai saat perhitungan dilanjutkan dari hasil. */
    private BigDecimal carry;
    private int carryLen;
    private boolean justEvaluated;
    private String lastExpression;

    // ---------------------------------------------------------------- state

    String expression() {
        return e.toString();
    }

    boolean isJustEvaluated() {
        return justEvaluated;
    }

    String lastExpression() {
        return lastExpression;
    }

    BigDecimal carryValue() {
        return carry;
    }

    int carryLength() {
        return carryLen;
    }

    void restore(String expr, BigDecimal carryValue, int carryLength, boolean evaluated, String lastExpr) {
        e.setLength(0);
        if (expr != null && expr.length() <= MAX_LENGTH && isClean(expr)) {
            e.append(expr);
        }
        if (carryValue != null && carryLength > 0 && carryLength <= e.length()) {
            carry = carryValue;
            carryLen = carryLength;
        } else {
            carry = null;
            carryLen = 0;
        }
        lastExpression = (lastExpr != null && isClean(lastExpr)) ? lastExpr : null;
        justEvaluated = evaluated && carry != null && lastExpression != null;
    }

    private static boolean isClean(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = Engine.isDigit(c) || Engine.isOperator(c) || c == Engine.POINT
                    || c == Engine.PERCENT || c == Engine.OPEN || c == Engine.CLOSE || c == Engine.EXP;
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------- helpers

    private char last() {
        return e.length() == 0 ? 0 : e.charAt(e.length() - 1);
    }

    private char beforeLast() {
        return e.length() < 2 ? 0 : e.charAt(e.length() - 2);
    }

    /** Posisi awal angka terakhir dalam ekspresi (sama dengan panjang ekspresi jika tidak ada). */
    private int numberStart() {
        int i = e.length();
        while (i > 0) {
            char c = e.charAt(i - 1);
            if (Engine.isDigit(c) || c == Engine.POINT || c == Engine.EXP) {
                i--;
            } else if ((c == Engine.MINUS || c == Engine.PLUS) && i >= 2 && e.charAt(i - 2) == Engine.EXP) {
                i--;
            } else {
                break;
            }
        }
        return i;
    }

    private String currentNumber() {
        return e.substring(numberStart());
    }

    private static int digitCount(String num) {
        int n = 0;
        for (int i = 0; i < num.length(); i++) {
            if (Engine.isDigit(num.charAt(i))) {
                n++;
            }
        }
        return n;
    }

    private int openParens() {
        int open = 0;
        for (int i = 0; i < e.length(); i++) {
            char c = e.charAt(i);
            if (c == Engine.OPEN) {
                open++;
            } else if (c == Engine.CLOSE) {
                open--;
            }
        }
        return open;
    }

    private boolean room(int extra) {
        return e.length() + extra <= MAX_LENGTH;
    }

    private void dropCarry() {
        carry = null;
        carryLen = 0;
    }

    /** Mengubah karakter di posisi index; hasil sebelumnya tidak lagi persis jika tersentuh. */
    private void touched(int index) {
        if (carryLen > 0 && index < carryLen) {
            dropCarry();
        }
    }

    /** Menambah karakter angka di akhir; jika menempel pada hasil sebelumnya, nilainya berubah. */
    private void appendNumberChar(char c) {
        if (carryLen > 0 && e.length() <= carryLen) {
            dropCarry();
        }
        e.append(c);
    }

    private void startFreshIfEvaluated() {
        if (justEvaluated) {
            e.setLength(0);
            dropCarry();
            justEvaluated = false;
        }
    }

    private boolean needsImplicitTimes() {
        char l = last();
        return l == Engine.CLOSE || l == Engine.PERCENT;
    }

    // ----------------------------------------------------------------- keys

    int digit(char d) {
        startFreshIfEvaluated();
        String num = currentNumber();
        if (num.indexOf(Engine.EXP) >= 0) {
            return IGNORED;
        }
        boolean times = needsImplicitTimes();
        if (!room(times ? 2 : 1)) {
            return TOO_LONG;
        }
        if (times) {
            e.append(Engine.TIMES);
            num = "";
        }
        if (num.equals("0")) {
            int idx = e.length() - 1;
            touched(idx);
            e.setCharAt(idx, d);
            return OK;
        }
        if (digitCount(num) >= MAX_DIGIT_COUNT) {
            return MAX_DIGITS;
        }
        appendNumberChar(d);
        return OK;
    }

    int tripleZero() {
        startFreshIfEvaluated();
        String num = currentNumber();
        if (num.indexOf(Engine.EXP) >= 0) {
            return IGNORED;
        }
        if (needsImplicitTimes()) {
            if (!room(2)) {
                return TOO_LONG;
            }
            e.append(Engine.TIMES);
            num = "";
        }
        if (num.length() == 0) {
            if (!room(1)) {
                return TOO_LONG;
            }
            e.append('0');
            return OK;
        }
        if (num.equals("0")) {
            return IGNORED;
        }
        int allowed = MAX_DIGIT_COUNT - digitCount(num);
        if (allowed <= 0) {
            return MAX_DIGITS;
        }
        int count = Math.min(3, allowed);
        if (!room(count)) {
            return TOO_LONG;
        }
        for (int k = 0; k < count; k++) {
            appendNumberChar('0');
        }
        return count < 3 ? MAX_DIGITS : OK;
    }

    int point() {
        startFreshIfEvaluated();
        String num = currentNumber();
        if (num.indexOf(Engine.POINT) >= 0 || num.indexOf(Engine.EXP) >= 0) {
            return IGNORED;
        }
        boolean times = needsImplicitTimes();
        if (times) {
            num = "";
        }
        int need = (times ? 1 : 0) + (num.length() == 0 ? 2 : 1);
        if (!room(need)) {
            return TOO_LONG;
        }
        if (times) {
            e.append(Engine.TIMES);
        }
        if (num.length() == 0) {
            e.append('0');
        }
        appendNumberChar(Engine.POINT);
        return OK;
    }

    int operator(char op) {
        justEvaluated = false;
        if (e.length() == 0) {
            if (op == Engine.MINUS) {
                e.append(Engine.MINUS);
                return OK;
            }
            return IGNORED;
        }
        char l = last();
        if (l == Engine.OPEN) {
            if (op != Engine.MINUS) {
                return IGNORED;
            }
            if (!room(1)) {
                return TOO_LONG;
            }
            e.append(Engine.MINUS);
            return OK;
        }
        if (Engine.isOperator(l)) {
            char p = beforeLast();
            boolean unaryMinus = l == Engine.MINUS
                    && (p == 0 || p == Engine.OPEN || p == Engine.TIMES || p == Engine.DIVIDE);
            if (unaryMinus) {
                if (op == Engine.MINUS) {
                    return IGNORED;
                }
                // Ganti pilihan: buang minus, lalu ganti operator sebelumnya bila ada.
                e.setLength(e.length() - 1);
                if (e.length() > 0 && Engine.isOperator(last())) {
                    e.setCharAt(e.length() - 1, op);
                }
                return OK;
            }
            if (op == Engine.MINUS && (l == Engine.TIMES || l == Engine.DIVIDE)) {
                if (!room(1)) {
                    return TOO_LONG;
                }
                e.append(Engine.MINUS);
                return OK;
            }
            e.setCharAt(e.length() - 1, op);
            return OK;
        }
        if (l == Engine.POINT) {
            e.setLength(e.length() - 1);
        }
        if (!room(1)) {
            return TOO_LONG;
        }
        e.append(op);
        return OK;
    }

    int percent() {
        justEvaluated = false;
        char l = last();
        if (l == Engine.POINT) {
            e.setLength(e.length() - 1);
            l = last();
        }
        if (Engine.isDigit(l) || l == Engine.CLOSE || l == Engine.PERCENT) {
            if (!room(1)) {
                return TOO_LONG;
            }
            e.append(Engine.PERCENT);
            return OK;
        }
        return IGNORED;
    }

    /** Tombol kurung pintar: membuka atau menutup kurung sesuai konteks. */
    int parens() {
        justEvaluated = false;
        char l = last();
        if (e.length() == 0 || Engine.isOperator(l) || l == Engine.OPEN) {
            if (!room(1)) {
                return TOO_LONG;
            }
            e.append(Engine.OPEN);
            return OK;
        }
        if (l == Engine.POINT) {
            e.setLength(e.length() - 1);
        }
        if (openParens() > 0) {
            if (!room(1)) {
                return TOO_LONG;
            }
            e.append(Engine.CLOSE);
            return OK;
        }
        if (!room(2)) {
            return TOO_LONG;
        }
        e.append(Engine.TIMES).append(Engine.OPEN);
        return OK;
    }

    int backspace() {
        justEvaluated = false;
        int len = e.length();
        if (len == 0) {
            return IGNORED;
        }
        int start = numberStart();
        if (start < len && e.indexOf(String.valueOf(Engine.EXP), start) >= 0) {
            // Angka bernotasi E dihapus utuh.
            touched(start);
            e.setLength(start);
            return OK;
        }
        touched(len - 1);
        e.setLength(len - 1);
        return OK;
    }

    void clear() {
        e.setLength(0);
        dropCarry();
        justEvaluated = false;
    }

    /** Memasukkan angka dari riwayat. */
    int insertValue(String value) {
        if (value == null || value.length() == 0 || !isClean(value)) {
            return IGNORED;
        }
        char l = last();
        boolean append = e.length() > 0 && !justEvaluated && (Engine.isOperator(l) || l == Engine.OPEN);
        if (append) {
            String piece = value;
            if (value.charAt(0) == Engine.MINUS && (l == Engine.PLUS || l == Engine.MINUS)) {
                piece = Engine.OPEN + value + Engine.CLOSE;
            }
            if (!room(piece.length())) {
                return TOO_LONG;
            }
            e.append(piece);
        } else {
            e.setLength(0);
            e.append(value);
            dropCarry();
        }
        justEvaluated = false;
        return OK;
    }

    /**
     * Tombol "=". Mengembalikan null jika tidak ada yang perlu dicatat ke riwayat,
     * Result dengan error != 0 jika gagal, atau Result berisi ekspresi dan hasil.
     */
    Result equalsKey() {
        if (e.length() == 0 || justEvaluated) {
            return null;
        }
        String work = lenient(e.toString());
        if (work.length() == 0) {
            return new Result(null, null, Engine.ERR_SYNTAX);
        }
        BigDecimal v;
        try {
            v = Engine.evaluate(work, carry, carryLen <= work.length() ? carryLen : 0);
        } catch (Engine.CalcError err) {
            return new Result(null, null, err.kind);
        }
        String value = Engine.canonical(v);
        boolean meaningful = hasOperation(work);
        e.setLength(0);
        e.append(value);
        carry = v;
        carryLen = value.length();
        if (meaningful) {
            lastExpression = work;
            justEvaluated = true;
            return new Result(work, value, 0);
        }
        return null;
    }

    /** Pratinjau hasil saat mengetik; null jika tidak perlu ditampilkan. */
    String preview() {
        if (justEvaluated || e.length() == 0) {
            return null;
        }
        String work = lenient(e.toString());
        if (work.length() == 0 || !hasOperation(work)) {
            return null;
        }
        try {
            BigDecimal v = Engine.evaluate(work, carry, carryLen <= work.length() ? carryLen : 0);
            return Engine.canonical(v);
        } catch (Engine.CalcError err) {
            return null;
        }
    }

    /** Membuang operator/kurung buka di akhir dan menutup kurung yang masih terbuka. */
    static String lenient(String s) {
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (Engine.isOperator(c) || c == Engine.OPEN) {
                end--;
            } else {
                break;
            }
        }
        StringBuilder sb = new StringBuilder(s.substring(0, end));
        int open = 0;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == Engine.OPEN) {
                open++;
            } else if (c == Engine.CLOSE) {
                open--;
            }
        }
        while (open-- > 0) {
            sb.append(Engine.CLOSE);
        }
        return sb.toString();
    }

    /** Apakah ekspresi berisi operasi sungguhan (bukan sekadar satu angka). */
    static boolean hasOperation(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == Engine.PERCENT) {
                return true;
            }
            if (Engine.isOperator(c) && i > 0) {
                char p = s.charAt(i - 1);
                if (p == Engine.EXP) {
                    continue;
                }
                if (Engine.isDigit(p) || p == Engine.CLOSE || p == Engine.PERCENT || p == Engine.POINT) {
                    return true;
                }
            }
        }
        return false;
    }
}
