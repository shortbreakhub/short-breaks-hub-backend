#!/bin/sh
# Disable tracing before referencing any injected configuration.
set +x
set -eu

if [ "${APP_CONFIG_PROPERTIES+x}" != x ]; then
    exec java -jar /app/app.jar "$@"
fi

fail() {
    printf '%s\n' 'Configuration bootstrap failed; secret details suppressed.' >&2
    exit 78
}

[ -n "$APP_CONFIG_PROPERTIES" ] || fail
umask 077
config_dir=$(mktemp -d /tmp/shortbreakhub-config.XXXXXX 2>/dev/null) || fail
cleanup() {
    if ! rm -rf -- "$config_dir" 2>/dev/null; then
        printf '%s\n' 'Temporary configuration cleanup failed; secret details suppressed.' >&2
    fi
}
trap cleanup EXIT
child=''
terminate() {
    trap '' TERM INT HUP
    if [ -n "$child" ]; then
        kill -TERM "$child" 2>/dev/null || :
        wait "$child" 2>/dev/null || :
    fi
    exit "$1"
}
trap 'terminate 143' TERM
trap 'terminate 130' INT
trap 'terminate 129' HUP
config_file="$config_dir/application.properties"
# printf writes the exact environment text, without shell expansion or evaluation.
if ! printf '%s' "$APP_CONFIG_PROPERTIES" > "$config_file" 2>/dev/null; then
    fail
fi
chmod 0600 "$config_file" 2>/dev/null || fail
unset APP_CONFIG_PROPERTIES

SPRING_CONFIG_ADDITIONAL_LOCATION="${SPRING_CONFIG_ADDITIONAL_LOCATION:+$SPRING_CONFIG_ADDITIONAL_LOCATION,}file:$config_file"
export SPRING_CONFIG_ADDITIONAL_LOCATION

# Preserve application diagnostics; the bootstrap itself never prints configuration.
java -jar /app/app.jar "$@" &
child=$!
status=0
wait "$child" || status=$?
if [ "$status" -ne 0 ]; then
    printf '%s\n' 'Application exited unsuccessfully.' >&2
fi
exit "$status"
