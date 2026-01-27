# Deployment Guide - Document Intake Service

This guide covers deploying the Document Intake Service to Docker, AWS, and Red Hat OpenShift.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Docker Deployment](#docker-deployment)
- [AWS Deployment](#aws-deployment)
  - [AWS ECS (Fargate)](#aws-ecs-fargate)
  - [AWS EKS (Kubernetes)](#aws-eks-kubernetes)
  - [AWS EC2](#aws-ec2)
- [Red Hat OpenShift Deployment](#red-hat-openshift-deployment)
- [Configuration](#configuration)
- [Monitoring and Health Checks](#monitoring-and-health-checks)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Build Requirements
- Java 21+
- Maven 3.9+
- teda library installed: `cd ../../../../teda && mvn clean install`

### Runtime Dependencies
- PostgreSQL 16+ (database: `intake_db`)
- Apache Kafka 3.0+
- Eureka Discovery Server (optional, port 8761)

### Build the Application

```bash
# Build the JAR
mvn clean package

# Verify the build
ls -lh target/document-intake-service-1.0.0-SNAPSHOT.jar
```

---

## Docker Deployment

### 1. Build Docker Image

```bash
# Build the image
docker build -t document-intake-service:1.0.0 .

# Tag for registry (optional)
docker tag document-intake-service:1.0.0 yourdockerhub/document-intake-service:1.0.0
```

### 2. Run with Docker Compose

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: invoice-intake-db
    environment:
      POSTGRES_DB: intake_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: invoice-kafka
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: invoice-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  document-intake-service:
    image: document-intake-service:1.0.0
    container_name: document-intake-service
    environment:
      # Database
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: intake_db
      DB_USERNAME: postgres
      DB_PASSWORD: postgres

      # Kafka
      KAFKA_BROKERS: kafka:9092

      # Eureka (optional)
      EUREKA_URL: http://eureka:8761/eureka/
      EUREKA_ENABLED: "false"

      # Spring Boot
      SPRING_PROFILES_ACTIVE: docker
    ports:
      - "8081:8081"
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_started
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    restart: unless-stopped

volumes:
  postgres-data:
```

### 3. Run the Stack

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f document-intake-service

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

### 4. Standalone Docker Run

```bash
docker run -d \
  --name document-intake-service \
  -p 8081:8081 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=intake_db \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  -e KAFKA_BROKERS=host.docker.internal:9092 \
  -e EUREKA_ENABLED=false \
  --restart unless-stopped \
  document-intake-service:1.0.0
```

---

## AWS Deployment

### AWS ECS (Fargate)

#### 1. Push Image to Amazon ECR

```bash
# Authenticate to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Create repository
aws ecr create-repository \
  --repository-name document-intake-service \
  --region us-east-1

# Tag and push image
docker tag document-intake-service:1.0.0 \
  <account-id>.dkr.ecr.us-east-1.amazonaws.com/document-intake-service:1.0.0

docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/document-intake-service:1.0.0
```

#### 2. Create ECS Task Definition

Create `task-definition.json`:

```json
{
  "family": "document-intake-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::<account-id>:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::<account-id>:role/ecsTaskRole",
  "containerDefinitions": [
    {
      "name": "document-intake-service",
      "image": "<account-id>.dkr.ecr.us-east-1.amazonaws.com/document-intake-service:1.0.0",
      "cpu": 1024,
      "memory": 2048,
      "essential": true,
      "portMappings": [
        {
          "containerPort": 8081,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "aws"
        },
        {
          "name": "KAFKA_BROKERS",
          "value": "kafka-broker.example.com:9092"
        },
        {
          "name": "EUREKA_ENABLED",
          "value": "false"
        }
      ],
      "secrets": [
        {
          "name": "DB_HOST",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:<account-id>:secret:invoice/db-host"
        },
        {
          "name": "DB_USERNAME",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:<account-id>:secret:invoice/db-username"
        },
        {
          "name": "DB_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:<account-id>:secret:invoice/db-password"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/document-intake-service",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": [
          "CMD-SHELL",
          "wget --quiet --tries=1 --spider http://localhost:8081/actuator/health || exit 1"
        ],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
```

#### 3. Deploy to ECS

```bash
# Register task definition
aws ecs register-task-definition \
  --cli-input-json file://task-definition.json

# Create ECS service
aws ecs create-service \
  --cluster invoice-cluster \
  --service-name document-intake-service \
  --task-definition document-intake-service \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxx,subnet-yyy],securityGroups=[sg-xxx],assignPublicIp=ENABLED}" \
  --load-balancers "targetGroupArn=arn:aws:elasticloadbalancing:us-east-1:<account-id>:targetgroup/invoice-intake-tg,containerName=document-intake-service,containerPort=8081"
```

#### 4. Setup RDS PostgreSQL

```bash
# Create RDS instance
aws rds create-db-instance \
  --db-instance-identifier invoice-intake-db \
  --db-instance-class db.t3.medium \
  --engine postgres \
  --engine-version 16.1 \
  --master-username postgres \
  --master-user-password <secure-password> \
  --allocated-storage 20 \
  --vpc-security-group-ids sg-xxx \
  --db-subnet-group-name invoice-db-subnet \
  --backup-retention-period 7 \
  --publicly-accessible false

# Create database
psql -h <rds-endpoint> -U postgres -c "CREATE DATABASE intake_db;"
```

#### 5. Setup Amazon MSK (Kafka)

```bash
# Create MSK cluster
aws kafka create-cluster \
  --cluster-name invoice-kafka-cluster \
  --broker-node-group-info file://broker-config.json \
  --kafka-version 3.5.1 \
  --number-of-broker-nodes 3
```

### AWS EKS (Kubernetes)

#### 1. Create Kubernetes Manifests

Create `k8s/deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: document-intake-service
  namespace: invoice
  labels:
    app: document-intake-service
    version: v1
spec:
  replicas: 3
  selector:
    matchLabels:
      app: document-intake-service
  template:
    metadata:
      labels:
        app: document-intake-service
        version: v1
    spec:
      containers:
      - name: document-intake-service
        image: <account-id>.dkr.ecr.us-east-1.amazonaws.com/document-intake-service:1.0.0
        imagePullPolicy: Always
        ports:
        - containerPort: 8081
          name: http
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: invoice-db-secret
              key: host
        - name: DB_PORT
          value: "5432"
        - name: DB_NAME
          value: "intake_db"
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: invoice-db-secret
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: invoice-db-secret
              key: password
        - name: KAFKA_BROKERS
          valueFrom:
            configMapKeyRef:
              name: invoice-config
              key: kafka.brokers
        - name: EUREKA_ENABLED
          value: "false"
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
---
apiVersion: v1
kind: Service
metadata:
  name: document-intake-service
  namespace: invoice
  labels:
    app: document-intake-service
spec:
  type: ClusterIP
  ports:
  - port: 8081
    targetPort: 8081
    protocol: TCP
    name: http
  selector:
    app: document-intake-service
---
apiVersion: v1
kind: Secret
metadata:
  name: invoice-db-secret
  namespace: invoice
type: Opaque
stringData:
  host: "<rds-endpoint>"
  username: "postgres"
  password: "<secure-password>"
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: invoice-config
  namespace: invoice
data:
  kafka.brokers: "kafka-broker.invoice.svc.cluster.local:9092"
```

Create `k8s/ingress.yaml`:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: invoice-intake-ingress
  namespace: invoice
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/healthcheck-path: /actuator/health
spec:
  rules:
  - host: invoice-intake.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: document-intake-service
            port:
              number: 8081
```

#### 2. Deploy to EKS

```bash
# Create namespace
kubectl create namespace invoice

# Apply manifests
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/ingress.yaml

# Check deployment
kubectl get pods -n invoice
kubectl get svc -n invoice
kubectl logs -f deployment/document-intake-service -n invoice
```

#### 3. Horizontal Pod Autoscaling

Create `k8s/hpa.yaml`:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: invoice-intake-hpa
  namespace: invoice
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: document-intake-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

```bash
kubectl apply -f k8s/hpa.yaml
```

### AWS EC2

#### 1. Launch EC2 Instance

```bash
# Launch instance
aws ec2 run-instances \
  --image-id ami-xxxxxxxxx \
  --instance-type t3.medium \
  --key-name your-key \
  --security-group-ids sg-xxx \
  --subnet-id subnet-xxx \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=document-intake-service}]' \
  --user-data file://user-data.sh
```

Create `user-data.sh`:

```bash
#!/bin/bash
# Install Docker
yum update -y
yum install -y docker
systemctl start docker
systemctl enable docker

# Install Docker Compose
curl -L "https://github.com/docker/compose/releases/download/v2.20.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# Pull and run the service
docker pull <account-id>.dkr.ecr.us-east-1.amazonaws.com/document-intake-service:1.0.0

docker run -d \
  --name document-intake-service \
  -p 8081:8081 \
  -e DB_HOST=<rds-endpoint> \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=<password> \
  -e KAFKA_BROKERS=<kafka-broker>:9092 \
  --restart always \
  <account-id>.dkr.ecr.us-east-1.amazonaws.com/document-intake-service:1.0.0
```

---

## Red Hat OpenShift Deployment

### 1. Login to OpenShift

```bash
# Login
oc login https://api.openshift-cluster.example.com:6443 --token=<your-token>

# Create project
oc new-project invoice-microservices
```

### 2. Create ImageStream

```bash
# Create ImageStream
oc create imagestream document-intake-service

# Tag the image
oc tag docker.io/yourdockerhub/document-intake-service:1.0.0 \
  document-intake-service:1.0.0
```

### 3. Create OpenShift Resources

Create `openshift/deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: document-intake-service
  namespace: invoice-microservices
  labels:
    app: document-intake-service
    app.kubernetes.io/component: document-intake-service
    app.kubernetes.io/instance: document-intake-service
    app.kubernetes.io/name: document-intake-service
    app.kubernetes.io/part-of: invoice-app
    app.openshift.io/runtime: java
spec:
  replicas: 2
  selector:
    matchLabels:
      app: document-intake-service
  template:
    metadata:
      labels:
        app: document-intake-service
        deployment: document-intake-service
    spec:
      containers:
      - name: document-intake-service
        image: document-intake-service:1.0.0
        imagePullPolicy: Always
        ports:
        - containerPort: 8081
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "openshift"
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: database-host
        - name: DB_PORT
          value: "5432"
        - name: DB_NAME
          value: "intake_db"
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: database-user
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: database-password
        - name: KAFKA_BROKERS
          value: "kafka-cluster-kafka-bootstrap.invoice-microservices.svc.cluster.local:9092"
        - name: EUREKA_ENABLED
          value: "false"
        resources:
          requests:
            cpu: 500m
            memory: 1Gi
          limits:
            cpu: 1000m
            memory: 2Gi
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
            scheme: HTTP
          initialDelaySeconds: 60
          timeoutSeconds: 5
          periodSeconds: 10
          successThreshold: 1
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
            scheme: HTTP
          initialDelaySeconds: 30
          timeoutSeconds: 3
          periodSeconds: 5
          successThreshold: 1
          failureThreshold: 3
---
apiVersion: v1
kind: Service
metadata:
  name: document-intake-service
  namespace: invoice-microservices
  labels:
    app: document-intake-service
spec:
  ports:
  - name: 8081-tcp
    port: 8081
    protocol: TCP
    targetPort: 8081
  selector:
    app: document-intake-service
  type: ClusterIP
---
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: document-intake-service
  namespace: invoice-microservices
  labels:
    app: document-intake-service
spec:
  host: invoice-intake.apps.openshift-cluster.example.com
  to:
    kind: Service
    name: document-intake-service
    weight: 100
  port:
    targetPort: 8081-tcp
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
  wildcardPolicy: None
```

### 4. Create Secrets and ConfigMaps

```bash
# Create database secret
oc create secret generic postgres-secret \
  --from-literal=database-host=postgresql.invoice-microservices.svc.cluster.local \
  --from-literal=database-user=postgres \
  --from-literal=database-password=<secure-password>

# Create ConfigMap
oc create configmap invoice-config \
  --from-literal=kafka.brokers=kafka-cluster-kafka-bootstrap.invoice-microservices.svc.cluster.local:9092
```

### 5. Deploy PostgreSQL on OpenShift

```bash
# Deploy PostgreSQL using template
oc new-app postgresql-persistent \
  -p POSTGRESQL_USER=postgres \
  -p POSTGRESQL_PASSWORD=<secure-password> \
  -p POSTGRESQL_DATABASE=intake_db \
  -p VOLUME_CAPACITY=10Gi
```

### 6. Deploy Kafka on OpenShift (using Strimzi)

```bash
# Install Strimzi Operator (if not already installed)
oc apply -f https://strimzi.io/install/latest?namespace=invoice-microservices

# Create Kafka cluster
cat <<EOF | oc apply -f -
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: kafka-cluster
  namespace: invoice-microservices
spec:
  kafka:
    version: 3.5.1
    replicas: 3
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
      - name: tls
        port: 9093
        type: internal
        tls: true
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
      default.replication.factor: 3
      min.insync.replicas: 2
    storage:
      type: persistent-claim
      size: 20Gi
      deleteClaim: false
  zookeeper:
    replicas: 3
    storage:
      type: persistent-claim
      size: 10Gi
      deleteClaim: false
  entityOperator:
    topicOperator: {}
    userOperator: {}
EOF
```

### 7. Deploy Application

```bash
# Apply deployment
oc apply -f openshift/deployment.yaml

# Check status
oc get pods
oc get svc
oc get route

# View logs
oc logs -f deployment/document-intake-service

# Scale deployment
oc scale deployment/document-intake-service --replicas=3
```

### 8. Build from Source (S2I)

```bash
# Create BuildConfig
oc new-build java:openjdk-21-ubi8~https://github.com/yourusername/invoice-microservices.git \
  --context-dir=services/document-intake-service \
  --name=document-intake-service

# Start build
oc start-build document-intake-service

# Create app from ImageStream
oc new-app document-intake-service
```

### 9. Setup CI/CD Pipeline

Create `openshift/pipeline.yaml`:

```yaml
apiVersion: tekton.dev/v1beta1
kind: Pipeline
metadata:
  name: invoice-intake-pipeline
  namespace: invoice-microservices
spec:
  params:
  - name: git-url
    type: string
    default: https://github.com/yourusername/invoice-microservices.git
  - name: git-revision
    type: string
    default: main
  workspaces:
  - name: shared-workspace
  tasks:
  - name: fetch-repository
    taskRef:
      name: git-clone
      kind: ClusterTask
    workspaces:
    - name: output
      workspace: shared-workspace
    params:
    - name: url
      value: $(params.git-url)
    - name: revision
      value: $(params.git-revision)
  - name: build-maven
    taskRef:
      name: maven
      kind: ClusterTask
    runAfter:
    - fetch-repository
    workspaces:
    - name: source
      workspace: shared-workspace
    params:
    - name: GOALS
      value:
      - clean
      - package
      - -DskipTests
  - name: build-image
    taskRef:
      name: buildah
      kind: ClusterTask
    runAfter:
    - build-maven
    workspaces:
    - name: source
      workspace: shared-workspace
    params:
    - name: IMAGE
      value: image-registry.openshift-image-registry.svc:5000/invoice-microservices/document-intake-service:latest
  - name: deploy
    taskRef:
      name: openshift-client
      kind: ClusterTask
    runAfter:
    - build-image
    params:
    - name: SCRIPT
      value: |
        oc rollout restart deployment/document-intake-service -n invoice-microservices
```

---

## Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DB_HOST` | Yes | `localhost` | PostgreSQL host |
| `DB_PORT` | No | `5432` | PostgreSQL port |
| `DB_NAME` | Yes | `intake_db` | Database name |
| `DB_USERNAME` | Yes | `postgres` | Database username |
| `DB_PASSWORD` | Yes | - | Database password |
| `KAFKA_BROKERS` | Yes | `localhost:9092` | Kafka broker list |
| `EUREKA_URL` | No | `http://localhost:8761/eureka/` | Eureka server URL |
| `EUREKA_ENABLED` | No | `true` | Enable Eureka registration |
| `SPRING_PROFILES_ACTIVE` | No | `default` | Active Spring profile |

### Spring Profiles

- `default`: Local development
- `docker`: Docker deployment
- `aws`: AWS deployment (ECS/EKS)
- `kubernetes`: Kubernetes/EKS
- `openshift`: OpenShift deployment

### Application Properties for Production

Create `src/main/resources/application-prod.yaml`:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

kafka:
  producer:
    retries: 3
    batch-size: 16384
    linger-ms: 10
  consumer:
    max-poll-records: 500
    max-poll-interval-ms: 300000

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics,info
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## Monitoring and Health Checks

### Actuator Endpoints

- `/actuator/health` - Overall health status
- `/actuator/health/liveness` - Liveness probe
- `/actuator/health/readiness` - Readiness probe
- `/actuator/metrics` - Metrics
- `/actuator/prometheus` - Prometheus metrics
- `/actuator/info` - Application info

### Prometheus Configuration

Create `prometheus.yml`:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'document-intake-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['document-intake-service:8081']
```

### Grafana Dashboard

Import dashboard ID `4701` (JVM Micrometer) or create custom dashboard with these metrics:

- `jvm_memory_used_bytes`
- `jvm_threads_live_threads`
- `http_server_requests_seconds_count`
- `http_server_requests_seconds_sum`
- `kafka_producer_record_send_total`
- `hikaricp_connections_active`

---

## Troubleshooting

### Common Issues

#### 1. Database Connection Failures

```bash
# Check database connectivity
docker exec -it document-intake-service sh
wget -qO- http://localhost:8081/actuator/health

# Verify database settings
kubectl get secret postgres-secret -o yaml
```

#### 2. Kafka Connection Issues

```bash
# Test Kafka connectivity
kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic invoice.received --from-beginning

# Check consumer groups
kafka-consumer-groups.sh --bootstrap-server kafka:9092 --list
```

#### 3. OutOfMemoryError

Increase memory limits:

```yaml
# Kubernetes/OpenShift
resources:
  limits:
    memory: "3Gi"

# Docker
docker run -m 3g ...

# ECS
"memory": "3072"
```

#### 4. Slow Startup

Increase health check start period:

```yaml
# Kubernetes
livenessProbe:
  initialDelaySeconds: 90

# Docker
healthcheck:
  start_period: 90s
```

### Logs

```bash
# Docker
docker logs -f document-intake-service

# Kubernetes/OpenShift
kubectl logs -f deployment/document-intake-service
oc logs -f deployment/document-intake-service

# AWS CloudWatch
aws logs tail /ecs/document-intake-service --follow
```

### Debug Mode

```bash
# Enable debug logging
docker run -e LOGGING_LEVEL_ROOT=DEBUG ...

# Kubernetes
kubectl set env deployment/document-intake-service LOGGING_LEVEL_ROOT=DEBUG
```

---

## Security Considerations

### 1. Use Secrets Management

- AWS: AWS Secrets Manager or Systems Manager Parameter Store
- Kubernetes: Kubernetes Secrets + encryption at rest
- OpenShift: OpenShift Secrets + sealed-secrets

### 2. Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: invoice-intake-network-policy
spec:
  podSelector:
    matchLabels:
      app: document-intake-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: nginx-ingress
    ports:
    - protocol: TCP
      port: 8081
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: postgresql
    ports:
    - protocol: TCP
      port: 5432
  - to:
    - podSelector:
        matchLabels:
          app: kafka
    ports:
    - protocol: TCP
      port: 9092
```

### 3. Run as Non-Root

Already configured in Dockerfile:

```dockerfile
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
```

---

## Performance Tuning

### JVM Options

```bash
JAVA_OPTS="-XX:+UseG1GC \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseStringDeduplication \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -Djava.security.egd=file:/dev/./urandom"
```

### Database Connection Pool

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

### Kafka Tuning

```yaml
kafka:
  producer:
    batch-size: 32768
    linger-ms: 10
    buffer-memory: 67108864
```

---

## Backup and Disaster Recovery

### Database Backups

```bash
# AWS RDS automated backups
aws rds modify-db-instance \
  --db-instance-identifier invoice-intake-db \
  --backup-retention-period 7

# Manual backup
pg_dump -h <db-host> -U postgres intake_db > backup.sql
```

### Application State

- Kafka offsets are automatically managed by consumer groups
- Ensure persistent volumes for PostgreSQL
- Regular snapshots of EBS volumes (AWS) or PVs (Kubernetes/OpenShift)

---

## Scaling Guidelines

- **Light load (< 100 req/s)**: 2 replicas, 1 CPU, 2GB RAM
- **Medium load (100-500 req/s)**: 3-5 replicas, 2 CPU, 2GB RAM
- **Heavy load (> 500 req/s)**: 5-10 replicas, 2 CPU, 3GB RAM, enable HPA

---

For additional information, see:
- [README.md](../README.md)
- [PROGRAM_FLOW.md](PROGRAM_FLOW.md)