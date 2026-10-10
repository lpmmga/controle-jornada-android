# Controle de Jornada — Android nativo

Projeto Android nativo que mantém a interface do Controle de Jornada em WebView, mas usa `AlarmManager` + notificações Android para os alertas preventivos de 2h extras.

## Alertas
- 10 minutos antes das 2h extras.
- 5 minutos antes das 2h extras.
- Nenhum alerta ao atingir 2h.
- Som e vibração.
- Agendamento exato quando a permissão de alarmes exatos estiver liberada.
- Reagendamento após reinício/atualização do aparelho.

## Build
O workflow `.github/workflows/build-apk.yml` compila o APK automaticamente no GitHub Actions. Depois do build, o APK fica disponível como artifact `controle-jornada-debug-apk`.
