# 浮序 Windows 1.0.1

紧急修复：1.0.0 打开后可能让整机鼠标失灵。

安装包：[floatap-windows-1.0.1.exe](https://github.com/Armstrong2015/floatap/releases/download/v-windows-1.0.1/floatap-windows-1.0.1.exe)

请不要再使用 1.0.0。若 1.0.0 仍在运行且鼠标异常，用 Ctrl+Alt+Del 打开任务管理器结束「浮序」或 `floatap-windows-1.0.0.exe`，一般即可恢复，不必重启。

## 修复内容

- 去掉启动时安装的全局低级鼠标钩子（WH_MOUSE_LL）
- 「用户按下则暂停」改为安全的按键状态轮询，默认关闭

其余能力与 1.0.0 相同。
