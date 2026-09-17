@echo off
echo Adaug regula de firewall pentru portul 5050...
netsh advfirewall firewall add rule name="VolumeAgent" dir=in action=allow protocol=TCP localport=5050 >nul 2>&1
echo Pornesc VolumeAgent...
start "" "%~dp0VolumeAgent.exe"
echo Terminat. Agentul ruleaza in fundal.
echo Pentru pornire automata la boot, copiaza acest folder in:
echo shell:startup  (Win+R -> shell:startup)
pause
