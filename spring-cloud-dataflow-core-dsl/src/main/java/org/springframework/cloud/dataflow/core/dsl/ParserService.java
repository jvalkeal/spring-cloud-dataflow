package org.springframework.cloud.dataflow.core.dsl;

import java.util.Optional;

public interface ParserService {

	Optional<StreamNode> parseStream(String dsl, String name);

	// Optional<TaskNode> parseTask(String taskName, String taskDefinition, boolean inAppMode, boolean validate);
}
