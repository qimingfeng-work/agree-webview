# agree · Chromium M119 适配 patch

本目录是 agree 面向 **Chromium M119** 重写的一套 patch，取代 `patches/` 目录下原始的三份 patch
（`001.android_webview.support_library.patch` / `002.android_webview.glue.patch` /
`003.android_webview.BUILD.gn.patch`）。

原始 patch 基于 Chromium M130 源码编写，在 M119 上**无法直接应用**：源码树在
M119→M130 之间重构过（`Profile`/`ProfileStore` 拆分、`AwContents` API 变动、资源列表调整等），
补丁上下文大面积失配。

---

## 文件清单

| 文件 | 覆盖内容 | 大小 |
|---|---|---|
| `001.java_port.patch` | 64 个 Java 文件的包名替换 + agree 定制改动 + API 26 兼容改造 | ~103 KB |
| `002.build_gn.patch` | 3 个 `BUILD.gn`（`android_webview` / `glue` / `support_library`）+ `dist_aar.py` + `rules.gni`（--abi-mapping 双 ABI） | ~12 KB |
| `003.boundary_interfaces.BUILD.gn.patch` | `support_library/boundary_interfaces/BUILD.gn`：`boundary_interface_java` 加 `//android_webview/agree:webkit_glue_java` dep（001 把 `ProfileBoundaryInterface` 的 import 改成 `agree.webkit.*`，缺此 dep javac 报 symbol not found） | ~1 KB |
| `004.callback_java.BUILD.gn.patch` | `support_library/callback/BUILD.gn`：`callback_java` 加 `//android_webview/agree:webkit_glue_java` dep（001 把 `SupportLibWebViewContentsClientAdapter` 的 import 改成 `agree.webkit.*`，缺此 dep javac 报 symbol not found。**曾漏固化：首次成功构建是树内手工加的 dep，重建时丢失、javac 复现**） | ~1 KB |
| `005.webview_delegate_wrapper_import.patch` | `android_webview/glue/.../WebViewChromiumFactoryProvider.java`：补 `import android.webkit.WebViewDelegateWrapper;`。001 把 `WebViewDelegate` 全部替换成 `WebViewDelegateWrapper` 但漏了 import，javac 报 could not resolve | ~1 KB |
| `006.glue_javac.patch` | `WebViewChromium.java` + `SharedWebViewContentsClientAdapter.java`：glue 的 javac 阶段修复——`setTextClassifier` 形参 `TextClassifier`→`Object`（对齐仓库 `WebViewProvider` 的 API 24 兼容签名）、去掉 `autofill`/`onProvideAutofillVirtualStructure` 的 `@Override`（仓库 `WebViewProvider` 无此方法）、`ApiHelperForM.onPageCommitVisible` 换成直接调用仓库 `WebViewClient.onPageCommitVisible` | ~2.5 KB |

> draw_fn 的 M119 适配不做成 patch，已直接并入仓库 `java/src/draw_fn/`（见下文"六"）。

## 应用方法

```bash
cd /path/to/chromium/src

# 1. 确认处于干净的 M119 源码树
git status --short          # 应为空

# 2. 依次应用
git apply --check ../agree-webview/patches/001.java_port.patch
git apply         ../agree-webview/patches/001.java_port.patch

git apply --check ../agree-webview/patches/002.build_gn.patch
git apply         ../agree-webview/patches/002.build_gn.patch

git apply --check ../agree-webview/patches/003.boundary_interfaces.BUILD.gn.patch
git apply         ../agree-webview/patches/003.boundary_interfaces.BUILD.gn.patch

git apply --check ../agree-webview/patches/004.callback_java.BUILD.gn.patch
git apply         ../agree-webview/patches/004.callback_java.BUILD.gn.patch

git apply --check ../agree-webview/patches/005.webview_delegate_wrapper_import.patch
git apply         ../agree-webview/patches/005.webview_delegate_wrapper_import.patch

git apply --check ../agree-webview/patches/006.glue_javac.patch
git apply         ../agree-webview/patches/006.glue_javac.patch
```

