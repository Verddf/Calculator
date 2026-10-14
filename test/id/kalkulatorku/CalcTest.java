package id.kalkulatorku;

import java.math.BigDecimal;

/** Uji otomatis mesin hitung dan logika tombol (dijalankan di JVM biasa). */
public final class CalcTest {

    private static int passed;
    private static int failed;

    private static final char M = Engine.MINUS;

    public static void main(String[] args) throws Exception {
        engineTests();
        formatTests();
        keyTests();
        System.out.println();
        System.out.println("PASSED: " + passed + "  FAILED: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------ helpers

    private static String ops(String s) {
        // Tulis tes dengan '-', '*', '/' lalu ubah ke simbol internal.
        return s.replace('-', Engine.MINUS).replace('*', Engine.TIMES).replace('/', Engine.DIVIDE);
    }

    private static void check(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + name + "\n     expected: " + expected + "\n     actual:   " + actual);
        }
    }

    private static String eval(String expr) {
        try {
            return Engine.canonical(Engine.evaluate(ops(expr), null, 0)).replace(Engine.MINUS, '-');
        } catch (Engine.CalcError e) {
            return "ERR" + e.kind;
        }
    }

    private static Calculator press(String keys) {
        Calculator c = new Calculator();
        press(c, keys);
        return c;
    }

    /** Simulasi tombol: 0-9, '.', 'Z'=000, + - * /, '%', 'P'=( ), 'B'=hapus, 'C'=AC, '='. */
    private static void press(Calculator c, String keys) {
        for (int i = 0; i < keys.length(); i++) {
            char k = keys.charAt(i);
            if (k >= '0' && k <= '9') {
                c.digit(k);
            } else if (k == '.') {
                c.point();
            } else if (k == 'Z') {
                c.tripleZero();
            } else if (k == '+') {
                c.operator(Engine.PLUS);
            } else if (k == '-') {
                c.operator(Engine.MINUS);
            } else if (k == '*') {
                c.operator(Engine.TIMES);
            } else if (k == '/') {
                c.operator(Engine.DIVIDE);
            } else if (k == '%') {
                c.percent();
            } else if (k == 'P') {
                c.parens();
            } else if (k == 'B') {
                c.backspace();
            } else if (k == 'C') {
                c.clear();
            } else if (k == '=') {
                c.equalsKey();
            } else {
                throw new IllegalArgumentException("tombol tidak dikenal: " + k);
            }
        }
    }

    private static String expr(Calculator c) {
        return c.expression().replace(Engine.MINUS, '-').replace(Engine.TIMES, '*').replace(Engine.DIVIDE, '/');
    }

    private static String preview(Calculator c) {
        String p = c.preview();
        return p == null ? null : p.replace(Engine.MINUS, '-');
    }

    // ------------------------------------------------------------- engine

