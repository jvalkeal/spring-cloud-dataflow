/*
 * Copyright 2019-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.integration.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.assertj.core.api.Condition;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.integration.test.tags.DockerCompose;
import org.springframework.cloud.dataflow.integration.test.util.DockerComposeFactory;
import org.springframework.cloud.dataflow.integration.test.util.DockerComposeFactoryProperties;
import org.springframework.cloud.dataflow.integration.test.util.ResourceExtractor;
import org.springframework.cloud.dataflow.integration.test.util.RuntimeApplicationHelper;
import org.springframework.cloud.dataflow.integration.test.util.SkipSslRestHelper;
import org.springframework.cloud.dataflow.rest.client.DataFlowClientException;
import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.client.dsl.DeploymentPropertiesBuilder;
import org.springframework.cloud.dataflow.rest.client.dsl.Stream;
import org.springframework.cloud.dataflow.rest.client.dsl.StreamApplication;
import org.springframework.cloud.dataflow.rest.client.dsl.StreamDefinition;
import org.springframework.cloud.dataflow.rest.client.dsl.task.Task;
import org.springframework.cloud.dataflow.rest.client.dsl.task.TaskBuilder;
import org.springframework.cloud.dataflow.rest.resource.DetailedAppRegistrationResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionStatus;
import org.springframework.cloud.dataflow.rest.resource.about.AboutResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DataFlow smoke tests that by default uses docker-compose files to install the Data Flow local platform:
 *  - https://dataflow.spring.io/docs/installation/local/docker/
 *  - https://dataflow.spring.io/docs/installation/local/docker-customize/
 * The Palantir DockerMachine and DockerComposeExtension are used to programmatically deploy the docker-compose files.
 *
 * The {@link DockerComposeFactoryProperties} properties and variables are used to configure the {@link DockerComposeFactory}.
 *
 * The {@link DockerComposeFactoryProperties#TEST_DOCKER_COMPOSE_PATHS} property allow to configure the list of docker-compose files
 * used for the test. It accepts a comma separated list of docker-compose yaml file names. It supports local files names
 * as well  http:/https:, classpath: or specific file: locations. Consult the {@link ResourceExtractor} for further
 * information.
 *
 * The {@link DockerComposeFactoryProperties#TEST_DOCKER_COMPOSE_DATAFLOW_VERSIONN},
 * {@link DockerComposeFactoryProperties#TEST_DOCKER_COMPOSE_SKIPPER_VERSIONN},
 * {@link DockerComposeFactoryProperties#TEST_DOCKER_COMPOSE_STREAM_APPS_URI},
 * {@link DockerComposeFactoryProperties#TEST_DOCKER_COMPOSE_TASK_APPS_URI}
 * properties specify the dataflow/skipper versions as well as the version of the Apps and Tasks used.
 *
 * Set the {@link DockerComposeFactoryProperties#TEST_DOCKER_COMPOSE_PULLONSTARTUP} to false to use the local docker images instead
 * of pulling latest on from the Docker Hub.
 *
 * Logs for all docker containers (expect deployed apps) are saved under target/dockerLogs/DockerComposeIT.
 *
 * The Data Flow REST API (https://docs.spring.io/spring-cloud-dataflow/docs/current/reference/htmlsingle/#api-guide),
 * Java REST Clients (such as DataFlowTemplate, RuntimeOperations, TaskOperations) and the
 * Java DSL (https://dataflow.spring.io/docs/feature-guides/streams/java-dsl/) are used by the tests to interact with
 * the Data Flow environment.
 *
 * When the {@link DockerComposeFactoryProperties#TEST_DOCKER_COMPOSE_DISABLE_EXTENSION} is set to true the
 * Docker Compose installation is skipped. In this case the {@link IntegrationTestProperties#getPlatform#getConnection} should be
 * used to connect the IT tests to an external pre-configured SCDF server.
 *
 * For example to run the following test suite against SCDF Kubernetes cluster deployed on GKE:
 * <code>
 *    ./mvnw clean install -pl spring-cloud-dataflow-server -Dtest=foo -DfailIfNoTests=false \
 *        -Dtest.docker.compose.disable.extension=true \
 *        -Dtest.platform.connection.dataflowServerUrl=https://scdf-server.gke.io \
 *        -Pfailsafe
 * </code>
 *
 * The {@link Awaitility} is DSL utility that allows to timeout block the test execution until certain stream or application
 * state is reached or certain log content appears.
 *
 * The {@link RuntimeApplicationHelper} help to retrieve the application attributes and log files across the Local,
 * CF and K8s platforms.
 *
 * NOTE: if you manually interrupt the test execution before it has completed of failed, it is likely that some docker
 * containers will be left hanging. Use 'docker rm $(docker ps -a -q) -f' to remove all docker containers. To clean all
 * Spring app on K8s platform use 'kubectl delete all,cm -l role=spring-app'
 *
 * --------------------------------------------------------------------------------------------------------------------
 * For testing streams on remote platforms (k8s and CF). If you configure K8s or CF runtime platforms as explained below,
 * you can have the test feature that uses the local run SCDF/Skipper/MySQL to deploy and run Stream only test to the
 * remote K8s or CF environments. Note that Tasks can only be run locally!
 *
 * Follow the https://dataflow.spring.io/docs/installation/local/docker-customize/#multi-platform-support
 * multi-platform instructions to prepare docker-compose-k8s.yml and docker-compose-cf.yml files.
 *
 * Stream tests on Kubernetes (k8s) platform:
 * - Add the docker-compose-k8s.yml to the DOCKER_COMPOSE_PATHS list.
 * - Start Kafka message broker on the k8s cluster. Follow the kubectl DataFlow instructions:
 *   https://dataflow.spring.io/docs/installation/kubernetes/kubectl/#choose-a-message-broker
 * - Set the TEST_PLATFORM_NAME to 'k8s'.
 * - In the DockerMachine configuration set the STREAM_APPS_URI variable to link loading Kafka/Docker apps (e.g
 *   https://dataflow.spring.io/rabbitmq-maven-latest).
 *
 * Stream tests on CloudFoundry (CF) platform:
 * - Add the docker-compose-cf.yml to the DOCKER_COMPOSE_PATHS list.
 * - On the CF platform start a RabbitMQ service called 'rabbit'.
 * - Set the TEST_PLATFORM_NAME to 'cf'.
 * - In the DockerMachine configuration set the STREAM_APPS_URI variable to link loading Rabbit/Maven apps. (e.g.
 *   https://dataflow.spring.io/rabbitmq-maven-latest)
 *
 * @author Christian Tzolov
 */
@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties({ IntegrationTestProperties.class })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DockerCompose
public class DataFlowIT {

	private static final Logger logger = LoggerFactory.getLogger(DataFlowIT.class);

