#!/usr/bin/env python3
"""Container regressions using synthetic configuration only; no AWS calls.

Requires a locally built image. --runtime additionally starts disposable PostgreSQL
and the real application on an internal Docker network with no internet access.
"""
import argparse
import hashlib
import io
import os
import pathlib
import subprocess
import tarfile
import tempfile
import time
import uuid

CANARY = "SYNTHETIC_CONFIG_BOOTSTRAP_CANARY"
PAYLOAD = ("# comments and blank lines must survive\n\nprivate.value=" + CANARY
           + "\nescaped.value=one\\=two\\:three\ncontinued.value=first\\\n second\n\n")
FAKE_JAVA = r'''#!/bin/sh
set -eu
umask 022
printf '%s\n' "$@" > /result/args
if [ "${APP_CONFIG_PROPERTIES+x}" = x ]; then
    printf '%s\n' leaked > /result/environment
else
    printf '%s\n' unset > /result/environment
fi
if [ -n "${SPRING_CONFIG_ADDITIONAL_LOCATION:-}" ]; then
    printf '%s\n' "$SPRING_CONFIG_ADDITIONAL_LOCATION" > /result/location
    path=${SPRING_CONFIG_ADDITIONAL_LOCATION##*,}
    path=${path#file:}
    printf '%s\n' "$path" > /result/path
    stat -c '%a' "$path" > /result/file-mode
    stat -c '%a' "$(dirname "$path")" > /result/dir-mode
    id -u > /result/uid
    cat "$path" > /result/contents
fi
case "${PROBE_MODE:-normal}" in
    fail) printf '%s\n' 'synthetic-java-startup-failure' >&2; exit 42 ;;
    hold) trap 'printf stopped > /result/signal; exit 0' TERM INT HUP
          touch /result/ready
          while :; do sleep 0.2; done ;;
    *) printf '%s\n' normal-java-output; printf '%s\n' normal-java-error-output >&2 ;;
esac
'''


