/*
 * Copyright 2015-2021 the original author or authors.
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

package org.springframework.cloud.dataflow.server.config.web;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.cloud.dataflow.rest.job.StepExecutionHistory;
import org.springframework.cloud.dataflow.rest.support.jackson.ExecutionContextJacksonMixIn;
// import org.springframework.cloud.dataflow.rest.support.jackson.ExecutionContextJacksonMixIn;
import org.springframework.cloud.dataflow.rest.support.jackson.ISO8601DateFormatWithMilliSeconds;
import org.springframework.cloud.dataflow.rest.support.jackson.xxx.ExitStatusJacksonMixIn;
import org.springframework.cloud.dataflow.rest.support.jackson.xxx.JobExecutionJacksonMixIn;
import org.springframework.cloud.dataflow.rest.support.jackson.xxx.JobInstanceJacksonMixIn;
import org.springframework.cloud.dataflow.rest.support.jackson.xxx.JobParameterJacksonMixIn;
import org.springframework.cloud.dataflow.rest.support.jackson.xxx.JobParametersJacksonMixIn;
import org.springframework.cloud.dataflow.rest.support.jackson.xxx.StepExecutionHistoryJacksonMixIn;
import org.springframework.cloud.dataflow.rest.support.jackson.xxx.StepExecutionJacksonMixIn;
// import org.springframework.cloud.dataflow.rest.support.jackson.StepExecutionJacksonMixIn;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.config.HypermediaMappingInformation;
import org.springframework.http.MediaType;

@Configuration
public class HypermediaBareJsonConfiguration implements HypermediaMappingInformation {

	@Override
	public List<MediaType> getMediaTypes() {
		return Collections.singletonList(MediaType.APPLICATION_JSON);
	}

	// @Override
	// public ObjectMapper configureObjectMapper(ObjectMapper mapper) {
	// 	mapper.setDateFormat(new ISO8601DateFormatWithMilliSeconds(TimeZone.getDefault(), Locale.getDefault(), true));
	// 	// apply SCDF Batch Mixins to
	// 	// ignore the JobExecution in StepExecution to prevent infinite loop.
	// 	// https://github.com/spring-projects/spring-hateoas/issues/333
	// 	mapper.addMixIn(StepExecution.class, StepExecutionJacksonMixIn.class);
	// 	mapper.addMixIn(ExecutionContext.class, ExecutionContextJacksonMixIn.class);
	// 	mapper.addMixIn(JobExecution.class, JobExecutionJacksonMixIn.class);
	// 	mapper.addMixIn(JobParameters.class, JobParametersJacksonMixIn.class);
	// 	mapper.addMixIn(JobParameter.class, JobParameterJacksonMixIn.class);
	// 	mapper.addMixIn(JobInstance.class, JobInstanceJacksonMixIn.class);
	// 	mapper.addMixIn(ExitStatus.class, ExitStatusJacksonMixIn.class);
	// 	mapper.addMixIn(StepExecution.class, StepExecutionJacksonMixIn.class);
	// 	mapper.addMixIn(ExecutionContext.class, ExecutionContextJacksonMixIn.class);
	// 	mapper.addMixIn(StepExecutionHistory.class, StepExecutionHistoryJacksonMixIn.class);;
	// 	mapper.registerModules(new JavaTimeModule(), new Jdk8Module());
	// 	return mapper;
	// }
}
