/*
 * Copyright 2016-2023 the original author or authors.
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

package org.springframework.cloud.dataflow.server.controller;

import java.util.ArrayList;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.aggregate.task.AggregateExecutionSupport;
import org.springframework.cloud.dataflow.aggregate.task.TaskDefinitionReader;
import org.springframework.cloud.dataflow.rest.support.jackson.ISO8601DateFormatWithMilliSeconds;
import org.springframework.cloud.dataflow.rest.support.jackson.Jackson2DataflowModule;
import org.springframework.cloud.dataflow.schema.SchemaVersionTarget;
import org.springframework.cloud.dataflow.server.config.apps.CommonApplicationProperties;
import org.springframework.cloud.dataflow.server.configuration.JobDependencies;
import org.springframework.cloud.dataflow.server.repository.JobRepositoryContainer;
import org.springframework.cloud.dataflow.server.repository.TaskBatchDaoContainer;
import org.springframework.cloud.dataflow.server.repository.TaskExecutionDaoContainer;
import org.springframework.cloud.dataflow.server.service.TaskJobService;
import org.springframework.cloud.task.batch.listener.TaskBatchDao;
import org.springframework.cloud.task.repository.TaskExecution;
import org.springframework.cloud.task.repository.dao.TaskExecutionDao;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Glenn Renfro
 * @author Corneil du Plessis
 */
@SpringBootTest(classes = { JobDependencies.class,
		PropertyPlaceholderAutoConfiguration.class, BatchProperties.class })
@EnableConfigurationProperties({ CommonApplicationProperties.class })
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureTestDatabase(replace = Replace.ANY)
public class JobStepExecutionControllerTests {

	private final static String BASE_JOB_NAME = "myJob";

	private final static String JOB_NAME_ORIG = BASE_JOB_NAME + "_ORIG";

	private final static String JOB_NAME_FOO = BASE_JOB_NAME + "_FOO";

	private final static String JOB_NAME_FOOBAR = BASE_JOB_NAME + "_FOOBAR";

	private final static String BASE_STEP_NAME = "myStep";

	private final static String STEP_NAME_ORIG = BASE_STEP_NAME + "_ORIG";

	private final static String STEP_NAME_FOO = BASE_STEP_NAME + "_FOO";

	private final static String STEP_NAME_FOOBAR = BASE_STEP_NAME + "_FOOBAR";

	private boolean initialized = false;

	@Autowired
	TaskExecutionDaoContainer daoContainer;

	@Autowired
	JobRepositoryContainer jobRepositoryContainer;

	@Autowired
	TaskBatchDaoContainer taskBatchDaoContainer;

	private MockMvc mockMvc;

	@Autowired
	WebApplicationContext wac;

	@Autowired
	RequestMappingHandlerAdapter adapter;

	@Autowired
	AggregateExecutionSupport aggregateExecutionSupport;

	@Autowired
	TaskDefinitionReader taskDefinitionReader;

	@Autowired
	TaskJobService taskJobService;

	@BeforeEach
	public void setupMockMVC() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
				.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		if (!initialized) {
			createStepExecution(JOB_NAME_ORIG, STEP_NAME_ORIG);
			createStepExecution(JOB_NAME_FOO, STEP_NAME_ORIG, STEP_NAME_FOO);
			createStepExecution(JOB_NAME_FOOBAR, STEP_NAME_ORIG, STEP_NAME_FOO, STEP_NAME_FOOBAR);
			initialized = true;
		}
		for (HttpMessageConverter<?> converter : adapter.getMessageConverters()) {
			if (converter instanceof MappingJackson2HttpMessageConverter) {
				final MappingJackson2HttpMessageConverter jacksonConverter = (MappingJackson2HttpMessageConverter) converter;
				jacksonConverter.getObjectMapper().registerModule(new Jackson2DataflowModule());
				jacksonConverter.getObjectMapper().setDateFormat(new ISO8601DateFormatWithMilliSeconds());
			}
		}
	}

	@Test
	public void testJobStepExecutionControllerConstructorMissingRepository() {
		assertThrows(IllegalArgumentException.class, () -> {
			new JobStepExecutionController(null);
		});
	}

	@Test
	public void testGetExecutionNotFound() throws Exception {
		mockMvc.perform(get("/jobs/executions/1342434234/steps").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	public void testSingleGetStepExecution() throws Exception {
		validateStepDetail(1, 1, STEP_NAME_ORIG);
		validateStepDetail(2, 2 ,STEP_NAME_ORIG);
		validateStepDetail(2, 3 ,STEP_NAME_FOO);
		validateStepDetail(3, 4 ,STEP_NAME_ORIG);
		validateStepDetail(3, 5 ,STEP_NAME_FOO);
		validateStepDetail(3, 6 ,STEP_NAME_FOOBAR);
	}

	private void validateStepDetail(int jobId, int stepId, String contextValue) throws Exception{
		mockMvc.perform(get(String.format("/jobs/executions/%d/steps/%d", jobId, stepId)).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobExecutionId", is(jobId)))
				.andExpect(jsonPath("$.stepExecution.stepName", is(contextValue)));
	}

	@Test
	public void testGetMultipleStepExecutions() throws Exception {
		mockMvc.perform(get("/jobs/executions/3/steps").accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$._embedded.stepExecutionResourceList[*]", hasSize(3)))
				.andExpect(jsonPath("$._embedded.stepExecutionResourceList[0].stepExecution.id", is(4)))
				.andExpect(jsonPath("$._embedded.stepExecutionResourceList[1].stepExecution.id", is(5)))
				.andExpect(jsonPath("$._embedded.stepExecutionResourceList[2].stepExecution.id", is(6)));
	}

	@Test
	public void testSingleGetStepExecutionProgress() throws Exception {
		mockMvc.perform(get("/jobs/executions/1/steps/1/progress").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk()).andExpect(content().json("{finished: " + false + "}"))
				.andExpect(content().json("{percentageComplete: " + 0.5 + "}"))
				.andExpect(jsonPath("$.stepExecutionHistory.count", is(0)))
				.andExpect(jsonPath("$.stepExecutionHistory.commitCount.count", is(0)));
	}

	private void createStepExecution(String jobName, String... stepNames) {
		SchemaVersionTarget schemaVersionTarget = aggregateExecutionSupport.findSchemaVersionTarget(jobName, taskDefinitionReader);
		JobRepository jobRepository = jobRepositoryContainer.get(schemaVersionTarget.getName());
		JobInstance instance = jobRepository.createJobInstance(jobName, new JobParameters());
		JobExecution jobExecution = jobRepository.createJobExecution(instance, new JobParameters(), null);
		for (String stepName : stepNames) {
			StepExecution stepExecution = new StepExecution(stepName, jobExecution, 1L);
			stepExecution.setId(null);
			ExecutionContext context = new ExecutionContext();
			context.put("stepval", stepName);
			stepExecution.setExecutionContext(context);
			jobRepository.add(stepExecution);
		}
		TaskExecutionDao dao = daoContainer.get(schemaVersionTarget.getName());
		TaskExecution taskExecution = dao.createTaskExecution(jobName, new Date(), new ArrayList<String>(), null);
		TaskBatchDao taskBatchDao = taskBatchDaoContainer.get(schemaVersionTarget.getName());
		taskBatchDao.saveRelationship(taskExecution, jobExecution);
	}
}
