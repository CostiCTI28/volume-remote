@echo off
echo Adaug regula de firewall pentru portul 5050...
netsh advfirewall firewall add rule name="VolumeAgent" dir=in action=allow protocol=TCP localport=5050 >nul 2>&1

echo Instalez dependintele Python (o singura data)...
pip install -r "%~dp0requirements.txt" >nul 2>&1

echo Pornesc VolumeAgent...
pythonw "%~dp0volume_agent.py"
echo Terminat. Agentul ruleaza in fundal (verifica Task Manager: pythonw.exe).
pause
