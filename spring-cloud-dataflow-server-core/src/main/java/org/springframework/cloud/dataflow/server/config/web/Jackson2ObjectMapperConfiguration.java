/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.cloud.dataflow.server.config.web;

import java.util.Locale;
import java.util.TimeZone;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.cloud.dataflow.rest.job.support.ISO8601DateFormatWithMilliSeconds;
import org.springframework.cloud.dataflow.server.job.support.ExecutionContextJacksonMixIn;
import org.springframework.cloud.dataflow.server.job.support.StepExecutionJacksonMixIn;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.core.AnnotationRelProvider;
import org.springframework.hateoas.hal.HalConfiguration;
import org.springframework.hateoas.hal.Jackson2HalModule;

/**
 * Separate configuration for jackson2 to ease testing and not forcing it to be
 * in {@link WebConfiguration}.
 *
 * @author Janne Valkealahti
 *
 */
@Configuration
public class Jackson2ObjectMapperConfiguration {

	@Bean
	public Jackson2ObjectMapperBuilderCustomizer dataflowObjectMapperBuilderCustomizer() {
		return (builder) -> {
			builder.dateFormat(new ISO8601DateFormatWithMilliSeconds(TimeZone.getDefault(), Locale.getDefault(), true));
			// apply SCDF Batch Mixins to
			// ignore the JobExecution in StepExecution to prevent infinite loop.
			// https://github.com/spring-projects/spring-hateoas/issues/333
			builder.mixIn(StepExecution.class, StepExecutionJacksonMixIn.class);
			builder.mixIn(ExecutionContext.class, ExecutionContextJacksonMixIn.class);
//			builder.modulesToInstall(new JavaTimeModule());
//			builder.modulesToInstall(new JavaTimeModule(), new Jackson2HalModule());

			// boot already changes FAIL_ON_UNKNOWN_PROPERTIES from default true to false
			// se we don't set it here
//			builder.handlerInstantiator(new Jackson2HalModule.HalHandlerInstantiator(new AnnotationRelProvider(), null,
//					null, new HalConfiguration()));
		};
	}
}
