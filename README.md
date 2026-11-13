# agree webview

English | [简体中文](README.zh-CN.md)

**agree webview** is an embeddable WebView AAR SDK built directly from Chromium source code. Instead of relying on the device's system WebView (whose version and behaviour vary by Android release, vendor skin and Play Services updates), it packs the Chromium kernel — `libwebviewchromium.so` — inside the host app itself as a standard Android AAR. The app gets a fixed, known engine that renders identically on every supported device.

- **Kernel**: Chromium **M119** (119.0.6045.214), official release build, dual ABI in one package (`arm64-v8a` + `armeabi-v7a`)
- **API**: the full `android.webkit.*` surface is mirrored under `agree.webkit.*` — swap the import and existing WebView code keeps working unchanged
- **Rendering**: hardware-accelerated, Vulkan on Android 10+ (API 29) with an automatic GL/EGL fallback for Android 7–9 (API 24–28) devices
- **Min platform**: Android **7.0 (API 24)**
- **Delivery**: a **5-AAR set** — one main AAR (kernel + assets) plus 4 auxiliary resource AARs carrying the Chromium R classes (all required)

# How to use agree webview

## 1. Add the AAR files into your project

Delivery is a **5-AAR set** (the 4 aux resource AARs are required, not optional):

```
agree.webkit.webview-v1.0.0.aar               main AAR (kernel + arm64/arm dual ABI + assets)
agree.webkit.webview-v1.0.0.compressed.aar    main AAR, DEFLATE-compressed entries (93.9 MB vs 213.3 MB)
agree-res-ui.aar / agree-res-content.aar /
agree-res-android_webview.aar /
agree-res-embedder_support.aar                     4 aux resource AARs (R classes)
```

Use either the plain or the `compressed` main AAR — **both have identical content**, they differ only in how the zip entries are stored. The compressed one is ~57% smaller but requires `useLegacyPackaging` (see step 3).

## 2. Change the import in your source code

Replace

```java
import android.webkit.WebView;
```

with

```java
import agree.webkit.WebView;
```

(The same applies to every other `android.webkit.*` class — the whole package is mirrored under `agree.webkit.*`.)

## 3. Required build configuration

The M119 kernel needs a few things from the host project that are easy to miss. Everything
below was found by integrating the AAR into a real app (AGP 8.x, `compileSdk 34`) and is
still required.

### 3.1 `app/build.gradle` — packaging

```gradle
android {
    ...
    // assets/*.pak, *.bin and icudtl.dat are mmap-loaded by the kernel: they must stay uncompressed.
    // Almost every failure to render a page traces back to a missing entry here.
    androidResources {
        noCompress 'bin', 'pak', 'dat'
    }

    packaging {
        // The compressed main AAR stores libwebviewchromium.so with DEFLATE, so it must be
        // extracted to disk before loading. The plain AAR works with or without this.
        jniLibs {
            useLegacyPackaging = true
        }
        // The kernel's classes.jar bundles google/protobuf/*.proto; protobuf-javalite ships
        // the same files. Without this the build fails on a duplicate-resource error.
        resources {
            excludes += ['google/protobuf/*.proto']
        }
    }

    // libc++_shared.so appears in several AARs
    packagingOptions {
        pickFirst '**/libc++_shared.so'
    }
}
```

| Setting | Why it is needed | Symptom if missing |
|---|---|---|
| `noCompress 'bin', 'pak', 'dat'` | Kernel mmaps these at runtime and needs raw file offsets | Blank page / WebView init failure. `icudtl.dat` has a `.dat` extension, so `'bin', 'pak'` is **not** enough |
| `useLegacyPackaging = true` | Compressed AAR's `.so` cannot be mmap'd from inside the APK | `UnsatisfiedLinkError` at startup |
| `excludes += ['google/protobuf/*.proto']` | Kernel AAR and protobuf-javalite both ship them | `2 files found with path 'google/protobuf/field_mask.proto'` |
| `pickFirst '**/libc++_shared.so'` | Copied by multiple AARs | Duplicate-file build error |

`androidResources` must be set on the **application** module, not only on a library module: the
assets of all modules are merged into the final APK, and the application module decides the
compression method. A library-level setting does not necessarily propagate.

### 3.2 `app/build.gradle` — dependencies

```gradle
dependencies {
    implementation 'com.google.protobuf:protobuf-javalite:3.21.12'
}
```

The AAR bundles the generated proto classes (e.g.
`org.chromium.android_webview.proto.AwVariationsSeedOuterClass`) but **not** the protobuf
runtime. Those classes extend `com.google.protobuf.GeneratedMessageLite`, so the host must
supply it.

Missing it on **API 26** produces a crash that looks completely unrelated to protobuf:

