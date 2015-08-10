/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.data.module.deployer.yarn;

import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.data.core.ModuleCoordinates;
import org.springframework.cloud.data.core.ModuleDefinition;
import org.springframework.cloud.data.core.ModuleDeploymentId;
import org.springframework.cloud.data.core.ModuleDeploymentRequest;
import org.springframework.cloud.data.module.ModuleStatus;
import org.springframework.cloud.data.module.deployer.ModuleDeployer;
import org.springframework.util.Assert;
import org.springframework.yarn.boot.app.YarnContainerClusterApplication;

/**
 * {@link ModuleDeployer} which communicates to XD's yarn app running
 * on a hadoop cluster waiting for deployment requests. This app is
 * utilising spring yarn's container grouping functionality to create a
 * new group per module type which then allows all modules to share same
 * settings and group itself can controlled, i.e. ramp up/down or shutdown/destroy
 * whole group.
 *
 * @author Janne Valkealahti
 */
public class YarnModuleDeployer implements ModuleDeployer {

	private static final Logger logger = LoggerFactory.getLogger(YarnModuleDeployer.class);
	private static final String PREFIX = "spring.yarn.internal.ContainerClusterApplication.";

	private final String yarnApplicationId;

	public YarnModuleDeployer(String yarnApplicationId) {
		Assert.notNull(yarnApplicationId, "Yarn ApplicationId cannot be null");
		this.yarnApplicationId = yarnApplicationId;
	}

	@Override
	public ModuleDeploymentId deploy(ModuleDeploymentRequest request) {
		int count = request.getCount();
		ModuleCoordinates coordinates = request.getCoordinates();
		ModuleDefinition definition = request.getDefinition();

		Map<String, String> definitionParameters = definition.getParameters();
		Map<String, String> deploymentProperties = request.getDeploymentProperties();


		String module = coordinates.toString();
		logger.info("deploying module: " + module);

		String clusterId = module;

		// Using same app instance that what yarn boot cli is using to
		// communicate with an app running on yarn via its boot actuator
		YarnContainerClusterApplication app = new YarnContainerClusterApplication();
		Properties appProperties = new Properties();
		appProperties.setProperty(PREFIX + "operation", "CLUSTERCREATE");
		appProperties.setProperty(PREFIX + "applicationId", yarnApplicationId);
		appProperties.setProperty(PREFIX + "clusterId", clusterId);
		appProperties.setProperty(PREFIX + "clusterDef", "module-template");
		appProperties.setProperty(PREFIX + "projectionType", "default");
		appProperties.setProperty(PREFIX + "projectionData.any", Integer.toString(count));
		appProperties.setProperty(PREFIX + "extraProperties.containerModules", module);
		app.appProperties(appProperties);
		String output = app.run();
		logger.info("Output from YarnContainerClusterApplication run for CLUSTERCREATE: " + output);

		return new ModuleDeploymentId(definition.getGroup(), definition.getLabel());
	}

	@Override
	public void undeploy(ModuleDeploymentId id) {
		// shut down group
		throw new UnsupportedOperationException("todo");
	}

	@Override
	public ModuleStatus status(ModuleDeploymentId id) {
		// check group state from yarn app and map it to status
		throw new UnsupportedOperationException("todo");
	}

	@Override
	public Map<ModuleDeploymentId, ModuleStatus> status() {
		// check all group statuses and map it back to id and status
		throw new UnsupportedOperationException("todo");
	}
}
