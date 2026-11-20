# SC LIVE 2 Android build status

- Working branch: `sc-live2-rank-stems-browser-rec`.
- Reference APK: build #151; do not describe a later APK as feature-complete without device validation.
- GitHub Actions builds a debug APK on pushes to this working branch.
- The build now includes a Browser-session regression check: D1/D2 hide the dialog while retaining the same WebView; reopening must not reload the page; CERRAR must destroy and unpin the session.
- This static regression check does **not** validate YouTube audio processing through SC RACK/DriverRack, playback after Android process death, or device-specific background behavior. Those remain unimplemented/unverified.
- Keep `sc-live2-punto-gasolina` unchanged.