    private static void engineTests() {
        check("1+2", "3", eval("1+2"));
        check("0.1+0.2 tepat", "0.3", eval("0.1+0.2"));
        check("presedensi", "14", eval("2+3*4"));
        check("kurung", "20", eval("(2+3)*4"));
        check("bagi", "2.5", eval("10/4"));
        check("1/3", "0.333333333333333", eval("1/3"));
        check("2/3 dibulatkan", "0.666666666666667", eval("2/3"));
        check("1/3*3", "1", eval("1/3*3"));
        check("persen tambah", "220", eval("200+10%"));
        check("persen kurang", "180", eval("200-10%"));
        check("persen kali", "20", eval("200*10%"));
        check("persen bagi", "2000", eval("200/10%"));
        check("persen tunggal", "0.5", eval("50%"));
        check("persen ganda", "0.005", eval("50%%"));
        check("diskon bertingkat", "162", eval("200-10%-10%"));
        check("pajak 11%", "1110000", eval("1000000+11%"));
        check("persen dalam kurung", "2.2", eval("(200+10%)%"));
        check("persen lalu kali", "100", eval("10%*1000"));
        check("minus di depan", "-2", eval("-5+3"));
        check("kali minus", "-15", eval("5*-3"));
        check("minus minus", "8", eval("5--3"));
        check("bagi nol", "ERR2", eval("5/0"));
        check("bagi nol dalam kurung", "ERR2", eval("5/(3-3)"));
        check("format salah", "ERR1", eval("5+"));
        check("kurung tidak tertutup", "ERR1", eval("(5+1"));
        check("angka besar jadi E", "9.9999998E15", eval("99999999*99999999"));
        check("batas 15 digit", "1.23456789012345E15", eval("123456789012345*10"));
        check("1E15", "1E15", eval("100000000000000*10"));
        check("tepat di bawah 1E15", "999999999999999", eval("999999999999999"));
        check("angka kecil jadi E", "1E-10", eval("0.000000001/10"));
        check("1E-9 tetap biasa", "0.000000001", eval("0.00000001/10"));
        check("baca notasi E", "10000000000", eval("1E20/1E10"));
        check("baca E negatif", "0.003", eval("1.5E-3*2"));
        check("negatif desimal", "-0.5", eval("-1/2"));
        check("nol", "0", eval("5-5"));
        check("pembulatan 15 digit", "0.1", eval("0.1000000000000004"));
        check("terlalu besar", "ERR3", eval("1E9000*1E9000"));
        try {
            BigDecimal third = Engine.evaluate(ops("1/3"), null, 0);
            String shown = Engine.canonical(third);
            String next = shown + Engine.TIMES + "3";
            check("lanjut dari hasil persis", "1",
                    Engine.canonical(Engine.evaluate(next, third, shown.length())));
            BigDecimal neg = Engine.evaluate(ops("2-5"), null, 0);
            String negShown = Engine.canonical(neg);
            check("hasil negatif sebagai awalan", "-6",
                    Engine.canonical(Engine.evaluate(negShown + Engine.TIMES + "2", neg, negShown.length()))
                            .replace(Engine.MINUS, '-'));
        } catch (Engine.CalcError e) {
            check("carry tanpa error", "ok", "ERR" + e.kind);
        }
    }

    // ------------------------------------------------------------- format

    private static void formatTests() {
        check("format Indonesia", "1.234.567,89+1.000",
                Engine.format("1234567.89+1000", ',', '.'));
        check("format negatif", M + "1.234", Engine.format(M + "1234", ',', '.'));
        check("format E", "1,23456789012345E15", Engine.format("1.23456789012345E15", ',', '.'));
        check("format E negatif", "1,5E" + M + "10", Engine.format("1.5E" + M + "10", ',', '.'));
        check("format Inggris", "1,000,000.5", Engine.format("1000000.5", '.', ','));
        check("format ratusan", "999", Engine.format("999", ',', '.'));
        check("format angka diketik", "0,", Engine.format("0.", ',', '.'));
        check("format operator", "12" + Engine.TIMES + "(3+4)%",
                Engine.format("12" + Engine.TIMES + "(3+4)%", ',', '.'));
    }

    // --------------------------------------------------------------- keys

