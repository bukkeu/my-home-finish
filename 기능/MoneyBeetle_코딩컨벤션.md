# 코딩 컨벤션

> 💡 **팀원들 간에 코드 작성 스타일을 맞추기 위한 문서입니다.** 네이밍 컨벤션, 패키지 구조, DTO & Entity 규칙, 코드 스타일을 정합니다. 개발 전에 읽고, 코드 리뷰에서 이 문서를 기준으로 봅니다.

**기준의 우선순위**

| 무엇을 정하는가 | 기준 문서 | 충돌하면 |
| --- | --- | --- |
| **기능·규칙·데이터** (무엇을 만들지, 어떤 규칙인지, 테이블·API 형태) | 요구사항 명세서, 정책 정의서, DB 설계, API 명세서, 화면 설계 (`MoneyBeetle_*.md`) | 이 문서가 아니라 **md 문서를 따른다**. 문서가 틀렸으면 코드를 맞추지 말고 문서를 먼저 고친다 |
| **코드를 쓰는 방식** (계층, 네이밍, Entity·DTO·Policy 배치, 보안 구조) | 학습한 구조 + `moneybeetle/` 골격 | 이 문서를 따른다 |

**읽는 법**

- 기준: 부트캠프에서 학습한 구조(Java → JDBC → Servlet → Spring Core → Spring Web MVC → JPA → Spring Security)와, 팀에서 만든 `moneybeetle/` 프로젝트 골격(`moneybeetle/README.md`)입니다. 새로운 방식을 들이지 않고 **배운 구조 그대로** 씁니다.
- 참고한 학습 노트: 모듈 2 백엔드 25일차(JPA 미니 프로젝트: 조회·검증), 26일차(등록·수정·삭제, 정책 위치), 27일차(Spring Security 세션 로그인).
- 근거 칸: **학습** = 학습 노트에서 확인한 내용, **골격** = `moneybeetle/` 프로젝트에 이미 있는 내용, **DB·API 문서** = 팀 설계 문서에 이미 정한 내용, **제안** = 이 문서에서 새로 제안하는 내용(팀 확인 필요).
- 기술 스택: Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security, Thymeleaf, MySQL 8, Gradle (`build.gradle`). **화면은 Thymeleaf 서버 렌더링으로 확정**되었고, 시스템 설계·API 명세서·화면 설계도 이에 맞춰 수정되었습니다. 화면 안의 일부 동작만 `/api/v1` JSON을 `fetch`로 호출합니다.

---

## 1. 전체 구조 한눈에 보기

요청 하나가 지나가는 순서와 각 계층의 역할입니다.

```
브라우저 → Controller → Service → Repository(dao) → DB
              │            │
              │            └ Entity ↔ DTO 변환은 Service에서
              └ DTO만 다룸 (Entity를 직접 만지지 않음)
```

| 계층 | 하는 일 | 하지 않는 일 |
| --- | --- | --- |
| Controller | 요청 받기, 입력 검증(`@Valid`), Service 호출, View 이름 또는 JSON 응답 결정 | `try-catch`, 업무 규칙 판단, Entity 직접 사용 |
| Service | 트랜잭션, 업무 흐름, Entity ↔ DTO 변환, 규칙 위반 시 `BusinessException` | 화면·HTTP 처리 |
| Repository (`model/dao`) | DB 조회·저장 | 업무 규칙 |
| Entity | 테이블 매핑, **현재 상태로 판단되는 규칙** (도메인 정책) | DTO·Service·다른 도메인 Entity 참조 |
| DTO | 요청·응답 데이터 운반, **입력값만으로 판단되는 규칙** (입력 정책) | DB 조회 |
| Policy | DB 조회나 다른 도메인 정보가 필요한 규칙 (비즈니스 정책) | 화면·HTTP 처리 |
| GlobalExceptionHandler | 모든 실패를 에러 화면·JSON으로 변환 | |

---

## 2. 네이밍 컨벤션

### 2-1 Java

