# 文件说明

本仓库是 agree 的**源码侧工程**：它不含 Chromium 源码，而是「叠加到 Chromium M119 源码树上的一组文件」。
把这些目录按约定放到 `src/android_webview/agree/` 下、再应用 `patches/`，就能用 GN 构建出可分发的 AAR。

- 想知道**怎么在自己的 App 里用 AAR** → 看 [README.md](README.md)
- 想知道**怎么从源码构建 AAR** → 看本文 + [patches/README.md](patches/README.md)
- 想知道**补丁具体改了什么** → 看 [patches/README.md](patches/README.md)

## 目录总览

```
agree/
├── BUILD.gn                     GN 构建定义（AAR 的 target）
├── AndroidManifest.xml          打进 AAR 的 manifest（沙箱进程服务等）
├── proguard.flags               AAR 内置的混淆保留规则
├── README.md                    接入方文档：怎么用 AAR
├── FILES.md                     本文：每个文件的作用与用法
├── java/                        Java / C++ 源码（会被编进 AAR）
│   ├── src/android/webkit/      必须放在 android.webkit 包下的类
│   ├── src/agree/webkit/       包名重定向后的 WebView 实现（agree.webkit 包）
│   ├── src/draw_fn/             渲染 functor 的 C++ 实现
│   └── res_webkit/              WebView 内部用到的布局/菜单资源
├── patches/                     叠加到 Chromium 源码树的补丁
│   ├── 001~006 *.patch          按序号顺序应用
│   └── README.md                每个 patch 覆盖哪些文件、怎么应用、已知限制
└── skills/                     构建流程的 Hermes 技能快照（压缩包）
```

---

## 一、构建入口文件

### `BUILD.gn`

AAR 构建的唯一入口，定义 7 个 target，`dist_aar("webview_aar")` 是最终产物。

| target | 类型 | 作用 |
|---|---|---|
| `copy_snapshot_blob` | `copy` | 从 `$root_build_dir` 取 V8 快照，重命名为 `snapshot_blob_64.bin` 打进 AAR；64 位与 32 位共用同一份快照 |
| `webkit_glue_resources` | `android_resources` | 编译 `java/res_webkit/` 下的 3 个 XML，`custom_package` 设为 `agree.webkit` |
| `draw_fn_impl_jni_headers` | `generate_jni` | 由 `MyDrawFunctor.java` 的 `@NativeMethods` 生成 `MyDrawFunctorJni.java` |
| `draw_fn_impl_java` | `android_library` | 编译 `MyDrawFunctor`，依赖 `//base:jni_java`（M119 的 JNI 注解在这个独立 target 里，不在 `base_java`） |
| `webkit_glue_java` | `android_library` | 编译 `java/src/` 下 57 个 Java 文件（BUILD.gn 里列 58 条，`LegacyErrorStrings.java` 被注释掉），是 AAR 的 Java 主体 |
| `copy_webkit_java` | `java_library` | 只编 3 个「占位配置类」：`BuildConfig` / `ProductConfig` / `NativeLibraries` |
| `webview_aar` | `dist_aar` | 汇总所有依赖打成 AAR：`.so`、`.pak`、`.bin`、资源、Java 类 |

**`declare_args()` 里的 `enable_agree_arm`**（默认 `true`）：控制是否把 32 位 `armeabi-v7a` 的 `libwebviewchromium.so` 一起打进 AAR。

```gn
# args.gn 里只出 arm64-v8a 时：
enable_agree_arm = false
```

前置条件是构建时 `target_cpu = "arm64"`（GN 才会生成 secondary ABI toolchain）。若 32 位产物没生成，置 `false` 退回单 ABI。

**用 `webview_aar` 的注意点**：它依赖 `:webkit_glue_java`，缺这条依赖 AAR 里不会有任何 agree 类。反向依赖也很关键——`//android_webview:libwebviewchromium`（native 库）必须在 deps 里，否则 AAR 只有 Java 没有 `.so`。

### `AndroidManifest.xml`

打进 AAR、由宿主 App 合并的 manifest。**不是**系统组件的 manifest。

三个改之前必须知道的约束：

1. `SandboxedProcessService` 必须 `exported=true` + `externalService="true"`。系统用 `bindServiceAsUser` 拉起 renderer，`exported=false` 会让绑定失败 → 白屏 / 渲染进程起不来。
2. `NUM_SANDBOXED_SERVICES` 的 meta-data 值必须与实际声明的 service 条数一致（这里用 40，与原生 WebView 相同）。不一致会导致多 WebView 实例分不到 renderer 进程。
3. 不要声明 `RECORD_VIDEO` 权限——Android 从未有此权限，只会触发 lint 报错。