	@Autowired
	protected IntegrationTestProperties testProperties;

	/**
	 * REST and DSL clients used to interact with the SCDF server and run the tests.
	 */
	protected DataFlowTemplate dataFlowOperations;
	protected RuntimeApplicationHelper runtimeApps;
	protected RestTemplate restTemplate;

	/**
	 * Folder that collects the external docker-compose YAML files such as
	 * coming from external classpath, http/https or file locations.
	 */
	static Path tempYamlFolder = DockerComposeFactory.createTempDirectory();

	/**
	 * A JUnit 5 extension to bring up Docker containers defined in docker-compose-xxx.yml files before running tests.
	 * You can set either test.docker.compose.disable.extension property of DISABLE_DOCKER_COMPOSE_EXTENSION variable to
	 * disable the extension.
	 */
	@RegisterExtension
	public static Extension dockerCompose = DockerComposeFactory.startDockerCompose(tempYamlFolder);

	@AfterAll
	public static void afterAll() {
		if (tempYamlFolder != null && tempYamlFolder.toFile().exists()) {
			tempYamlFolder.toFile().delete();
		}
	}

	@BeforeEach
	public void before() {
		dataFlowOperations = SkipSslRestHelper
				.dataFlowTemplate(testProperties.getPlatform().getConnection().getDataflowServerUrl());
		runtimeApps = new RuntimeApplicationHelper(dataFlowOperations,
				testProperties.getPlatform().getConnection().getPlatformName());
		restTemplate = SkipSslRestHelper.restTemplate(); // used for HTTP post in tests

		Awaitility.setDefaultPollInterval(Duration.ofSeconds(5));
		Awaitility.setDefaultTimeout(Duration.ofMinutes(15));
	}

	@AfterEach
	public void after() {
		dataFlowOperations.streamOperations().destroyAll();
		dataFlowOperations.taskOperations().destroyAll();
	}

	@Test
	@Order(Integer.MIN_VALUE)
	public void aboutTestInfo() {
		logger.info("Available platforms: " + dataFlowOperations.streamOperations().listPlatforms().stream()
				.map(d -> String.format("[name: %s, type: %s]", d.getName(), d.getType())).collect(Collectors.joining()));
		logger.info(String.format("Selected platform: [name: %s, type: %s]", runtimeApps.getPlatformName(), runtimeApps.getPlatformType()));
		logger.info("Wait until at least 60 apps are registered in SCDF");
		Awaitility.await().until(() -> dataFlowOperations.appRegistryOperations().list().getMetadata().getTotalElements() >= 60L);
	}

	@Test
	public void applicationMetadataMavenTests() {
		logger.info("application-metadata-maven-test");

		try {
			// Maven app with metadata
			DetailedAppRegistrationResource mavenAppWithJarMetadata = dataFlowOperations.appRegistryOperations()
					.info("file", ApplicationType.sink, false);
			assertThat(mavenAppWithJarMetadata.getOptions()).hasSize(8);

			// Maven app without metadata
			dataFlowOperations.appRegistryOperations().register("maven-app-without-metadata", ApplicationType.sink,
					"maven://org.springframework.cloud.stream.app:file-sink-kafka:2.1.1.RELEASE", null, true);
			DetailedAppRegistrationResource mavenAppWithoutMetadata = dataFlowOperations.appRegistryOperations()
					.info("maven-app-without-metadata", ApplicationType.sink, false);
			assertThat(mavenAppWithoutMetadata.getOptions()).hasSize(8);
		}
		finally {
			// unregister the test apps
			dataFlowOperations.appRegistryOperations().unregister("maven-app-without-metadata", ApplicationType.sink);
		}
	}

