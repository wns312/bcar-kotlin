# 모듈 구조

`core`, `infrastructure`, `app` 세 개의 Gradle 서브모듈로 구성한다.

| 모듈 | 책임 | 프로젝트 의존성 |
| --- | --- | --- |
| `core` | 차량 도메인, 할당·업로드 정책, Job의 업무 흐름, 외부 기능의 인터페이스 | 없음 |
| `infrastructure` | DynamoDB, Google Sheets, AWS Batch, Playwright, HTTP·HTML 구현 | `core` |
| `app` | Spring Boot 실행, CLI 옵션 해석, 설정 바인딩과 객체 조립 | `core`, `infrastructure` |

`core`는 Kotlin, 코루틴, 로깅만 사용한다. Spring, AWS SDK, Playwright, Jackson 등의 타입은 경계를 넘어오지 않는다. 기존 패키지명은 가능한 한 유지하고, 모듈 간 의존성은 Gradle이 제한한다.

## 경계

- `repository` 인터페이스와 `port`는 `core`가 소유한다. 외부 구현은 `infrastructure`에 둔다.
- 수집 Job은 수집 포트에 요청하고, 차량 동기화·저장·체인 종료 여부를 판단한다. HTTP 호출과 HTML 파싱은 어댑터가 담당한다. 상세 수집은 fetch 실패와 parse 실패를 구분해 기존 차단·비활성화 규칙을 유지한다.
- 업로드 Job은 `TargetCarSite.withSession`에서 받은 세션으로 동기화와 여러 차량 업로드를 수행한다. 실제 로그인·브라우저 생명주기는 어댑터가 관리한다. 차량별 상태 저장과 무료 한도 소진·취소 처리는 Job에 남긴다.
- `AssignPolicy`, `UploadPolicy`, `DetailCollectionPolicy`는 일반 Kotlin 값이다. Spring 설정과 CLI 인자는 `app`의 `JobConfiguration`에서 변환한다.
- `CategoryTree` JSON 형식은 DynamoDB 어댑터의 Jackson mixin으로 유지한다.

## 빌드와 실행

JDK 21을 사용한다.

```bash
./gradlew clean build
./gradlew :core:test
./gradlew :infrastructure:test
./gradlew :app:test
./gradlew bootJar
./gradlew playwrightInstall
./gradlew :app:bootRun --args="--job=collect-draft --next=false --spring.profiles.active=local"
```

실행 JAR은 `app/build/libs/bcar-0.0.1.jar`에 생성된다. Docker와 CI는 이 경로를 사용한다. 로컬 설정 파일도 `app/src/main/resources/application-local.yaml`에 둔다.

테스트는 해당 책임의 모듈에 둔다. `core`의 업무 흐름 테스트는 외부 포트의 fake를 사용하고, `infrastructure`는 파서·저장 형식을 검증하며, `app`은 Spring 컨텍스트와 Job 조립을 검증한다.