用法：构建时 `dist_aar.py` 会把它作为 AAR 的 `AndroidManifest.xml`。修改后需要重新构建 AAR 才能生效，接入方无需改动。

### `proguard.flags`

AAR 自带的混淆保留规则，会随 AAR 一起交付，由 `consumerProguardFiles` 生效，接入方无需手写。

主要保留项：`WebViewChromiumFactoryProvider`（框架通过反射找的入口）、`WebViewChromium`、各 `*Adapter`、`DrawFunctor` / `DrawGLFunctor` / `GraphicsUtils`（含 native 方法的类）、`org.chromium.build.BuildConfig`、`GEN_JNI`。

---

## 二、`java/src/android/webkit/`

只有一个文件。这个包名是**必须的**——它要让 `WebViewFactory` 通过 `android.webkit.WebViewDelegateWrapper` 这个名字找到它。

### `WebViewDelegateWrapper.java`

`WebViewDelegate` 的替代实现。原生 WebView 里 `WebViewDelegate` 是 framework 内部类（`@hide`），第三方 AAR 拿不到，所以必须自己实现一份。

差异点：原版直接使用 `android.graphics.RecordingCanvas`（API 29+），这里改用**类名字符串判断**（`isHardwareAcceleratedCanvas`），避免 API 24~28 上类加载期校验失败。

用法：由 `WebViewChromiumFactoryProvider` 实例化并传给 glue 层，接入方无感知。

---

## 三、`java/src/agree/webkit/`

WebView 的 Java 实现主体，56 个文件。包名从 `com.android.webview.chromium` 改成 `agree.webkit`，避免与系统 WebView 冲突。

### 3.1 核心入口

| 文件 | 作用 |
|---|---|
| `WebView.java` | 3294 行，WebView 主体。**所有 `android.webkit.WebView` 的公开 API 都在这里**，接入方 `import` 改为这个类即可。类加载期会校验常量池里所有方法引用，所以引用新版 API 类型的 `@Override` 方法已移到 `WebViewApi*` 隔离类 |
| `WebViewProvider.java` | `WebView` 的接口抽象（538 行）。跨 `WebView` 与 glue 层的契约，**改签名会同时影响 glue 层 javac**，改动需同步 `006.glue_javac.patch` |
| `WebViewFactory.java` | 顶层工厂，创建所有 WebView 实现类。`getProvider()` 反射调用 glue 层 provider 的 `create()`，并在此构造 `WebViewDelegateWrapper` 传入；`.so` 加载与内核版本判断不在这里（由 glue 层 provider 经 `NativeLibraries` 完成） |
| `WebViewFactoryProvider.java` | 提供给系统的 provider 接口 |
| `MyDrawFunctor.java` | 渲染 functor 的 Java 侧入口，`@JNINamespace("draw_fn")`。**`useVulkan()` 按 SDK_INT 分流**：API >= 29 走 Vulkan（M119 要求 Vulkan 1.1，Android 10 才满足），API < 29 走 GL/EGL |

### 3.2 版本隔离类（API 兼容的关键手法）

三个 `WebViewApi*` 类解决的是同一个问题：**ART 在类加载期校验方法签名，早于任何 `if (SDK_INT)` 分支**。签名里出现低版本不存在的类型，`WebView` 类加载时直接抛 `NoClassDefFoundError`——放在方法体内用分支守卫是**无效的**，因为方法引用仍留在常量池里。

| 文件 | 隔离的 API | 具体问题与代价 |
|---|---|---|
| `WebViewApi26.java` | API 26 (O) | `autofill` / `onProvideAutofillVirtualStructure` 引用了 `AutofillValue`。移除后 **Android 8.0+ 的 AutofillService（密码管理器）对 WebView 内表单填充失效**，但 HTML `<input>` 基本输入不受影响（那由 `.so` 处理） |
| `WebViewApi28.java` | API 28 (P) | `TextClassifier` 类型。签名改为 `Object` 而非删方法——Java/Kotlin 宿主源码零改动，只有 `getTextClassifier()` 显式接收为 `TextClassifier` 时需补一次强转 |
| `WebViewApiQ.java` | API 29 (Q) | `getImportantForContentCapture` 等 Q 新增方法。这就是当初华为 BZT-AL00 / API 26 上 `Java_J_N_MPLJeS1v` 处 `SIGTRAP` 的根因 |

**使用约定（三个类通用）**：所有方法必须由调用方先用 `Build.VERSION.SDK_INT >= X` 守卫；`@RequiresApi` 只用于 Lint 静态检查，**不提供运行时保护**。

### 3.3 公开 API 镜像类

以下是 `android.webkit.*` 的镜像，接入方若用到相应 API，`import` 需要一并改成这里的类：

