# 魔法单词本 Android 3.28 源代码

此目录对应已提供的 3.28 测试版。页面代码以相对 3.17 的补丁保存，插画、词库和 Android 外壳从仓库内的 3.17 APK 读取。

在仓库根目录使用 Python 3 运行以下命令，即可生成完整、可编辑的 HTML 源文件及未签名安装包。

```sh
python3 android/src/3.28/build.py
```

生成的 `android/src/3.28/build/index.html` 包含全部页面、样式、JavaScript 和内置资源。可以直接把这个文件交给其他编辑器继续开发。修改后重新打包可使用以下命令。

```sh
python3 android/src/3.28/build.py --html android/src/3.28/build/index.html --out android/src/3.28/build-edited
```

APK 需另行使用 Android 的 apksigner 签名后安装。覆盖安装现有 App 时需使用原签名密钥，密钥由维护者单独保管。

本次累计更新包括词条详细释义、词性及分隔线，搭配与词族中文展示，单词本搜索完整词匹配与连字符兼容，列表下拉查词，以及最长 90 天的递增复习间隔和「不认识」自动朗读。
