# bcar-kotlin

개인용 자동화 배치 앱.

## 문서

- [앱 실행 가이드](docs/app-run.md)
- [Terraform 운영 가이드](docs/infra-terraform.md)
- [Secrets Manager 운영 가이드](docs/secrets-manager.md)
- [CI/CD 및 이미지 푸시 가이드](docs/ci-cd.md)


## Quick Start

```bash
./gradlew playwrightInstall
./gradlew bootRun --args="--job=collect-draft --next=false --spring.profiles.active=local"
```

실행 인자/잡 목록/운영 시나리오는 [앱 실행 가이드](docs/app-run.md) 참고.
