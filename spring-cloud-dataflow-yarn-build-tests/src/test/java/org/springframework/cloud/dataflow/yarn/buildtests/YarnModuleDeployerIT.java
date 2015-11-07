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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.util.ConverterUtils;
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
import org.springframework.cloud.dataflow.module.deployer.yarn.YarnCloudAppService.CloudAppInstanceInfo;
import org.springframework.cloud.dataflow.module.deployer.yarn.YarnCloudAppServiceApplication;
import org.springframework.cloud.dataflow.module.deployer.yarn.YarnModuleDeployer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.yarn.test.context.MiniYarnClusterTest;
import org.springframework.yarn.test.support.ContainerLogUtils;

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
		YarnCloudAppService yarnCloudAppService = context.getBean(YarnCloudAppService.class);
	
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
		ApplicationId applicationId = assertWaitApp(2, TimeUnit.MINUTES, yarnCloudAppService);
		File timeStdoutFile = assertWaitFileContent(2, TimeUnit.MINUTES, applicationId, "Started TimeSourceApplication");
		
		ModuleDeploymentId logId = deployer.deploy(log);
		File logStdoutFile = assertWaitFileContent(2, TimeUnit.MINUTES, applicationId, "Started LogSinkApplication");
		
		deployer.undeploy(timeId);
		timeStdoutFile = assertWaitFileContent(2, TimeUnit.MINUTES, applicationId, "stopped outbound.ticktock.0");
		
		deployer.undeploy(logId);
		logStdoutFile = assertWaitFileContent(2, TimeUnit.MINUTES, applicationId, "stopped inbound.ticktock.0");
		
		Collection<CloudAppInstanceInfo> instances = yarnCloudAppService.getInstances();
		assertThat(instances.size(), is(1));
		
		List<Resource> resources = ContainerLogUtils.queryContainerLogs(
				getYarnCluster(), applicationId);
		
		assertThat(resources, notNullValue());
		assertThat(resources.size(), is(6));
		
		for (Resource res : resources) {
			File file = res.getFile();
			String content = ContainerLogUtils.getFileContent(file);
			if (file.getName().endsWith("stdout")) {
				assertThat(file.length(), greaterThan(0l));
			} else if (file.getName().endsWith("stderr")) {
				assertThat("stderr with content: " + content, file.length(), is(0l));
			}
		}
	}
	
	private File assertWaitFileContent(long timeout, TimeUnit unit, ApplicationId applicationId, String search) throws Exception {
		File file = null;

		long end = System.currentTimeMillis() + unit.toMillis(timeout);
		done:
		do {

			List<Resource> resources = ContainerLogUtils.queryContainerLogs(
					getYarnCluster(), applicationId);
			for (Resource res : resources) {
				File f = res.getFile();
				String content = ContainerLogUtils.getFileContent(f);
				if (content.contains(search)) {
					file = f;
					break done;
				}
			}
			
			Thread.sleep(1000);
		} while (System.currentTimeMillis() < end);
		
		
		assertThat(file, notNullValue());
		return file;
	}
	
	private ApplicationId assertWaitApp(long timeout, TimeUnit unit, YarnCloudAppService yarnCloudAppService) throws Exception {
		ApplicationId applicationId = null;
		Collection<CloudAppInstanceInfo> instances;
		long end = System.currentTimeMillis() + unit.toMillis(timeout);

		do {
			instances = yarnCloudAppService.getInstances();
			if (instances.size() == 1) {
				CloudAppInstanceInfo cloudAppInstanceInfo = instances.iterator().next();
				if (StringUtils.hasText(cloudAppInstanceInfo.getAddress())) {
					applicationId = ConverterUtils.toApplicationId(cloudAppInstanceInfo.getApplicationId());
					break;
				}
			}
			Thread.sleep(1000);
		} while (System.currentTimeMillis() < end);
		
		assertThat(applicationId, notNullValue());
		return applicationId;
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
