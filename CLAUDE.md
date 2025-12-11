# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run application (requires Redis running)
./mvnw spring-boot:run

# Start Redis via Docker Compose
docker compose up -d

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=DemoAgentApplicationTests

# Build without tests
./mvnw package -DskipTests

# Clean build
./mvnw clean package
```

## Required Environment Variables

```bash
export DASHSCOPE_API_KEY=<your-dashscope-api-key>
# Optional overrides:
export DASHSCOPE_MODEL=qwen-max
```

## Architecture Overview

This is a Spring Boot 3.5.8 WebFlux application using Spring AI Alibaba's ReactAgent framework to create an AI-powered book reading assistant.

### Key Components

- **BookAgentConfig** (`book/BookAgentConfig.java`): Configuration class that provides Redis-backed checkpoint saver for session state management.

- **BookAgentFactory** (`book/BookAgentFactory.java`): Factory class that creates and caches ReactAgent instances with different DashScope models. Defines the agent's system prompt for book reading Q&A.

- **BookService** (`book/BookService.java`): Orchestrates agent execution, transforms `ReactAgent.stream()` output into `BookResponseEvent` Flux for SSE streaming.

- **BookController** (`book/BookController.java`): WebFlux REST controller exposing POST `/api/book/ask` with SSE streaming response.

### Data Flow

1. Client sends POST request with question
2. Controller creates reactive stream pipeline
3. BookService calls `bookAgent.stream()` with conversation thread ID
4. Agent uses LLM to generate comprehensive book-related answers
5. Results stream back as Server-Sent Events with status (START, PROGRESS, RESULT, ERROR)

### Infrastructure

- **Redis**: Required for `RedisSaver` checkpoint persistence (session state management)
- **DashScope API**: Alibaba Cloud's model service API (default model: qwen-max)

### API Documentation

Swagger UI available at `http://localhost:8080/swagger-ui.html` when running.

## Tech Stack

- Java 25
- Spring Boot 3.5.8 with WebFlux
- Spring AI Alibaba 1.1.0.0-RC1 (ReactAgent framework)
- Spring AI Alibaba DashScope client
- Redisson for Redis checkpoint storage
- Lombok for boilerplate reduction
