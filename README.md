# 原神 · 蒙德序章 J2ME Demo

离线单机试玩。将 `dist/Windward.jar` 装入支持 MIDP 2.0、CLDC 1.1 和 JSR-184（M3G）的模拟器或手机。需要安装描述文件的设备请将同目录的 `Windward.jad` 与 JAR 一起复制。1.1 版已在 FreeJ2ME-Plus 的 240×320 竖屏和 640×360 横屏跑通主线。可直接播放同目录的 [17 秒模拟器实录](dist/gameplay.mp4)，或查看[游戏截图](dist/preview.png)。

## 试玩范围

进入标题后，开场影片先讲述双子遭遇陌生神灵。选择空或荧后播放相应的失散片段，随后在鹰翔海滩与派蒙交谈。跟随她前往锚点和星落湖，在七天神像获得风元素，迎战火史莱姆。低语森林中会遇到神秘少年与巨龙，调查赤色结晶后在城外交会安柏。接下来可操作安柏射击，清理丘丘人营地，进入蒙德城，在高处领取风之翼并前往喷泉广场。

试玩到安柏的滑翔教学为止。九个剧情节点共选用 PC 1.0 简体中文文本表中的 23 条原文，按说话人分页。森林旁白、部分衔接对白和操作提示为试玩改写。开场影片字幕来自 PC 1.0 简体中文 SRT。风碑谜题、宝箱、微粒收集和采集是可选探索内容。使用 RMS 保存角色、位置、剧情进度、战斗结果和收集状态。

## 操作

- **2 / 8** 前进、后退。**4 / 6** 转向。方向键和确定键也能操作。
- **5** 对话、调查、采集或普攻。安柏加入后可用弓攻击范围内的敌人。
- **1** 旅行者的风元素战技，长按再松开可蓄力。**9** 元素爆发。**＊** 在加入的角色间切换。
- **3** 跳跃。领取风之翼后在空中再按一次可展开或收起。**7** 切换疾跑。
- **0** 地图。神像激活后，在地图按 **1** 传送。**#** 暂停、操作手册、保存并返回标题。
- **5** 可跳过影片。触屏设备点按屏幕可唤出触控条。

## 地图和资源

游戏使用 PC 1.0 的 `BigWorldTerrain_2_-2.bin` 与 `BigWorldTerrain_2_-1.bin`，覆盖原图 1.024 × 2.048 公里的连续区域。两块 513×513 高度场降采样并拼接，玩法空间按四分之一缩放。32 块地面纹理随视野加载，城内另从蒙德城 HLOD 网格烘焙街道高度与建筑阻挡。旅行者、派蒙、安柏、温迪、特瓦林、丘丘人、城景与主要贴图来自提供的 PC 包。靠近城内 29 栋建筑时会切换到按原版城景定位的独立模型，保留各自的贴图和材质分区。海滩一带的树群沿用原版坐标和图集，草、蕨和岩石使用原版模型，草丛按试玩地图重新布置。空、荧和安柏的待机、步行、疾跑、攻击、跳跃和滑翔动作由原版动作曲线和骨架离线转换，每人共烘焙 49 个姿势，游戏中插值播放。光照、人体关节转动和次要角色动作均有简化。安装图标来自提供的 Android 1.0 APK。

用 FFmpeg 将原版影片缩为 320×180、12 fps 的 Motion JPEG 帧流。双子共用的开场、性别分支和进城过场均保留原片时长。每三秒插入一张 PNG，供仅能解码 PNG 的设备按时间线显示关键画面。中文音轨转为 16 kHz 单声道 WAV。MIDlet 依次解码帧，音频设备支持相应格式时按播放时钟同步。

## 构建与验证

运行 `bash tools/build.sh`。普通构建直接使用仓库内已转换的资源。项目沿用了 GTAVCJ2ME 的 CLDC 预校验器，编译时使用真实的 MIDP 与 M3G API，模拟器自身的类不会进入 JAR。

构建需要 JDK 17 或以上版本运行预校验器、JDK 8 javac 生成兼容字节码，以及含 `cldc11.jar`、`midp21.jar`、`jsr184.jar` 的 SDK。通过 `JAVAC8` 和 `J2ME_API` 指定后两者的位置。默认值对应本机的 Temurin 8 和 KEmulator UEI。

桌面集成测试用 FreeJ2ME-Plus 实际加载打包的 MIDlet。传入模拟器 JAR 的绝对路径即可运行：

```sh
FREEJ2ME_JAR=/path/to/freej2me_plus.jar sh tools/test.sh
```

测试在新的 RMS 目录中行走和操作，覆盖双子分支、主线、战斗、错误与正确风碑顺序、宝箱和存档续玩，并在 build 下留存截图。设置 `FULL_OPENING=true` 可等待第一段影片自然播放完毕。

## 重新导入美术

用 blkstuff 的 batch 输出取得 PC 1.0 的序列化数据，将输出的 `00` 子目录作为输入。使用 GI-cutscenes 解出对应 USM 的 IVF 及第 0 路中文 HCA。导入脚本需要 Python、Pillow、NumPy、SciPy 与 FFmpeg。导出角色动作还需为本机编译 [ACL_1.1.0](https://github.com/Razmoth/ACL_1.1.0) 的解码动态库。

```sh
python3 tools/import_assets.py /path/to/decoded/00 /path/to/yuanshen_1.0.0_mihoyo.apk
python3 tools/actors.py /path/to/decoded/00 --acl /path/to/acl.dylib
python3 tools/streets.py
python3 tools/world_detail.py /path/to/decoded/00
python3 tools/coast.py /path/to/decoded/00
python3 tools/dialogue.py /path/to/decoded/01/26692920.0.bin
python3 tools/models.py
python3 tools/cinema.py /path/to/ivf-and-hca /path/to/StreamingAssets/VideoAssets
python3 tools/lettering.py
bash tools/build.sh
```

字形烘焙脚本默认使用 macOS 系统字体。工具及剧情顺序核对参考 [blkstuff](https://github.com/qwertroot123456/genshinblkstuff)、[GI-cutscenes](https://github.com/ToaHartor/GI-cutscenes)、[FreeJ2ME-Plus](https://github.com/TASEmulators/freej2me-plus) 和 [2020 年 1.0 开服图文流程](https://www.gamersky.com/handbook/202009/1320869_2.shtml)。
