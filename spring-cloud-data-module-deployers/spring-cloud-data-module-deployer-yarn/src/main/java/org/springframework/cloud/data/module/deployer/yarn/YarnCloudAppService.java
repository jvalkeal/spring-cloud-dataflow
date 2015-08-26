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

import java.util.Collection;
import java.util.Map;

/**
 * Interface used to glue a state machine and yarn application logic together.
 *
 * @author Janne Valkealahti
 */
public interface YarnCloudAppService {

	Collection<CloudAppInfo> getApplications();

	Collection<CloudAppInstanceInfo> getInstances();

	void pushApplication(String appVersion);

	String submitApplication(String appVersion);

	void createCluster(String yarnApplicationId, String clusterId, int count, String module,
			Map<String, String> definitionParameters);

	void startCluster(String yarnApplicationId, String clusterId);

	void stopCluster(String yarnApplicationId, String clusterId);

	Map<String, String> getClustersStates();

	Collection<String> getClusters(String yarnApplicationId);

	void destroyCluster(String yarnApplicationId, String clusterId);

	public class CloudAppInfo {

		private final String name;

		public CloudAppInfo(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

	}

	public class CloudAppInstanceInfo {

		private final String applicationId;
		private final String name;
		private final String address;

		public CloudAppInstanceInfo(String applicationId, String name, String address) {
			this.applicationId = applicationId;
			this.name = name;
			this.address = address;
		}

		public String getApplicationId() {
			return applicationId;
		}

		public String getName() {
			return name;
		}

		public String getAddress() {
			return address;
		}

	}

}
