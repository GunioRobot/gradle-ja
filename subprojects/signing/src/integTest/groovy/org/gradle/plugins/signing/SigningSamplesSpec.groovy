/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.signing

import org.gradle.integtests.fixtures.*
import org.gradle.integtests.fixtures.internal.*
import org.junit.*

class SigningSamplesSpec extends AbstractIntegrationSpec {

    @Rule public final Sample mavenSample = new Sample('signing/maven')
    @Rule public final Sample conditionalSample = new Sample('signing/conditional')

    def "upload attaches signatures"() {
        given:
        sample mavenSample

        expect:
        succeeds "uploadPublished"
    }

    def "conditional signing"() {
        given:
        sample conditionalSample

        expect:
        succeeds "uploadPublished"

        and:
        !(":signArchives" in executedTasks)
    }

}