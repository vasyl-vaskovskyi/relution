# Pins the Prometheus image whose promtool the CI "alerts" job uses to check and
# unit-test ops/alerts.yml (ADR-0050). Dependabot's docker ecosystem updates the
# tag and digest here; a workflow container image or a docker run tag would not be updated.
FROM prom/prometheus:v3.14.0@sha256:5ce7540c3c00ef4ab0c9d2c995c6a5b9c421f44b4a115d97a2c7af3b1c21cbb0
ENTRYPOINT ["promtool"]
