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

import java.util.concurrent.TimeUnit;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;
import org.springframework.yarn.boot.SpringApplicationCallback;
import org.springframework.yarn.boot.SpringApplicationTemplate;
import org.springframework.yarn.client.YarnClient;
import org.springframework.yarn.test.context.YarnCluster;
import org.springframework.yarn.test.junit.ApplicationInfo;

@RunWith(SpringJUnit4ClassRunner.class)
public class AbstractCliBootYarnClusterTests implements ApplicationContextAware {

	protected ApplicationContext applicationContext;

	protected Configuration configuration;

	protected YarnCluster yarnCluster;

	protected YarnClient yarnClient;

	public ApplicationContext getApplicationContext() {
		return applicationContext;
	}

	@Override
	public final void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	@Autowired
	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}
	
	public void setYarnClient(YarnClient yarnClient) {
		this.yarnClient = yarnClient;
	}
	
	public YarnClient getYarnClient() {
		return yarnClient;
	}
	
	public YarnCluster getYarnCluster() {
		return yarnCluster;
	}

	@Autowired
	public void setYarnCluster(YarnCluster yarnCluster) {
		this.yarnCluster = yarnCluster;
	}

	protected ApplicationInfo submitApplicationAndWait(Object source, String[] args) throws Exception {
		return submitApplicationAndWait(source, args, 60, TimeUnit.SECONDS);
	}

	protected ApplicationInfo submitApplicationAndWait(Object source, String[] args, long timeout, final TimeUnit unit) throws Exception {
		return submitApplicationAndWaitState(source, args, timeout, unit, YarnApplicationState.FINISHED, YarnApplicationState.FAILED);
	}
	
	protected ApplicationInfo submitApplicationAndWaitState(long timeout, TimeUnit unit, YarnApplicationState... applicationStates) throws Exception {
		Assert.notEmpty(applicationStates, "Need to have atleast one state");
		Assert.notNull(getYarnClient(), "Yarn client must be set");

		YarnApplicationState state = null;
		ApplicationReport report = null;

		ApplicationId applicationId = submitApplication();
		Assert.notNull(applicationId, "Failed to get application id from submit");

		long end = System.currentTimeMillis() + unit.toMillis(timeout);

		// break label for inner loop
		done:
		do {
			report = findApplicationReport(getYarnClient(), applicationId);
			if (report == null) {
				break;
			}
			state = report.getYarnApplicationState();
			for (YarnApplicationState stateCheck : applicationStates) {
				if (state.equals(stateCheck)) {
					break done;
				}
			}
			Thread.sleep(1000);
		} while (System.currentTimeMillis() < end);
		return new ApplicationInfo(applicationId, report);
	}
	
	protected ApplicationInfo waitState(ApplicationId applicationId, long timeout, TimeUnit unit, YarnApplicationState... applicationStates) throws Exception {
		YarnApplicationState state = null;
		ApplicationReport report = null;
		long end = System.currentTimeMillis() + unit.toMillis(timeout);

		// break label for inner loop
		done:
		do {
			report = findApplicationReport(getYarnClient(), applicationId);
			if (report == null) {
				break;
			}
			state = report.getYarnApplicationState();
			for (YarnApplicationState stateCheck : applicationStates) {
				if (state.equals(stateCheck)) {
					break done;
				}
			}
			Thread.sleep(1000);
		} while (System.currentTimeMillis() < end);
		return new ApplicationInfo(applicationId, report);		
	}
	
	
	protected ApplicationInfo submitApplicationAndWaitState(Object source, String[] args, final long timeout,
			final TimeUnit unit, final YarnApplicationState... applicationStates) throws Exception {

		SpringApplicationBuilder builder = new SpringApplicationBuilder(source);
		builder.initializers(new HadoopConfigurationInjectingInitializer(getConfiguration()));

		SpringApplicationTemplate template = new SpringApplicationTemplate(builder);
		return template.execute(new SpringApplicationCallback<ApplicationInfo>() {

			@Override
			public ApplicationInfo runWithSpringApplication(ApplicationContext context) throws Exception {
				setYarnClient(context.getBean(YarnClient.class));
				return submitApplicationAndWaitState(timeout, unit, applicationStates);
			}

		}, args);
	}

	private ApplicationReport findApplicationReport(YarnClient client, ApplicationId applicationId) {
		Assert.notNull(getYarnClient(), "Yarn client must be set");
		for (ApplicationReport report : client.listApplications()) {
			if (report.getApplicationId().equals(applicationId)) {
				return report;
			}
		}
		return null;
	}
	
	protected ApplicationId submitApplication() {
		Assert.notNull(getYarnClient(), "Yarn client must be set");
		ApplicationId applicationId = getYarnClient().submitApplication();
		Assert.notNull(applicationId, "Failed to get application id from submit");
		return applicationId;
	}
	
	public static class HadoopConfigurationInjectingInitializer
			implements ApplicationContextInitializer<ConfigurableApplicationContext> {

		private final Configuration configuration;

		public HadoopConfigurationInjectingInitializer(Configuration configuration) {
			this.configuration = configuration;
		}

		@Override
		public void initialize(ConfigurableApplicationContext applicationContext) {
			applicationContext.getBeanFactory().registerSingleton("miniYarnConfiguration", configuration);
		}

	}
	
}