    private static void keyTests() {
        Calculator c = press("123+4");
        check("ketik ekspresi", "123+4", expr(c));
        check("pratinjau", "127", preview(c));

        check("tanpa nol di depan", "5", expr(press("05")));
        check("koma ganda diabaikan", "0.5", expr(press("..5.")));
        check("koma menggantung dibuang", "5+", expr(press("5.+")));
        check("ganti operator", "5*", expr(press("5+*")));
        check("minus setelah kali", "5*-", expr(press("5*-")));
        check("ganti dari minus unary", "5+", expr(press("5*-+")));
        check("minus di awal", "-", expr(press("-")));
        check("minus dua kali", "-", expr(press("--")));
        check("plus di awal diabaikan", "", expr(press("+")));
        check("minus lalu plus", "", expr(press("-+")));
        check("pratinjau angka tunggal kosong", null, preview(press("5")));
        check("pratinjau minus angka kosong", null, preview(press("-5")));

        check("kurung pintar", "(2)*(", expr(press("P2PP")));
        check("kurung setelah angka", "5*(", expr(press("5P")));
        check("kurung otomatis ditutup", "35", preview(press("5*P3+4")));
        check("kali otomatis setelah kurung", "(2+3)*4", expr(press("P2+3P4")));
        check("kali otomatis setelah persen", "5%*2", expr(press("5%2")));
        check("persen setelah operator diabaikan", "5+", expr(press("5+%")));
        check("pratinjau persen", "220", preview(press("200+10%")));

        c = press("1/3=");
        check("hasil 1/3", "0.333333333333333", expr(c));
        check("status setelah =", Boolean.TRUE, c.isJustEvaluated());
        check("ekspresi sebelumnya", "1/3", c.lastExpression().replace(Engine.DIVIDE, '/'));
        press(c, "*3=");
        check("presisi terbawa", "1", expr(c));

        c = press("1/3=");
        press(c, "B*3=");
        check("hapus digit memutus presisi", "0.99999999999999", expr(c));

        check("angka baru setelah =", "7", expr(press("2+3=7")));
        check("operator setelah = melanjutkan", "5+", expr(press("2+3=+")));
        check("koma setelah = mulai baru", "0.", expr(press("2+3=.")));

        c = press("5/0=");
        check("bagi nol tidak mengubah ekspresi", "5/0", expr(c));
        Calculator.Result r = press("5").equalsKey();
        check("= pada angka tunggal tidak dicatat", null, r);
        c = press("5+");
        r = c.equalsKey();
        check("= dengan operator menggantung", null, r);
        check("ekspresi jadi angka", "5", expr(c));
        c = press("5/0");
        r = c.equalsKey();
        check("kode error bagi nol", Integer.valueOf(Engine.ERR_DIV_ZERO), Integer.valueOf(r.error));
        c = press("-");
        r = c.equalsKey();
        check("kode error format", Integer.valueOf(Engine.ERR_SYNTAX), Integer.valueOf(r.error));

        check("000 pada kosong", "0", expr(press("Z")));
        check("000 setelah angka", "1000", expr(press("1Z")));
        check("000 setelah nol diabaikan", "0", expr(press("0Z")));
        check("000 setelah operator", "5+0", expr(press("5+Z")));
        check("rupiah", "15000000", expr(press("15ZZ")));

        Calculator many = press("123456789012345");
        check("batas 15 digit", Integer.valueOf(Calculator.MAX_DIGITS), Integer.valueOf(many.digit('6')));
        check("digit tetap 15", "123456789012345", expr(many));

        c = press("2-5=");
        check("hasil negatif", "-3", expr(c));
        press(c, "*2=");
        check("lanjut dari hasil negatif", "-6", expr(c));
        c = press("2-5=");
        press(c, "%");
        check("persen dari hasil", "-0.03", preview(c));

        c = press("5*");
        c.insertValue(M + "3");
        check("sisip riwayat negatif setelah kali", "5*-3", expr(c));
        c = press("5-");
        c.insertValue(M + "3");
        check("sisip riwayat negatif setelah minus", "5-(-3)", expr(c));
        check("hasil sisip", "8", preview(c));
        c = press("5+3");
        c.insertValue("100");
        check("sisip riwayat mengganti", "100", expr(c));

        c = press("99999999*99999999=");
        check("hasil E", "9.9999998E15", expr(c));
        press(c, "B");
        check("hapus angka E utuh", "", expr(c));
        c = press("99999999*99999999=");
        press(c, "5");
        check("digit setelah hasil E mulai baru", "5", expr(c));

        c = press("12+3");
        press(c, "C");
        check("AC", "", expr(c));

        c = new Calculator();
        c.restore("12" + Engine.TIMES + "3", null, 0, false, null);
        check("pulihkan ekspresi", "36", c.preview());
        c.restore("12abc", null, 0, false, null);
        check("pulihkan menolak teks aneh", "", c.expression());
    }
}