| 대상 | 규칙 | 예시 | 근거 |
| --- | --- | --- | --- |
| 패키지 | 소문자, 점으로 구분 | `com.wanted.moneybeetle.domain.member` | 골격 |
| 클래스 | PascalCase, 명사 | `MemberService`, `CourseController` | 학습 |
| Controller | `도메인명Controller`. 관리자·강사용은 역할을 앞에 붙임 | `CourseController`, `AdminCourseController`, `InstructorCourseController` | 골격 |
| Service | `도메인명Service` | `MemberService`, `AdminHousingService` | 골격 |
| Repository | `Entity명Repository` | `MemberRepository` | 골격 |
| Entity | DB 테이블 이름을 PascalCase 단수형으로 | `member` → `Member`, `quiz_attempt` → `QuizAttempt` | 골격 |
| DTO | `용도DTO` — **`DTO`는 대문자** | `SignupDTO`, `CourseListDTO`, `MenuDTO` | 학습·골격 |
| 에러 코드 | `도메인명ErrorCode` (enum) | `MemberErrorCode` | 골격 |
| Policy | `대상+규칙Policy` | `MenuNamePolicy`, `MenuDeletionPolicy` | 학습 |
| 메서드 | camelCase, 동사로 시작 | `findMenuByMenuCode`, `stopSelling` | 학습 |
| 변수·필드 | camelCase | `menuPrice`, `refCategoryCode` | 학습 |
| 상수·enum 값 | UPPER_SNAKE_CASE | `MENU_NOT_FOUND`, `ACTIVE` | 학습·DB 문서 |

**메서드 이름 접두어**

| 접두어 | 용도 | 예시 |
| --- | --- | --- |
| `find` | 조회 (없을 수 있음) | `findMemberById` |
| `get` | 조회 (없으면 예외) | `getMember` |
| `create` | 생성 | `Menu.create(...)` |
| `change` | 값 변경 | `changeMenuName` |
| `delete` | 삭제 | `deleteCourse` |
| `check` / `is` / `has` | 검사 / 불리언 | `checkDuplicatedEmail`, `isHidden` |

### 2-2 DB와 URL, 화면

| 대상 | 규칙 | 예시 | 근거 |
| --- | --- | --- | --- |
| 테이블 | 영문 소문자 단수형 snake_case | `member`, `quiz_attempt` | DB 문서 |
| 컬럼 | snake_case | `created_at`, `member_id` | DB 문서 |
| PK | `테이블명_id` | `member_id` | DB 문서 |
| FK | 참조하는 PK 이름 그대로, 역할이 다르면 역할 이름 | `instructor_id`, `admin_id` | DB 문서 |
| 상태값 | ENUM 대신 VARCHAR + 영문 대문자 코드 | `ACTIVE`, `REVIEW` | DB 문서 |
| API URI | 소문자 복수형 kebab-case | `/api/v1/wrong-notes` | API 문서 |
| JSON 필드 | camelCase | `memberId` | API 문서 |
| 화면 URL | 소문자 kebab-case | `/user/my-page`, `/instructor/course-create` | 화면 설계 |
| 템플릿 파일 | `templates/도메인/화면.html`, 소문자 kebab-case | `templates/course/detail.html` | 골격 |
| 정적 파일 | `static/css`, `static/js`, `static/images` | `static/js/drawer.js` | 골격 |
| SQL 스크립트 | `resources/sql/번호_도메인.sql` | `01_member.sql` | 골격 |

Java 필드(camelCase)와 DB 컬럼(snake_case)은 Spring Boot가 자동으로 변환하므로 `@Column(name = ...)`을 따로 적지 않아도 됩니다. 자동 변환이 되지 않는 경우에만 적습니다. *(제안)*

---

## 3. 패키지 구조

`moneybeetle/README.md`의 구조를 그대로 따릅니다.

```
com.wanted.moneybeetle
├─ global                 공통 (여러 도메인이 함께 씀)
│  ├─ config              Application, ContextConfig, JpaConfig, WebConfig
│  ├─ common              ApiResponse, PageResponse, BaseTimeEntity
│  ├─ exception           ErrorCode, CommonErrorCode, BusinessException, GlobalExceptionHandler
│  ├─ security            레벨 해금 확인, 중복 요청 방지
│  └─ file                파일 업로드
└─ domain
   ├─ auth · member · inquiry · operation   (SCM)
   ├─ course · quiz · trading               (DBA)
   └─ growth · home · term · notification · housing   (PM)
```

**도메인 안은 모두 같은 모양입니다.**

```
domain/member
├─ controller          MemberController, AdminMemberController
├─ exception           MemberErrorCode
└─ model
   ├─ dao              MemberRepository
   ├─ dto              SignupDTO, MyInfoDTO
   ├─ entity           Member
   └─ service          MemberService
```

- 도메인을 나누는 기준은 **기능**입니다. `controller/`, `service/`를 맨 위에 두고 모든 도메인을 한곳에 모으지 않습니다.
- 관리자·강사용 Controller와 Service도 해당 도메인 안에 둡니다. 별도의 `admin` 패키지를 만들지 않습니다.
- 한 파일 상단에는 담당 도메인과 역할을 설명하는 주석 헤더를 둡니다 (골격에 이미 있음).