验证应用结果：应当 **71 个文件变更**（64 Java + 5 GN + dist_aar.py + rules.gni），约 +575 / −300 行。

---

## patch 内容详解

### 一、Java 层：包名空间替换

agree 的核心思路是把 WebView 从"系统组件"改造成"独立 AAR"，因此所有原本位于
`android.webkit.*` 的公开 API 都要换到自己的包 `agree.webkit.*`。

替换示例：

```java
// 原版（系统 WebView）
import android.webkit.WebView;
import android.webkit.WebSettings;
// agree
import agree.webkit.WebView;
import agree.webkit.WebSettings;
```

同时把 `android_webview` 内部的 `org.chromium.android_webview.*` 也一并改名，
避免与宿主 App 内可能存在的同名包冲突。

### 二、Java 层：WebViewDelegate 抽象

这是 agree 最关键的改造，涉及 8 个文件。

原生 WebView 作为系统组件运行时，由系统 framework 注入一个 `@hide` 的
`android.webkit.WebViewDelegate` 实例，提供 `addWebViewAssetPath()`、
`getDataDirectorySuffix()`、`callDrawGlFunction()` 等底层能力。
agree 是普通 AAR，拿不到这个实例，因此自己实现一个
`android.webkit.WebViewDelegateWrapper`：

| 文件 | 改动 |
|---|---|
| `WebViewChromiumFactoryProvider` | `create()` / `initialize()` / `getWebViewDelegate()` 形参和返回类型换成 `WebViewDelegateWrapper`；`packageInfo` 改为从 `PackageManager` 查宿主包，不再走 `WebViewFactory.getLoadedPackageInfo()` |
| `WebViewContentsClientAdapter` | 构造函数形参类型替换 |
| `SharedWebViewContentsClientAdapter` | 字段和构造形参类型替换 |
| `DrawGLFunctor` | 字段、构造形参以及全部方法调用点替换 |
| `GlueApiHelperForO` / `GlueApiHelperForP` | 静态方法形参替换 |
| `WebViewChromiumAwInit` | `initPlatSupportLibrary()` 改为直接用自带的 `MyDrawFunctor` 函数表；`setUpResources()` 注释掉系统的 `R.onResourcesLoaded()` |

对应的 `WebViewDelegateWrapper.java` 本身由 agree 提供（位于 `java/src/android/webkit/`），
不在本 patch 范围内。

### 三、Java 层：API 26 兼容（本次新增）

原始 agree 从 Android 11+ 的 AOSP 分支拷贝源码，因此带有大量 API 28+ 的引用。
这些引用在 Android 8.0（API 26）上会导致 `NoSuchFieldError` / `NoClassDefFoundError` 崩溃。

处理方式：

**1) `Build.VERSION_CODES.*` 常量换成整数字面量（44 处 / 20 文件）**

```java
// 原版：Build.VERSION_CODES.Q 在 API 26 的 android.jar 里不存在，
//       javac 直接编译失败，运行时也是 NoSuchFieldError
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { ... }

// 改造后
if (Build.VERSION.SDK_INT >= 29) { ... }
```

同理，`@RequiresApi(Build.VERSION_CODES.Q)` 改为 `@RequiresApi(29)`——
注解参数同样会被打进 class 常量池，不换会崩。

涉及常量：`O`(26) `O_MR1`(27) `P`(28) `Q`(29) `R`(30) `S`(31)。

**2) `android.app.compat.CompatChanges` 降级（API 31+）**

```java
// 原版
if (BuildInfo.isAtLeastT()
        ? CompatChanges.isChangeEnabled(WebSettings.ENABLE_SIMPLIFIED_DARK_MODE)
        : BuildInfo.targetsAtLeastT()) {
    AwDarkMode.enableSimplifiedDarkMode();
}

// 改造后：CompatChanges 在 API 26 上不存在（NoClassDefFoundError），
//        统一走 targetsAtLeastT()，与 API 31 之前原生 WebView 行为一致
if (BuildInfo.targetsAtLeastT()) {
    AwDarkMode.enableSimplifiedDarkMode();
}
```

