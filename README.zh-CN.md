# agree webview

[English](README.md) | 简体中文

**agree webview** 是一套直接从 Chromium 源码构建的可内嵌 WebView AAR SDK。它不依赖设备的系统 WebView（其版本和行为会随 Android 版本、厂商定制、Play 服务更新而变化），而是把 Chromium 内核——`libwebviewchromium.so`——以标准 Android AAR 的形式打进宿主 App 自身。App 因此拥有一个版本固定、行为确定的引擎，在所有受支持设备上渲染表现一致。

- **内核**：Chromium **M119**（119.0.6045.214），官方 release 构建，单包双 ABI（`arm64-v8a` + `armeabi-v7a`）
- **API**：完整的 `android.webkit.*` 接口镜像到 `agree.webkit.*` 下——替换 import 即可，现有 WebView 代码无需改动
- **渲染**：硬件加速，Android 10+（API 29）走 Vulkan，Android 7–9（API 24–28）自动回退 GL/EGL
- **最低平台**：Android **7.0（API 24）**
- **交付形态**：**5-AAR 套件**——1 个主 AAR（内核 + assets）加 4 个携带 Chromium R 类的辅助资源 AAR（均为必需）

# 如何使用 agree webview

## 1. 将 AAR 文件加入工程

交付物是一套 **5-AAR**（4 个辅助资源 AAR 是必需的，不是可选项）：

```
agree.webkit.webview-v1.0.0.aar               主 AAR（内核 + arm64/arm 双 ABI + assets）
agree.webkit.webview-v1.0.0.compressed.aar    主 AAR 的 DEFLATE 压缩版（93.9 MB vs 213.3 MB）
agree-res-ui.aar / agree-res-content.aar /
agree-res-android_webview.aar /
agree-res-embedder_support.aar                     4 个辅助资源 AAR（R 类）
```

普通版和 `compressed` 版主 AAR 二选一即可——**两者内容完全一致**，区别仅在 zip 条目的存储方式。压缩版体积小约 57%，但需要配置 `useLegacyPackaging`（见第 3 步）。

## 2. 修改源码中的 import

把

```java
import android.webkit.WebView;
```

替换为

```java
import agree.webkit.WebView;
```

（其他所有 `android.webkit.*` 类同理——整个包都镜像在 `agree.webkit.*` 下。）

## 3. 必需的构建配置

M119 内核对宿主工程有几个容易遗漏的要求。以下每一项都是在真实 App（AGP 8.x、`compileSdk 34`）集成中踩出来的，且至今仍然必需。

### 3.1 `app/build.gradle` —— 打包配置

```gradle
android {
    ...
    // assets/*.pak、*.bin 和 icudtl.dat 由内核 mmap 加载：必须保持不压缩。
    // 几乎所有页面渲染失败都能追溯到这里漏配。
    androidResources {
        noCompress 'bin', 'pak', 'dat'
    }

    packaging {
        // 压缩版主 AAR 里 libwebviewchromium.so 是 DEFLATE 存储，
        // 加载前必须先解压到磁盘。普通版配不配都行。
        jniLibs {
            useLegacyPackaging = true
        }
        // 内核的 classes.jar 自带 google/protobuf/*.proto；protobuf-javalite 也带同样的文件。
        // 不排除会因重复资源导致构建失败。
        resources {
            excludes += ['google/protobuf/*.proto']
        }
    }

    // libc++_shared.so 出现在多个 AAR 中
    packagingOptions {
        pickFirst '**/libc++_shared.so'
    }
}
```

| 配置 | 为什么需要 | 缺失时的症状 |
|---|---|---|
| `noCompress 'bin', 'pak', 'dat'` | 内核运行时 mmap 这些文件，需要原始文件偏移 | 白屏 / WebView 初始化失败。`icudtl.dat` 后缀是 `.dat`，只配 `'bin', 'pak'` **不够** |
| `useLegacyPackaging = true` | 压缩版 AAR 的 `.so` 无法在 APK 内直接 mmap | 启动时 `UnsatisfiedLinkError` |
| `excludes += ['google/protobuf/*.proto']` | 内核 AAR 与 protobuf-javalite 都携带这些文件 | `2 files found with path 'google/protobuf/field_mask.proto'` |
| `pickFirst '**/libc++_shared.so'` | 多个 AAR 都拷贝了该库 | 构建期重复文件报错 |

`androidResources` 必须配在 **application 模块**上，不能只配在 library 模块：所有模块的 assets 最终合并进 APK，由 application 模块决定压缩方式；library 模块的配置不一定会传递。

### 3.2 `app/build.gradle` —— 依赖

```gradle
dependencies {
    implementation 'com.google.protobuf:protobuf-javalite:3.21.12'
}
```

AAR 自带生成的 proto 类（如
`org.chromium.android_webview.proto.AwVariationsSeedOuterClass`），但**不带** protobuf 运行时。这些类继承自 `com.google.protobuf.GeneratedMessageLite`，所以宿主必须提供它。

在 **API 26** 上缺这个依赖会触发一个看起来和 protobuf 完全无关的崩溃：

