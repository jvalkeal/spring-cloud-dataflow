/*
 * Copyright 2020 the original author or authors.
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
package org.springframework.cloud.dataflow.core.dsl;

import java.util.Optional;

/**
 * Abstracts dsl parser into its own service allowing to switch an actual
 * implementation at runtime.
 *
 * @author Janne Valkealahti
 *
 */
public interface ParserService {

	/**
	 * Parse a stream dsl returning an optional stream node result. If result is not
	 * present it means particular service doesn't know how to handle it.
	 *
	 * @param dsl  the dsl
	 * @param name the name
	 * @return Optional stream node result
	 */
	Optional<StreamNode> parseStream(String dsl, String name);
}
