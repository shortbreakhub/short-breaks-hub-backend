# ECS properties secret bootstrap

The container accepts the complete `.properties` text in `APP_CONFIG_PROPERTIES`.
The entrypoint runs as the existing non-root `appuser`, disables shell tracing,
creates a private `0700` directory under `/tmp`, and writes the exact text to a
`0600` `application.properties` file. Comments, blank lines, escapes and
continuations are preserved; the text is not evaluated by a shell or converted
from JSON. The payload is never placed in Java command-line arguments.

The entrypoint unsets the payload before Java starts and appends the temporary
file to `SPRING_CONFIG_ADDITIONAL_LOCATION`. Other additional locations and the
normal Spring configuration locations remain available. Environment variables
and command-line settings retain their normal higher precedence; do not provide
conflicting Spring config-location flags. See [Spring Boot configuration](https://docs.spring.io/spring-boot/3.3/reference/features/external-config.html).

Without `APP_CONFIG_PROPERTIES`, the existing Java invocation, logging and local
configuration behavior remain. A present-but-empty value fails closed. Missing
write access to `/tmp` also fails before Java starts, with a generic message.
This is runtime injection only; the production properties file remains excluded
from the build context and JAR. The Docker allowlist admits just the required
entrypoint script in addition to the existing Maven inputs, not its sibling files.

## Logging and lifecycle limitations

Java stdout/stderr is inherited normally in both modes so the existing ECS log
driver can collect Spring startup, database, Flyway and application diagnostics.
The entrypoint never prints the payload or environment, disables shell tracing
before referencing the secret, and emits only generic bootstrap/lifecycle errors.
It preserves Java's exit status. There is no blanket log filter or suppression.

Residual risk: arbitrary application exceptions, Spring binding errors and
third-party libraries can still log individual sensitive values. For example,
an invalid secret used as a numeric property can appear in a conversion error.
The tests verify selected paths, not universal application redaction. Do not
enable configuration/environment dumps, verbose mail/HTTP credential logging,
or unsecured diagnostic endpoints. Keep secrets out of connection URLs and
non-secret numeric fields; validate the document without displaying values.
Restrict CloudWatch access/retention and review library logging separately.
Bootstrap code cannot safely sanitize every arbitrary application message.

TERM/INT/HUP are forwarded to Java; after shutdown or startup failure the private
directory is removed. No secret file is created at build time. SIGKILL, host loss
or a forced timeout cannot run cleanup: a runtime writable-layer file can remain
until the container is removed. Use an ephemeral writable `/tmp`; a memory-backed
mount can further reduce disk exposure where supported. A read-only root filesystem
requires a writable temporary mount. Never commit/export a running production
container as an image. Allow time for JVM graceful shutdown before forced killing.

Unsetting the variable reduces Java environment exposure but does not erase the
initial ECS/container metadata, supervisor memory or privileged process access.
Restrict ECS Exec, host/debug privileges and runtime environment inspection.

## ECS owner configuration (not applied by this change)

Region: `us-east-1`. Secret name:
`shortbreakhub/production/application-properties`. Its SecretString must be the
complete standard `.properties` document, not JSON or a quoted/escaped document.
Copy the actual full secret ARN, including its generated suffix, into the
existing container definition:

```json
{
  "secrets": [
    {
      "name": "APP_CONFIG_PROPERTIES",
      "valueFrom": "<full ARN of shortbreakhub/production/application-properties in us-east-1>"
    }
  ]
}
```

Do not put the payload in the ordinary `environment` list, Docker ARG/ENV, an
image build command or source control. No JSON-key selector is used. ECS injects
the value when a task starts; changing the secret requires fresh tasks. Check
supported platform/agent versions and access to the Secrets Manager endpoint.
See [AWS ECS secret injection](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/secrets-envvar-secrets-manager.html).

The **Task Execution Role**, used by the ECS agent, needs
`secretsmanager:GetSecretValue` scoped to that secret's actual ARN. With a
customer-managed KMS key, also grant `kms:Decrypt` for its actual key ARN and
ensure its key policy permits the role. Do not grant wildcard secrets access.
The application's Task Role is separate; retain existing SES permissions.
No Secrets Manager SDK/bootstrap permissions are required inside this application.
See [AWS task execution permissions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html).

## Owner deployment checklist

1. Review the code, logging boundaries and tests before approving commit/push.
2. Verify the secret contains every required production property. Confirm the
   database/schema and previously applied V10 checksum separately. No production
   secret or schema was inspected by this task.
3. Verify the actual secret ARN, execution-role/key permissions, agent/platform
   support, secret size and endpoint connectivity without printing its value.
4. Preserve the working task definition, immutable image reference and compatible
   secret version for rollback. Existing runtime variables may override file
   values; review them for conflicts without displaying secrets.
5. After separate deployment authorization, select the reviewed image and add
   the secret reference to the existing container. Preserve infrastructure,
   sender identities, task role and unrelated settings.
6. Ensure non-root write access to temporary storage, appropriate graceful-stop
   time and health-check grace period. Check health/readiness and stopped-task
   reasons without dumping the environment or file.
7. Deploy fresh tasks whenever the secret changes; existing tasks do not reload it.

## Rollback

The owner can return the service to the preserved working task-definition/image
revision using the existing release procedure. If that revision uses a different
configuration mechanism, retain its compatible settings; do not add secrets to
an image to make rollback work. Restore/pin an approved compatible secret version
when needed, and start fresh tasks. Keep the additive V10 table and ownership
records; do not repair or roll back Flyway as part of this bootstrap rollback.
Remove stopped containers/runtime storage through the normal ECS lifecycle.
No rollback, infrastructure change or deployment was performed here.

## Automated local checks

```sh
docker build -t shortbreakhub-api:ecs-config-bootstrap .
python3 scripts/check-docker-secret-exclusion.py --image shortbreakhub-api:ecs-config-bootstrap
docker pull postgres:16
python3 scripts/check-ecs-config-bootstrap.py --image shortbreakhub-api:ecs-config-bootstrap --runtime
./mvnw -q -DskipTests=false test
```

The bootstrap checker uses synthetic payloads only, never a real secret. Eight
container probes check absent/present config, exact text and permissions, existing
locations, child failures, empty config, signal forwarding, unwritable storage and
tracing, and preservation of Java stdout/stderr. Three real-app checks run on
disposable PostgreSQL behind an internal Docker network: successful startup with
Spring/Flyway logs and health, non-secret numeric binding failure, and database
authentication failure with a synthetic password marker. They assert useful
diagnostics remain, no bulk config/marker is logged, and cleanup occurs. Temporary
test containers/network are removed. GitHub Actions also runs these checks.
