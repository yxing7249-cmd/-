# TVLauncher - 仿 Apple TV 风格 Android 电视桌面

一个专为 Android TV / 电视盒子设计的自定义桌面（Launcher），支持遥控器操作，界面仿 Apple TV 风格。

## 主要功能

- **仿 Apple TV 界面**：深色主题、大图标、焦点放大动画
- **最近使用**：横向滚动最近打开的应用
- **壁纸支持**：可从相册选择壁纸，带淡入淡出 + 缩放动画
- **遥控器友好**：完整 D-pad 导航，焦点记忆
- **返回键屏蔽**：按返回键无法退出桌面
- **开机自启动**
- **Shizuku 加强**（可选）：
  - 强制设为默认桌面
  - 禁用/恢复系统原桌面

## 环境要求

- Android Studio Hedgehog 或更新版本
- minSdk 24
- targetSdk 34
- 推荐在 Android TV / 盒子上运行

## 快速开始

1. 用 Android Studio 打开本项目
2. 同步 Gradle
3. 连接电视盒子或模拟器，运行即可

### 设置为默认桌面

安装后请到系统设置 → 应用 → 默认应用 → 桌面，将本应用设为默认主屏幕。

或使用 Shizuku 的「强制设为默认桌面」功能。

### Shizuku 使用（可选）

1. 安装并启动 [Shizuku](https://shizuku.rikka.app/)
2. 在本应用顶部「Shizuku」分类中授权
3. 即可使用强制默认桌面、禁用系统桌面等功能

## 项目结构

```
app/src/main/
├── java/com/tv/launcher/
│   ├── MainActivity.java      # 主界面逻辑
│   └── BootReceiver.java      # 开机自启动
├── res/
│   ├── layout/                # 布局文件
│   ├── drawable/              # 背景与焦点样式
│   └── values/                # 字符串、主题等
└── AndroidManifest.xml
```

## 遥控器操作

| 按键       | 功能                     |
|------------|--------------------------|
| 方向键     | 在应用 / 最近使用间移动  |
| OK / 确认  | 打开应用                 |
| 返回键     | 已屏蔽，不会退出桌面     |
| 菜单键     | 打开 Shizuku 功能菜单    |
| 长按应用   | 打开 Shizuku 功能菜单    |

## 壁纸

顶部「壁纸」分类可：

- 从相册选择图片作为壁纸（带切换动画）
- 清除壁纸
- 调整遮罩浓度（保证文字清晰）

## License

MIT License
