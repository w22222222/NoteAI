param(
    [switch]$Install
)

$ErrorActionPreference = "Continue"

$ROOT = $PSScriptRoot
$SDK = "C:\Program Files (x86)\Android\android-sdk"
$JDK = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
$BT = "$SDK\build-tools\35.0.0"
$PLAT = "$SDK\platforms\android-35"
$ADB = "$SDK\platform-tools\adb.exe"
$KEYSTORE = "$env:USERPROFILE\.android\debug.keystore"
$ANDROID_JAR = "$PLAT\android.jar"
$NDK_ROOT = "C:\Program Files (x86)\Android\AndroidNDK\android-ndk-r23c"
$NDK_TOOLCHAIN = "$NDK_ROOT\toolchains\llvm\prebuilt\windows-x86_64\bin"
$ARCH_ABI = "arm64-v8a"
$CPP_SRC = "$ROOT\engine\src\main\cpp"
$NATIVE_OUT = "$ROOT\build\native"
$CPP_STL = "$NDK_ROOT\sources\cxx-stl\llvm-libc++\libs\$ARCH_ABI\libc++_shared.so"
$LIBS_DIR = "$ROOT\libs"
$KOTLIN_STDLIB = "$SDK\cmdline-tools\12.0\lib\external\org\jetbrains\kotlin\kotlin-stdlib\1.9.0\kotlin-stdlib-1.9.0.jar"

$JAVA = "$JDK\bin\java.exe"
$JAVAC = "$JDK\bin\javac.exe"
$JAR = "$JDK\bin\jar.exe"
$KEYTOOL = "$JDK\bin\keytool.exe"

if (Test-Path "$JDK\bin\javac.exe") {
    $env:JAVA_HOME = $JDK
    $env:Path = "$JDK\bin;$env:Path"
}

function Assert-Path($path, $message) {
    if (-not (Test-Path $path)) {
        throw "${message}: $path"
    }
}

Assert-Path $ANDROID_JAR "Missing Android platform jar"
Assert-Path $JAVAC "Missing javac"
Assert-Path $JAVA "Missing java"
Assert-Path "$BT\aapt2.exe" "Missing aapt2"
Assert-Path "$BT\lib\d8.jar" "Missing d8"
Assert-Path "$BT\lib\apksigner.jar" "Missing apksigner"
Assert-Path "$BT\zipalign.exe" "Missing zipalign"
Assert-Path $KOTLIN_STDLIB "Missing Kotlin stdlib"
Assert-Path "$LIBS_DIR\recyclerview-1.3.2.jar" "Missing RecyclerView jar"
Assert-Path "$LIBS_DIR\rv_extracted\res" "Missing RecyclerView extracted resources"

function Invoke-NativeBuild {
    $soPath = "$NATIVE_OUT\$ARCH_ABI\libnote_engine.so"
    if (Test-Path $soPath) {
        Write-Host "[native] .so up to date: $soPath"
        return $soPath
    }

    Write-Host "[native] compiling C++ engine..."
    New-Item -ItemType Directory -Force -Path "$NATIVE_OUT\$ARCH_ABI" | Out-Null

    & "$NDK_TOOLCHAIN\clang++.exe" --target=aarch64-linux-android31 `
        -fPIC -shared -std=c++17 -O3 `
        -I $CPP_SRC `
        "$CPP_SRC\engine.cpp" "$CPP_SRC\jni_bridge.cpp" `
        -o $soPath 2>&1

    if (-not (Test-Path $soPath)) {
        throw "Native build failed"
    }
    return $soPath
}

function Ensure-DebugKeystore {
    if (-not (Test-Path $KEYSTORE)) {
        New-Item -ItemType Directory -Force -Path "$env:USERPROFILE\.android" | Out-Null
        & $KEYTOOL -genkey -v -keystore $KEYSTORE -storepass android -alias androiddebugkey `
            -keypass android -keyalg RSA -keysize 2048 -validity 10000 `
            -dname "CN=Android Debug,O=Android,C=US" 2>&1 | Out-Null
    }
}

