/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea

import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.idea.model.Workspace

/**
 * Generates an IDEA workspace file *only* for root project.
 * There's little you can configure about workspace generation at the moment.
 * <p>
 * Example:
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'idea'
 *
 * ideaWorkspace {
 *   doLast {
 *     //...
 *   }
 * }
 * </pre>
 *
 * @author Hans Dockter
 */
public class GenerateIdeaWorkspace extends XmlGeneratorTask<Workspace> {

    @Override protected Workspace create() {
        return new Workspace(xmlTransformer)
    }

    @Override protected void configure(Workspace object) {
    }
}
