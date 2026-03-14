# 🛒 ShopEcom — Production-Grade E-Commerce Platform

> Full-stack Spring Boot e-commerce with AI recommendations, deployed on AWS EKS with zero-downtime CI/CD pipeline.

![AWS](https://img.shields.io/badge/AWS-EKS-orange?logo=amazon-aws)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-green?logo=spring)
![Jenkins](https://img.shields.io/badge/CI%2FCD-Jenkins-red?logo=jenkins)
![Docker](https://img.shields.io/badge/Container-Docker-blue?logo=docker)
![Terraform](https://img.shields.io/badge/IaC-Terraform-purple?logo=terraform)
![Kubernetes](https://img.shields.io/badge/K8s-EKS-326CE5?logo=kubernetes)
![MySQL](https://img.shields.io/badge/DB-MySQL-4479A1?logo=mysql)
![Redis](https://img.shields.io/badge/Cache-Redis-DC382D?logo=redis)

---

## 🏗️ Architecture

```
Developer (git push)
        ↓ webhook
Jenkins Pipeline (7 stages)
  Checkout → Tests → Maven Build → Docker Build
  → Trivy Scan → Push DockerHub
  → Terraform Apply → Deploy EKS → Health Check
        ↓
AWS EKS — ap-south-1
  ├── ShopEcom App (2→8 pods, HPA)
  ├── MySQL (PersistentVolume 10GB)
  ├── Redis (cache layer)
  └── Monitoring (Prometheus + Grafana)
        ↓
AWS LoadBalancer → Users
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17 + Spring Boot 3.2.3 |
| Frontend | Thymeleaf + Bootstrap 5 |
| Database | MySQL 8.0 (K8s PersistentVolume) |
| Cache | Redis 7.2 (product listings, sessions) |
| AI | Groq API — llama-3.3-70b-versatile |
| Security | Spring Security + BCrypt |
| Storage | AWS S3 (product/profile images) |
| CI/CD | Jenkins (7-stage pipeline) |
| IaC | Terraform (VPC + EKS + S3) |
| Orchestration | Kubernetes on AWS EKS |
| Autoscaling | HPA — 2→8 pods |
| Fault Tolerance | Resilience4j circuit breaker |
| Monitoring | Prometheus + Grafana |

---

## ✨ Features

**User Features:**
- Browse products by category with pagination
- AI-powered product recommendations
- Smart search with suggestions
- Shopping cart with quantity management
- Secure checkout with address management
- Order tracking and history
- Email notifications (order updates)
- Password reset via email
- User profile with photo upload

**Admin Features:**
- Product management (CRUD + image upload to S3)
- Category management
- Order management (update status)
- User management
- Sales dashboard

**Production Features:**
- Zero downtime deployments (RollingUpdate, maxUnavailable: 0)
- Auto-scaling 2→8 pods on CPU/memory
- Pod Disruption Budget (always 1+ pod running)
- Circuit breaker (Resilience4j) — app never crashes if AI/email fails
- Redis caching — product pages load in <100ms
- Prometheus metrics + Grafana dashboards
- Liveness + readiness probes on /health and /ready
- S3 for images — survives pod restarts
- All secrets in K8s secrets — nothing hardcoded

---

## 📁 Project Structure

```
shopping-cart-spring-boot/
├── src/main/java/com/ecom/
│   ├── ai/                     # AI recommendation service (Groq)
│   ├── config/                 # Security, Redis, cache config
│   ├── controller/             # Home, User, Admin, Health controllers
│   ├── model/                  # JPA entities
│   ├── repository/             # Spring Data JPA repositories
│   ├── service/                # Business logic + S3 image service
│   └── util/                   # Constants, helpers
├── src/main/resources/
│   ├── templates/              # Thymeleaf HTML templates
│   ├── static/                 # CSS, JS, images
│   └── application.properties  # Config (all secrets from env vars)
├── Dockerfile                  # Multi-stage Java build
├── Jenkinsfile                 # Full CI/CD pipeline
├── terraform/                  # AWS infrastructure as code
│   ├── main.tf                 # VPC + EKS + S3
│   └── variables.tf
├── k8s/                        # Kubernetes manifests
│   ├── deployment.yaml         # App + Service + HPA + PDB
│   ├── mysql.yaml              # MySQL + PVC
│   └── redis.yaml              # Redis cache
└── monitoring/
    └── prometheus-grafana.yaml # Monitoring stack
```

---

## 🔐 Security Highlights

- All secrets injected via K8s secrets and env variables — never in code
- Spring Security with BCrypt password encoding
- Non-root Docker container user
- Trivy security scan on every build
- IAM role on EC2 — no AWS keys stored on disk
- CSRF protection enabled
- Account lockout after failed login attempts

---

## 👤 Author

**Aryan Singh Chauhan**
- GitHub: [@AryanSingh9832](https://github.com/AryanSingh9832)
- Email: arysingh9832@gmail.com
- B.Tech CSE — Galgotias University