	@Test
	@DisabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "cloudfoundry")
	public void applicationMetadataDockerTests() {
		logger.info("application-metadata-docker-test");

		try {
			// Docker app with container image metadata
			dataFlowOperations.appRegistryOperations().register("docker-app-with-container-metadata", ApplicationType.source,
					"docker:springcloudstream/time-source-kafka:2.1.4.RELEASE", null, true);
			DetailedAppRegistrationResource dockerAppWithContainerMetadata = dataFlowOperations.appRegistryOperations()
					.info("docker-app-with-container-metadata", ApplicationType.source, false);
			assertThat(dockerAppWithContainerMetadata.getOptions()).hasSize(6);

			// Docker app with container image metadata with escape characters.
			dataFlowOperations.appRegistryOperations().register("docker-app-with-container-metadata-escape-chars", ApplicationType.source,
					"docker:springcloudstream/http-source-rabbit:2.1.3.RELEASE", null, true);
			DetailedAppRegistrationResource dockerAppWithContainerMetadataWithEscapeChars = dataFlowOperations.appRegistryOperations()
					.info("docker-app-with-container-metadata-escape-chars", ApplicationType.source, false);
			assertThat(dockerAppWithContainerMetadataWithEscapeChars.getOptions()).hasSize(6);

			// Docker app without metadata
			dataFlowOperations.appRegistryOperations().register("docker-app-without-metadata", ApplicationType.sink,
					"docker:springcloudstream/file-sink-kafka:2.1.1.RELEASE", null, true);
			DetailedAppRegistrationResource dockerAppWithoutMetadata = dataFlowOperations.appRegistryOperations()
					.info("docker-app-without-metadata", ApplicationType.sink, false);
			assertThat(dockerAppWithoutMetadata.getOptions()).hasSize(0);

			// Docker app with jar metadata
			dataFlowOperations.appRegistryOperations().register("docker-app-with-jar-metadata", ApplicationType.sink,
					"docker:springcloudstream/file-sink-kafka:2.1.1.RELEASE",
					"maven://org.springframework.cloud.stream.app:file-sink-kafka:jar:metadata:2.1.1.RELEASE", true);
			DetailedAppRegistrationResource dockerAppWithJarMetadata = dataFlowOperations.appRegistryOperations()
					.info("docker-app-with-jar-metadata", ApplicationType.sink, false);
			assertThat(dockerAppWithJarMetadata.getOptions()).hasSize(8);
		}
		finally {
			// unregister the test apps
			dataFlowOperations.appRegistryOperations().unregister("docker-app-with-container-metadata", ApplicationType.source);
			dataFlowOperations.appRegistryOperations().unregister("docker-app-with-container-metadata-escape-chars", ApplicationType.source);
			dataFlowOperations.appRegistryOperations().unregister("docker-app-without-metadata", ApplicationType.sink);
			dataFlowOperations.appRegistryOperations().unregister("docker-app-with-jar-metadata", ApplicationType.sink);
		}

	}

	// -----------------------------------------------------------------------
	//                          PLATFORM  TESTS
	// -----------------------------------------------------------------------
	@Test
	public void featureInfo() {
		logger.info("platform-feature-info-test");
		AboutResource about = dataFlowOperations.aboutOperation().get();
		assertThat(about.getFeatureInfo().isAnalyticsEnabled()).isTrue();
		assertThat(about.getFeatureInfo().isStreamsEnabled()).isTrue();
		assertThat(about.getFeatureInfo().isTasksEnabled()).isTrue();
	}

	@Test
	public void appsCount() {
		logger.info("platform-apps-count-test");
		assertThat(dataFlowOperations.appRegistryOperations().list().getMetadata().getTotalElements())
				.isGreaterThanOrEqualTo(60L);
	}

	// -----------------------------------------------------------------------
	//                            STREAM  TESTS
	// -----------------------------------------------------------------------
	/**
	 * Target Data FLow platform to use for the testing: https://dataflow.spring.io/docs/concepts/architecture/#platforms
	 *
	 * By default the Local (e.g. platformName=default) Data Flow environment is used for testing. If you have
	 * provisioned docker-compose file to add remote access ot CF or K8s environments you can use the target
	 * platform/account name instead.
	 */
	private static final String SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME = "spring.cloud.dataflow.skipper.platformName";

	// Stream lifecycle states
	public static final String DEPLOYED = "deployed";
	public static final String DELETED = "deleted";
	public static final String UNDEPLOYED = "undeployed";
	public static final String DEPLOYING = "deploying";
	public static final String PARTIAL = "partial";

	@Test
	public void streamTransform() {
		logger.info("stream-transform-test");
		try (Stream stream = Stream.builder(dataFlowOperations)
				.name("transform-test")
				.definition("http | transform --expression=payload.toUpperCase() | log")
				.create()
				.deploy(testDeploymentProperties())) {

			assertThat(stream.getStatus()).is(
					condition(status -> status.equals(DEPLOYING) || status.equals(PARTIAL)));

			Awaitility.await().until(() -> stream.getStatus().equals(DEPLOYED));

			String message = "Unique Test message: " + new Random().nextInt();

			runtimeApps.httpPost(stream.getName(), "http", message);

			Awaitility.await().until(() -> stream.logs(app("log")).contains(message.toUpperCase()));
		}
	}

	@Test
	public void streamPartitioning() {
		logger.info("stream-partitioning-test (aka. WoodChuckTests)");
		StreamDefinition streamDefinition = Stream.builder(dataFlowOperations)
				.name("partitioning-test")
				.definition("http | splitter --expression=payload.split(' ') | log")
				.create();

		try (Stream stream = streamDefinition.deploy(new DeploymentPropertiesBuilder()
				.putAll(testDeploymentProperties())
				.put(SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME, runtimeApps.getPlatformName())
				// Create 2 log instances with partition key computed from the payload.
				.put("deployer.log.count", "2")
				.put("app.splitter.producer.partitionKeyExpression", "payload")
				.put("app.log.spring.cloud.stream.kafka.bindings.input.consumer.autoRebalanceEnabled", "false")
				.put("app.log.logging.pattern.level", "WOODCHUCK-${INSTANCE_INDEX:${CF_INSTANCE_INDEX:${spring.cloud.stream.instanceIndex:666}}} %5p")
				.build())) {

			Awaitility.await().until(() -> stream.getStatus().equals(DEPLOYED));

			String message = "How much wood would a woodchuck chuck if a woodchuck could chuck wood";
			runtimeApps.httpPost(stream.getName(), "http", message);

			Awaitility.await().until(() -> {
				Collection<String> logs = runtimeApps.applicationInstanceLogs(stream.getName(), "log").values();

				return (logs.size() == 2) && logs.stream()
						// partition order is undetermined
						.map(log -> (log.contains("WOODCHUCK-0")) ?
								asList("WOODCHUCK-0", "How", "chuck").stream().allMatch(log::contains) :
								asList("WOODCHUCK-1", "much", "wood", "would", "if", "a", "woodchuck", "could").stream().allMatch(log::contains))
						.reduce(Boolean::logicalAnd)
						.orElse(false);
			});
		}
	}

	@Test
	public void streamLifecycle() {
		logger.info("stream-lifecycle-test: DEPLOY");
		try (Stream stream = Stream.builder(dataFlowOperations)
				.name("lifecycle-test" + randomSuffix())
				.definition("time | log --log.name=\"\" --log.expression='TICKTOCK - TIMESTAMP: '.concat(payload)")
				.create()
				.deploy(testDeploymentProperties())) {

			assertThat(stream.getStatus()).is(
					condition(status -> status.equals(DEPLOYING) || status.equals(PARTIAL)));

			Awaitility.await().until(() -> stream.getStatus().equals(DEPLOYED));

			Awaitility.await().until(
					() -> stream.logs(app("log")).contains("TICKTOCK - TIMESTAMP:"));

			assertThat(stream.history().size()).isEqualTo(1L);
			Awaitility.await().until(() -> stream.history().get(1).equals(DEPLOYED));

			assertThat(stream.logs()).contains("TICKTOCK - TIMESTAMP:");
			assertThat(stream.logs(app("log"))).contains("TICKTOCK - TIMESTAMP:");

			// UPDATE
			logger.info("stream-lifecycle-test: UPDATE");
			stream.update(new DeploymentPropertiesBuilder()
					.put("app.log.log.expression", "'Updated TICKTOCK - TIMESTAMP: '.concat(payload)")
					// TODO investigate why on update the app-starters-core overrides the original web.exposure.include!!!
					.put("app.*.management.endpoints.web.exposure.include", "*")
					.build());

			Awaitility.await().until(() -> stream.getStatus().equals(DEPLOYED));

			Awaitility.await().until(
					() -> stream.logs(app("log")).contains("Updated TICKTOCK - TIMESTAMP:"));

			assertThat(stream.history().size()).isEqualTo(2);
			Awaitility.await().until(() -> stream.history().get(1).equals(DELETED));
			Awaitility.await().until(() -> stream.history().get(2).equals(DEPLOYED));

			// ROLLBACK
			logger.info("stream-lifecycle-test: ROLLBACK");
			stream.rollback(0);

			Awaitility.await().until(() -> stream.getStatus().equals(DEPLOYED));
			assertThat(stream.getStatus()).isEqualTo(DEPLOYED);

			Awaitility.await().until(
					() -> stream.logs(app("log")).contains("TICKTOCK - TIMESTAMP:"));

			assertThat(stream.history().size()).isEqualTo(3);
			Awaitility.await().until(() -> stream.history().get(1).equals(DELETED));
			Awaitility.await().until(() -> stream.history().get(2).equals(DELETED));
			Awaitility.await().until(() -> stream.history().get(3).equals(DEPLOYED));

			// UNDEPLOY
			logger.info("stream-lifecycle-test: UNDEPLOY");
			stream.undeploy();

			Awaitility.await().until(() -> stream.getStatus().equals(UNDEPLOYED));
			assertThat(stream.getStatus()).isEqualTo(UNDEPLOYED);

			assertThat(stream.history().size()).isEqualTo(3);
			Awaitility.await().until(() -> stream.history().get(1).equals(DELETED));
			Awaitility.await().until(() -> stream.history().get(2).equals(DELETED));
			Awaitility.await().until(() -> stream.history().get(3).equals(DELETED));

			assertThat(dataFlowOperations.streamOperations().list().getMetadata().getTotalElements()).isEqualTo(1L);
			// DESTROY
		}
		logger.info("stream-lifecycle-test: DESTROY");
		assertThat(dataFlowOperations.streamOperations().list().getMetadata().getTotalElements()).isEqualTo(0L);
	}

	@Test
	public void streamScaling() {
		logger.info("stream-scaling-test");
		try (Stream stream = Stream.builder(dataFlowOperations)
				.name("stream-scaling-test")
				.definition("time | log --log.expression='TICKTOCK - TIMESTAMP: '.concat(payload)")
				.create()
				.deploy(testDeploymentProperties())) {

			Awaitility.await().until(() -> stream.getStatus().equals(DEPLOYED));

			final StreamApplication time = app("time");
			final StreamApplication log = app("log");

			Map<StreamApplication, Map<String, String>> streamApps = stream.runtimeApps();
			assertThat(streamApps.size()).isEqualTo(2);
			assertThat(streamApps.get(time).size()).isEqualTo(1);
			assertThat(streamApps.get(log).size()).isEqualTo(1);

			// Scale up log
			stream.scaleApplicationInstances(log, 2, Collections.emptyMap());

			Awaitility.await().until(() -> stream.getStatus().equals(DEPLOYED));
			Awaitility.await().until(() -> stream.runtimeApps().get(log).size() == 2);

			assertThat(stream.getStatus()).isEqualTo(DEPLOYED);
			streamApps = stream.runtimeApps();
			assertThat(streamApps.size()).isEqualTo(2);
			assertThat(streamApps.get(time).size()).isEqualTo(1);
			assertThat(streamApps.get(log).size()).isEqualTo(2);
		}
	}

	@Test
	public void namedChannelDestination() {
		logger.info("stream-named-channel-destination-test");
		try (
				Stream logStream = Stream.builder(dataFlowOperations)
						.name("log-destination-sink")
						.definition(":LOG-DESTINATION > log")
						.create()
						.deploy(testDeploymentProperties());
				Stream httpStream = Stream.builder(dataFlowOperations)
						.name("http-destination-source")
						.definition("http > :LOG-DESTINATION")
						.create()
						.deploy(testDeploymentProperties())) {

			Awaitility.await().until(() -> logStream.getStatus().equals(DEPLOYED));
			Awaitility.await().until(() -> httpStream.getStatus().equals(DEPLOYED));

			String message = "Unique Test message: " + new Random().nextInt();

			runtimeApps.httpPost(httpStream.getName(), "http", message);

			Awaitility.await().until(() -> logStream.logs(app("log")).contains(message));
		}
	}

	@Test
	public void namedChannelTap() {
		logger.info("stream-named-channel-tap-test");
		try (
				Stream httpLogStream = Stream.builder(dataFlowOperations)
						.name("taphttp")
						.definition("http | log")
						.create()
						.deploy(testDeploymentProperties());
				Stream tapStream = Stream.builder(dataFlowOperations)
						.name("tapstream")
						.definition(":taphttp.http > log")
						.create()
						.deploy(testDeploymentProperties())) {

			Awaitility.await().until(() -> httpLogStream.getStatus().equals(DEPLOYED));
			Awaitility.await().until(() -> tapStream.getStatus().equals(DEPLOYED));

			String message = "Unique Test message: " + new Random().nextInt();

			runtimeApps.httpPost(httpLogStream.getName(), "http", message);

			Awaitility.await().until(() -> tapStream.logs(app("log")).contains(message));
		}
	}

	@Test
	public void namedChannelManyToOne() {
		logger.info("stream-named-channel-many-to-one-test");
		try (
				Stream logStream = Stream.builder(dataFlowOperations)
						.name("many-to-one")
						.definition(":MANY-TO-ONE-DESTINATION > log")
						.create()
						.deploy(testDeploymentProperties());
				Stream httpStreamOne = Stream.builder(dataFlowOperations)
						.name("http-source-1")
						.definition("http > :MANY-TO-ONE-DESTINATION")
						.create()
						.deploy(testDeploymentProperties());
				Stream httpStreamTwo = Stream.builder(dataFlowOperations)
						.name("http-source-2")
						.definition("http > :MANY-TO-ONE-DESTINATION")
						.create()
						.deploy(testDeploymentProperties())) {

			Awaitility.await().until(() -> logStream.getStatus().equals(DEPLOYED));
			Awaitility.await().until(() -> httpStreamOne.getStatus().equals(DEPLOYED));
			Awaitility.await().until(() -> httpStreamTwo.getStatus().equals(DEPLOYED));

			String messageOne = "Unique Test message: " + new Random().nextInt();

			runtimeApps.httpPost(httpStreamOne.getName(), "http", messageOne);

			Awaitility.await().until(
					() -> logStream.logs(app("log")).contains(messageOne));

			String messageTwo = "Unique Test message: " + new Random().nextInt();

			runtimeApps.httpPost(httpStreamTwo.getName(), "http", messageTwo);

			Awaitility.await().until(() -> logStream.logs(app("log")).contains(messageTwo));

		}
	}

	@Test
	public void namedChannelDirectedGraph() {
		logger.info("stream-named-channel-directed-graph-test");
		try (
				Stream fooLogStream = Stream.builder(dataFlowOperations)
						.name("directed-graph-destination1")
						.definition(":foo > transform --expression=payload+'-foo' | log")
						.create()
						.deploy(testDeploymentProperties());
				Stream barLogStream = Stream.builder(dataFlowOperations)
						.name("directed-graph-destination2")
						.definition(":bar > transform --expression=payload+'-bar' | log")
						.create()
						.deploy(testDeploymentProperties());
				Stream httpStream = Stream.builder(dataFlowOperations)
						.name("directed-graph-http-source")
						.definition("http | router --expression=payload.contains('a')?'foo':'bar'")
						.create()
						.deploy(testDeploymentProperties())) {

			Awaitility.await().until(() -> fooLogStream.getStatus().equals(DEPLOYED));
			Awaitility.await().until(() -> barLogStream.getStatus().equals(DEPLOYED));
			Awaitility.await().until(() -> httpStream.getStatus().equals(DEPLOYED));

			String httpAppUrl = runtimeApps.getApplicationInstanceUrl(httpStream.getName(), "http");
			runtimeApps.httpPost(httpAppUrl, "abcd");
			runtimeApps.httpPost(httpAppUrl, "defg");

			Awaitility.await().until(() -> fooLogStream.logs(app("log")).contains("abcd-foo"));
			Awaitility.await().until(() -> barLogStream.logs(app("log")).contains("defg-bar"));
		}
	}

	// -----------------------------------------------------------------------
	//                       STREAM  METRICS TESTS
	// -----------------------------------------------------------------------
	@DisplayName("Test Analytics")
	@Test
	public void analyticsCounter() {

		if (!prometheusPresent() && !influxPresent()) {
			logger.info("stream-analytics-test: SKIP - no metrics configured!");
		}

		Assumptions.assumeTrue(prometheusPresent() || influxPresent());

		logger.info("stream-analytics-test");

		try (Stream stream = Stream.builder(dataFlowOperations)
				.name("httpAnalytics")
				.definition("http | analytics --analytics.name=my_http_analytics --analytics.tag.expression.msgSize=payload.length()")
				.create()
				.deploy(testDeploymentProperties())) {

			Awaitility.await().until(() -> stream.getStatus().equals(DEPLOYED));

			String message1 = "Test message 1"; // length 14
			String message2 = "Test message 2 with extension";  // length 29
			String message3 = "Test message 2 with double extension";  // length 36

			String httpAppUrl = runtimeApps.getApplicationInstanceUrl(stream.getName(), "http");
			runtimeApps.httpPost(httpAppUrl, message1);
			runtimeApps.httpPost(httpAppUrl, message2);
			runtimeApps.httpPost(httpAppUrl, message3);

			// Prometheus tests
			Assumptions.assumingThat(this::prometheusPresent, () -> {
				logger.info("stream-analytics-test: Prometheus");

				// Wait for ~1 min for Micrometer to send first metrics to Prometheus.
				Awaitility.await().until(() -> (int) JsonPath.parse(
						runtimeApps.httpGet(testProperties.getPlatform().getConnection().getPrometheusUrl() + "/api/v1/query?query=my_http_analytics_total"))
						.read("$.data.result.length()") > 0);

				JsonAssertions.assertThatJson(runtimeApps.httpGet(testProperties.getPlatform().getConnection().getPrometheusUrl() + "/api/v1/query?query=my_http_analytics_total"))
						.isEqualTo(resourceToString("classpath:/my_http_analytics_total.json"));
			});

			// InfluxDB tests
			Assumptions.assumingThat(this::influxPresent, () -> {
				logger.info("stream-analytics-test: InfluxDB");

				// Wait for ~1 min for Micrometer to send first metrics to Influx.
				Awaitility.await().until(() -> !JsonPath.parse(runtimeApps.httpGet(testProperties.getPlatform().getConnection().getInfluxUrl() + "/query?db=myinfluxdb&q=SELECT * FROM \"my_http_analytics\""))
						.read("$.results[0][?(@.series)].length()").toString().equals("[]"));

				//http://localhost:8086/query?db=myinfluxdb&q=SELECT%20%22count%22%20FROM%20%22spring_integration_send%22
				//http://localhost:8086/query?db=myinfluxdb&q=SHOW%20MEASUREMENTS

				// http://localhost:8086/query?db=myinfluxdb&q=SELECT%20value%20FROM%20%22message_my_http_counter%22%20GROUP%20BY%20%2A%20ORDER%20BY%20ASC%20LIMIT%201

				// http://localhost:8086/query?q=SHOW%20DATABASES
				JsonAssertions.assertThatJson(runtimeApps.httpGet(testProperties.getPlatform().getConnection().getInfluxUrl() + "/query?q=SHOW DATABASES"))
						.inPath("$.results[0].series[0].values[1][0]")
						.isEqualTo("myinfluxdb");

				List<String> messageLengths = java.util.stream.Stream.of(message1, message2, message3)
						.map(s -> String.format("\"%s\"", s.length())).collect(Collectors.toList());

				// http://localhost:8086/query?db=myinfluxdb&q=SELECT%20%2A%20FROM%20%22my_http_counter%22
				String myHttpCounter = runtimeApps.httpGet(testProperties.getPlatform().getConnection().getInfluxUrl() + "/query?db=myinfluxdb&q=SELECT * FROM \"my_http_analytics\"");
				JsonAssertions.assertThatJson(myHttpCounter).inPath("$.results[0].series[0].values[0][7]").isIn(messageLengths);
				JsonAssertions.assertThatJson(myHttpCounter).inPath("$.results[0].series[0].values[1][7]").isIn(messageLengths);
				JsonAssertions.assertThatJson(myHttpCounter).inPath("$.results[0].series[0].values[2][7]").isIn(messageLengths);
			});
		}
	}

	/**
	 * For the purpose of testing, disable security, expose the all actuators, and configure logfiles.
	 * @return Deployment properties required for the deployment of all test pipelines.
	 */
	protected Map<String, String> testDeploymentProperties() {
		DeploymentPropertiesBuilder propertiesBuilder = new DeploymentPropertiesBuilder()
				.put(SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME, runtimeApps.getPlatformName())
				.put("app.*.logging.file", "/tmp/${PID}-test.log") // Keep it for Boot 2.x compatibility.
				.put("app.*.logging.file.name", "/tmp/${PID}-test.log")
				.put("app.*.endpoints.logfile.sensitive", "false")
				.put("app.*.endpoints.logfile.enabled", "true")
				.put("app.*.management.endpoints.web.exposure.include", "*")
				.put("app.*.spring.cloud.streamapp.security.enabled", "false");

		if (this.runtimeApps.getPlatformType().equalsIgnoreCase(RuntimeApplicationHelper.KUBERNETES_PLATFORM_TYPE)) {
			propertiesBuilder.put("app.*.server.port", "8080");
			propertiesBuilder.put("deployer.*.kubernetes.createLoadBalancer", "true"); // requires LoadBalancer support on the platform
		}

		return propertiesBuilder.build();
	}

	public static String resourceToString(String resourcePath) throws IOException {
		return StreamUtils.copyToString(new DefaultResourceLoader().getResource(resourcePath).getInputStream(), StandardCharsets.UTF_8);
	}

	protected boolean prometheusPresent() {
		return runtimeApps.isServicePresent(testProperties.getPlatform().getConnection().getPrometheusUrl() + "/api/v1/query?query=up");
	}

	protected boolean influxPresent() {
		return runtimeApps.isServicePresent(testProperties.getPlatform().getConnection().getInfluxUrl() + "/ping");
	}

	public static Condition<String> condition(Predicate predicate) {
		return new Condition<>(predicate, "");
	}

	protected StreamApplication app(String appName) {
		return new StreamApplication(appName);
	}

	// -----------------------------------------------------------------------
	//                               TASK TESTS
	// -----------------------------------------------------------------------
	public static final int EXIT_CODE_SUCCESS = 0;
	public static final int EXIT_CODE_ERROR = 1;

	@Test
	@EnabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "local")
	public void runBatchRemotePartitionJobLocal() {
		logger.info("runBatchRemotePartitionJob - local");

		TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
		try (Task task = taskBuilder
				.name(randomTaskName())
				.definition("batch-remote-partition")
				.description("runBatchRemotePartitionJob - local")
				.build()) {

			long launchId = task.launch(Collections.EMPTY_MAP, Arrays.asList("--platform=local"));

			Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE);
			assertThat(task.executions().size()).isEqualTo(1);
			assertThat(task.execution(launchId).isPresent()).isTrue();
			assertThat(task.execution(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
		}
	}

	@Test
	public void timestampTask() {
		logger.info("task-timestamp-test");

		try (Task task = Task.builder(dataFlowOperations)
				.name(randomTaskName())
				.definition("timestamp")
				.description("Test timestamp task")
				.build()) {

			// task first launch
			long launchId1 = task.launch();

			Awaitility.await().until(() -> task.executionStatus(launchId1) == TaskExecutionStatus.COMPLETE);
			assertThat(task.executions().size()).isEqualTo(1);
			assertThat(task.execution(launchId1).isPresent()).isTrue();
			assertThat(task.execution(launchId1).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

			// task second launch
			long launchId2 = task.launch();

			Awaitility.await().until(() -> task.executionStatus(launchId2) == TaskExecutionStatus.COMPLETE);
			assertThat(task.executions().size()).isEqualTo(2);
			assertThat(task.execution(launchId2).isPresent()).isTrue();
			assertThat(task.execution(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

			// All
			task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));
		}
	}

	@Test
	public void composedTask() {
		logger.info("task-composed-task-runner-test");

		TaskBuilder taskBuilder = Task.builder(dataFlowOperations);

		try (Task task = taskBuilder
				.name(randomTaskName())
				.definition("a: timestamp && b:timestamp")
				.description("Test composedTask")
				.build()) {

			assertThat(task.composedTaskChildTasks().size()).isEqualTo(2);

			// first launch

			long launchId1 = task.launch();

			Awaitility.await().until(() -> task.executionStatus(launchId1) == TaskExecutionStatus.COMPLETE);

			assertThat(task.executions().size()).isEqualTo(1);
			assertThat(task.executionStatus(launchId1)).isEqualTo(TaskExecutionStatus.COMPLETE);
			assertThat(task.execution(launchId1).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

			task.composedTaskChildTasks().forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(1);
				assertThat(childTask.executionByParentExecutionId(launchId1).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			});

			task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

			// second launch
			long launchId2 = task.launch();

			Awaitility.await().until(() -> task.executionStatus(launchId2) == TaskExecutionStatus.COMPLETE);

			assertThat(task.executions().size()).isEqualTo(2);
			assertThat(task.executionStatus(launchId2)).isEqualTo(TaskExecutionStatus.COMPLETE);
			assertThat(task.execution(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

			task.composedTaskChildTasks().forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(2);
				assertThat(childTask.executionByParentExecutionId(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			});

			assertThat(taskBuilder.allTasks().size()).isEqualTo(3);
		}
		assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
	}

	@Test
	public void multipleComposedTaskWithArguments() {
		logger.info("task-multiple-composed-task-with-arguments-test");

		TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
		try (Task task = taskBuilder
				.name(randomTaskName())
				.definition("a: timestamp && b:timestamp")
				.description("Test multipleComposedTaskWithArguments")
				.build()) {

			assertThat(task.composedTaskChildTasks().size()).isEqualTo(2);

			// first launch
			List<String> arguments = asList("--increment-instance-enabled=true");
			long launchId1 = task.launch(arguments);

			Awaitility.await().until(() -> task.executionStatus(launchId1) == TaskExecutionStatus.COMPLETE);

			assertThat(task.executions().size()).isEqualTo(1);
			assertThat(task.executionStatus(launchId1)).isEqualTo(TaskExecutionStatus.COMPLETE);
			assertThat(task.execution(launchId1).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

			task.composedTaskChildTasks().forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(1);
				assertThat(childTask.executionByParentExecutionId(launchId1).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			});

			task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

			// second launch
			long launchId2 = task.launch(arguments);

			Awaitility.await().until(() -> task.executionStatus(launchId2) == TaskExecutionStatus.COMPLETE);

			assertThat(task.executions().size()).isEqualTo(2);
			assertThat(task.executionStatus(launchId2)).isEqualTo(TaskExecutionStatus.COMPLETE);
			assertThat(task.execution(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

			task.composedTaskChildTasks().forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(2);
				assertThat(childTask.executionByParentExecutionId(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			});

			assertThat(task.jobExecutionResources().size()).isEqualTo(2);

			assertThat(taskBuilder.allTasks().size()).isEqualTo(3);
		}
		assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
	}

	@Test
	public void ctrLaunchTest() {
		logger.info("composed-task-ctrLaunch-test");

		TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
		try (Task task = taskBuilder
				.name(randomTaskName())
				.definition("a: timestamp && b:timestamp")
				.description("ctrLaunchTest")
				.build()) {

			assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList()))
					.hasSameElementsAs(fullTaskNames(task, "a", "b"));

			long launchId = task.launch();

			Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE);

			// Parent Task Successfully completed
			assertThat(task.executions().size()).isEqualTo(1);
			assertThat(task.executionStatus(launchId)).isEqualTo(TaskExecutionStatus.COMPLETE);
			assertThat(task.execution(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

			// Child tasks successfully completed
			task.composedTaskChildTasks().forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(1);
				assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			});

			// Attempt a job restart
			assertThat(task.executions().size()).isEqualTo(1);
			List<Long> jobExecutionIds = task.executions().stream().findFirst().get().getJobExecutionIds();
			assertThat(jobExecutionIds.size()).isEqualTo(1);

			Exception exception = assertThrows(DataFlowClientException.class, () -> {
				dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0));
			});

			assertTrue(exception.getMessage().contains(" and state 'COMPLETED' is not restartable"));
		}
		assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
	}

	@Test
	public void ctrFailedGraph() {
		logger.info("composed-task-ctrFailedGraph-test");
		mixedSuccessfulFailedAndUnknownExecutions("ctrFailedGraph",
				"scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false && timestamp",
				emptyList(), // successful
				asList("scenario"),  // failed
				asList("timestamp")); // not-run
	}

	@Test
	public void ctrSplit() {
		logger.info("composed-task-split-test");
		allSuccessfulExecutions("ComposedTask Split Test",
				"<t1:timestamp || t2:timestamp || t3:timestamp>",
				"t1", "t2", "t3");
	}

	@Test
	public void ctrSequential() {
		logger.info("composed-task-sequential-test");
		allSuccessfulExecutions("ComposedTask Sequential Test",
				"t1:timestamp && t2:timestamp && t3:timestamp",
				"t1", "t2", "t3");
	}

	@Test
	public void ctrSequentialTransitionAndSplitWithScenarioFailed() {
		logger.info("composed-task-SequentialTransitionAndSplitWithScenarioFailed-test");
		mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Transition And Split With Scenario Failed Test",
				"t1: timestamp && scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED'->t3: timestamp && <t4: timestamp || t5: timestamp> && t6: timestamp",
				asList("t1", "t3"), // successful
				asList("scenario"),  // failed
				asList("t4", "t5", "t6")); // not-run
	}

	@Test
	public void ctrSequentialTransitionAndSplitWithScenarioOk() {
		logger.info("composed-task-SequentialTransitionAndSplitWithScenarioOk-test");
		mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Transition And Split With Scenario Ok Test",
				"t1: timestamp && t2: scenario 'FAILED'->t3: timestamp && <t4: timestamp || t5: timestamp> && t6: timestamp",
				asList("t1", "t2", "t4", "t5", "t6"), // successful
				emptyList(),  // failed
				asList("t3")); // not-run
	}

	@Test
	public void ctrNestedSplit() {
		logger.info("composed-task-NestedSplit");
		allSuccessfulExecutions("ctrNestedSplit",
				"<<t1: timestamp || t2: timestamp > && t3: timestamp || t4: timestamp>",
				"t1", "t2", "t3", "t4");
	}

	@Test
	public void testEmbeddedFailedGraph() {
		logger.info("composed-task-EmbeddedFailedGraph-test");
		mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Embedded Failed Graph Test",
				String.format("a: timestamp && b:scenario  --io.spring.fail-batch=true --io.spring.jobName=%s --spring.cloud.task.batch.fail-on-job-failure=true && c:timestamp", randomJobName()),
				asList("a"), // successful
				asList("b"),  // failed
				asList("c")); // not-run
	}

	@Test
	public void twoSplitTest() {
		logger.info("composed-task-twoSplit-test");
		allSuccessfulExecutions("twoSplitTest",
				"<t1: timestamp ||t2: timestamp||t3: timestamp> && <t4: timestamp||t5: timestamp>",
				"t1", "t2", "t3", "t4", "t5");
	}

	@Test
	public void sequentialAndSplitTest() {
		logger.info("composed-task-sequentialAndSplit-test");
		allSuccessfulExecutions("sequentialAndSplitTest",
				"<t1: timestamp && <t2: timestamp || t3: timestamp || t4: timestamp> && t5: timestamp>",
				"t1", "t2", "t3", "t4", "t5");
	}

	@Test
	public void sequentialTransitionAndSplitFailedInvalidTest() {
		logger.info("composed-task-sequentialTransitionAndSplitFailedInvalid-test");
		mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Transition And Split Failed Invalid Test",
				"t1: timestamp && b:scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED' -> t2: timestamp && t3: timestamp && t4: timestamp && <t5:timestamp || t6: timestamp> && t7: timestamp",
				asList("t1", "t2"), // successful
				asList("b"),  // failed
				asList("t3", "t4", "t5", "t6", "t7")); // not-run
	}

	@Test
	public void sequentialAndSplitWithFlowTest() {
		logger.info("composed-task-sequentialAndSplitWithFlow-test");
		allSuccessfulExecutions("sequentialAndSplitWithFlowTest",
				"t1: timestamp && <t2: timestamp && t3: timestamp || t4: timestamp ||t5: timestamp> && t6: timestamp",
				"t1", "t2", "t3", "t4", "t5", "t6");
	}

	@Test
	public void sequentialAndFailedSplitTest() {
		logger.info("composed-task-sequentialAndFailedSplit-test");

		TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
		try (Task task = taskBuilder
				.name(randomTaskName())
				.definition(String.format("t1: timestamp && <t2: timestamp ||b:scenario --io.spring.fail-batch=true --io.spring.jobName=%s --spring.cloud.task.batch.fail-on-job-failure=true || t3: timestamp> && t4: timestamp", randomJobName()))
				.description("sequentialAndFailedSplitTest")
				.build()) {

			assertThat(task.composedTaskChildTasks().size()).isEqualTo(5);
			assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList()))
					.hasSameElementsAs(fullTaskNames(task, "b", "t1", "t2", "t3", "t4"));

			long launchId = task.launch();

			Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE);

			// Parent Task
			assertThat(task.executions().size()).isEqualTo(1);
			assertThat(task.executionStatus(launchId)).isEqualTo(TaskExecutionStatus.COMPLETE);
			assertThat(task.execution(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

			// Successful
			childTasksBySuffix(task, "t1", "t2", "t3").forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(1);
				assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			});

			// Failed tasks
			childTasksBySuffix(task, "b").forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(1);
				assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_ERROR);
			});

			// Not run tasks
			childTasksBySuffix(task, "t4").forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(0);
			});

			// Parent Task
			assertThat(taskBuilder.allTasks().size()).isEqualTo(task.composedTaskChildTasks().size() + 1);

			// restart job
			assertThat(task.executions().size()).isEqualTo(1);
			List<Long> jobExecutionIds = task.executions().stream().findFirst().get().getJobExecutionIds();
			assertThat(jobExecutionIds.size()).isEqualTo(1);
			dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0));

			long launchId2 = task.executions().stream().mapToLong(TaskExecutionResource::getExecutionId).max().getAsLong();

			Awaitility.await().until(() -> task.executionStatus(launchId2) == TaskExecutionStatus.COMPLETE);

			assertThat(task.executions().size()).isEqualTo(2);
			assertThat(task.executionStatus(launchId2)).isEqualTo(TaskExecutionStatus.COMPLETE);
			assertThat(task.execution(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

			childTasksBySuffix(task, "b").forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(2);
				assertThat(childTask.executionByParentExecutionId(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			});

			childTasksBySuffix(task, "t4").forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(1);
				assertThat(childTask.executionByParentExecutionId(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			});

			assertThat(task.jobExecutionResources().size()).isEqualTo(2);
		}
		assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
	}

	@Test
	public void failedBasicTransitionTest() {
		logger.info("composed-task-failedBasicTransition-test");
		mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Failed Basic Transition Test",
				"b: scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED' -> t1: timestamp * ->t2: timestamp",
				asList("t1"), // successful
				asList("b"),  // failed
				asList("t2")); // not-run
	}

	@Test
	public void successBasicTransitionTest() {
		logger.info("composed-task-successBasicTransition-test");
		mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Success Basic Transition Test",
				"b: scenario --io.spring.launch-batch-job=false 'FAILED' -> t1: timestamp * ->t2: timestamp",
				asList("b", "t2"), // successful
				emptyList(),  // failed
				asList("t1")); // not-run
	}

	@Test
	public void basicTransitionWithTransitionTest() {
		logger.info("composed-task-basicTransitionWithTransition-test");
		mixedSuccessfulFailedAndUnknownExecutions("basicTransitionWithTransitionTest",
				"b1: scenario  --io.spring.launch-batch-job=false 'FAILED' -> t1: timestamp  && b2: scenario --io.spring.launch-batch-job=false 'FAILED' -> t2: timestamp * ->t3: timestamp ",
				asList("b1", "b2", "t3"), // successful
				emptyList(),  // failed
				asList("t1", "t2")); // not-run
	}

	@Test
	public void wildCardOnlyInLastPositionTest() {
		logger.info("composed-task-wildCardOnlyInLastPosition-test");
		mixedSuccessfulFailedAndUnknownExecutions("wildCardOnlyInLastPositionTest",
				"b1: scenario --io.spring.launch-batch-job=false 'FAILED' -> t1: timestamp  && b2: scenario --io.spring.launch-batch-job=false * ->t3: timestamp ",
				asList("b1", "b2", "t3"), // successful
				emptyList(),  // failed
				asList("t1")); // not-run
	}

	@Test
	public void failedCTRRetryTest() {
		logger.info("composed-task-failedCTRRetry-test");

		TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
		try (Task task = taskBuilder
				.name(randomTaskName())
				.definition(String.format("b1:scenario --io.spring.fail-batch=true --io.spring.jobName=%s --spring.cloud.task.batch.fail-on-job-failure=true && t1:timestamp", randomJobName()))
				.description("failedCTRRetryTest")
				.build()) {

			assertThat(task.composedTaskChildTasks().size()).isEqualTo(2);
			assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList()))
					.hasSameElementsAs(fullTaskNames(task, "b1", "t1"));

			long launchId = task.launch();

			Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE);

			// Parent Task
			assertThat(task.executions().size()).isEqualTo(1);
			assertThat(task.executionStatus(launchId)).isEqualTo(TaskExecutionStatus.COMPLETE);
			assertThat(task.execution(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

			// Failed tasks
			childTasksBySuffix(task, "b1").forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(1);
				assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_ERROR);
			});

			// Not run tasks
			childTasksBySuffix(task, "t1").forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(0);
			});

			// Parent Task
			assertThat(taskBuilder.allTasks().size()).isEqualTo(task.composedTaskChildTasks().size() + 1);

			// restart job
			assertThat(task.executions().size()).isEqualTo(1);
			List<Long> jobExecutionIds = task.executions().stream().findFirst().get().getJobExecutionIds();
			assertThat(jobExecutionIds.size()).isEqualTo(1);
			dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0));

			long launchId2 = task.executions().stream().mapToLong(TaskExecutionResource::getExecutionId).max().getAsLong();

			Awaitility.await().until(() -> task.executionStatus(launchId2) == TaskExecutionStatus.COMPLETE);

			assertThat(task.executions().size()).isEqualTo(2);
			assertThat(task.executionStatus(launchId2)).isEqualTo(TaskExecutionStatus.COMPLETE);
			assertThat(task.execution(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

			childTasksBySuffix(task, "b1").forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(2);
				assertThat(childTask.executionByParentExecutionId(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			});

			childTasksBySuffix(task, "t1").forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(1);
				assertThat(childTask.executionByParentExecutionId(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			});

			assertThat(task.jobExecutionResources().size()).isEqualTo(2);
		}
		assertThat(taskBuilder.allTasks().size()).isEqualTo(0);

	}

	private void allSuccessfulExecutions(String taskDescription, String taskDefinition, String... childLabels) {
		mixedSuccessfulFailedAndUnknownExecutions(taskDescription, taskDefinition, asList(childLabels),
				emptyList(), emptyList());
	}

	private void mixedSuccessfulFailedAndUnknownExecutions(String taskDescription, String taskDefinition,
			List<String> successfulTasks, List<String> failedTasks, List<String> unknownTasks) {

		TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
		try (Task task = taskBuilder
				.name(randomTaskName())
				.definition(taskDefinition)
				.description(taskDescription)
				.build()) {

			ArrayList<String> allTasks = new ArrayList<>(successfulTasks);
			allTasks.addAll(failedTasks);
			allTasks.addAll(unknownTasks);

			assertThat(task.composedTaskChildTasks().size()).isEqualTo(allTasks.size());
			assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList()))
					.hasSameElementsAs(fullTaskNames(task, allTasks.toArray(new String[0])));

			long launchId = task.launch();

			Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE);

			// Parent Task
			assertThat(task.executions().size()).isEqualTo(1);
			assertThat(task.executionStatus(launchId)).isEqualTo(TaskExecutionStatus.COMPLETE);
			assertThat(task.execution(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

			// Successful tasks
			childTasksBySuffix(task, successfulTasks.toArray(new String[0])).forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(1);
				assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
			});

			// Failed tasks
			childTasksBySuffix(task, failedTasks.toArray(new String[0])).forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(1);
				assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_ERROR);
			});

			// Not run tasks
			childTasksBySuffix(task, unknownTasks.toArray(new String[0])).forEach(childTask -> {
				assertThat(childTask.executions().size()).isEqualTo(0);
			});

			// Parent Task
			assertThat(taskBuilder.allTasks().size()).isEqualTo(task.composedTaskChildTasks().size() + 1);
		}
		assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
	}

	private List<String> fullTaskNames(Task task, String... childTaskNames) {
		return java.util.stream.Stream.of(childTaskNames)
				.map(cn -> task.getTaskName() + "-" + cn.trim()).collect(Collectors.toList());
	}

	private List<Task> childTasksBySuffix(Task task, String... suffixes) {
		return java.util.stream.Stream.of(suffixes)
				.map(suffix -> task.composedTaskChildTaskByLabel(suffix).get()).collect(Collectors.toList());
	}

	private static String randomTaskName() {
		return "task-" + randomSuffix();
	}

	private static String randomJobName() {
		return "job-" + randomSuffix();
	}

	private static String randomSuffix() {
		return UUID.randomUUID().toString().substring(0, 10);
	}

	private static List<String> asList(String... names) {
		return Arrays.asList(names);
	}

	private static List<String> emptyList() {
		return Collections.emptyList();
	}
}