def docker(*args, env=None):
    result = subprocess.run(["docker", *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            env=env)
    if result.returncode:
        # Docker error output/command arguments may contain environment values.
        raise AssertionError("Docker operation failed; details suppressed")
    return result.stdout + result.stderr if args and args[0] == "logs" else result.stdout


def exit_code(name):
    return int(docker("wait", name).decode().strip())


def assert_clean(name):
    data = docker("cp", name + ":/tmp/.", "-")
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        assert not any("shortbreakhub-config." in member.name for member in archive), "Temporary configuration not cleaned"


def probes(image, root):
    root.chmod(0o755)
    (root / "java").write_text(FAKE_JAVA)
    (root / "java").chmod(0o555)
    modes = ("absent", "success", "locations", "failure", "empty", "signal", "unwritable", "tracing")
    for mode in modes:
        output = root / mode
        output.mkdir(mode=0o777)
        output.chmod(0o777)  # Disposable synthetic probe output, not the configuration directory.
        name = "config-probe-" + uuid.uuid4().hex[:10]
        args = ["create", "--name", name, "--network", "none", "--mount",
                "type=bind,src=" + str(root) + ",dst=/probe,readonly", "--mount",
                "type=bind,src=" + str(output) + ",dst=/result", "-e",
                "PATH=/probe:/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "-e", "PROBE_MODE=" + ("hold" if mode == "signal" else "fail" if mode == "failure" else "normal")]
        environment = os.environ.copy()
        if mode != "absent":
            environment["APP_CONFIG_PROPERTIES"] = "" if mode == "empty" else PAYLOAD
            args += ["-e", "APP_CONFIG_PROPERTIES"]  # Never put the payload in command arguments.
        if mode == "unwritable":
            args += ["--read-only"]
        if mode == "locations":
            args += ["-e", "SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/missing-bootstrap-test.properties"]
        if mode == "tracing":
            args += ["--entrypoint", "/bin/sh"]
        args += [image]
        if mode == "tracing":
            args += ["-x", "/app/entrypoint.sh"]
        else:
            args += ["--synthetic-argument"]
        try:
            docker(*args, env=environment)
            docker("start", name)
            if mode == "signal":
                for _ in range(100):
                    if (output / "ready").exists():
                        break
                    time.sleep(0.1)
                else:
                    raise AssertionError("Probe did not start")
                docker("kill", "--signal", "TERM", name)
            code = exit_code(name)
            expected = 42 if mode == "failure" else 78 if mode in {"empty", "unwritable"} else 143 if mode == "signal" else 0
            assert code == expected, "Unexpected bootstrap exit code for " + mode
            logs = docker("logs", name).decode()
            assert CANARY not in logs and "private.value" not in logs, "Configuration leaked to logs"
            if mode == "failure":
                assert "synthetic-java-startup-failure" in logs, "Java failure diagnostics suppressed"
            elif mode in {"absent", "success", "locations", "tracing"}:
                assert "normal-java-output" in logs and "normal-java-error-output" in logs, "Java stdout/stderr suppressed"
            assert_clean(name)
            if mode in {"empty", "unwritable"}:
                assert not (output / "args").exists(), "Java launched after bootstrap failure"
            else:
                assert (output / "environment").read_text().strip() == "unset", "Secret passed to Java environment"
                assert CANARY not in (output / "args").read_text(), "Secret passed through Java arguments"
                if mode == "absent":
                    assert b"normal-java-output" in docker("logs", name), "Normal logging changed"
                    assert not (output / "path").exists(), "Absent configuration created a file"
                else:
                    assert (output / "contents").read_bytes() == PAYLOAD.encode(), "Properties syntax/whitespace changed"
                    assert (output / "file-mode").read_text().strip() == "600", "Unsafe file permissions"
                    assert (output / "dir-mode").read_text().strip() == "700", "Unsafe directory permissions"
                    assert int((output / "uid").read_text()) != 0, "Runtime unexpectedly root"
                if mode == "signal":
                    assert (output / "signal").read_text() == "stopped", "Shutdown signal not forwarded"
                if mode == "locations":
                    assert (output / "location").read_text().startswith("optional:file:/missing-bootstrap-test.properties,file:"), "Existing additional locations lost"
            print("PASS: bootstrap " + mode)
        finally:
            docker("rm", "-f", name)


def runtime(image):
    suffix = uuid.uuid4().hex[:10]
    network, db, app = ("config-network-" + suffix, "config-db-" + suffix, "config-app-" + suffix)
    try:
        docker("network", "create", "--internal", network)
        docker("run", "-d", "--name", db, "--network", network, "--network-alias", "test-db",
               "-e", "POSTGRES_PASSWORD=synthetic-local-only", "-e", "POSTGRES_DB=isolated_test", "postgres:16")
        for _ in range(60):
            result = subprocess.run(["docker", "exec", db, "pg_isready", "-U", "postgres"], capture_output=True)
            if result.returncode == 0:
                break
            time.sleep(0.5)
        else:
            raise AssertionError("Disposable PostgreSQL unavailable")
        properties = PAYLOAD + "\n" + "\n".join((
            "spring.datasource.url=jdbc:postgresql://test-db:5432/isolated_test",
            "spring.datasource.username=postgres", "spring.datasource.password=synthetic-local-only",
            "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
            "security.jwt.secret=synthetic-container-key-at-least-thirty-two-characters", "security.jwt.expiryMs=60000",
            "cloudinary.cloud-name=synthetic", "cloudinary.api-key=synthetic", "cloudinary.api-secret=synthetic",
            "app.public-base-url=https://synthetic.invalid", "contact.to-email=recipient@example.invalid",
            "contact.from-email=sender@example.invalid", "spring.mail.host=mail.invalid", "spring.mail.port=2525",
            "spring.mail.username=synthetic", "spring.mail.password=synthetic")) + "\n"
        environment = os.environ.copy()
        environment["APP_CONFIG_PROPERTIES"] = properties
        docker("run", "-d", "--name", app, "--network", network, "-e", "APP_CONFIG_PROPERTIES",
               "-e", "AWS_ACCESS_KEY_ID=synthetic", "-e", "AWS_SECRET_ACCESS_KEY=synthetic",
               "-e", "AWS_EC2_METADATA_DISABLED=true", image, env=environment)
        health = "exec 3<>/dev/tcp/127.0.0.1/8080; printf 'GET /api/health HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3; cat <&3"
        for _ in range(120):
            result = subprocess.run(["docker", "exec", app, "bash", "-c", health], capture_output=True)
            if b"HTTP/1.1 200" in result.stdout:
                break
            if docker("inspect", "--format", "{{.State.Running}}", app).strip() == b"false":
                raise AssertionError("Synthetic application startup failed; test logs withheld to avoid dumping configuration")
            time.sleep(0.5)
        else:
            raise AssertionError("Synthetic health check timed out")
        # Read only the synthetic runtime file; compare its hash, never print it.
        data = docker("exec", app, "sh", "-c", "cat /tmp/shortbreakhub-config.*/application.properties")
        assert hashlib.sha256(data).digest() == hashlib.sha256(properties.encode()).digest()
        logs = docker("logs", app)
        assert b"Started ShortbreakhubApplication" in logs, "Spring startup diagnostics suppressed"
        assert b"Successfully applied" in logs, "Flyway migration diagnostics suppressed"
        assert CANARY.encode() not in logs and b"private.value" not in logs, "Configuration dumped during startup"
        docker("stop", "--time", "20", app)
        assert_clean(app)
        print("PASS: real production-profile startup from injected properties; isolated DB, health 200 and cleanup")
        docker("rm", app)
        failures = (
            ("binding", properties.replace("security.jwt.expiryMs=60000", "security.jwt.expiryMs=not-a-number"), b"not-a-number"),
            ("database", properties.replace("spring.datasource.password=synthetic-local-only", "spring.datasource.password=" + CANARY), b"password authentication failed"),
        )
        for label, config, diagnostic in failures:
            environment["APP_CONFIG_PROPERTIES"] = config
            docker("run", "-d", "--name", app, "--network", network, "-e", "APP_CONFIG_PROPERTIES",
                   "-e", "AWS_ACCESS_KEY_ID=synthetic", "-e", "AWS_SECRET_ACCESS_KEY=synthetic",
                   "-e", "AWS_EC2_METADATA_DISABLED=true", image, env=environment)
            for _ in range(120):
                if docker("inspect", "--format", "{{.State.Running}}", app).strip() == b"false":
                    break
                time.sleep(0.5)
            else:
                raise AssertionError("Invalid synthetic configuration did not fail startup")
            assert exit_code(app) != 0
            logs = docker("logs", app)
            assert diagnostic in logs, "Relevant startup failure diagnostics suppressed"
            assert CANARY.encode() not in logs and b"private.value" not in logs, "Synthetic secret/configuration leaked to logs"
            assert_clean(app)
            docker("rm", app)
            print("PASS: real " + label + " failure preserves diagnostics without synthetic secret/config dump; cleanup verified")
    finally:
        for name in (app, db):
            subprocess.run(["docker", "rm", "-f", name], capture_output=True)
        subprocess.run(["docker", "network", "rm", network], capture_output=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", required=True)
    parser.add_argument("--runtime", action="store_true")
    options = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="ecs-config-probe-") as directory:
        probes(options.image, pathlib.Path(directory))
    if options.runtime:
        runtime(options.image)