### 四、BUILD.gn 层

| 文件 | 改动要点 |
|---|---|
| `android_webview/BUILD.gn` | ① 新增 `generate_jni("draw_fn_impl_jni_headers")`，为 `MyDrawFunctor.java` 生成 JNI 头<br>② 关闭 `remove_uncalled_jni`<br>③ `webkit_glue_java` 加入 `WebViewDelegateWrapper.java` 及全部 `agree.webkit.*` 源文件<br>④ 关闭 `--collect-inputs-only` ldflags<br>⑤ **新增 5 个资源 target**（见下） |
| `android_webview/glue/BUILD.gn` | deps 加 `//android_webview:webkit_glue_java` 和 support-annotations；注释掉 `ApiImplementationLogger.java`（M119 已无此文件） |
| `android_webview/support_library/BUILD.gn` | deps 加 `//android_webview:webkit_glue_java` |

新增的资源 target（`android_webview/BUILD.gn`）：

- `agree_web_contents_delegate_strings_grd`
- `agree_ui_strings_grd`
- `webkit_glue_resources`（`custom_package = agree.webkit`）
- `agree_embedder_support_resources`
- `agree_ui_android_resources`
- `agree_content_strings_grd`
- `agree_content_resources`

**为什么需要这些 target**：原生 WebView 作为系统组件时，这些资源由系统 `framework-res`
提供，Chromium 只需引用 id。agree 是独立 AAR，必须自己打包一份并生成自己的 `R` 类，
否则运行时找不到资源 id。

---

## 与旧 patch 的关系

**旧的三份 patch 已删除**（`patches/001.android_webview.support_library.patch`、
`patches/002.android_webview.glue.patch`、`patches/003.android_webview.BUILD.gn.patch`）。

删除依据 —— 实测它们在 M119 源码树上全部失败：

| 旧 patch | M119 应用结果 |
|---|---|
| `001.android_webview.support_library.patch` | 1 个 hunk 成功，**19 个文件失败** |
| `002.android_webview.glue.patch` | **0 个 hunk 成功，45 个文件全部失败** |
| `003.android_webview.BUILD.gn.patch` | 8 个 hunk 成功，第 9 个失败 |

需要参考历史实现时从 git 取回：

```bash
git show 67e5096:patches/001.android_webview.support_library.patch
git show 67e5096:patches/002.android_webview.glue.patch
git show 67e5096:patches/003.android_webview.BUILD.gn.patch
```

对应关系：

| 旧 patch | M119 对应 |
|---|---|
| `001.*`（22 块） | 拆分进 `001.java_port.patch`（Java）和 `002.build_gn.patch`（GN） |
| `002.*`（45 块） | 同上 |
| `003.*`（9 hunk） | `002.build_gn.patch` 中的 `android_webview/BUILD.gn` |

---

## 已知限制

### 1. Native 层必须重编才能支持 API 24

当前预编译的 `libwebviewchromium.so` 的 ELF `.note.android.ident` 段标记为：

```
ANDROID_API_LEVEL = 26      (0x1a)
NDK 版本 = r27
```

**这个 .so 只能保证在 API 26 (Android 8.0) 及以上加载。** 要支持 Android 7.0 (API 24)，
必须重新编译 native 层。

好消息是 M119 的构建配置默认就是 24，不需要改任何 `.gni`：

```gn
# build/config/android/config.gni
default_min_sdk_version = 24                        # :70
android32_ndk_api_level = default_min_sdk_version   # :198 自动继承
android64_ndk_api_level = default_min_sdk_version   # :201 自动继承
min_supported_sdk_version = 21                      # :91  下限够
```

`android64_ndk_api_level` **没有独立开关**，完全继承 `default_min_sdk_version`。
当前 .so 是 26，说明上次构建的 `args.gn` 里显式覆盖过，重编时删掉覆盖即可。

