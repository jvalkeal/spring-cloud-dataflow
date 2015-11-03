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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.dataflow.module.deployer.yarn.YarnCloudAppService.CloudAppInfo;
import org.springframework.cloud.dataflow.module.deployer.yarn.YarnCloudAppService.CloudAppInstanceInfo;
import org.springframework.cloud.dataflow.module.deployer.yarn.YarnCloudAppServiceApplication;
import org.springframework.data.hadoop.fs.FsShell;
import org.springframework.util.StringUtils;
import org.springframework.yarn.client.YarnClient;
import org.springframework.yarn.support.console.ContainerClusterReport.ClustersInfoReportData;
import org.springframework.yarn.test.context.MiniYarnClusterTest;
import org.springframework.yarn.test.junit.ApplicationInfo;

@MiniYarnClusterTest
public class YarnCloudAppIT extends AbstractCliBootYarnClusterTests {

	@Test
	public void testStream() throws Exception {		
		YarnCloudAppServiceApplication app = new YarnCloudAppServiceApplication("app");
		app.setInitializers(new HadoopConfigurationInjectingInitializer(getConfiguration()));
		
		Properties instanceProperties = new Properties();
		instanceProperties.setProperty("spring.yarn.applicationVersion", "app");
		app.configFile("application.properties", instanceProperties);
		
		app.afterPropertiesSet();
		setYarnClient(app.getContext().getBean(YarnClient.class));
				
		app.pushApplication();
		Collection<CloudAppInfo> pushedApplications = app.getPushedApplications();
		assertThat(pushedApplications.size(), is(1));
		
		dumpFs();
		
		String appId = app.submitApplication("app");
		ApplicationId applicationId = ConverterUtils.toApplicationId(appId);
		ApplicationInfo info = waitState(applicationId, 60, TimeUnit.SECONDS, YarnApplicationState.RUNNING);		
		assertThat(info.getYarnApplicationState(), is(YarnApplicationState.RUNNING));
		
		Collection<CloudAppInstanceInfo> submittedApplications = app.getSubmittedApplications();
		assertThat(submittedApplications.size(), is(1));
		
		Collection<String> clustersInfo = app.getClustersInfo(applicationId);
		assertThat(clustersInfo.size(), is(0));
		
		Map<String, Object> extraProperties1 = new HashMap<String, Object>();
		extraProperties1.put("containerModules", "org.springframework.cloud.stream.module:log-sink:jar:exec:1.0.0.BUILD-SNAPSHOT");		
		extraProperties1.put("containerArg1", "spring.cloud.stream.bindings.input.destination=ticktock.0");		
		app.createCluster(applicationId, "ticktock:log", "module-template", "default", 1, null, null, null, extraProperties1);
		
		Map<String, Object> extraProperties2 = new HashMap<String, Object>();
		extraProperties2.put("containerModules", "org.springframework.cloud.stream.module:time-source:jar:exec:1.0.0.BUILD-SNAPSHOT");		
		extraProperties2.put("containerArg1", "spring.cloud.stream.bindings.output.destination=ticktock.0");		
		app.createCluster(applicationId, "ticktock:time", "module-template", "default", 1, null, null, null, extraProperties2);
		

//		clustersInfo = app.getClustersInfo(applicationId);
//		assertThat(clustersInfo.size(), is(1));		
//		List<ClustersInfoReportData> clusterInfo = app.getClusterInfo(applicationId, "fooClusterId");
//		assertThat(clusterInfo.size(), is(1));
		
		app.startCluster(applicationId, "ticktock:log");
		app.startCluster(applicationId, "ticktock:time");
		Thread.sleep(120000);
		
//		app.destroyCluster(applicationId, "fooClusterId");
//		clustersInfo = app.getClustersInfo(applicationId);
//		assertThat(clustersInfo.size(), is(0));
		
		app.destroy();
	}
	
	
//	@Test
	public void testPushSubmit() throws Exception {		
		YarnCloudAppServiceApplication app = new YarnCloudAppServiceApplication("app");
		app.setInitializers(new HadoopConfigurationInjectingInitializer(getConfiguration()));
		
		Properties instanceProperties = new Properties();
		instanceProperties.setProperty("spring.yarn.applicationVersion", "app");
		app.configFile("application.properties", instanceProperties);
		
		app.afterPropertiesSet();
		setYarnClient(app.getContext().getBean(YarnClient.class));
				
		app.pushApplication();
		Collection<CloudAppInfo> pushedApplications = app.getPushedApplications();
		assertThat(pushedApplications.size(), is(1));
		
		dumpFs();
		
		String appId = app.submitApplication("app");
		ApplicationId applicationId = ConverterUtils.toApplicationId(appId);
		ApplicationInfo info = waitState(applicationId, 60, TimeUnit.SECONDS, YarnApplicationState.RUNNING);		
		assertThat(info.getYarnApplicationState(), is(YarnApplicationState.RUNNING));
		
		Collection<CloudAppInstanceInfo> submittedApplications = app.getSubmittedApplications();
		assertThat(submittedApplications.size(), is(1));
		
		Collection<String> clustersInfo = app.getClustersInfo(applicationId);
		assertThat(clustersInfo.size(), is(0));
		
		Map<String, Object> extraProperties = new HashMap<String, Object>();
		extraProperties.put("containerModules", "org.springframework.cloud.stream.module:hdfs-sink:jar:exec:1.0.0.BUILD-SNAPSHOT");
		
		app.createCluster(applicationId, "fooClusterId", "module-template", "default", 1, null, null, null, extraProperties);

		clustersInfo = app.getClustersInfo(applicationId);
		assertThat(clustersInfo.size(), is(1));
		
		List<ClustersInfoReportData> clusterInfo = app.getClusterInfo(applicationId, "fooClusterId");
		assertThat(clusterInfo.size(), is(1));
		
		app.startCluster(applicationId, "fooClusterId");
		Thread.sleep(60000);
		
//		app.destroyCluster(applicationId, "fooClusterId");
//		clustersInfo = app.getClustersInfo(applicationId);
//		assertThat(clustersInfo.size(), is(0));
		
		app.destroy();
	}
	
	private void dumpFs() {
//		FileSystem fs = FileSystem.get(getConfiguration());
		FsShell shell = new FsShell(getConfiguration());
		Collection<FileStatus> lsr = shell.lsr("/");
		for (FileStatus s : lsr) {
			System.out.println("XXXXXXXX " + s);
		}
	}
	
//	@EnableAutoConfiguration
//	public static class ClientApplication {
//
//		public static void main(String[] args) {
//			SpringApplication.run(ClientApplication.class, args)
//				.getBean(YarnClient.class)
//				.submitApplication();
//		}
//
//	}	
	
}
