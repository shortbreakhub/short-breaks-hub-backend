# Docker build secret exclusion

Build from a checkout with the committed `.dockerignore`. Its allowlist admits
only `pom.xml`, `src` and the specific `docker/entrypoint.sh`; explicit exclusions within `src` remove properties files,
local/production profile configuration, environment files, credential files,
private-key/keystore files and nested build/editor output. The legitimate
`application-destination-import.yml`, migrations, templates and seed data remain.
The local `src/main/resources/application.properties` is neither changed nor
committed. Docker applies these rules before sending files to the builder, so
`COPY src ./src` cannot see that file and Maven cannot package it in the JAR.

Run the Docker regression check and build locally:

```sh
python3 scripts/check-docker-secret-exclusion.py
docker build -t shortbreakhub-api:secret-exclusion-check .
python3 scripts/check-docker-secret-exclusion.py --image shortbreakhub-api:secret-exclusion-check
```

The check exports committed Maven inputs into a disposable directory, adds only
synthetic sensitive-file canaries, and exercises Docker's actual ignore rules.
It checks required files survive filtering and sensitive files/content do not.
With `--image`, it scans every saved final-image layer, application JAR resources
and image metadata, including files removed by a later layer. It never starts the
application or reads/copies the local credentials file. GitHub Actions runs the
context and final-image checks on PRs and pushes to master; it does not publish
images or deploy. These checks detect forbidden paths and synthetic canaries;
they are not a universal scanner for secrets deliberately placed in Java or JSON.
The check also rejects a Dockerfile-specific ignore override and populated
credential-like environment variables baked into the final image.

## Runtime configuration

The Dockerfile continues to set `SPRING_PROFILES_ACTIVE=prod`. Spring Boot can bind
the existing properties from runtime environment variables; no credentials are
needed to compile the image. Alternatively, ECS can inject the complete properties
document as described in [ECS bootstrap](ecs-properties-bootstrap.md). Confirm the
existing ECS environment or injected document supplies the equivalent settings:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SECURITY_JWT_SECRET`, `SECURITY_JWT_EXPIRYMS`
- `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`
- `APP_PUBLIC_BASE_URL`, `CONTACT_TO_EMAIL`, `CONTACT_FROM_EMAIL`
- Existing SMTP settings, including `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`,
  `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` and required SMTP properties.
- Existing schema/Flyway settings, including safe
  `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` where required by the deployment.

SES continues to use its existing region and AWS SDK credential chain. Existing
ECS task-role permissions and sender identities must be retained. Do not inject
AWS credentials during a build or put secrets in Docker ARG/ENV declarations.

The actual production ECS environment and previously applied V10 checksum/schema
remain owner verification steps; a successful local build does not verify them.
Previously built images may still contain configuration secrets. This fix does
not remove old images/layers or rotate credentials; owners should assess prior
image exposure and credential rotation separately. No AWS changes are included.

## Local validation

- Full Maven suite with tests explicitly enabled: 196 passed, zero failures,
  errors or skips; persistence used embedded PostgreSQL only.
- Docker context canaries and all final-image layer/JAR checks passed.
- The production-profile image started using only synthetic runtime variables
  and disposable PostgreSQL on an internal Docker network. Its local health
  endpoint returned HTTP 200; Flyway ran only on that empty disposable database.
  The test containers and network were removed afterward.
- The ignored local credentials file remained unchanged. No image was pushed,
  no production database was contacted and no deployment was performed.