`WebChromeClient`(657) · `WebViewClient`(605) · `WebSettings`(1701) · `CookieManager`(322) · `URLUtil`(705) · `WebResourceResponse`(245) · `WebViewDatabase`(166) · `WebStorage`(192) · `WebBackForwardList`(64) · `WebHistoryItem`(74) · `WebMessage`(65) · `WebMessagePort`(105) · `WebResourceRequest`(interface) · `WebResourceError` · `ConsoleMessage` · `PermissionRequest` · `HttpAuthHandler` · `SslErrorHandler` · `ClientCertRequest` · `GeolocationPermissions` · `SafeBrowsingResponse` · `RenderProcessGoneDetail` · `WebViewRenderProcess` · `WebViewRenderProcessClient` · `ServiceWorkerClient` / `Controller` / `WebSettings` · `TracingConfig` / `TracingController` · `JsResult` / `JsPromptResult` · `DownloadListener` · `ValueCallback` · `JavascriptInterface`

### 3.4 已弃用 / 兼容保留

`CacheManager` · `CookieSyncManager` · `WebSyncManager` · `WebIconDatabase` · `PluginData` / `PluginStub` · `UrlInterceptHandler` / `UrlInterceptRegistry` · `TokenBindingService` · `PacProcessor` · `FindAddress` · `WebViewFragment`

这些是低版本 App 可能仍在引用的老 API，保留是为了接入方不用改代码。新工程不要用。

### 3.5 内部实现

`JsDialogHelper`(186) · `FindActionModeCallback`(326)——对应 `res_webkit` 里的 `js_prompt.xml` / `webview_find.xml`。

---

## 四、`java/src/org/chromium/android_webview/` 与 `java/src/org/chromium/build/`

三个「占位配置类」。**它们之所以放在仓库里而不是构建时生成**，是因为 AAR 场景没有 APK 层，Chromium 的模板生成步骤不参与。

| 文件 | 作用 | 注意事项 |
|---|---|---|
| `ProductConfig.java` | locales 列表 + `IS_BUNDLE` / `USE_CHROMIUM_LINKER` 开关 | 字段必须与 `build/android/java/templates/ProductConfig.template` 生成版本一致。曾因缺 `IS_BUNDLE` 导致运行期 `NoSuchFieldError`——树内生成的版本被 `jar_excluded_patterns` 排除，**编译期用生成的、运行期用本 stub**，两者字段不一致就炸 |
| `BuildConfig.java` | `MIN_SDK_VERSION`、`VERSION_CODE`、`ENABLE_DEBUG_LOGS` 等 | `MIN_SDK_VERSION = 24`（与 `.so` 的 `.note.android.ident` 一致）。写 26 会让低版本设备被拒 |
| `NativeLibraries.java` | 需加载的 `.so` 列表与 CPU family | `LIBRARIES = {"webviewchromium"}`，加载顺序即数组顺序 |

改这三个文件的任何字段后必须重新构建 AAR；只改接入方侧无效。

---

## 五、`java/src/draw_fn/`（C++ 渲染层）

WebView 的渲染走 functor 机制：Java 侧拿到一张函数表，native 侧在 draw 回调里直接往 Surface 上绘制。

| 文件 | 作用 |
|---|---|
| `draw_functor.h` | `DrawFunctor` 抽象基类：`SetSurface` / `ResizeSurface` / `Sync` / `Draw` / `CreateContext` 等虚接口 |
| `draw_functor.cc` | **838 行，核心实现**。含两个具体子类：`MyDrawFunctorGL`（GL/EGL 路径，API < 29）与 `MyDrawFunctorVulkan`（Vulkan 路径，API >= 29）；另有 `VkFunctorDrawHandler` / `FunctorDrawable` 辅助类 |
| `allocator.h` / `allocator.cc` | `FunctorData` 与 `Allocator`：管理 draw functor 的共享内存块分配 |
| `overlays_manager.h` / `.cc` | `OverlaysManager`：管理 overlay surface（视频、WebGL 等独立合成层）。定义了 `AwDrawFn_DrawGLParams` / `AwDrawFn_DrawVkParams` 两个参数结构 |

**这两个路径的分流是 M119 在低版本 Android 上的关键**：M119 的 Vulkan 后端要求 Vulkan 1.1，Android 10 的兼容性定义才强制 1.1；Android 7/8/9 只有 Vulkan 1.0，loader 无法初始化，必须走 GL 路径（与官方 WebView 低版本同款方案）。

分流点由 `MyDrawFunctor.useVulkan()` 决定，`getDrawFnFunctionTable(useVulkan)` 与 `init(useVulkan)` 都接收这个布尔值。**改这里会同时影响 Java 与 native 两侧**，需要重编 `.so`。