---

## 4. Entity 규칙

**배운 원칙: 테이블에 맞추는 쪽이 Entity이고, 값을 바꾸는 입구는 이름 있는 메서드뿐이다.**

| 규칙 | 설명 | 근거 |
| --- | --- | --- |
| 테이블이 기준 | `ddl-auto: none`. `resources/sql`의 테이블 정의가 기준이고 Entity가 거기에 맞춘다. Entity를 바꿔서 테이블을 바꾸지 않는다 | 학습·골격 |
| setter 금지 | `@Setter`와 `@Data`를 Entity에 쓰지 않는다. 값 변경은 `changeXxx()`, `stopSelling()` 같은 **이름 있는 메서드**로만 한다 | 학습 |
| 생성은 정적 메서드 | 전체 필드 생성자는 `private`으로 막고 `Menu.create(...)`처럼 정적 팩토리로 만든다. JPA용 기본 생성자는 `protected` | 학습 (기본 생성자는 일반 관례) |
| 타입 선택 | `NOT NULL` 컬럼은 기본형(`int`), **NULL 가능 컬럼은 래퍼 타입**(`Integer`, `Long`) | 학습 |
| PK 타입 | DB 문서의 PK는 `BIGINT`이므로 `Long`. `@GeneratedValue(strategy = GenerationType.IDENTITY)` (MySQL AUTO_INCREMENT와 짝) | DB 문서·학습 |
| 다른 도메인은 ID로 참조 | 다른 도메인의 Entity를 `@ManyToOne`으로 잇지 않고 **ID 값**(`Long memberId`)으로 참조한다. 도메인끼리 서로 의존하는 순환 참조를 막기 위함 | 학습 (PRD 기준) |
| 같은 도메인 안의 연관관계 | 같은 도메인 안에서는 연관관계를 써도 되지만 팀 합의가 필요하다 (§12 참고) | 제안 |
| 상태값은 상수로 | `"Y"`, `"N"`, `"ACTIVE"` 같은 문자열을 Service·Policy에 직접 쓰지 않고, **Entity가 상수로 들고 있는다** (예: `ON_SALE`, `STOPPED`). 표현이 바뀌어도 고칠 곳이 Entity 한 곳이다 | 학습 |
| 수정은 변경 감지로 | 수정할 때 `save()`를 부르지 않는다. **`@Transactional` 안에서 조회 → 이름 있는 메서드로 값 변경**을 하면 commit 시점에 UPDATE가 나간다. 트랜잭션 밖에서 조회한 Entity는 변경 감지가 되지 않는다 | 학습 |
| 상태 해석은 Entity가 | `refCategoryCode != null` 같은 내부 필드 해석은 `isSubCategory()`처럼 **Entity 메서드에 가두고**, 다른 클래스가 필드를 직접 들여다보지 않는다 | 학습 |
| 검사 메서드 이름 | 상태를 보고 막는 메서드는 `validateXxx()` (예: `validateDeletable()`), 불리언 질문은 `isXxx()` | 학습 |
| 중복은 DB 제약도 함께 | Policy가 이름 중복을 막아도 동시 요청은 둘 다 통과할 수 있다. 마지막 방어선으로 **DB UNIQUE 제약**을 함께 둔다 | 학습 |
| 공통 컬럼 | `created_at`, `updated_at`은 `BaseTimeEntity`를 상속해서 쓴다. 변경이 없는 기록성 테이블은 `created_at`만 | 골격·DB 문서 |
| 삭제 | Hard Delete를 기본으로 쓰지 않는다. DB 문서 3장의 테이블별 방식(`deleted_at`, `is_hidden`, `is_public`)을 따른다 | DB 문서 |
| 도메인 정책 | "현재 상태를 봐야 판단되는 규칙"은 Entity 메서드 안에 둔다. 예: 이미 판매 중지된 메뉴는 다시 중지할 수 없다 | 학습 |

```java
// 예시 (학습 노트 25일차의 Menu를 기준으로 한 모양)
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberId;

    private String nickname;
    private String status;          // ACTIVE / SUSPENDED / WITHDRAWN

    // 값을 바꾸는 입구는 이름 있는 메서드뿐
    public void suspend(String reason) {
        if ("SUSPENDED".equals(this.status)) {
            throw new BusinessException(MemberErrorCode.ACCOUNT_SUSPENDED);  // 도메인 정책
        }
        this.status = "SUSPENDED";
    }
}
```

`@Getter`는 허용합니다. Lombok을 쓰는 범위는 §10 참고.

