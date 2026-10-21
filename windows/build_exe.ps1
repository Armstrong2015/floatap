$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
python -m PyInstaller --noconfirm --clean --onefile --windowed `
  --name floatap-windows-1.0.0 `
  --paths . `
  --hidden-import floatap_win `
  --hidden-import floatap_win.app `
  --hidden-import floatap_win.engine `
  --hidden-import floatap_win.click `
  main.py
Copy-Item -Force "dist\floatap-windows-1.0.0.exe" "..\..\.tools\floatap-windows-1.0.0.exe"
Get-Item "..\..\.tools\floatap-windows-1.0.0.exe"