重编后必须验证：

```bash
readelf -x .note.android.ident libwebviewchromium.so | head -5
# 期望 0x18 (24)；若仍是 0x1a (26) 说明 args 没生效或缓存没清
```

### 2. Java 层已通过 API 24 静态验证

用 `android-24.jar` 编译 agree 全部 60 个 Java 文件，**没有任何 `android.*` 符号错误**。
报告的错误全部来自构建期依赖，由 GN 构建保证：

| 错误类型 | 数量 | 来源 |
|---|---|---|
| 缺 androidx | 11 | GN deps |
| 缺生成的 R 类 | 9 | `:webkit_glue_resources` |
| 缺 chromium base / JNI | 5+ | GN deps |
| `@Nullable` / `@NonNull` | 11 | androidx annotation |

`WebViewApi26` / `WebViewApi28` / `WebViewApiQ` 三个隔离类覆盖了 26/28/29 的签名问题，
24 低于这些版本，同一套隔离机制有效。

### 3. `WebViewDelegateWrapper` 需自行提供

本 patch 只改调用方，`java/src/android/webkit/WebViewDelegateWrapper.java`
由 agree 仓库提供。

### 4. ABI 覆盖

当前 AAR 只含 `arm64-v8a`。若目标设备包含 32 位（`armeabi-v7a`），
需在构建时把 `target_cpu` 配成 `"arm"` 再产一份，或改 `target_cpu = "arm64"` 且
`android_webview` 开启多 ABI 打包。

### 5. 版本升级成本

每次 Chromium 大版本升级，这套 patch 的 GN 部分大概率要重做
（资源列表、target 结构会变）。

---

## 六、draw_fn M119 适配（已并入仓库）

仓库 `java/src/draw_fn/`（`allocator.cc` / `draw_functor.cc` / `overlays_manager.cc`）是
**M130 合并**（`92211ce merge chromium v130 to agree sdk`）时整体替换进来的 M130 代码，
在 M119 树上编译不过（缺头文件、`AwDrawFnFunctionTable` 字段数不符、`NOTREACHED()` 语义不同）。
现已**直接改成 M119 兼容并并入仓库**（M132 构建不再保留，无需再维护 M130 版本）。适配点：

| 差异点 | M130（仓库现状） | M119（006 适配后） |
|---|---|---|
| skia include 布局 | `gpu/ganesh/*`、`gpu/ganesh/vk/*` | `gpu/*`、`gpu/vk/*`（`gpu/ganesh/` 只剩 `SkSurfaceGanesh.h` 与 `vk/GrVkBackendSurface.h`） |
| Vulkan 内存分配器 | `gpu/vulkan/init/skia_vk_memory_allocator_impl.h` + `gpu::CreateSkiaVulkanMemoryAllocator` | `gpu/vulkan/init/gr_vk_memory_allocator_impl.h` + `gpu::CreateGrVkMemoryAllocator` |
| 后端上下文类型 | `skgpu::VulkanBackendContext` | `GrVkBackendContext` |
| 上下文创建 | `GrDirectContexts::MakeVulkan(backend_context)` | `GrDirectContext::MakeVulkan(backend_context, options)` |
| 信号量构造 | `GrBackendSemaphores::MakeVk(x)` | `GrBackendSemaphore s; s.initVulkan(x);` |
| 纹理状态 | `skgpu::MutableTextureStates::MakeVulkan(layout, queue)` | `skgpu::MutableTextureState state(layout, queue)` |
| `AwDrawFnFunctionTable` | 6 字段（含 M130 新增的 `report_rendering_threads`） | 5 字段（M119 无此字段，多写报 excess elements） |
| `NOTREACHED()` | noreturn，`CreateFunctor` 无需 return | 非 noreturn，必须补 `return 0;`（否则 -Wreturn-type） |

> 这些适配已直接并入仓库 `java/src/draw_fn/`（M132 构建不再保留，故不再需要 M130 版本）。