function Build-NoteAI {
    Ensure-DebugKeystore

    $soPath = Invoke-NativeBuild
    $buildDir = "$ROOT\build\noteai"
    Remove-Item -Recurse -Force $buildDir -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path "$buildDir\obj", "$buildDir\dex", "$buildDir\flat" | Out-Null

    $manifestPath = "$ROOT\src\main\AndroidManifest.xml"
    $apkUnsigned = "$buildDir\unsigned.apk"

    Write-Host "[1/6] aapt2 compile/link..."
    $rvFlatDir = "$buildDir\flat\rv"
    New-Item -ItemType Directory -Force -Path $rvFlatDir | Out-Null
    & "$BT\aapt2.exe" compile --dir "$LIBS_DIR\rv_extracted\res" -o $rvFlatDir --legacy 2>&1 | Out-Null
    $flatRefs = @(Get-ChildItem $rvFlatDir -Recurse | Where-Object { $_.Name.EndsWith(".flat") } | ForEach-Object { $_.FullName })
    & "$BT\aapt2.exe" link -o $apkUnsigned --manifest $manifestPath -I $ANDROID_JAR $flatRefs 2>&1 | Out-Null
    Assert-Path $apkUnsigned "aapt2 link failed"

    Write-Host "[2/6] javac..."
    $appJava = @(Get-ChildItem "$ROOT\src\main\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
    $engineJava = @(Get-ChildItem "$ROOT\engine\src\main\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
    $allJava = $engineJava + $appJava

    $extraCp = "$LIBS_DIR\recyclerview-1.3.2.jar" + [IO.Path]::PathSeparator +
               "$LIBS_DIR\core-1.10.1.jar" + [IO.Path]::PathSeparator +
               "$LIBS_DIR\collection-1.4.0.jar" + [IO.Path]::PathSeparator +
               "$LIBS_DIR\customview-poolingcontainer-1.0.0.jar" + [IO.Path]::PathSeparator +
               $KOTLIN_STDLIB
    $cp = $ANDROID_JAR + [IO.Path]::PathSeparator + $extraCp

    $javacOut = & $JAVAC -encoding UTF-8 -d "$buildDir\obj" -cp $cp -source 11 -target 11 $allJava 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host $javacOut
        throw "javac failed"
    }

    Write-Host "[3/6] d8..."
    $classJar = "$buildDir\obj\classes.jar"
    & $JAR cf $classJar -C "$buildDir\obj" . 2>&1 | Out-Null
    & $JAVA -cp "$BT\lib\d8.jar" com.android.tools.r8.D8 --lib $ANDROID_JAR `
        --output "$buildDir\dex" $classJar `
        "$LIBS_DIR\recyclerview-1.3.2.jar" `
        "$LIBS_DIR\core-1.10.1.jar" `
        "$LIBS_DIR\collection-1.4.0.jar" `
        "$LIBS_DIR\customview-poolingcontainer-1.0.0.jar" `
        $KOTLIN_STDLIB 2>&1 | Out-Null
    Assert-Path "$buildDir\dex\classes.dex" "d8 failed"

    Write-Host "[4/6] package dex/native..."
    $apkDex = "$buildDir\with-dex.apk"
    Copy-Item $apkUnsigned $apkDex
    Push-Location "$buildDir\dex"
    & $JAR uf $apkDex classes.dex 2>&1 | Out-Null
    Pop-Location

    $soDir = "$buildDir\native\lib\$ARCH_ABI"
    New-Item -ItemType Directory -Force -Path $soDir | Out-Null
    Copy-Item $soPath "$soDir\libnote_engine.so"
    Copy-Item $CPP_STL "$soDir\libc++_shared.so"
    Push-Location "$buildDir\native"
    & $JAR uf $apkDex "lib\$ARCH_ABI\libnote_engine.so" "lib\$ARCH_ABI\libc++_shared.so" 2>&1 | Out-Null
    Pop-Location

    Write-Host "[5/6] zipalign/sign..."
    $apkAligned = "$buildDir\aligned.apk"
    $apkFinal = "$ROOT\build\noteai.apk"
    & "$BT\zipalign.exe" -f -p 4 $apkDex $apkAligned 2>&1 | Out-Null
    & $JAVA -jar "$BT\lib\apksigner.jar" sign --ks $KEYSTORE --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out $apkFinal $apkAligned 2>&1 | Out-Null
    Assert-Path $apkFinal "sign failed"

    Write-Host "[6/6] done: $apkFinal ($((Get-Item $apkFinal).Length) bytes)"
    return $apkFinal
}

$apk = Build-NoteAI

if ($Install) {
    Write-Host "[install] installing..."
    & $ADB install -r $apk 2>&1 | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        throw "adb install failed"
    }

    Write-Host "[launch] starting app..."
    & $ADB shell am start -n com.noteai.noteai/.MainActivity 2>&1 | Out-Null
}
