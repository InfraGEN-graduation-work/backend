#!/usr/bin/env bash
set -euo pipefail

: "${DEPLOY_ID:?DEPLOY_ID is required}"

DEPLOY_ROOT="${DEPLOY_ROOT:-$HOME/infragen}"
ROLLBACK_DIR="$DEPLOY_ROOT/.rollback/$DEPLOY_ID"
COMPOSE_COMMAND=()

select_compose_command() {
    if docker compose version >/dev/null 2>&1; then
        COMPOSE_COMMAND=(docker compose)
    elif docker-compose version >/dev/null 2>&1; then
        COMPOSE_COMMAND=(docker-compose)
    else
        echo "Docker Compose v2와 v1을 모두 찾을 수 없습니다."
        exit 1
    fi
}

run_compose() {
    "${COMPOSE_COMMAND[@]}" "$@"
}

prepare_rollback() {
    cd "$DEPLOY_ROOT"

    if [ -e "$ROLLBACK_DIR" ]; then
        echo "이번 배포의 rollback 백업이 이미 존재합니다."
        exit 1
    fi

    for required_file in docker-compose.yaml .env nginx/default.conf; do
        if [ ! -f "$required_file" ]; then
            echo "현재 배포에 필요한 파일이 없습니다."
            exit 1
        fi
    done

    current_image_id="$(docker inspect --format '{{.Image}}' infragen-app 2>/dev/null || true)"
    current_image_reference="$(
        docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$current_image_id" 2>/dev/null \
            | awk '/@sha256:/ {print; exit}' \
            || true
    )"
    current_digest="${current_image_reference##*@}"

    case "$current_digest" in
        sha256:*) ;;
        *)
            echo "현재 애플리케이션에 immutable image digest가 없습니다."
            exit 1
            ;;
    esac

    mkdir -p "$ROLLBACK_DIR/nginx"
    cp -p docker-compose.yaml "$ROLLBACK_DIR/docker-compose.yaml"
    cp -p .env "$ROLLBACK_DIR/.env"
    cp -p nginx/default.conf "$ROLLBACK_DIR/nginx/default.conf"
    printf '%s\n' "$current_image_reference" > "$ROLLBACK_DIR/image-reference"
    printf '%s\n' "$current_digest" > "$ROLLBACK_DIR/image-digest"
    printf '%s\n' prepared > "$ROLLBACK_DIR/status"
    chmod 600 "$ROLLBACK_DIR/.env" "$ROLLBACK_DIR/image-reference" "$ROLLBACK_DIR/image-digest"
    echo "Rollback 백업을 준비했습니다."
}

wait_for_health() {
    echo "infragen-app 기동을 기다리는 중입니다..."
    for attempt in 1 2 3 4 5 6 7 8 9 10; do
        response="$(curl --silent --show-error --max-time 5 http://127.0.0.1:8080/health || true)"
        if [ "$response" = "UP" ]; then
            return 0
        fi
        if [ "$attempt" -lt 10 ]; then
            sleep 15
        fi
    done
    return 1
}

print_safe_logs() {
    # 장애 원인 확인에 필요한 범위만 남기고 일반적인 credential 키의 값을 가린다.
    run_compose logs --tail=100 infragen-app 2>&1 \
        | sed -E 's/((password|secret|token|authorization|cookie)[[:space:]]*[=:])[[:space:]]*(Bearer[[:space:]]+)?[^[:space:]]+/\1[REDACTED]/Ig'
}

restore_rollback_files() {
    cp -p "$ROLLBACK_DIR/docker-compose.yaml" docker-compose.yaml
    cp -p "$ROLLBACK_DIR/.env" .env
    cp -p "$ROLLBACK_DIR/nginx/default.conf" nginx/default.conf
}

rollback() {
    printf '%s\n' rollback-started > "$ROLLBACK_DIR/status"
    echo "배포에 실패했습니다. 서비스 상태와 마스킹된 제한 로그를 수집합니다."
    run_compose ps || true
    print_safe_logs || true

    restore_rollback_files
    chmod 600 .env
    export IMAGE_DIGEST="$(sed -n '1p' "$ROLLBACK_DIR/image-digest")"

    rollback_compose_file="$ROLLBACK_DIR/rollback-compose.yaml"
    printf '%s\n' \
        'services:' \
        '  infragen-app:' \
        '    image: ${DOCKERHUB_USERNAME}/infragen-app@${IMAGE_DIGEST:?IMAGE_DIGEST is required}' \
        > "$rollback_compose_file"

    rollback_failed=0
    if ! run_compose -f docker-compose.yaml -f "$rollback_compose_file" pull infragen-app; then
        echo "Rollback image pull에 실패했습니다."
        rollback_failed=1
    fi
    if [ "$rollback_failed" -eq 0 ] \
        && ! run_compose -f docker-compose.yaml -f "$rollback_compose_file" up -d --force-recreate; then
        echo "Rollback 서비스 재기동에 실패했습니다."
        rollback_failed=1
    fi

    if [ "$rollback_failed" -eq 0 ] && wait_for_health; then
        printf '%s\n' rollback-succeeded > "$ROLLBACK_DIR/status"
        echo "Rollback health check를 통과했습니다."
        return 1
    fi

    printf '%s\n' rollback-failed > "$ROLLBACK_DIR/status"
    echo "Rollback health check에 실패했습니다. 수동 조치가 필요합니다."
    run_compose ps || true
    print_safe_logs || true
    return 1
}

deploy() {
    cd "$DEPLOY_ROOT"
    : "${DOCKERHUB_USERNAME:?DOCKERHUB_USERNAME is required}"
    : "${IMAGE_DIGEST:?IMAGE_DIGEST is required}"

    if [ ! -f "$ROLLBACK_DIR/image-digest" ]; then
        echo "Rollback 백업이 없습니다."
        exit 1
    fi

    select_compose_command
    export DOCKERHUB_USERNAME IMAGE_DIGEST
    chmod 600 .env
    printf '%s\n' deploying > "$ROLLBACK_DIR/status"

    deploy_failed=0
    if ! run_compose pull infragen-app; then
        echo "Immutable image pull에 실패했습니다."
        deploy_failed=1
    fi
    if [ "$deploy_failed" -eq 0 ] && ! run_compose up -d; then
        echo "서비스 재기동에 실패했습니다."
        deploy_failed=1
    fi

    if [ "$deploy_failed" -eq 0 ] && wait_for_health; then
        printf '%s\n' succeeded > "$ROLLBACK_DIR/status"
        echo "Health check를 통과했습니다."
        return 0
    fi

    rollback
}

case "${1:-}" in
    prepare)
        prepare_rollback
        ;;
    deploy)
        deploy
        ;;
    *)
        echo "사용법: $0 {prepare|deploy}" >&2
        exit 2
        ;;
esac