```
WebView.<init>(Context)
  → BrowserSafeModeActionList.<clinit>
  → FastVariationsSeedSafeModeAction.<clinit>
  → AwVariationsSeedOuterClass$AwVariationsSeed  → NoClassDefFoundError: com/google/protobuf/GeneratedMessageLite
  → WebView.setOverScrollMode(int)
  → native SIGTRAP (TRAP_BRKPT) in libwebviewchromium.so
  → ActivityManager: Force finishing activity
```

The app just returns to the launcher, with no Java stack trace pointing at the cause.
On API 32+ the same static chain is not triggered eagerly, so the problem stays hidden.

Use the **lite** runtime: the kernel is built against the lite protobuf API. `protobuf-java`
(full) has the same package names, is several MB larger, and conflicts with the lite API the
generated code expects. Chromium 119 matches `protobuf-javalite` **3.21.x**.

### 3.3 Root `build.gradle` — `androidx.core` and `kotlin-stdlib`

The kernel's `BuildInfo.getAllProperties()` calls `BuildCompat.isAtLeastU()`, which only exists
in `androidx.core` **1.12.0+**. If the project resolves an older version:

```
NoSuchMethodError: No static method isAtLeastU()Z in Landroidx/core/os/BuildCompat;
  at org.chromium.base.BuildInfo.getAllProperties
  at org.chromium.android_webview.AwBrowserProcess.start
```

Raise `androidx.core` to 1.12.0 (requires `compileSdk 34`):

```gradle
allprojects {
    configurations.all {
        resolutionStrategy {
            force 'androidx.core:core:1.12.0'
            force 'androidx.core:core-ktx:1.12.0'

            // androidx.core 1.12.0 pulls kotlin-stdlib 1.8.22, while most projects still
            // carry kotlin-stdlib-jdk7/jdk8 1.5.x. From Kotlin 1.8 on, the jdk7/jdk8 classes
            // were merged into the main artifact, so both cannot coexist:
            //   Duplicate class kotlin.streams.jdk8.StreamsKt$asSequence$...
            //     found in kotlin-stdlib-1.8.22 and kotlin-stdlib-jdk8-1.5.32
            // Raising jdk7/jdk8 to the same version turns them into empty shells
            // (only META-INF module declarations) and the duplicate disappears.
            force 'org.jetbrains.kotlin:kotlin-stdlib:1.8.22'
            force 'org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22'
            force 'org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22'
            force 'org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22'
        }
    }
}
```

`androidx.core` version → required `compileSdk`:

| androidx.core | compileSdk |
|---|---|
| 1.12.x | 34 |
| 1.15.x | 35 |
| 1.16+ | 36 |

`compileSdk 34` is the minimum viable choice for the M119 kernel. If your project already uses
a newer `compileSdk`, only the `force` lines matter.

### 3.4 Summary

```gradle
// global.gradle (or wherever versions live)
compileSdkVersion 34
minSdkVersion     26   // 24 is the kernel's own floor; 26 is what we validated
```

| Requirement | Minimum |
|---|---|
| `compileSdk` | 34 |
| `minSdk` | 24 (kernel floor) |
| `androidx.core` | 1.12.0 |
| `kotlin-stdlib` | 1.8.22 |
| `protobuf-javalite` | 3.21.x |
| ABI | `armeabi-v7a` and/or `arm64-v8a` |

## 4. Verifying the integration

The kernel is running if logcat shows:

```
I cr_WVCFactoryProvider: Loaded version=119.0.6045.214 minSdkVersion=24 isBundle=false ...
I cr_LibraryLoader: Successfully loaded native library
```

If the app launches but the screen stays blank, check the DOM rather than the screenshot — on
some devices `screencap` cannot capture the hardware-composited WebView layer and returns a
white frame even when the page is rendering correctly. Connect to the DevTools socket instead:

```bash
adb forward tcp:9444 localabstract:webview_devtools_remote_<pid>
curl -s http://127.0.0.1:9444/json
```

`"empty": false` plus a sensible `title`/`url` means the page is loaded.

### Known limitation: 10240-byte `prompt()` truncation

Chromium's renderer caps the `prompt()` message at `kMaxMessageSize = 10 * 1024` bytes
(`render_frame_impl.cc`). Any JS bridge built on `window.prompt()` — including
`WebChromeClient.onJsPrompt` — receives a **truncated** string for larger payloads, which then
fails to parse and leaves the JS main thread blocked forever (a blank page).

This is a Chromium behaviour, not a kernel defect, and it is identical in every version
(measured on both 119 and 132, and on the system WebView 113).

On the host side you must always confirm the `JsPromptResult` in your exception path, even when
parsing failed:

```java
} catch (Exception e) {
    XLog.e("JSBridge exec failed", e);
    // Without this, prompt() never returns and the JS main thread hangs → blank page.
    if (result != null) {
        result.confirm(null);
    }
}
```

That only prevents the deadlock; it does not recover the truncated payload. For payloads that
can exceed 10 KB, use an asynchronous bridge channel instead of `execSync`.
