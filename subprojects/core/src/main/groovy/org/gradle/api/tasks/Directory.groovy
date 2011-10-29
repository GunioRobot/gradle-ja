/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException

/**
 * Creates a directory.
 *
 * @author Hans Dockter
 */
public class Directory extends DefaultTask {
    File dir

    Directory() {
        if (new File(name).isAbsolute()) { throw new InvalidUserDataException('Path must not be absolute.')}
        dir = project.file(name)
    }

    @TaskAction
    protected void mkdir() {
        if (dir.exists()) {
            if (dir.isFile()) {
                throw new InvalidUserDataException("The directory $name can't be created. There exists a file already with this path.")
            }
        } else {
            dir.mkdirs()
        }
    }
}
