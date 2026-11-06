# OldPoint 0.6.153 · Viewpoint nightly 0.1.7-rc2 兼容版

**必须搭配 Viewpoint nightly `0.1.7-rc2` 使用。**普通版 Viewpoint `0.1.6.x` 与此包不兼容；请勿同时启用两个 Viewpoint 版本。此发布附件只包含 OldPoint，Viewpoint nightly 需要单独获取和安装。

## 安装

1. 安装 Project Zomboid Build `42.21`、Viewpoint nightly `0.1.7-rc2` 和 ZombieBuddy `2.3.0` 或更新版本。
2. 将附件 `OldPoint-0.6.153-Viewpoint-nightly-0.1.7-rc2.zip` 解压到 `C:\Users\<用户名>\Zomboid\mods\`，形成 `mods\OldPoint\42\mod.info`。
3. 在游戏中启用 Viewpoint、ZombieBuddy 和 OldPoint。若此前安装过 OldPoint，先停用旧副本，避免同 ID 模组同时加载。

## 本版内容

- 适配 nightly 的物品悬停设置、画面放大参数、树木着色器接口和模型可见性接口。
- 适配俯视镜头的新增接口，并补充镜头插值与俯视音频监听位置。
- 带入 Authentic Z 荧光棒适配代码。该功能需要额外的 `OldPointAuthenticZCarriedLightsV1` Lua 桥接表；本附件没有提供该桥接表，因此单独安装时不会启用这项适配。

本包已针对上述 nightly 编译，并通过 Java Hook 测试；游戏内表现仍需验证。此兼容版基于 `master` 的 `0.6.153`，未合并到 `master`，也未同步到创意工坊。

---

# OldPoint 0.6.153 · Viewpoint nightly 0.1.7-rc2 compatibility build

**Requires Viewpoint nightly `0.1.7-rc2`.** The regular Viewpoint `0.1.6.x` release is incompatible with this package. Do not enable both Viewpoint versions. This asset contains OldPoint only; obtain and install the nightly Viewpoint build separately.

Install Project Zomboid Build `42.21`, Viewpoint nightly `0.1.7-rc2`, and ZombieBuddy `2.3.0` or later. Extract the OldPoint ZIP into `Zomboid\mods\` so that `mods\OldPoint\42\mod.info` exists, then enable the three mods. Disable any older OldPoint copy first.

This build adapts the nightly loot, upscaler, tree shader, camera, and model visibility APIs; adds overhead camera interpolation and audio listener positioning; and includes an Authentic Z glowstick adapter. The glowstick adapter needs an additional `OldPointAuthenticZCarriedLightsV1` Lua bridge that is not part of this asset.

Compilation and Java Hook tests passed against the specified nightly build. In-game behavior still needs verification. This build is based on `master` version `0.6.153`; it has not been merged into `master` or published to Steam Workshop.
