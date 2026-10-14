#!/usr/bin/env bash
# Merakit Laughin-coffen.apk tanpa Android Studio/Gradle.
# Butuh: JDK, dan paket Ubuntu aapt, dalvik-exchange, zipalign, apksigner, android-sdk-platform-23.
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
OUT=build
KEYSTORE=keystore/kalkulatorku.jks
KS_PASS=kalkulatorku
KEY_ALIAS=kalkulatorku
APK=Laughin-coffen.apk

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "1/6 Kompilasi resource"
aapt2 compile --dir res -o "$OUT/res.zip"

echo "2/6 Tautkan resource dan manifest"
aapt2 link -o "$OUT/base.apk" -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
  --java "$OUT/gen" "$OUT/res.zip"

echo "3/6 Kompilasi Java"
javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -proc:none -encoding UTF-8 \
  -Xlint:-options -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')

echo "4/6 Ubah ke format Android (dex)"
dalvik-exchange --dex --min-sdk-version=23 --output="$OUT/dex/classes.dex" "$OUT/classes"

echo "5/6 Gabung dan rapikan (zipalign)"
cp "$OUT/base.apk" "$OUT/unaligned.apk"
(cd "$OUT/dex" && zip -q -X ../unaligned.apk classes.dex)
zipalign -p -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "6/6 Tanda tangan"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p keystore
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Kalkulatorku, O=Kalkulatorku, C=ID"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KEY_ALIAS" \
  --out "$APK" "$OUT/aligned.apk"
apksigner verify "$APK"
echo "Selesai: $APK"
