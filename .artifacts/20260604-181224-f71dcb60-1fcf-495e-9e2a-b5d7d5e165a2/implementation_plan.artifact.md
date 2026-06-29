# Implementation Plan - Fix Register Error and Alignment with Frontend

Resolve the 500 error on `/register` and ensure the response matches the specific class expected by the mobile app.

## User Review Required

> [!IMPORTANT]
> The frontend error indicates it expects a class named `RegisterResponse` (in package `com.example.myapplication_prueba`). On the backend, I will define a matching `RegisterResponse` data class to ensure Ktor serializes the JSON correctly as an object rather than a simple map.

## Proposed Changes

### Models

#### [AuthModels.kt](file:///C:/Users/isaac/AndroidStudioProjects/Backend/proyecto-backend-ktor/src/main/kotlin/models/AuthModels.kt)
- Add `RegisterResponse` data class:
  ```kotlin
  @Serializable
  data class RegisterResponse(
      val success: Boolean,
      val message: String
  )
  ```

---

### Routing

#### [AuthRoutes.kt](file:///C:/Users/isaac/AndroidStudioProjects/Backend/proyecto-backend-ktor/src/main/kotlin/routes/AuthRoutes.kt)
- Update `post("/register")` to respond with `RegisterResponse` instead of `mapOf(...)`.
- Improve error logging to identify why the 500 error is occurring (e.g., if the email already exists).
- Return `HttpStatusCode.Conflict` (409) if the user already exists, or `BadRequest` (400) for other errors, instead of letting it bubble up to a 500.

## Verification Plan

### Automated Tests
- `./gradlew assemble` to check for syntax errors.

### Manual Verification
1.  **Test Successful Registration**:
    ```bash
    curl -v -X POST http://localhost:8080/register -H "Content-Type: application/json" -d '{"nombres":"Test","apellidos":"User","email":"unique@test.com","telefono":"123","password":"123"}'
    ```
    *Expectation: 201 Created and a JSON matching `RegisterResponse`.*
2.  **Test Duplicate Registration**:
    ```bash
    curl -v -X POST http://localhost:8080/register -H "Content-Type: application/json" -d '{"nombres":"Test","apellidos":"User","email":"unique@test.com","telefono":"123","password":"123"}'
    ```
    *Expectation: A clear error message (not 500).*