---

## 六、`java/res_webkit/`

WebView 内部用到的资源，`custom_package = agree.webkit`：

| 文件 | 用途 |
|---|---|
| `layout/js_prompt.xml` | `prompt()` 对话框布局，被 `JsDialogHelper` 引用 |
| `layout/webview_find.xml` | 页内查找栏布局 |
| `menu/webview_find.xml` | 页内查找菜单 |

缺这些资源会在运行时 `ClassNotFoundException: org.chromium.content.R$array` 或布局 inflate 失败。**四个辅助资源 AAR 必须一起交付**就是为此——AAR 的 `R` 类要能解析到这些资源 ID。

---

## 七、`patches/`

叠加到 Chromium M119 源码树的补丁，**按序号顺序应用**。完整说明见 [patches/README.md](patches/README.md)。

| 文件 | 覆盖内容 |
|---|---|
| `001.java_port.patch` | 64 个 Java 文件：包名替换 + agree 定制 + API 26 兼容改造（~104 KB，最大的一个） |
| `002.build_gn.patch` | 3 个 `BUILD.gn` + `dist_aar.py` + `rules.gni`（`--abi-mapping` 双 ABI） |
| `003.boundary_interfaces.BUILD.gn.patch` | `boundary_interface_java` 加 `webkit_glue_java` dep |
| `004.callback_java.BUILD.gn.patch` | `callback_java` 加 `webkit_glue_java` dep |
| `005.webview_delegate_wrapper_import.patch` | `WebViewChromiumFactoryProvider.java` 补 `WebViewDelegateWrapper` 的 import |
| `006.glue_javac.patch` | glue 层 javac 修复：`setTextClassifier` 形参改 `Object`、去掉 autofill 的 `@Override`、`onPageCommitVisible` 直连 |

应用方法：

```bash
cd /path/to/chromium/src

# 确认处于干净的 M119 源码树
git status --short          # 应为空

# 逐个 check + apply（006 补齐后 git apply 会自动处理）
for p in ../agree/patches/00*.patch; do
  git apply --check "$p" && git apply "$p" || { echo "FAILED: $p"; break; }
done
```

**验证**：全部应用后应为 71 个文件变更（64 Java + 5 GN + 2 其他）。

003 / 004 之所以存在，是因为 001 把两处 import 改成了新包名，但没有对应加 dep，javac 会报 symbol not found。004 尤其容易漏——首次构建时是在源码树里手工加的 dep，没有固化，重建时才暴露。

---

## 八、`skills/`

`agree-webview-aar-build-m119.tar.gz`：构建流程的 Hermes 技能快照，含 56KB 的 `SKILL.md` 和 9 个打包脚本（`assemble_aar.py`、`make_res_aars.py`、`recompress_aar.py`、`merge_aars.py` 等）。

**这是给 AI 助手用的操作手册，不参与构建**。想了解构建细节可以直接解压读：

```bash
tar -xzf skills/agree-webview-aar-build-m119.tar.gz -C /tmp
ls /tmp/agree-webview-aar-build-m119/scripts/
```

里面的 `references/` 记录了踩过的坑：`runtime-js-dialog-chain.md`（JS dialog 调用链与 10240 截断）、`fat-multi-abi-builds.md`（双 ABI 构建）、`m119-baseline-build.md`（基线构建参数）。

---

## 常见改动场景

| 想做什么 | 改哪里 | 要不要重编 `.so` |
|---|---|---|
| 改 WebView 的 Java 行为 | `java/src/agree/webkit/WebView.java` | 否，重编 AAR 即可 |
| 加一个 WebView 公开 API | 同上的类 + 同步 `WebViewProvider.java` + 检查 glue 层 javac | 否 |
| 改渲染路径 / Vulkan 分流阈值 | `MyDrawFunctor.useVulkan()` + `java/src/draw_fn/` | **要** |
| 改最小支持版本 | `BuildConfig.MIN_SDK_VERSION` + 核对 `.so` 的 `.note.android.ident` | 可能要（取决于内核编译时的 `default_min_sdk_version`） |
| 改包名 | 全仓库 java/ 目录 + `patches/001/002/003/005` + BUILD.gn 的 `custom_package`/`resources_package` + manifest 的 `package` + 技能快照脚本（`port_agree_patch.py` 映射、`make_res_aars.py` 注释） | **要** |
| 只改构建产物组织方式 | `BUILD.gn` 的 `webview_aar` deps | 否 |

验证 `.so` 的实际最低 SDK：

```bash
$NDK/llvm-readelf -n libwebviewchromium.so | grep -i android.ident
# 期望 0x18 (24)；若为 0x1a (26) 说明编译 args 没生效或缓存没清
```