---

## 5. DTO 규칙

**배운 원칙: Entity는 Service 밖으로 나가지 않는다. Controller는 DTO만 다룬다.**

| 규칙 | 설명 | 근거 |
| --- | --- | --- |
| 이름 | `용도DTO`. 요청은 `XxxSaveDTO`·`SignupDTO`, 응답은 `XxxDetailDTO`·`XxxListDTO`처럼 **무엇을 위한 DTO인지** 드러낸다 | 학습·골격 |
| 위치 | `domain/도메인/model/dto` | 골격 |
| Entity → DTO | DTO 안의 정적 메서드 `from(entity)`로 변환한다. Entity가 DTO를 알면 안 되므로 변환 코드는 **DTO 쪽**에 둔다 | 학습 |
| DTO → Entity | Entity의 `create(...)`에 값을 넘겨서 만든다. DTO 안에서 `new Entity()`를 하지 않는다 | 제안 |
| 입력 검증 | 입력값만 보고 판단되는 규칙(빈 값, 길이, 범위)은 DTO에 `@NotBlank`, `@Size`, `@Min` 같은 어노테이션으로 선언한다 | 학습 |
| 검증 메시지 | `message`에 **사용자에게 보여 줄 문구**를 적는다. 예: `@Min(value = 0, message = "가격은 0원 이상 입력!")` | 학습 |
| 한 DTO에 모두 담지 않기 | 등록·수정·조회가 서로 다르면 DTO도 나눈다 | 제안 |

```java
// 예시
public class SignupDTO {
    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @Size(min = 2, max = 12, message = "닉네임은 2~12자로 입력해 주세요.")
    private String nickname;
}

public class MyInfoDTO {
    private Long memberId;
    private String nickname;

    public static MyInfoDTO from(Member member) {       // 변환은 DTO 쪽에
        ...
    }
}
```

---

## 6. 정책(규칙)을 어디에 두는가

정책 정의서의 규칙을 코드로 옮길 때 **"그 규칙을 판단하려면 무엇을 알아야 하는가"**로 위치를 정합니다. (학습 25일차)

| 종류 | 판단에 필요한 것 | 둘 곳 | 이 프로젝트의 예 |
| --- | --- | --- | --- |
| 입력 정책 | 입력값만 | DTO · Controller (`@Valid`, `@Min`) | 닉네임 2~12자, 문의 제목 50자 이하 |
| 도메인 정책 | Entity의 **현재 상태** | Entity 메서드 | 정지된 계정은 로그인할 수 없다, 이미 승인된 강사는 다시 승인할 수 없다 |
| 비즈니스 정책 | **DB 조회·다른 도메인** 정보 | `Policy` 클래스 (Service가 호출) | 닉네임 중복, 신청자가 있는 청약 공고는 삭제 금지 |

**Policy 클래스를 만드는 방법** (학습 26일차: 세 Policy 모두 같은 모양)

- "된다 / 안 된다"만 판단합니다. 안 되면 `BusinessException`을 던지고, 통과하면 **아무것도 돌려주지 않습니다**(`void`).
- 메서드 이름은 `validateXxx()` (예: `validateNewName`, `validateRename`).
- 규칙을 Service에 직접 쓰면 ① 등록·수정에 같은 검사가 **복사**되고 ② Service가 다른 클래스의 내부 사정을 알게 되고 ③ `if`가 쌓여 **흐름이 묻히고** ④ 규칙 하나만 테스트하기 어려워집니다. 그래서 Policy로 뺍니다.
- Service에는 **흐름만** 남깁니다: `검사(Policy) → 생성(Entity) → 저장(Repository)`.
- 다른 팀 도메인의 정보가 필요하면 **인터페이스**로 묻습니다(§11 참고).
- **검사 순서가 곧 응답 순서입니다.** 틀린 곳이 둘이면 먼저 호출한 Policy의 에러만 보입니다. 순서는 정책 정의서의 처리 순서를 따릅니다.
- 한 규칙을 세 곳에 중복해서 쓰지 않습니다. 가장 알맞은 한 곳에만 둡니다.
- 정책 번호(SP-10, DP-37 등)는 메서드나 클래스 주석에 적어서 정책 정의서와 연결합니다. *(제안)*

---

## 7. Controller · Service · Repository 규칙

### 7-1 Controller

