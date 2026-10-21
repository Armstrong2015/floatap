# 浮序 Windows

Python 3 + tkinter + SendInput。作业 JSON 与安卓 `format: 1` 一致。

## 开发运行

```powershell
cd windows
python -m unittest discover -s tests -v
python main.py
```

## 打包

```powershell
.\build_exe.ps1
```

产物：`dist/floatap-windows-1.0.0.exe`，并复制到仓库上级 `.tools/`。
