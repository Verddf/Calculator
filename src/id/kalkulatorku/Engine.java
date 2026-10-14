package id.kalkulatorku;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Mesin hitung Kalkulatorku.
 *
 * Ekspresi disimpan sebagai teks internal: angka memakai titik sebagai pemisah
 * desimal (tanpa pemisah ribuan), operator memakai simbol + − × ÷ % ( ).
 * Semua perhitungan memakai BigDecimal, jadi 0,1 + 0,2 hasilnya tepat 0,3.
 *
 * Aturan persen mengikuti kalkulator bisnis:
 *   200 + 10%  = 220   (10% dari 200 ditambahkan)
 *   200 − 10%  = 180
 *   200 × 10%  = 20
 *   200 ÷ 10%  = 2000
 *   50%        = 0,5
 */
final class Engine {

    static final char PLUS = '+';
    static final char MINUS = '−';
    static final char TIMES = '×';
    static final char DIVIDE = '÷';
    static final char PERCENT = '%';
    static final char OPEN = '(';
    static final char CLOSE = ')';
    static final char POINT = '.';
    static final char EXP = 'E';

    static final int ERR_SYNTAX = 1;
    static final int ERR_DIV_ZERO = 2;
    static final int ERR_OVERFLOW = 3;

    /** Presisi kerja: 34 digit signifikan (setara decimal128). */
    private static final MathContext WORK = new MathContext(34, RoundingMode.HALF_EVEN);
    /** Presisi tampilan: 15 digit signifikan, dibulatkan setengah ke atas. */
    private static final MathContext SHOW = new MathContext(15, RoundingMode.HALF_UP);
    /** Batas pangkat sepuluh agar angka tidak membengkak tanpa batas. */
    private static final int MAX_EXPONENT = 9999;

    private Engine() {
    }

    /** Kesalahan hitung (format salah, bagi nol, angka terlalu besar). */
    static final class CalcError extends Exception {
        final int kind;

        CalcError(int kind) {
            super("calc error " + kind);
            this.kind = kind;
        }
    }

    static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    static boolean isOperator(char c) {
        return c == PLUS || c == MINUS || c == TIMES || c == DIVIDE;
    }

    /**
     * Menghitung ekspresi.
     *
     * @param carry    nilai persis hasil sebelumnya yang menjadi awalan ekspresi (boleh null)
     * @param carryLen panjang teks awalan yang diwakili carry
     */
    static BigDecimal evaluate(String expr, BigDecimal carry, int carryLen) throws CalcError {
        Parser p = new Parser(expr, carry, carryLen);
        BigDecimal v = p.expression();
        if (p.pos != expr.length()) {
            throw new CalcError(ERR_SYNTAX);
        }
        return v;
    }

    /**
     * Mengubah hasil menjadi teks internal: dibulatkan ke 15 digit signifikan,
     * nol di belakang koma dibuang, dan notasi E untuk angka sangat besar/kecil.
     */
    static String canonical(BigDecimal v) {
        if (v.signum() == 0) {
            return "0";
        }
        BigDecimal r = v.round(SHOW);
        if (r.signum() == 0) {
            return "0";
        }
        r = r.stripTrailingZeros();
        int exp = r.precision() - r.scale() - 1;
        StringBuilder sb = new StringBuilder();
        if (r.signum() < 0) {
            sb.append(MINUS);
        }
        BigDecimal a = r.abs();
        if (exp >= 15 || exp < -9) {
            BigDecimal mantissa = a.movePointLeft(exp).stripTrailingZeros();
            sb.append(mantissa.toPlainString()).append(EXP);
            if (exp < 0) {
                sb.append(MINUS).append(-exp);
            } else {
                sb.append(exp);
            }
        } else {
            sb.append(a.toPlainString());
        }
        return sb.toString();
    }