| 규칙 | 설명 | 근거 |
| --- | --- | --- |
| **`try-catch`를 쓰지 않는다** | 실패는 모두 `GlobalExceptionHandler`가 처리한다 | 학습 |
| DTO만 다룬다 | Entity를 Model이나 응답에 직접 담지 않는다 | 학습 |
| 화면 요청 | `ModelAndView`(또는 `Model`)에 DTO를 담고 View 이름을 지정한다. Model 이름은 템플릿의 `th:each` 등과 **이름을 맞춘다** (예: `menuList`) | 학습 |
| 데이터 요청 | `@ResponseBody`로 JSON을 반환한다. 팀 공통 형식은 `ApiResponse` | 학습·골격 |
| 등록·수정·삭제 후 | 화면으로 바로 응답하지 않고 **redirect** 한다 (PRG 패턴: 새로고침 시 POST가 반복되는 것을 막음). 예: 등록 후 상세 조회로 302 | 학습 |
| `@Valid`를 붙여야 검증된다 | DTO에 `@NotBlank` 등을 선언만 해서는 실행되지 않는다. **Controller 파라미터에 `@Valid`가 있어야** 검사한다. 검증은 Service에 들어가기 전에 끝난다 | 학습 |
| 수정·삭제 입력도 검증 | 등록뿐 아니라 **수정 요청에도 검증**을 단다. 검증이 비어 있으면 빈 값·중복 값·너무 긴 값이 그대로 저장되거나 빈 200 응답이 나간다 | 학습 |
| 화면 vs 데이터 | 화면을 돌려주면 `@Controller`, 데이터(JSON)만 주고받으면 `@RestController`(= `@Controller` + `@ResponseBody`). 한 메서드만 JSON이면 그 메서드에만 `@ResponseBody` | 학습 |
| 로그인 처리 | `POST /auth/login`을 처리하는 Controller는 **만들지 않는다**. Security 필터가 처리한다 (§9) | 학습 |
| HTTP 상태 코드 | 입력·검증 오류 400, 없는 대상 404, 중복·상태 충돌 409, 권한 없음 403 | 학습 |
| 권한·레벨 | 해금 레벨은 `@RequireLevel(8)`처럼 표시하고, 권한은 Security 설정에서 처리한다 | 골격 |

### 7-2 Service

| 규칙 | 설명 | 근거 |
| --- | --- | --- |
| 조회는 `Optional` 처리 | `findById()`는 없어도 예외를 던지지 않고 빈 `Optional`만 준다. **`orElseThrow()`로 `BusinessException`을 직접 던진다** | 학습 |
| 변환은 Service에서 | Repository에서 꺼낸 Entity를 DTO로 바꿔서 Controller에 돌려준다 | 학습 |
| 트랜잭션 | 쓰기 메서드에 `@Transactional`. 캐시·경험치·자산이 함께 바뀌는 작업은 **하나의 트랜잭션**으로 묶는다 (DP-40) | DB·정책 문서 |
| **다른 도메인 접근 금지** | 다른 도메인의 Repository를 직접 쓰지 않고, 그 도메인의 **Service 메서드**를 호출한다 | 골격 README |

공통 Service 창구(골격 README의 충돌 방지 규칙 2번)

| 하려는 일 | 부르는 곳 |
| --- | --- |
| 캐시·경험치 지급/차감 | `growth.RewardService` |
| 알림 생성 | `notification.NotificationService` |
| 관리자 작업 기록 | `operation.AdminLogService` |

### 7-3 Repository

| 규칙 | 설명 | 근거 |
| --- | --- | --- |
| 인터페이스만 작성 | `JpaRepository<Entity, PK타입>`을 상속한다. 기본 CRUD는 새로 만들지 않는다 | 학습 |
| 단순 조건 | **쿼리 메서드** 이름으로 만든다. 예: `findByMenuPriceGreaterThanOrderByMenuPrice` | 학습 |
| 복잡한 조건 | `@Query`. **JPQL을 우선**하고, Native Query는 꼭 필요할 때만 쓰며 이유를 주석으로 남긴다 | 학습 (Notion 자료 기준) |
| 정렬 | `Sort.by("menuCode")`처럼 **Entity 필드 이름**을 쓴다 (컬럼 이름이 아님) | 학습 |
| "초과/이상" 주의 | `GreaterThan`은 `>`, `GreaterThanEqual`은 `>=`. 정책 문구와 **실제 SQL이 맞는지** 확인한다 | 학습 |

---

## 8. 예외 처리와 응답 형식

### 8-1 예외

```
규칙 위반 → Service에서 throw new BusinessException(XxxErrorCode.CODE)
         → GlobalExceptionHandler가 받아서
              · 화면 요청 → templates/error/403·404·500.html
              · 데이터 요청 → ApiResponse.fail(errorCode)
```

