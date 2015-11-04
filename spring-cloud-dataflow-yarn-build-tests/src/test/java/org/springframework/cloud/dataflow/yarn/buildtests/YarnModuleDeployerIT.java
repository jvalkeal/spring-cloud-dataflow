/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.yarn.buildtests;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.dataflow.admin.config.YarnConfiguration;
import org.springframework.cloud.dataflow.core.ArtifactCoordinates;
import org.springframework.cloud.dataflow.core.ModuleDefinition;
import org.springframework.cloud.dataflow.core.ModuleDeploymentId;
import org.springframework.cloud.dataflow.core.ModuleDeploymentRequest;
import org.springframework.cloud.dataflow.module.deployer.ModuleDeployer;
import org.springframework.cloud.dataflow.module.deployer.yarn.DefaultYarnCloudAppService;
import org.springframework.cloud.dataflow.module.deployer.yarn.YarnCloudAppService;
import org.springframework.cloud.dataflow.module.deployer.yarn.YarnCloudAppServiceApplication;
import org.springframework.cloud.dataflow.module.deployer.yarn.YarnModuleDeployer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.yarn.test.context.MiniYarnClusterTest;

/**
 * Integration tests for {@link YarnCloudAppServiceApplication}.
 * 
 * @author Janne Valkealahti
 *
 */
@MiniYarnClusterTest
public class YarnModuleDeployerIT  extends AbstractCliBootYarnClusterTests {

	private static final String GROUP_ID = "org.springframework.cloud.stream.module";
	private static final String VERSION = "1.0.0.BUILD-SNAPSHOT";
	private AnnotationConfigApplicationContext context;
	
	@Before
	public void setup() {
		context = new AnnotationConfigApplicationContext();
		context.getEnvironment().setActiveProfiles("yarn");
		context.register(TestYarnConfiguration.class);
		context.setParent(getApplicationContext());
		context.refresh();
	}
	
	@After
	public void clean() {
		if (context != null) {
			context.close();
		}
		context = null;
	}
	
	@Test
	public void testSimpleDeployLifecycle() throws Exception {
		assertThat(context.containsBean("processModuleDeployer"), is(true));
		assertThat(context.getBean("processModuleDeployer"), instanceOf(YarnModuleDeployer.class));
		ModuleDeployer deployer = context.getBean("processModuleDeployer", ModuleDeployer.class);
	
		ModuleDefinition timeDefinition = new ModuleDefinition.Builder()
				.setGroup("ticktock")
				.setName("time")
				.setParameter("spring.cloud.stream.bindings.output", "ticktock.0")
				.build();
		ModuleDefinition logDefinition = new ModuleDefinition.Builder()
				.setGroup("ticktock")
				.setName("log")
				.setParameter("spring.cloud.stream.bindings.input", "ticktock.0")
				.build();
		ArtifactCoordinates timeCoordinates = new ArtifactCoordinates.Builder()
				.setGroupId(GROUP_ID)
				.setArtifactId("time-source")
				.setVersion(VERSION)
				.setClassifier("exec")
				.build();
		ArtifactCoordinates logCoordinates = new ArtifactCoordinates.Builder()
				.setGroupId(GROUP_ID)
				.setArtifactId("log-sink")
				.setVersion(VERSION)
				.setClassifier("exec")
				.build();
		ModuleDeploymentRequest time = new ModuleDeploymentRequest(timeDefinition, timeCoordinates);
		ModuleDeploymentRequest log = new ModuleDeploymentRequest(logDefinition, logCoordinates);
		ModuleDeploymentId timeId = deployer.deploy(time);
		ModuleDeploymentId logId = deployer.deploy(log);
		
		Thread.sleep(200000);
		
		deployer.undeploy(timeId);
		Thread.sleep(20000);
		deployer.undeploy(logId);
		Thread.sleep(20000);
		
		
	}
	
	@Configuration
	@Profile("yarn")
	public static class TestYarnConfiguration extends YarnConfiguration {

		@Autowired
		private org.apache.hadoop.conf.Configuration configuration;
		
		@Override
		@Bean
		public YarnCloudAppService yarnCloudAppService() {
			return new DefaultYarnCloudAppService(yarnCloudAppServiceApplication(), null);
		}
		
		@Bean
		public YarnCloudAppServiceApplication yarnCloudAppServiceApplication() {
			YarnCloudAppServiceApplication app = new YarnCloudAppServiceApplication("app");
			app.setInitializers(new HadoopConfigurationInjectingInitializer(configuration));			
			return app;
		}
		
	}
	
}
