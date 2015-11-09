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
package org.springframework.cloud.dataflow.module.deployer.yarn;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.hadoop.yarn.util.ConverterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.util.StringUtils;
import org.springframework.yarn.support.console.ContainerClusterReport.ClustersInfoReportData;

/**
 * Default implementation of {@link YarnCloudAppService} which talks to
 * rest api's exposed by specific yarn controlling container clusters.
 *
 * @author Janne Valkealahti
 * @author Mark Fisher
 */
public class DefaultYarnCloudAppService implements YarnCloudAppService, InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(DefaultYarnCloudAppService.class);
	private final ApplicationContextInitializer<?>[] initializers;
	private final String bootstrapName;
	private final Map<String, YarnCloudAppServiceApplication> appCache = new HashMap<String, YarnCloudAppServiceApplication>();

	public DefaultYarnCloudAppService(String bootstrapName, ApplicationContextInitializer<?>... initializers) {
		this.bootstrapName = bootstrapName;
		this.initializers = initializers;
	}
	
	@Override
	public void afterPropertiesSet() throws Exception {
	}
		
	@Override
	public Collection<CloudAppInfo> getApplications() {
		return getApp(null).getPushedApplications();
	}

	@Override
	public Collection<CloudAppInstanceInfo> getInstances() {
		return getApp(null).getSubmittedApplications();
	}

	@Override
	public void pushApplication(String appVersion) {
		getApp(appVersion).pushApplication(appVersion);
	}

	@Override
	public String submitApplication(String appVersion) {
		return getApp(appVersion).submitApplication(appVersion);
	}

	@Override
	public void createCluster(String yarnApplicationId, String clusterId, int count, String module,
			Map<String, String> definitionParameters) {
		
		Map<String, Object> extraProperties = new HashMap<String, Object>();
		extraProperties.put("containerModules", module);		
		
		int i = 0;
		for (Map.Entry<String, String> entry : definitionParameters.entrySet()) {
			extraProperties.put("containerArg" + i++, entry.getKey() + "=" + entry.getValue());
		}
		getApp(null).createCluster(ConverterUtils.toApplicationId(yarnApplicationId), clusterId, "module-template", "default", 1, null, null, null, extraProperties);
	}

	@Override
	public void startCluster(String yarnApplicationId, String clusterId) {
		getApp(null).startCluster(ConverterUtils.toApplicationId(yarnApplicationId), clusterId);
	}

	@Override
	public void stopCluster(String yarnApplicationId, String clusterId) {
		getApp(null).stopCluster(ConverterUtils.toApplicationId(yarnApplicationId), clusterId);
	}

	@Override
	public Map<String, String> getClustersStates() {
		HashMap<String, String> states = new HashMap<String, String>();
		for (CloudAppInstanceInfo instanceInfo : getInstances()) {
			for (String cluster : getClusters(instanceInfo.getApplicationId())) {
				states.putAll(getInstanceClustersStates(instanceInfo.getApplicationId(), cluster));
			}
		}
		return states;
	}

	@Override
	public Collection<String> getClusters(String yarnApplicationId) {
		return getApp(null).getClustersInfo(ConverterUtils.toApplicationId(yarnApplicationId));
	}

	@Override
	public void destroyCluster(String yarnApplicationId, String clusterId) {
		getApp(null).destroyCluster(ConverterUtils.toApplicationId(yarnApplicationId), clusterId);
	}

	private Map<String, String> getInstanceClustersStates(String yarnApplicationId, String clusterId) {
		HashMap<String, String> states = new HashMap<String, String>();
		List<ClustersInfoReportData> clusterInfo = getApp(null).getClusterInfo(ConverterUtils.toApplicationId(yarnApplicationId), clusterId);
		if (clusterInfo.size() == 1) {
			states.put(clusterId, clusterInfo.get(0).getState());			
		}
		return states;
	}
	
	private synchronized YarnCloudAppServiceApplication getApp(String appVersion) {
		YarnCloudAppServiceApplication app = appCache.get(appVersion);
		if (app == null) {
			
			Properties configFileProperties = new Properties();
			if (StringUtils.hasText(appVersion)) {
				configFileProperties.setProperty("spring.yarn.applicationVersion", appVersion);				
			}
			
			String[] runArgs = null;
			if (StringUtils.hasText(bootstrapName)) {
				runArgs = new String[] { "--spring.config.name=" + bootstrapName };
			}
			
			app = new YarnCloudAppServiceApplication(appVersion, "application.properties", configFileProperties, runArgs, initializers);
			try {
				app.afterPropertiesSet();
			} catch (Exception e) {
				throw new RuntimeException("Error initializing YarnCloudAppServiceApplication", e);
			}
			appCache.put(appVersion, app);
		}
		return app;
	}

}