| 규칙 | 설명 | 근거 |
| --- | --- | --- |
| 에러 코드는 도메인별 enum | `MemberErrorCode`처럼 `ErrorCode` 인터페이스(code, httpStatus, message)를 구현한 enum을 **자기 도메인 안에** 만든다. 한 파일을 여럿이 고쳐서 충돌하는 일을 막기 위함 | 골격 |
| 공통 에러 | `CommonErrorCode`는 SCM만 수정한다 | 골격 |
| 던지는 예외 | 업무 규칙 위반은 `BusinessException` 하나로 통일한다. `RuntimeException`·`IllegalArgumentException`을 직접 던지지 않는다 | 학습 |
| 에러 코드 이름 | `UPPER_SNAKE_CASE`, 상황이 드러나게. 예: `EMAIL_DUPLICATED`, `LOGIN_LOCKED` | 골격 |
| 검증 실패 메시지 | 사용자에게 `@Min`·`@NotBlank`에 적은 **`message`가 그대로 보이는지** 확인한다 (예외 전체를 `getMessage()`로 찍으면 `Validation failure`만 나올 수 있음) | 학습 |
| `@Valid` 실패 처리 | `GlobalExceptionHandler`에서 `e.getFieldError()`의 첫 번째 에러를 꺼낸다. 형식 자체가 틀린 경우(`isBindingFailure()`, 숫자 칸에 문자)와 검증 규칙 위반을 나누어 메시지를 만든다. 여러 칸이 틀려도 **첫 번째 하나만** 보인다 | 학습 |
| 핸들러는 `null`을 반환하지 않는다 | 비워 둔 핸들러(`return null`)는 **200 OK + 빈 본문**으로 조용히 성공한다. 필수 값 누락, DB 제약 위반(`DataIntegrityViolationException`), 없는 URL, 그 외 예외 핸들러를 모두 채운다 | 학습 |

### 8-2 응답 형식

- JSON 응답은 `ApiResponse`로 통일합니다: `{ success, data, error }` (API명세서 1-2).
- 목록은 `PageResponse`로 페이지 정보를 함께 담습니다.
- 화면 응답은 `ModelAndView` + Thymeleaf 템플릿입니다.

### 8-3 개발 중 실수하기 쉬운 부분 (학습 25일차에서 확인)

- 뼈대 코드에 남겨 둔 `return null;`은 컴파일도 되고 **200 OK에 빈 화면**을 돌려준다. TODO를 채울 때 **정상 요청과 실패 요청을 둘 다 호출**해서 확인한다.
- 주석과 실제 동작이 다를 수 있다. 정책 문서와 **실제 SQL**·**실제 응답**을 기준으로 삼는다.
- 쓰기 기능은 DB를 바꾸므로, 확인할 때는 **원본 테이블이 아닌 복사본(임시 테이블·테스트 DB)**에서 한다.

---

## 9. 인증·보안 (Spring Security 세션 로그인)

학습 27일차 구조를 따릅니다. 팀 골격의 `auth` 도메인(`config/SecurityConfig`, `handler/AuthSuccessHandler·AuthFailHandler·OAuth2SuccessHandler`, `model/dto/AuthDetails`, `model/service/AuthService`)이 이 구조입니다.

**한 번의 로그인 흐름**: 로그인 화면(GET)만 Controller가 만들고, 나머지는 Security 필터가 이어 갑니다. 우리가 만드는 것은 **"회원을 알려 주는 일"과 "결과를 기록하는 일"**이고, 검사는 Security가 합니다.

