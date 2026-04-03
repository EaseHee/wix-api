# Wix API Client SDK

[Wix](https://www.wix.com/) REST API를 Java로 연동하기 위한 클라이언트 라이브러리.
Spring Boot 자동 설정 지원, 독립 실행 가능.

- Wix 공식 사이트: https://www.wix.com/
- Wix API Reference: https://dev.wix.com/docs/api-reference

## 지원 API 모듈

| 모듈 | 서비스 클래스 | 주요 기능 |
|---|---|---|
| Contacts | `ContactsService` | 연락처 CRUD, 라벨 관리, 쿼리/검색 |
| CMS | `CmsService` | 데이터 컬렉션/아이템 CRUD, 쿼리, 벌크 작업 |
| Inbox | `InboxService` | 대화 목록, 메시지 조회/발송 |
| Members | `MembersService` | 회원 CRUD, 승인/차단 |
| Analytics | `AnalyticsService` | 사이트 통계 조회 (최근 62일) |
| SEO | `SeoService` | SEO 태그, robots.txt 관리 |

---

## 사전 준비

### 1. Wix API Key 발급

1. [Wix API Keys Manager](https://manage.wix.com/account/api-keys) 접속
2. **Create API Key** 클릭
3. 필요한 API 권한(Permissions) 선택:
   - `Contacts` — Read/Write Contacts, Read/Write Labels
   - `CMS` — Read/Write Data Collections, Read/Write Data Items
   - `Inbox` — Read/Write Inbox
   - `Members` — Read/Write Members
   - `Analytics` — Read Analytics
   - `SEO` — Read/Write SEO
4. 생성된 **API Key** 복사

### 2. Site ID / Account ID 확인

- **Site ID**: Wix 대시보드 URL에서 확인 → `https://manage.wix.com/dashboard/{SITE_ID}/...`
- **Account ID**: API Keys Manager 페이지에서 확인

> Site ID는 개별 사이트 단위, Account ID는 계정 전체 단위의 API 접근에 사용.

---

## 설치

### Gradle

```groovy
repositories {
    mavenLocal()  // publishToMavenLocal로 배포한 경우
}

dependencies {
    implementation 'com.wix.api:wix-api-client:0.1.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.wix.api</groupId>
    <artifactId>wix-api-client</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 빌드 및 로컬 배포

```bash
./gradlew build
./gradlew publishToMavenLocal
```

---

## 사용법

### 방법 1: 독립 실행 (Spring Boot 없이)

```java
import com.wix.api.WixClient;
import com.wix.api.common.PagingRequest;

WixClient wix = WixClient.builder()
        .apiKey("YOUR_API_KEY")
        .siteId("YOUR_SITE_ID")      // 또는 .accountId("YOUR_ACCOUNT_ID")
        .build();
```

### 방법 2: Spring Boot 자동 설정

`application.yml`에 설정 추가 (아래 [Spring Boot 설정](#spring-boot-설정) 참고) 후,
`WixClient` 빈을 주입받아 사용:

```java
@Service
@RequiredArgsConstructor
public class WixSyncService {

    private final WixClient wixClient;

    public void syncData() {
        var contacts = wixClient.contacts().listContacts(
                PagingRequest.builder().limit(100).build());
        // ...
    }
}
```

---

## API 사용 예시

### Contacts — 연락처

```java
// 연락처 목록 조회
ContactList contacts = wix.contacts().listContacts(
        PagingRequest.builder().limit(50).offset(0).build());

for (Contact c : contacts.getContacts()) {
    System.out.println(c.getId() + " / " + c.getPrimaryEmail());
}

// 연락처 단건 조회
Contact contact = wix.contacts().getContact("contact-id");

// 연락처 생성
ContactInfo info = new ContactInfo();
ContactInfo.Name name = new ContactInfo.Name();
name.setFirst("홍");
name.setLast("길동");
info.setName(name);

ContactInfo.Email email = new ContactInfo.Email();
email.setEmail("hong@example.com");
email.setPrimary(true);
info.setEmails(List.of(email));

Contact created = wix.contacts().createContact(info);

// 연락처 검색 (필터)
ContactList filtered = wix.contacts().queryContacts(
        QueryContactsRequest.builder()
                .filter(Map.of("info.name.last", Map.of("$eq", "홍")))
                .paging(PagingRequest.builder().limit(20).build())
                .build());

// 연락처 삭제
wix.contacts().deleteContact("contact-id");

// 라벨 관리
LabelList labels = wix.contacts().listLabels(PagingRequest.builder().build());
Label newLabel = wix.contacts().createLabel("VIP 고객");
wix.contacts().labelContact("contact-id", List.of(newLabel.getKey()));
```

### CMS — 데이터 컬렉션

```java
// 컬렉션 목록 조회
DataCollectionList collections = wix.cms().listDataCollections(
        PagingRequest.builder().limit(20).build());

// 컬렉션 상세 조회
DataCollection collection = wix.cms().getDataCollection("my-collection");

// 데이터 아이템 쿼리
DataItemList items = wix.cms().queryDataItems("my-collection",
        WixQueryFilter.create()
                .eq("status", "published")
                .sortDesc("createdDate")
                .withPaging(PagingRequest.builder().limit(10).build()));

for (DataItem item : items.getDataItems()) {
    System.out.println(item.getId() + " → " + item.getData());
}

// 데이터 아이템 추가
DataItem newItem = wix.cms().insertDataItem("my-collection",
        Map.of("title", "새 게시글", "status", "draft"));

// 데이터 아이템 수정
wix.cms().updateDataItem("my-collection", newItem.getId(),
        Map.of("title", "수정된 게시글", "status", "published"));

// 데이터 아이템 삭제
wix.cms().removeDataItem("my-collection", "item-id");

// 벌크 추가
wix.cms().bulkInsertDataItems("my-collection", List.of(
        Map.of("title", "글 1", "status", "published"),
        Map.of("title", "글 2", "status", "draft")));
```

### Inbox — 대화/메시지

```java
// 대화 목록 조회
ConversationList conversations = wix.inbox().listConversations(
        CursorPagingRequest.builder().limit(20).build());

// 특정 대화의 메시지 조회
MessageList messages = wix.inbox().listMessages("conversation-id",
        CursorPagingRequest.builder().limit(50).build());

// 대화 단건 조회
Conversation conv = wix.inbox().getConversation("conversation-id");

// 메시지 발송
Message sent = wix.inbox().sendMessage("conversation-id",
        SendMessageRequest.builder()
                .direction("BUSINESS_TO_PARTICIPANT")
                .content(Map.of("plainText", Map.of("text", "안녕하세요!")))
                .build());
```

### Members — 회원

```java
// 회원 목록 조회
MemberList members = wix.members().listMembers(
        PagingRequest.builder().limit(100).build());

// 회원 단건 조회
Member member = wix.members().getMember("member-id");

// 회원 검색
MemberList filtered = wix.members().queryMembers(
        QueryMembersRequest.builder()
                .filter(Map.of("status", Map.of("$eq", "APPROVED")))
                .paging(PagingRequest.builder().limit(50).build())
                .build());

// 회원 승인 / 차단
wix.members().approveMember("member-id");
wix.members().blockMember("member-id");
```

### Analytics — 사이트 통계

```java
// 최근 30일 통계 조회
QueryStatisticsResponse stats = wix.analytics().queryStatistics(
        QueryStatisticsRequest.builder()
                .dateRange(QueryStatisticsRequest.DateRange.builder()
                        .from("2026-03-01")
                        .to("2026-03-31")
                        .build())
                .metrics(List.of("SESSIONS", "UNIQUE_VISITORS", "PAGE_VIEWS"))
                .dimensions(List.of("DATE"))
                .build());

for (StatisticsEntry entry : stats.getData()) {
    System.out.println(entry.getDimensionValues().get("DATE")
            + " → 세션: " + entry.getMetricValues().get("SESSIONS")
            + ", 방문자: " + entry.getMetricValues().get("UNIQUE_VISITORS"));
}
```

> Analytics 데이터는 최근 62일까지만 조회 가능. 초과 시 `IllegalArgumentException` 발생.

**사용 가능한 metrics**: `SESSIONS`, `UNIQUE_VISITORS`, `PAGE_VIEWS`, `ORDERS`, `SALES`, `AVG_SESSION_DURATION`, `BOUNCE_RATE`

**사용 가능한 dimensions**: `DATE`, `PAGE_URL`, `DEVICE_TYPE`, `TRAFFIC_SOURCE`, `COUNTRY`

### SEO — 검색엔진 최적화

```java
// 페이지 SEO 태그 조회
SeoTagList tags = wix.seo().listTags("https://mysite.com/page");

// robots.txt 조회
RobotsTxt robots = wix.seo().getRobotsTxt();
```

---

## Spring Boot 설정

### application.yml

```yaml
wix:
  api:
    api-key: ${WIX_API_KEY}
    site-id: ${WIX_SITE_ID}
    # account-id: ${WIX_ACCOUNT_ID}  # site-id 대신 사용 가능
    # base-url: https://www.wixapis.com  # 기본값
    # connect-timeout-seconds: 10        # 기본값
    # read-timeout-seconds: 30           # 기본값
    # max-retries: 3                     # 기본값
```

### 환경 변수

```bash
export WIX_API_KEY=your-api-key-here
export WIX_SITE_ID=your-site-id-here
```

---

## 고급 설정

### Builder 옵션 (독립 실행 시)

```java
WixClient wix = WixClient.builder()
        .apiKey("YOUR_API_KEY")
        .siteId("YOUR_SITE_ID")
        .baseUrl("https://www.wixapis.com")   // 기본값
        .connectTimeoutSeconds(10)             // 기본값
        .readTimeoutSeconds(30)                // 기본값
        .maxRetries(3)                         // 기본값
        .initialRetryDelayMs(1000)             // 기본값
        .retryMultiplier(2.0)                  // 기본값
        .maxRetryDelayMs(60000)                // 기본값
        .build();
```

### 재시도 정책

- HTTP 429 (Rate Limit) 및 5xx (Server Error) 발생 시 자동 재시도
- 지수 백오프: 1초 → 2초 → 4초 (기본 최대 3회)
- 최대 대기 시간: 60초
- 4xx 에러 (429 제외)는 즉시 `WixApiException` throw

### 페이징

**Offset 기반** (Contacts, Members, CMS Collections):
```java
PagingRequest paging = PagingRequest.builder()
        .limit(50)    // 페이지당 개수
        .offset(0)    // 시작 위치
        .build();
```

**Cursor 기반** (Inbox Messages, Conversations):
```java
CursorPagingRequest paging = CursorPagingRequest.builder()
        .limit(50)
        .cursor(previousResponse.getPagingMetadata().getCursors().getNext())
        .build();
```

### 쿼리 필터

```java
WixQueryFilter filter = WixQueryFilter.create()
        .eq("status", "published")
        .contains("title", "검색어")
        .gt("price", 10000)
        .in("category", List.of("A", "B"))
        .sortDesc("createdDate")
        .withPaging(PagingRequest.builder().limit(20).build());
```

---

## 에러 처리

```java
try {
    Contact contact = wix.contacts().getContact("invalid-id");
} catch (WixRateLimitException e) {
    // HTTP 429 — 자동 재시도 초과 시 발생
    System.err.println("Rate limit 초과: " + e.getMessage());
} catch (WixApiException e) {
    // 기타 API 에러
    System.err.println("HTTP " + e.getStatusCode() + ": " + e.getMessage());
    if (e.getErrorResponse() != null) {
        System.err.println("상세: " + e.getErrorResponse().getDetails());
    }
}
```

---

## 기술 스택

- Java 17+
- Spring Framework 6.1 (RestClient)
- Jackson 2.17
- Lombok
- SLF4J

## 참고 링크

- [Wix 공식 사이트](https://www.wix.com/)
- [Wix API Reference](https://dev.wix.com/docs/api-reference)
- [Wix REST API 문서](https://dev.wix.com/docs/rest)
- [Wix API Keys Manager](https://manage.wix.com/account/api-keys)

## 라이선스

Internal use — NextPangaea
