#!/usr/bin/env python3
"""Exercise Docker's actual ignore rules and optionally inspect every image layer/JAR.

Only tracked files and synthetic canaries are used in the fixture. Local secrets
are never read or copied. Requires Docker; never starts the application.
"""
import argparse
import io
import json
import pathlib
import subprocess
import tarfile
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
CANARY = b"SYNTHETIC_DOCKER_SECRET_EXCLUSION_CANARY"
FORBIDDEN = (
    "src/main/resources/application.properties",
    "src/main/resources/application-local.yml",
    "src/main/resources/application-prod.yaml",
    "src/main/resources/.env.production",
    "src/main/resources/credentials.json",
    "src/main/resources/secrets/token.txt",
    "src/main/resources/client.pem",
    "src/main/resources/client.key",
    "src/main/resources/client.p12",
    "src/main/resources/client.pfx",
    "src/main/resources/client.jks",
    "src/main/resources/.aws/credentials",
    "src/main/resources/.ssh/id_rsa",
    "src/main/resources/target/old.jar",
    "src/test/.idea/workspace.xml",
    ".env", ".env.local", ".git/config", ".aws/credentials",
    ".ssh/id_rsa", ".idea/workspace.xml", ".vscode/settings.json",
    "target/old.jar", "build/old.jar", "node_modules/package.json",
)


def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def check_context(work):
    assert not (ROOT / "Dockerfile.dockerignore").exists(), "Dockerfile-specific ignore rules can override the audited root rules"
    fixture = work / "context"
    fixture.mkdir()
    # Export committed Maven inputs, not the secret-bearing local working tree.
    tracked = subprocess.check_output(["git", "ls-files", "-z", "pom.xml", "src"], cwd=ROOT)
    for name in tracked.decode().split("\0"):
        if not name:
            continue
        assert name != "src/main/resources/application.properties", "Local properties must never be tracked"
        destination = fixture / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(subprocess.check_output(["git", "show", "HEAD:" + name], cwd=ROOT))
    entrypoint = ROOT / "docker/entrypoint.sh"
    if entrypoint.exists():
        (fixture / "docker").mkdir()
        (fixture / "docker/entrypoint.sh").write_bytes(entrypoint.read_bytes())
    (fixture / "docker/.env").parent.mkdir(exist_ok=True)
    (fixture / "docker/.env").write_bytes(CANARY)
    (fixture / ".dockerignore").write_bytes((ROOT / ".dockerignore").read_bytes())
    for name in FORBIDDEN:
        destination = fixture / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(CANARY)
    exported = work / "exported"
    run("docker", "build", "--file", "-", "--output", "type=local,dest=" + str(exported),
        str(fixture), input=b"FROM scratch\nCOPY . /\n")
    for name in FORBIDDEN:
        assert not (exported / name).exists(), "Sensitive/unnecessary path entered context: " + name
    assert not (exported / "docker/.env").exists(), "Entrypoint exception admitted a secret sibling"
    if entrypoint.exists():
        assert (exported / "docker/entrypoint.sh").is_file(), "Entrypoint excluded"
    for name in ("pom.xml", "src/main/java/com/shortbreakshub/ShortbreakhubApplication.java",
                 "src/main/resources/db/migration/V10__add_draft_cover_upload_ownership.sql",
                 "src/main/resources/application-destination-import.yml"):
        assert (exported / name).is_file(), "Required Maven input excluded: " + name
    for path in exported.rglob("*"):
        if path.is_file():
            assert CANARY not in path.read_bytes(), "Synthetic secret entered context"
    print("PASS: Docker excludes sensitive canaries and preserves required build inputs")


def sensitive_name(name):
    path = pathlib.PurePosixPath(name)
    return (path.name.startswith(".env") or path.name.startswith("credentials")
            or any(part in {".aws", ".ssh", "secrets"} for part in path.parts)
            or path.name == "application.properties"
            or path.name.startswith(("application-local.", "application-prod.", "application-production."))
            or (name.startswith("BOOT-INF/classes/") and path.suffix in {".pem", ".key", ".p12", ".pfx", ".jks"}))


def check_image(work, image):
    archive = work / "image.tar"
    run("docker", "image", "save", "--output", str(archive), image)
    jars = 0
    with tarfile.open(archive) as saved:
        # Docker archives may use layer.tar or OCI content-addressed blobs.
        for member in saved:
            if not member.isfile():
                continue
            stream = saved.extractfile(member)
            try:
                layer = tarfile.open(fileobj=stream, mode="r|*")
            except tarfile.ReadError:
                continue
            with layer:
                for entry in layer:
                    if not entry.isfile():
                        continue
                    assert not sensitive_name(entry.name), "Sensitive path in image layer: " + entry.name
                    if entry.name.endswith("/app.jar") or entry.name == "app.jar":
                        jars += 1
                        data = layer.extractfile(entry).read()
                        assert CANARY not in data, "Synthetic secret in application JAR"
                        with zipfile.ZipFile(io.BytesIO(data)) as jar:
                            for name in jar.namelist():
                                if name.startswith("BOOT-INF/classes/"):
                                    assert not sensitive_name(name), "Sensitive application resource: " + name
                                    assert CANARY not in jar.read(name), "Synthetic secret in application resource"
                            assert "BOOT-INF/classes/db/migration/V10__add_draft_cover_upload_ownership.sql" in jar.namelist()
    assert jars > 0, "Application JAR not found; image check cannot pass"
    metadata = subprocess.check_output(["docker", "image", "inspect", image])
    assert CANARY not in metadata, "Synthetic secret in image metadata"
    for variable in json.loads(metadata)[0]["Config"].get("Env", []):
        name, _, value = variable.partition("=")
        if name == "APP_CONFIG_PROPERTIES" or any(marker in name.upper() for marker in ("PASSWORD", "SECRET", "TOKEN", "API_KEY", "ACCESS_KEY")):
            assert not value, "Credential-like environment variable baked into image: " + name
    print("PASS: all final-image layers and application JAR exclude sensitive configuration")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", help="Optional locally built image to inspect (no application startup)")
    options = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="docker-secret-check-") as directory:
        work = pathlib.Path(directory)
        check_context(work)
        if options.image:
            check_image(work, options.image)
