package com.payments.integration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.TransportConfig;

import java.net.URI;

/**
 * Custom Testcontainers strategy for Docker Desktop 29.x on Mac.
 * Docker Desktop 29.x dropped support for API versions below 1.40.
 * This strategy forces API version 1.44 on /var/run/docker.sock.
 */
public class DockerDesktop29Strategy extends DockerClientProviderStrategy {

    private static final String DOCKER_SOCKET = "unix:///var/run/docker.sock";

    @Override
    public TransportConfig getTransportConfig() {
        return TransportConfig.builder()
                .dockerHost(URI.create(DOCKER_SOCKET))
                .build();
    }

    @Override
    public DockerClient getDockerClient() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(DOCKER_SOCKET)
                .withApiVersion("1.44")
                .build();

        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(URI.create(DOCKER_SOCKET))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    @Override
    public String getDescription() {
        return "Docker Desktop 29.x strategy using API v1.44 on " + DOCKER_SOCKET;
    }

    @Override
    protected boolean isApplicable() {
        return true;
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE; // Highest priority — try this first
    }
}
