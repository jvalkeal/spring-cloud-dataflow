/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.dataflow.server.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.server.controller.support.ApplicationMetrics;
import org.springframework.cloud.dataflow.server.controller.support.GroupedMetrics;
import org.springframework.cloud.dataflow.server.controller.support.MetricStore;
import org.springframework.cloud.dataflow.server.repository.DeploymentIdRepository;
import org.springframework.cloud.dataflow.server.repository.DeploymentKey;
import org.springframework.cloud.dataflow.server.repository.StreamDefinitionRepository;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.ResourceAssembler;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metrics")
@ExposesResourceFor(MetricsController.AppMetricResource.class)
public class MetricsController {

	private final ResourceAssembler<GroupedMetrics, AppMetricResource> statusAssembler = new Assembler();
	private final MetricStore metricStore;
	private final StreamDefinitionRepository streamDefinitionRepository;
	private final DeploymentIdRepository deploymentIdRepository;
	private final AppDeployer appDeployer;
	private final ForkJoinPool forkJoinPool;

	public MetricsController(MetricStore metricStore, StreamDefinitionRepository streamDefinitionRepository,
			DeploymentIdRepository deploymentIdRepository, AppDeployer appDeployer, ForkJoinPool forkJoinPool) {
		this.metricStore = metricStore;
		this.streamDefinitionRepository = streamDefinitionRepository;
		this.deploymentIdRepository = deploymentIdRepository;
		this.appDeployer = appDeployer;
		this.forkJoinPool = forkJoinPool;
	}

	@RequestMapping
	public PagedResources<AppMetricResource> list(Pageable pageable, PagedResourcesAssembler<GroupedMetrics> assembler) throws ExecutionException, InterruptedException {

		List<StreamDefinition> asList = new ArrayList<>();
		for (StreamDefinition streamDefinition : this.streamDefinitionRepository.findAll()) {
			asList.add(streamDefinition);
		}

		// First build a sorted list of deployment id's so that we have
		// a predictable paging order.
		List<String> deploymentIds = asList.stream()
				.flatMap(sd -> sd.getAppDefinitions().stream())
				.flatMap(sad -> {
					String key = DeploymentKey.forStreamAppDefinition(sad);
					String id = this.deploymentIdRepository.findOne(key);
					return id != null ? Stream.of(id) : Stream.empty();
				})
				.sorted((o1, o2) -> o1.compareTo(o2))
				.collect(Collectors.toList());

		// Running this this inside the FJP will make sure it is used by the parallel stream
		// Skip first items depending on page size, then take page and discard rest.
		List<AppStatus> statuses = forkJoinPool.submit(() ->
				deploymentIds.stream()
						.skip(pageable.getPageNumber() * pageable.getPageSize())
						.limit(pageable.getPageSize())
						.parallel()
						.map(appDeployer::status)
						.collect(Collectors.toList())
		).get();

		List<GroupedMetrics> metricsIn = metricStore.getMetrics();
		List<GroupedMetrics> metricsOut = new ArrayList<>();
		Map<String, UniqueAppIdHolder> uniqueAppIdToInstanceId = new HashMap<>();

		for (AppStatus as : statuses) {
			String deploymentId = as.getDeploymentId();
			for (Entry<String, AppInstanceStatus> e : as.getInstances().entrySet()) {
				AppInstanceStatus value = e.getValue();
				String instanceId = value.getId();
				Map<String, String> attributes = value.getAttributes();
				String uniqueAppId = attributes.get("uniqueAppId");
				if (StringUtils.hasText(uniqueAppId)) {
					uniqueAppIdToInstanceId.put(uniqueAppId, new UniqueAppIdHolder(deploymentId, instanceId));
				}
			}
		}

		for (GroupedMetrics gmIn : metricsIn) {
			GroupedMetrics gmOut = new GroupedMetrics();
			Set<ApplicationMetrics> instances = new HashSet<>();
			for (ApplicationMetrics am : gmIn.getInstances()) {
				String iname = am.getName();
				String[] split2 = iname.split("\\.");
				UniqueAppIdHolder holder = uniqueAppIdToInstanceId.get(split2[2]);
				if (holder != null) {
					gmOut.setName(holder.deploymentId);
					instances.add(new ApplicationMetrics(holder.instanceId, am.getInstanceIndex(), am.getMetrics()));
				}
			}
			gmOut.setInstances(instances);
			metricsOut.add(gmOut);
		}

		return assembler.toResource(new PageImpl<>(metricsOut), statusAssembler);
	}

	private static class UniqueAppIdHolder {
		String deploymentId;
		String instanceId;
		public UniqueAppIdHolder(String deploymentId, String instanceId) {
			this.deploymentId = deploymentId;
			this.instanceId = instanceId;
		}
	}

	public static class AppMetricResource extends ResourceSupport {

		private String deploymentId;
		private Resources<AppInstanceMetricResource> instances;
		private Map<String, Number> metrics = new HashMap<>();

		public AppMetricResource(GroupedMetrics entity) {
			this.deploymentId = entity.getName();
			Set<ApplicationMetrics> aps = entity.getInstances();

			List<AppInstanceMetricResource> aimr = new ArrayList<>();
			for (ApplicationMetrics am : aps) {
				aimr.add(new AppInstanceMetricResource(am));
			}
			this.instances = new Resources<>(aimr);
		}

		public String getDeploymentId() {
			return deploymentId;
		}

		public Resources<AppInstanceMetricResource> getInstances() {
			return instances;
		}

		public Map<String, Number> getMetrics() {
			return metrics;
		}
	}

	public static class AppInstanceMetricResource extends ResourceSupport {

		private String instanceId;
		private Map<String, Number> metrics = new HashMap<>();

		public AppInstanceMetricResource(ApplicationMetrics am) {
//			this.instanceId = am.getName() + "-" + am.getInstanceIndex();
			this.instanceId = am.getName();
			for (Metric<?> mm : am.getMetrics()) {
				this.metrics.put(mm.getName(), mm.getValue());
			}
		}

		public String getInstanceId() {
			return instanceId;
		}

		public Map<String, Number> getMetrics() {
			return metrics;
		}
	}

	private static class Assembler extends ResourceAssemblerSupport<GroupedMetrics, AppMetricResource> {

		public Assembler() {
			super(MetricsController.class, AppMetricResource.class);
		}

		@Override
		public AppMetricResource toResource(GroupedMetrics entity) {
			return new AppMetricResource(entity);
		}

	}
}
