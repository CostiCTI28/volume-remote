# Volume Remote — control volumul laptopului de pe telefon

Doua parti:

1. **windows-agent/** — un mic server care ruleaza pe laptop (Windows) si primeste comenzi de volum. Se compileaza automat in `.exe` prin GitHub Actions.
2. **android-app/** — aplicatia de telefon (Android) cu un slider de volum. Se compileaza automat in `.apk` prin GitHub Actions.

Presupunere: laptopul si telefonul sunt pe **aceeasi retea WiFi** (nu peste internet). Daca vrei si de pe 4G/afara de acasa, spune-mi si adaptam (VPN/Tailscale sau relay).

## 1. Pune codul pe GitHub

```
cd volume-remote
git init
git add .
git commit -m "initial"
git branch -M main
git remote add origin https://github.com/<user_tau>/volume-remote.git
git push -u origin main
```

La primul push, cele 2 workflow-uri (`.github/workflows/build-windows.yml` si `build-android.yml`) se declanseaza automat.

## 2. Ia executabilele compilate

Pe GitHub: tab **Actions** → alege rularea → sectiunea **Artifacts**:
- `VolumeAgent-windows` → contine `VolumeAgent.exe` + `install_windows_agent.bat`
- `VolumeRemote-apk` → contine `app-debug.apk`

## 3. Instaleaza pe laptop (Windows)

1. Descarca si dezarhiveaza `VolumeAgent-windows`.
2. Ruleaza `install_windows_agent.bat` (deschide portul 5050 in firewall si porneste agentul).
3. In consola apare IP-ul laptopului, ex: `Volume Agent ruleaza pe 192.168.1.100:5050`. Noteaza-l.
4. (Optional) Pentru pornire automata la boot: copiaza folderul in `shell:startup` (Win+R → scrie `shell:startup` → Enter).

Token-ul implicit e `schimba-ma`, definit in `volume_agent.py` (variabila `TOKEN`). Schimba-l inainte de compilare pentru siguranta.

## 4. Instaleaza pe telefon (Android)

1. Descarca `app-debug.apk` pe telefon.
2. Activeaza "Instalare din surse necunoscute" pentru fisierul respectiv (Android intreaba automat la instalare).
3. Deschide aplicatia, introdu IP-ul si portul laptopului (ex: `192.168.1.100:5050`) si token-ul.
4. Miscarea slider-ului schimba volumul laptopului in timp real.

## Note

- Daca laptopul isi schimba IP-ul (DHCP), fie ii setezi IP static in router, fie actualizezi IP-ul in aplicatie.
- `VolumeAgent.exe` ruleaza fara fereastra (`--noconsole`); pentru a-l inchide, foloseste Task Manager → cauta `VolumeAgent.exe`.
