#!/bin/bash
# 不用 Gradle，直接用 SDK 自带的工具打出 BatiaoPet.apk
# 需要：JDK 17、Android SDK（platforms;android-34、build-tools;34.0.0）
# 签名用的 keystore 不在仓库里：KEYSTORE=路径 KS_PASS=密码 ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
VERSION="${VERSION:-1.0.2}"
CODE="${CODE:-1}"

rm -rf build && mkdir -p build/gen build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -A assets build/res.zip \
    --java build/gen --min-sdk-version 26 --target-sdk-version 34 \
    --version-code "$CODE" --version-name "$VERSION" -o build/base.apk

javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -Xlint:-options \
    -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --release --min-api 26 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')

cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --out build/BatiaoPet.apk build/aligned.apk
"$BT/apksigner" verify build/BatiaoPet.apk
ls -la build/BatiaoPet.apk
