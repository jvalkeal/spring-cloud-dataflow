/*
 * Copyright 2021 the original author or authors.
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

package org.springframework.cloud.dataflow.integration.test.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.dataflow.integration.test.tags.TagNames;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

@Tag("oauth")
@ActiveProfiles({TagNames.PROFILE_DB_SHARED})
public class UaaOauthIT extends AbstractDataflowTests {

	private final Logger log = LoggerFactory.getLogger(UaaOauthIT.class);

	@Test
	public void testSecuredSetup() throws Exception {
		log.info("Running testSecuredSetup()");

		this.dataflowCluster.startIdentityProvider(TagNames.UAA_4_32);

		GenericContainer<?> curlContainer = new GenericContainer<>("praqma/network-multitool:latest");
		curlContainer.withNetwork(this.dataflowCluster.getNetwork());
		curlContainer.start();

		// start defined database
		this.dataflowCluster.startSkipperDatabase(TagNames.POSTGRES_10);
		this.dataflowCluster.startDataflowDatabase(TagNames.POSTGRES_10);


		this.dataflowCluster.startSkipper(TagNames.SKIPPER_main);
		log.info("Checking skipper");
		assertSkipperServerRunning(this.dataflowCluster);

		this.dataflowCluster.startDataflow(TagNames.DATAFLOW_main);
		log.info("Checking dataflow");

		Container.ExecResult lsResult = curlContainer.execInContainer("curl", "-u", "janne:janne", "http://dataflow:9393/about");
		String stdout = lsResult.getStdout();
		// int exitCode = lsResult.getExitCode();
		assertThat(stdout).contains("asdf");

		// assertDataflowServerRunning(this.dataflowCluster);
	}
}