```
WebView.<init>(Context)
  → BrowserSafeModeActionList.<clinit>
  → FastVariationsSeedSafeModeAction.<clinit>
  → AwVariationsSeedOuterClass$AwVariationsSeed  → NoClassDefFoundError: com/google/protobuf/GeneratedMessageLite
  → WebView.setOverScrollMode(int)
  → native SIGTRAP (TRAP_BRKPT) in libwebviewchromium.so
  → ActivityManager: Force finishing activity
```

App 直接退回桌面，没有任何 Java 栈能指出真因。
在 API 32+ 上这条静态初始化链不会被提前触发，问题会一直隐藏着。

要用 **lite** 运行时：内核是按 lite protobuf API 构建的。`protobuf-java`（完整版）包名相同、体积大几 MB，且与生成代码期望的 lite API 冲突。Chromium 119 对应 `protobuf-javalite` **3.21.x**。

### 3.3 根 `build.gradle` —— `androidx.core` 与 kotlin-stdlib

内核的 `BuildInfo.getAllProperties()` 调用了 `BuildCompat.isAtLeastU()`，该方法只存在于
`androidx.core` **1.12.0+**。如果工程解析到旧版本：

```
NoSuchMethodError: No static method isAtLeastU()Z in Landroidx/core/os/BuildCompat;
  at org.chromium.base.BuildInfo.getAllProperties
  at org.chromium.android_webview.AwBrowserProcess.start
```

把 `androidx.core` 提升到 1.12.0（要求 `compileSdk 34`）：

```gradle
allprojects {
    configurations.all {
        resolutionStrategy {
            force 'androidx.core:core:1.12.0'
            force 'androidx.core:core-ktx:1.12.0'

            // androidx.core 1.12.0 会带入 kotlin-stdlib 1.8.22，而多数工程仍带着
            // kotlin-stdlib-jdk7/jdk8 1.5.x。Kotlin 1.8 起 jdk7/jdk8 的类已并入主制品，
            // 两者无法共存：
            //   Duplicate class kotlin.streams.jdk8.StreamsKt$asSequence$...
            //     found in kotlin-stdlib-1.8.22 and kotlin-stdlib-jdk8-1.5.32
            // 把 jdk7/jdk8 提到同一版本后它们变成空壳（只剩 META-INF 模块声明），
            // 重复类即消失。
            force 'org.jetbrains.kotlin:kotlin-stdlib:1.8.22'
            force 'org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22'
            force 'org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22'
            force 'org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22'
        }
    }
}
```

`androidx.core` 版本与所需 `compileSdk` 对应关系：

| androidx.core | compileSdk |
|---|---|
| 1.12.x | 34 |
| 1.15.x | 35 |
| 1.16+ | 36 |

`compileSdk 34` 是 M119 内核的最低可行选择。如果工程已经用了更高的 `compileSdk`，只需关注上面的 `force` 行。

### 3.4 汇总

```gradle
// global.gradle（或统一管理版本的地方）
compileSdkVersion 34
minSdkVersion     26   // 24 是内核自身的下限；26 是我们实测验证过的下限
```

| 要求 | 最低值 |
|---|---|
| `compileSdk` | 34 |
| `minSdk` | 24（内核下限） |
| `androidx.core` | 1.12.0 |
| `kotlin-stdlib` | 1.8.22 |
| `protobuf-javalite` | 3.21.x |
| ABI | `armeabi-v7a` 和/或 `arm64-v8a` |

## 4. 验证集成是否成功

logcat 出现以下日志即说明内核已运行：

```
I cr_WVCFactoryProvider: Loaded version=119.0.6045.214 minSdkVersion=24 isBundle=false ...
I cr_LibraryLoader: Successfully loaded native library
```

如果 App 已启动但屏幕看起来是白的，先查 DOM 而不是看截图——部分设备上 `screencap` 无法捕获硬件合成的 WebView 图层，页面正常渲染时也会返回白帧。改连 DevTools socket：

```bash
adb forward tcp:9444 localabstract:webview_devtools_remote_<pid>
curl -s http://127.0.0.1:9444/json
```

`"empty": false` 且 `title`/`url` 合理，即页面已加载。

### 已知限制：`prompt()` 的 10240 字节截断

Chromium 渲染进程把 `prompt()` 消息上限设为 `kMaxMessageSize = 10 * 1024` 字节
（`render_frame_impl.cc`）。任何基于 `window.prompt()` 的 JS bridge——包括
`WebChromeClient.onJsPrompt`——收到更大的载荷时都会得到**被截断**的字符串，随后解析失败并导致 JS 主线程永久阻塞（白屏）。

这是 Chromium 的行为而非内核缺陷，每个版本都一样（在 119、132 以及系统 WebView 113 上均实测确认）。

宿主侧必须在异常路径里始终 confirm 这个 `JsPromptResult`，即使解析失败：

```java
} catch (Exception e) {
    XLog.e("JSBridge exec failed", e);
    // 不 confirm 的话 prompt() 永不返回，JS 主线程挂死 → 白屏。
    if (result != null) {
        result.confirm(null);
    }
}
```

这只能防止死锁，无法恢复被截断的载荷。可能超过 10 KB 的载荷应改用异步 bridge 通道，而不是 `execSync`。