| 규칙 | 설명 | 근거 |
| --- | --- | --- |
| 방식 | **세션 로그인**(쿠키 `JSESSIONID`). 세션 만료 30분 | 학습·API 문서 |
| 설정은 한 곳에 | 접근 권한, 로그인, 로그아웃, Remember Me, 세션, CSRF 설정은 `SecurityConfig`에서만 선언한다 | 학습 |
| 권한 규칙의 순서 | **위에서부터 검사**한다. 좁은 규칙(`permitAll`)을 앞에 두고 마지막을 `anyRequest().authenticated()`로 닫는다 | 학습 |
| 폼 이름 맞추기 | 로그인 폼 `input`의 `name`과 `usernameParameter`·`passwordParameter` 설정값이 **일치**해야 한다 | 학습 |
| `UserDetailsService` | `AuthService.loadUserByUsername()`은 **회원 조회만** 한다. 비밀번호는 비교하지 않는다. 없으면 `null`이 아니라 `UsernameNotFoundException`을 던진다 | 학습 |
| `UserDetails` | `AuthDetails`가 구현한다. `getPassword()`, `isAccountNonLocked()`, `getAuthorities()`를 Security가 호출해 로그인 가능 여부를 판단한다. 이 객체가 **세션에 저장된다** | 학습 |
| `equals`/`hashCode` | `AuthDetails`에 `getUsername()` 기준으로 **반드시 오버라이드**한다. 비워 두면 `maximumSessions(1)`(한 계정 한 세션)이 아무 효과가 없다 | 학습 |
| 비밀번호 | `BCryptPasswordEncoder` Bean으로 `encode()` 후 저장한다. 같은 비밀번호도 해시가 다른 것이 정상이다. 비교는 직접 하지 않고 Security(`matches`)에 맡긴다 | 학습 |
| 권한은 서버가 정한다 | 회원가입 폼에서 받은 값으로 `role`(권한)을 정하지 **않는다**. 학습 예제는 폼에서 ADMIN을 고를 수 있었지만 실제 서비스에서는 금지 | 학습 |
| 로그인 결과 기록 | 성공·실패는 `AuthSuccessHandler`·`AuthFailHandler`에서 처리한다. 실패 시 횟수를 올리고 로그를 남기고, 성공 시 횟수를 0으로 되돌린다 | 학습 |
| 잠금 규칙은 Entity에 | 실패 횟수와 잠금은 `Member` 같은 **Entity의 메서드**(예: `increaseLoginFailCount()`)가 가진다. 5회 실패 시 잠금(정책 정의서 기준 10분). 변경 감지로 저장하므로 `save()`는 부르지 않는다 | 학습·정책 문서 |
| 잠금 검사 순서 | Security는 **잠금 검사를 비밀번호 검사보다 먼저** 한다. 잠긴 계정은 맞는 비밀번호로도 로그인되지 않고, 이때는 실패 횟수가 늘지 않는다 | 학습 |
| 없는 아이디 메시지 | Security는 없는 아이디를 기본적으로 `BadCredentialsException`으로 바꾼다. **"아이디 또는 비밀번호가 올바르지 않습니다"처럼 구분하지 않는 메시지**를 쓴다. `UsernameNotFoundException` 분기는 사실상 도달하지 않는다 | 학습 |
| 메서드 권한 | `@PreAuthorize`를 쓰려면 `SecurityConfig`에 **`@EnableMethodSecurity`가 있어야** 한다. 없으면 경로 규칙이 없는 주소는 로그인만 하면 누구나 호출한다 | 학습 |
| URL 권한 vs 메서드 권한 | 경로 단위로 한 번에 막을 때는 `SecurityConfig`, 같은 Controller 안에서 메서드마다 권한이 다를 때는 `@PreAuthorize` | 학습 |
| CSRF | 학습 예제는 수업 목적으로 `csrf.disable()` 했다. **우리 프로젝트는 켜 두고** 로그아웃은 POST만 받는다 (꺼 두면 GET으로도 로그아웃된다) | 학습 (켜는 것은 제안) |
| 응답 확인 | 보안 설정은 설정만 믿지 말고 **익명·일반 회원·관리자로 각각 호출**해서 302(로그인 이동)·403(권한 없음)을 확인한다 | 학습 |
| 로그인 후 이동 | 로그인 전에 가려던 주소로 돌아가야 하면 요청을 기억하는 핸들러(`SavedRequestAware…`)를 쓴다. 학습 예제의 부모 핸들러는 항상 `/`로 이동했다 | 학습 |

> 권한 이름은 DB 문서의 `role`(`MEMBER` / `INSTRUCTOR` / `ADMIN`) 한 값을 기준으로 정할지, 학습 예제처럼 쉼표로 구분한 여러 권한으로 둘지 팀 확인이 필요합니다 (§12).

---

## 10. 코드 스타일

