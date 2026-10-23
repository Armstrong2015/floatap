$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$name = "floatap-windows-1.0.4"
python -m PyInstaller --noconfirm --clean --onefile --windowed `
  --name $name `
  --paths . `
  --hidden-import floatap_win `
  --hidden-import floatap_win.app `
  --hidden-import floatap_win.engine `
  --hidden-import floatap_win.click `
  main.py
Copy-Item -Force "dist\$name.exe" "..\..\.tools\$name.exe"
Get-Item "..\..\.tools\$name.exe"