    /**
     * Mengubah teks internal menjadi teks tampilan sesuai bahasa HP,
     * misalnya "1234567.5" menjadi "1.234.567,5" untuk Bahasa Indonesia.
     */
    static String format(String expr, char decimalSep, char groupSep) {
        StringBuilder out = new StringBuilder(expr.length() + 8);
        int n = expr.length();
        int i = 0;
        while (i < n) {
            char c = expr.charAt(i);
            if (isDigit(c) || c == POINT) {
                int start = i;
                while (i < n && (isDigit(expr.charAt(i)) || expr.charAt(i) == POINT)) {
                    i++;
                }
                String num = expr.substring(start, i);
                boolean scientific = i < n && expr.charAt(i) == EXP;
                int dot = num.indexOf(POINT);
                String intPart = dot >= 0 ? num.substring(0, dot) : num;
                if (scientific) {
                    out.append(intPart);
                } else {
                    appendGrouped(out, intPart, groupSep);
                }
                if (dot >= 0) {
                    out.append(decimalSep).append(num.substring(dot + 1));
                }
                if (scientific) {
                    out.append(EXP);
                    i++;
                    if (i < n && (expr.charAt(i) == MINUS || expr.charAt(i) == PLUS)) {
                        out.append(expr.charAt(i));
                        i++;
                    }
                    while (i < n && isDigit(expr.charAt(i))) {
                        out.append(expr.charAt(i));
                        i++;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static void appendGrouped(StringBuilder out, String digits, char groupSep) {
        int len = digits.length();
        for (int k = 0; k < len; k++) {
            out.append(digits.charAt(k));
            int remaining = len - k - 1;
            if (remaining > 0 && remaining % 3 == 0) {
                out.append(groupSep);
            }
        }
    }

    private static BigDecimal checked(BigDecimal v) throws CalcError {
        if (v.signum() != 0) {
            int exp = v.precision() - v.scale() - 1;
            if (exp > MAX_EXPONENT || exp < -MAX_EXPONENT) {
                throw new CalcError(ERR_OVERFLOW);
            }
        }
        return v;
    }

    /** Nilai satu suku, beserta tanda apakah suku itu hanya berupa persen (mis. "10%"). */
    private static final class Term {
        final BigDecimal value;
        final boolean percentOnly;

        Term(BigDecimal value, boolean percentOnly) {
            this.value = value;
            this.percentOnly = percentOnly;
        }
    }

    /**
     * Pengurai rekursif:
     *   expression := term (('+' | '−') term)*
     *   term       := factor (('×' | '÷') factor)*
     *   factor     := ('−' | '+') factor | primary '%'*
     *   primary    := angka | '(' expression ')'
     */
    private static final class Parser {
        final String s;
        final BigDecimal carry;
        final int carryLen;
        int pos;

        Parser(String s, BigDecimal carry, int carryLen) {
            this.s = s;
            this.carry = carry;
            this.carryLen = (carry != null && carryLen > 0 && carryLen <= s.length()) ? carryLen : 0;
        }

        char peek() {
            return pos < s.length() ? s.charAt(pos) : 0;
        }

        BigDecimal expression() throws CalcError {
            BigDecimal acc = term().value;
            while (true) {
                char op = peek();
                if (op != PLUS && op != MINUS) {
                    return acc;
                }
                pos++;
                Term t = term();
                BigDecimal rhs = t.percentOnly ? acc.multiply(t.value, WORK) : t.value;
                acc = checked(op == PLUS ? acc.add(rhs, WORK) : acc.subtract(rhs, WORK));
            }
        }

        Term term() throws CalcError {
            Term first = factor();
            BigDecimal acc = first.value;
            boolean percentOnly = first.percentOnly;
            while (true) {
                char op = peek();
                if (op != TIMES && op != DIVIDE) {
                    return new Term(acc, percentOnly);
                }
                pos++;
                Term f = factor();
                percentOnly = false;
                if (op == TIMES) {
                    acc = checked(acc.multiply(f.value, WORK));
                } else {
                    if (f.value.signum() == 0) {
                        throw new CalcError(ERR_DIV_ZERO);
                    }
                    acc = checked(acc.divide(f.value, WORK));
                }
            }
        }

        Term factor() throws CalcError {
            if (pos == 0 && carryLen > 0) {
                pos = carryLen;
                return postfix(carry);
            }
            char c = peek();
            if (c == MINUS || c == '-') {
                pos++;
                Term inner = factor();
                return new Term(inner.value.negate(), inner.percentOnly);
            }
            if (c == PLUS) {
                pos++;
                return factor();
            }
            return postfix(primary());
        }

        Term postfix(BigDecimal v) throws CalcError {
            boolean percent = false;
            while (peek() == PERCENT) {
                pos++;
                v = checked(v.movePointLeft(2));
                percent = true;
            }
            return new Term(v, percent);
        }

        BigDecimal primary() throws CalcError {
            char c = peek();
            if (c == OPEN) {
                pos++;
                BigDecimal v = expression();
                if (peek() != CLOSE) {
                    throw new CalcError(ERR_SYNTAX);
                }
                pos++;
                return v;
            }
            int start = pos;
            while (isDigit(peek()) || peek() == POINT) {
                pos++;
            }
            if (pos == start) {
                throw new CalcError(ERR_SYNTAX);
            }
            if (peek() == EXP) {
                pos++;
                char sign = peek();
                if (sign == MINUS || sign == '-' || sign == PLUS) {
                    pos++;
                }
                int digitsStart = pos;
                while (isDigit(peek())) {
                    pos++;
                }
                if (pos == digitsStart) {
                    throw new CalcError(ERR_SYNTAX);
                }
            }
            String num = s.substring(start, pos).replace(MINUS, '-');
            try {
                return checked(new BigDecimal(num));
            } catch (NumberFormatException e) {
                throw new CalcError(ERR_SYNTAX);
            }
        }
    }
}