| 항목 | 규칙 | 근거 |
| --- | --- | --- |
| 들여쓰기 | 4칸 공백 (탭 사용 안 함) | 제안 |
| 줄 길이 | 120자 이내 | 제안 |
| 중괄호 | 같은 줄에서 열고(`{`), 한 줄짜리 `if`도 중괄호를 쓴다 | 제안 |
| import | 와일드카드(`.*`) 금지, 안 쓰는 import는 지운다 | 제안 |
| 주석 | 클래스 상단: 담당·역할·정책 번호. 메서드: **왜** 이렇게 하는지가 불분명할 때만. 코드를 그대로 설명하는 주석은 쓰지 않는다 | 골격·제안 |
| 파일 인코딩 | UTF-8 | 제안 |
| Lombok | `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `@RequiredArgsConstructor`(생성자 주입)만 허용. `@Data`·`@Setter`는 Entity에 쓰지 않는다 | 제안 |
| 의존성 주입 | **생성자 주입** (`private final` 필드 + `@RequiredArgsConstructor`). `@Autowired` 필드 주입 금지 | 제안 |
| 시간 | 모든 시간은 KST. `LocalDateTime` 사용 | DB 문서 |
| 매직 넘버 | 해금 레벨·통과 점수·보상 금액 등 바뀔 수 있는 값은 코드에 직접 쓰지 않고 `system_setting`·`reward_rule`·`level_policy`에서 읽는다 (NFR-13) | DB·정책 문서 |
| 템플릿(Thymeleaf) | `th:text`·`th:each` 사용, 공통 조각은 `templates/fragments/`에 둔다. 인라인 `<script>`보다 `static/js`로 분리한다 | 골격·제안 |
| 포매터 | IntelliJ 기본 포매터 (Settings → Code Style → Java, 위 규칙에 맞게 설정) | 제안 |

---

## 11. 도메인 간 협업 규칙

골격 README의 충돌 방지 규칙을 코드 리뷰 기준으로 삼습니다.

1. 자기 도메인 패키지 밖의 파일은 고치지 않는다. 필요하면 담당자에게 요청한다.
2. 다른 도메인의 테이블(Repository)을 직접 쓰지 않는다. 그 도메인의 Service 메서드를 부른다.
3. 에러 코드는 도메인별 enum(`XxxErrorCode`)에 추가한다. `CommonErrorCode`는 SCM만 고친다.
4. SQL은 `resources/sql`의 번호 파일로 나누고 DBA가 관리한다. 테이블 변경은 DBA에게 요청한다.
5. 마이페이지·관리자 대시보드는 틀(SCM)과 탭 내용(`templates/fragments/...`, 각 담당)을 나눈다.

**아직 구현이 없는 도메인과 협업할 때** (학습 25·26일차): 필요한 쪽이 **인터페이스**를 먼저 정의하고, 상대 팀의 실제 구현이 나올 때까지 `Fake` 구현으로 대신한다. 실제 구현이 생기면 Fake만 지우면 되고 사용하는 쪽 코드는 바뀌지 않는다.

---

## 12. 팀 확인 필요

| 항목 | 현재 상태 | 정할 것 |
| --- | --- | --- |
| 같은 도메인 안의 연관관계 | 다른 도메인은 ID 참조(학습). 같은 도메인 안의 `@OneToMany`·`@ManyToOne` 사용 기준은 미정 | 쓸지 / 쓰면 지연 로딩과 N+1 확인 방법 |
| Lombok 범위 | 의존성은 있으나 사용 규칙 없음 | §10의 제안대로 할지 |
| `DTO` 대문자 표기 | 학습·골격이 모두 `DTO` 대문자 | 그대로 통일 (Java 일반 관례는 `Dto`이므로 팀 합의 필요) |
| open-in-view | Spring Boot 기본값이 켜져 있음 (학습 25일차에서 경고 확인) | 끌지 여부 |
| 포매터·린터 | 없음 | IntelliJ 설정 공유 또는 Spotless 같은 도구 도입 여부 |
| 권한(role) 표현 | DB 문서는 `role` 한 값(`MEMBER`/`INSTRUCTOR`/`ADMIN`), 학습 예제는 `"USER,ADMIN"`처럼 쉼표로 구분한 여러 권한 | 한 값으로 할지 / 여러 권한으로 할지, `hasAnyAuthority`에 쓸 이름 |
| 로그인 잠금 | 학습 예제는 잠금 여부 불리언 + 수동 해제, DB 문서는 `failed_login_count`·`locked_until`(5회 실패 시 10분 잠금) | DB 문서 기준으로 할지 |
| CSRF | 학습 예제는 꺼 둠. 이 문서는 켜 두기를 제안 | 켤지, 켜면 Thymeleaf 폼·fetch에서 토큰을 어떻게 보낼지 |
| 업데이트 쿼리 | 변경 감지는 바뀐 컬럼이 아니라 **모든 컬럼**을 UPDATE한다(학습 26일차) | `@DynamicUpdate` 사용 여부 |
| 수정 요청 DTO | 등록과 수정의 검증 규칙이 다를 수 있다 | DTO를 나눌지, 검증 그룹을 쓸지 |
| 에러 코드 위치 | 학습 예제는 하나의 `ErrorCode` enum, 팀 골격은 도메인별 `XxxErrorCode` | 골격대로 도메인별로 통일 (이 문서는 골격 기준) |
