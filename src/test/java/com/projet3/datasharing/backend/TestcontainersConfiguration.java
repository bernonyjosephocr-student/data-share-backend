package com.projet3.datasharing.backend;

import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  private static final String MINIO_ACCESS_KEY = "test-access-key";
  private static final String MINIO_SECRET_KEY = "test-secret-key-12345";
  private static final String MINIO_BUCKET = "datashare-integration-tests";

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
  }

  @Bean(initMethod = "start", destroyMethod = "stop")
  GenericContainer<?> minioContainer() {
    return new GenericContainer<>(
        DockerImageName.parse("cgr.dev/chainguard/minio:latest"))
        .withExposedPorts(9000)
        .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
        .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
        .withCommand("server", "/data")
        .withTmpFs(Map.of("/data", "rw,uid=65532,gid=65532"))
        .waitingFor(Wait.forHttp("/minio/health/ready")
            .forPort(9000)
            .forStatusCode(200));
  }

  @Bean
  DynamicPropertyRegistrar minioProperties(GenericContainer<?> minioContainer) {
    return registry -> {
      registry.add("aws.s3.endpoint",
          () -> "http://" + minioContainer.getHost() + ":"
              + minioContainer.getMappedPort(9000));
      registry.add("aws.s3.access-key", () -> MINIO_ACCESS_KEY);
      registry.add("aws.s3.secret-key", () -> MINIO_SECRET_KEY);
      registry.add("aws.s3.region", () -> "us-east-1");
      registry.add("aws.s3.bucket-name", () -> MINIO_BUCKET);
    };
  }

}
