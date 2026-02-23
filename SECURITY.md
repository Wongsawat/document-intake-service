# Security Configuration Guide

## Overview

The document-intake-service implements several security measures to protect against common web application vulnerabilities.

## Security Features

### 1. OAuth2/JWT Authentication

All API endpoints require valid OAuth2 JWT authentication by default.

**Configuration:**
```yaml
app:
  security:
    enabled: true
    jwt:
      issuer-uri: https://your-identity-provider.com
      # Alternative: jwks-uri for custom JWKS endpoint
```

**Public Endpoints:**
- `/actuator/health` - Health check (always public)
- `/actuator/health/readiness` - Readiness probe
- `/actuator/health/liveness` - Liveness probe

**Protected Endpoints:**
- `/api/v1/documents/*` - All document submission and retrieval endpoints

**Disabling (Development Only):**
```bash
export SECURITY_ENABLED=false
```

### 2. XXE (XML External Entity) Protection

All XML parsing is protected against XXE attacks:

- **JAXB Unmarshaling**: Uses secure SAX parser factory
- **DOM Parsing**: Disables DTD and external entities
- **Features Disabled**:
  - DTD declarations
  - External general entities
  - External parameter entities
  - XInclude processing
  - Entity reference expansion

### 3. Rate Limiting

Apache Camel throttler provides token bucket rate limiting per client IP to prevent abuse and DoS attacks.

**Configuration:**
```yaml
app:
  rate-limit:
    enabled: true
    requests-per-second: 10    # Maximum requests per second per client
    time-period-seconds: 60    # Time period for rate limit
```

**How It Works:**
- Rate limiting is applied per client IP (using correlation ID as proxy)
- Uses Camel's throttler EIP with async delayed delivery
- Requests exceeding the limit are queued and processed when capacity is available

**Rate Limit Behavior:**
- When enabled: Requests are throttled to the configured rate
- When disabled: No rate limiting is applied (development/testing only)

**Environment Variables:**
- `RATE_LIMIT_ENABLED` - Enable/disable rate limiting (default: true)
- `RATE_LIMIT_REQUESTS_PER_SECOND` - Max requests per second (default: 10)
- `RATE_LIMIT_TIME_PERIOD_SECONDS` - Time period in seconds (default: 60)

### 4. Input Size Limits

Multiple layers of size validation:

**Spring Boot Level:**
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

**Application Level:**
```yaml
app:
  validation:
    max-xml-size: 10485760      # 10MB in bytes
    max-xml-depth: 100          # Maximum element nesting
    max-element-count: 10000    # Maximum elements
```

**Response when exceeded:**
- HTTP 413 (Payload Too Large)

### 5. Secure Headers

The following security headers are configured:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY` (when CSRF enabled)
- `Strict-Transport-Security` (when HTTPS enabled)

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SECURITY_ENABLED` | `true` | Enable OAuth2/JWT authentication |
| `JWT_ISSUER_URI` | - | OAuth2 issuer URI |
| `JWT_JWKS_URI` | - | JWKS endpoint (alternative to issuer-uri) |
| `RATE_LIMIT_ENABLED` | `true` | Enable rate limiting |
| `RATE_LIMIT_REQUESTS_PER_SECOND` | `10` | Max requests per second per client |
| `RATE_LIMIT_TIME_PERIOD_SECONDS` | `60` | Time period for rate limit |
| `MAX_XML_SIZE` | `10485760` | Maximum XML size in bytes |
| `MAX_XML_DEPTH` | `100` | Maximum element nesting depth |
| `MAX_ELEMENT_COUNT` | `10000` | Maximum number of elements |

## Testing with Security Disabled

For integration testing, you can disable security:

```bash
# Set environment variable
export SECURITY_ENABLED=false
export RATE_LIMIT_ENABLED=false

# Or use JVM properties
java -Dapp.security.enabled=false -Dapp.rate-limit.enabled=false -jar service.jar
```

**Warning:** Never disable security in production environments.

## JWT Token Format

The service expects JWT tokens with the following structure:

```json
{
  "sub": "user-id",
  "scope": ["document:submit", "document:read"],
  "exp": 1234567890
}
```

**Scope Claims:**
- `document:submit` - Submit documents via POST
- `document:read` - Read documents via GET

## Health Check Without Authentication

Health endpoints are publicly accessible for monitoring tools:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/health/readiness
curl http://localhost:8081/actuator/health/liveness
```

## Example curl Commands

### With JWT Token
```bash
curl -X POST \
  -H "Content-Type: application/xml" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d @document.xml \
  http://localhost:8081/api/v1/documents
```

### Rate Limit Behavior
```bash
# Rate limiting is applied via Camel throttler
# Requests exceeding the limit are queued and processed when capacity allows
# No 429 response is returned - requests are simply delayed
```

## Security Checklist for Deployment

- [ ] Set `SECURITY_ENABLED=true`
- [ ] Configure `JWT_ISSUER_URI` for your identity provider
- [ ] Enable HTTPS with valid TLS certificate
- [ ] Configure `RATE_LIMIT_CAPACITY` appropriate for your load
- [ ] Set `MAX_XML_SIZE` appropriate for your use case
- [ ] Review and lock down CORS configuration
- [ ] Enable security audit logging
- [ ] Configure firewall rules
- [ ] Set up security monitoring and alerting
