# JDBG Integration Test Environment
#
# Build: docker build -t jdbg-test -f test/Dockerfile .
# Run:   docker run --rm jdbg-test
#
# For coverage:
#   docker run --rm -v $(pwd)/coverage:/jdbg/coverage jdbg-test coverage

FROM eclipse-temurin:17-jdk-jammy AS base

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates build-essential pkg-config libssl-dev git \
    && rm -rf /var/lib/apt/lists/*

# Install Rust and coverage tools
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain stable
ENV PATH="/root/.cargo/bin:${PATH}"
RUN rustup component add llvm-tools-preview \
    && cargo install cargo-llvm-cov

# Install Maven
ARG MAVEN_VERSION=3.9.6
RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    | tar xzf - -C /opt \
    && ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn

WORKDIR /jdbg
COPY . .

# Build everything
FROM base AS builder
RUN cd server && mvn package -DskipTests -q
RUN mkdir -p test/test-target/target/classes \
    && find test/test-target/src -name "*.java" -exec javac -d test/test-target/target/classes -g {} +

# Test runner
FROM base AS test-runner
COPY --from=builder /jdbg /jdbg

ENV JAVA_HOME=/opt/java/openjdk
ENV JDBG_SERVER_JAR=/jdbg/server/target/jdbg-server.jar
ENV TEST_TARGET_CLASSES=/jdbg/test/test-target/target/classes

WORKDIR /jdbg/cli

# Entrypoint: "test" (default) or "coverage"
COPY test/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
CMD ["test"]
