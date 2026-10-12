# 泥视频（nivod.cc）安卓客户端

把 `www.nivod.cc` 包成原生安卓 App。**不复刻网站**，只做原生浏览 + 原生播放器 + 多线路切换。

> 云端构建：GitHub Actions 出包，本地零 Android 环境。
> 构建逻辑复用 `Code-xy/video_hex` 的可复用工作流。

## 站点侦察结论（全部实测，2026-10）

站点是 **Nuxt SSR**（`#__nuxt`），播放器前端用 DPlayer + hls.js。

### 页面结构

| 用途 | URL | 实测卡片数 |
|---|---|---|
| 首页 | `/` | 72 |
| 频道 | `/class.html?channel=movie\|tv\|show\|anime\|doc` | 76 / 111 / … |
| 筛选 | `/filter.html?channel=X&region=Y&showtype=Z` | 48 |
| 搜索 | `/search.html?keyword=X` | 48 |
| 详情 | `/voddetail/<9位id>` | — |
| 播放页（网页兜底用） | `/vodplay/<id>/ep<N>` | — |
| **取流接口** | **`/xhr_playinfo/<id>-<epSlug>`** | **JSON** |

### ⚠ 站点没有分页

`page` / `p` / `pageIndex` / `pn` / `offset` 参数**全部无效**，页面里也没有分页控件。
首页 144 个 `/voddetail/` 链接实为 72 张卡片（每卡 2 处链接）。
所以 App 用 **region(8) × showtype(18) 筛选组合**来翻找内容 —— 那才是站点真正的浏览入口。

### 卡片 DOM

```html
<li class="qy-mod-li ...">
  <a class="qy-mod-link" href="/voddetail/<id>">
    <img src="/imgs/small/<id>.jpg" data-original="/imgs/small/<id>.jpg" alt="标题">
    <span class="qy-mod-label">更新至11集</span>   <!-- 角标，部分页面无 -->
  </a>
  <p class="main"><a class="link-txt" title="标题" href="/voddetail/<id>">…</a></p>
  <p class="sub" title="年份/地区/类型"></p>        <!-- 部分页面无 -->
</li>
```

封面 `/imgs/small/<id>.jpg` 实测 `200 image/jpeg`，**无需 Referer**。

### 详情页字段（固定 id，直接取）

`#director` `#actors` `#region` `#reso` `#postYear` `#langName` `#types-label` `#updateTxt` `#show-desc`
标题：`meta[name=keywords]` 首个逗号前（如 `花朵盛开,2026,日语,日本`）
评分：`.score-label`　简介：`meta[name=description]`
分集：`/vodplay/<id>/ep<N>`（电影是 `/vodplay/<id>/v`）

### 取流：这是本站最大优势

```
GET /xhr_playinfo/<id>-<epSlug>
→ {"pdatas":[{"playurl":"https://…/index.m3u8","from":"ikzy","name":"第01集"}, …],
   "ep_slug":"ep1", "more_eps":[]}
```

- 实测每个分集给 **3~12 条线路**（不同资源站：ikzy / mdzy / jszy / jyzy / hnzy / ffzy / lzzy / xlzy / gszy / ukzy / wjzy2 / snzy）
- 线路数**不固定**：ep1~ep10 各 12 条，ep11 只有 3 条
- **线路存活率不一**：实测某电影 5 条里挂了 1 条（`hd.ijycnd.com` 连接失败）
- m3u8 三级链路验证通过：master → variant（1920x1080/3702kb）→ 分片 `206 video/mp2t`
- 播放列表是 **VOD**（`EXT-X-ENDLIST`），312 个分片，可拖动
- **无防盗链**，裸拉 `200 application/vnd.apple.mpegurl`
- 分片是**跨域绝对地址**（如 `kkzycdn.com:65`），ExoPlayer 直接支持

→ App 因此做了 **并行探活 + 自动选可用线路 + 手动切换**。

## 架构

```
MainActivity     首页 72 卡（Grid 3）
ChannelActivity  频道 + 地区/类型 筛选（替代不存在的分页）
SearchActivity   搜索（48 条，本地历史）
DetailActivity   详情：元数据 + 选集（代码铺行，避开嵌套滚动）+ 收藏
PlayerActivity   GSY(Exo2) 播放 + 12 线路切换 + 倍速 + 收藏 + 手势
MineActivity     播放历史 / 收藏
WebActivity      WebView 兜底 + inject.js 嗅探 m3u8
```

- `data/Site.kt` —— 全部解析逻辑，选择器基于实测 DOM
- `ui/PlayerLauncher.kt` —— 取流 + **并行探活**（CountDownLatch 7s 超时，选第一条能拉到 `#EXTM3U` 的）
- `App.kt` —— **必须** `PlayerFactory.setPlayManager(Exo2PlayerManager)`，否则默认 IJK 内核会因缺 `.so` 闪退

## 编译链

| 组件 | 版本 |
|---|---|
| AGP / Gradle / Kotlin | 8.11.1 / 8.13（自带 wrapper）/ 2.2.0 |
| compileSdk / targetSdk / minSdk | 36 / 35 / 23 |
| GSYVideoPlayer | 14.0.0（JitPack）→ androidx.media3 1.10.1 |

剔除了 `libijk*.so`（约 40MB），因为用 Exo2 内核不加载它们。

## 签名

固定签名：`signing/dev-release.jks`（随仓库提交，保证可覆盖升级）。
优先级：CI secrets > 仓库内置 jks > debug。

⚠️ 验证签名稳定性要**比对证书 SHA256 指纹**，不要比对 `META-INF/CERT.RSA` 字节
（PKCS#7 带 signingTime，字节每次都不同，但 Android 只认证书）。

## 出包

```
APK : https://github.com/Code-xy/ni_video/raw/ci-logs/.ci/app-release.apk
日志: https://github.com/Code-xy/ni_video/raw/ci-logs/.ci/build.log
```
或在 Actions 页面点 **Run workflow** 手动构建（可选 ABI / 版本号 / 分包）。
