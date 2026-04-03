# GitHub Organization Access Report Service

A Spring Boot REST API that connects to GitHub and generates a structured report showing which users have access to which repositories within a given organization.

## Live Demo
```bash
GET http://localhost:8080/api/access-report?org=prashant-report-test
```

### Sample Output
```json
{
    "organization": "prashant-report-test",
    "generatedAt": "2026-04-03T12:01:00.578651700Z",
    "totalRepositories": 5,
    "totalUsers": 1,
    "report": [
        {
            "username": "Prashantsingh0003",
            "type": "User",
            "repositoryCount": 5,
            "repositories": [
                {
                    "name": "ai-api",
                    "fullName": "prashant-report-test/ai-api",
                    "permission": "admin",
                    "private": false
                },
                {
                    "name": "backend-api",
                    "fullName": "prashant-report-test/backend-api",
                    "permission": "admin",
                    "private": false
                },
                {
                    "name": "flutter-api",
                    "fullName": "prashant-report-test/flutter-api",
                    "permission": "admin",
                    "private": false
                },
                {
                    "name": "frontend-app",
                    "fullName": "prashant-report-test/frontend-app",
                    "permission": "admin",
                    "private": false
                },
                {
                    "name": "mobile-app",
                    "fullName": "prashant-report-test/mobile-app",
                    "permission": "admin",
                    "private": false
                }
            ]
        }
    ]
}
```

---

## How to Run

### Prerequisites
- Java 17+
- Maven 3.8+
- GitHub Personal Access Token

### 1. Clone the repository
```bash
git clone https://github.com/Prashantsingh0003/github-access-report.git
cd github-access-report
```

### 2. Set your GitHub token

**Windows:**
```cmd
set GITHUB_TOKEN=your_github_token_here
```

**Mac/Linux:**
```bash
export GITHUB_TOKEN=your_github_token_here
```

### 3. Run the application
```bash
mvn spring-boot:run
```
Server starts at `http://localhost:8080`

---

## How Authentication is Configured

- Authentication uses a **GitHub Personal Access Token (PAT)**
- Token is read from the `GITHUB_TOKEN` **environment variable** — never hardcoded
- Automatically injected into every GitHub API request as a Bearer token via a `RestTemplate` interceptor

### Required token scopes:
| Scope | Purpose |
|---|---|
| `repo` | Read repository data |
| `read:org` | Read organization members |
| `read:user` | Read user information |

---

## API Endpoints

### Get access report
```
GET /api/access-report?org={orgName}
```
```bash
curl "http://localhost:8080/api/access-report?org=prashant-report-test"
```

### Health check
```
GET /api/health
```
```bash
curl http://localhost:8080/api/health
```

### Error responses
| Status | Meaning |
|---|---|
| 400 | Missing org parameter |
| 401 | Invalid GitHub token |
| 403 | Rate limit exceeded |
| 404 | Organization not found |
| 500 | Unexpected server error |

---

## Project Structure
```plaintext
src/main/java/com/github/report/
├── GithubAccessReportApplication.java
├── config/
│   └── AppConfig.java                  ← RestTemplate + thread pool setup
├── controller/
│   └── AccessReportController.java     ← REST endpoints
├── service/
│   └── AccessReportService.java        ← parallel calls + aggregation logic
├── client/
│   └── GitHubApiClient.java            ← GitHub API calls + pagination
├── model/
│   ├── Repository.java
│   ├── Collaborator.java
│   └── AccessReport.java               ← JSON response shape
└── exception/
    ├── GitHubApiException.java
    └── GlobalExceptionHandler.java     ← clean error responses
```

## Design Decisions

### Parallel API Calls
Collaborator data for all repositories is fetched **simultaneously** using `CompletableFuture` with a dedicated thread pool (10 threads). This handles organizations with 100+ repos efficiently instead of waiting sequentially.

### Caching
Reports are cached for **10 minutes** using Spring Cache + Caffeine. Repeated requests for the same org return instantly without hitting the GitHub API again.

### Pagination
All GitHub API calls automatically loop through pages (100 results per page) until all data is retrieved — supporting orgs with **1000+ users**.

### Error Handling
- Restricted repos are **skipped gracefully** instead of crashing
- All errors return **structured JSON** with timestamp and details
- Token issues are caught early via org validation before fetching data

### Assumptions
- Token has sufficient permissions to read org and repo data
- Bot accounts are included but clearly marked as `type: "Bot"`
- Permission shown is the **highest level** the user has on that repository
