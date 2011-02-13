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
package org.gradle.tooling.internal.provider;

import org.gradle.BuildAdapter;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.invocation.Gradle;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.protocol.eclipse.EclipseBuildVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectDependencyVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseSourceDirectoryVersion1;

import java.io.File;
import java.util.*;

public class DefaultConnection implements ConnectionVersion1 {
    private final ActorFactory actorFactory;
    private Worker worker;
    private final ConnectionParametersVersion1 parameters;

    public DefaultConnection(ConnectionParametersVersion1 parameters, ActorFactory actorFactory) {
        this.parameters = parameters;
        this.actorFactory = actorFactory;
    }

    public String getDisplayName() {
        return "Gradle connection";
    }

    public <T extends BuildVersion1> void getModel(Class<T> type, ResultHandlerVersion1<? super T> handler) throws UnsupportedOperationException {
        if (worker == null) {
            worker = actorFactory.createActor(new WorkerImpl()).getProxy(Worker.class);
        }
        worker.build(type, handler);
    }

    private static class ModelBuilder extends BuildAdapter {
        private DefaultEclipseProject rootProject;
        private final Map<Project, EclipseProjectVersion1> projectMapping = new HashMap<Project, EclipseProjectVersion1>();

        @Override
        public void projectsEvaluated(Gradle gradle) {
            rootProject = build(gradle.getRootProject());
        }

        private DefaultEclipseProject build(Project project) {
            Configuration configuration = project.getConfigurations().findByName(
                    "testRuntime");
            List<ExternalDependencyVersion1> dependencies = new ArrayList<ExternalDependencyVersion1>();
            final List<EclipseProjectDependencyVersion1> projectDependencies = new ArrayList<EclipseProjectDependencyVersion1>();

            if (configuration != null) {
                Set<File> classpath = configuration.getFiles();
                for (final File file : classpath) {
                    dependencies.add(new ExternalDependencyVersion1() {
                        public File getFile() {
                            return file;
                        }
                    });
                }
                for (final ProjectDependency projectDependency : configuration.getAllDependencies(ProjectDependency.class)) {
                    projectDependencies.add(new EclipseProjectDependencyVersion1() {
                        public EclipseProjectVersion1 getTargetProject() {
                            return projectMapping.get(projectDependency.getDependencyProject());
                        }

                        public String getPath() {
                            return projectDependency.getDependencyProject().getName();
                        }
                    });
                }
            }

            List<EclipseSourceDirectoryVersion1> sourceDirectories = new ArrayList<EclipseSourceDirectoryVersion1>();
            sourceDirectories.add(sourceDirectory(project, "src/main/java"));
            sourceDirectories.add(sourceDirectory(project, "src/main/resources"));
            sourceDirectories.add(sourceDirectory(project, "src/test/java"));
            sourceDirectories.add(sourceDirectory(project, "src/test/resources"));

            List<EclipseProjectVersion1> children = new ArrayList<EclipseProjectVersion1>();
            for (Project child : project.getChildProjects().values()) {
                children.add(build(child));
            }

            DefaultEclipseProject eclipseProject = new DefaultEclipseProject(project.getName(), children, sourceDirectories, dependencies, projectDependencies);
            projectMapping.put(project, eclipseProject);
            return eclipseProject;
        }

        private EclipseSourceDirectoryVersion1 sourceDirectory(final Project project, final String path) {
            return new EclipseSourceDirectoryVersion1() {
                public File getDirectory() {
                    return project.file(path);
                }

                public String getPath() {
                    return path;
                }
            };
        }
    }

    private interface Worker {
        <T extends BuildVersion1> void build(Class<T> type, ResultHandlerVersion1<? super T> handler);
    }

    private class WorkerImpl implements Worker {
        public <T extends BuildVersion1> void build(Class<T> type, ResultHandlerVersion1<? super T> handler) {
            try {
                handler.onComplete(build(type));
            } catch (Throwable t) {
                handler.onFailure(t);
            }
        }

        private <T extends BuildVersion1> T build(Class<T> type) throws UnsupportedOperationException {
            if (type.isAssignableFrom(EclipseBuildVersion1.class)) {
                StartParameter startParameter = new ConnectionToStartParametersConverter().convert(parameters);

                final GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);
                final ModelBuilder builder = new ModelBuilder();
                gradleLauncher.addListener(builder);
                gradleLauncher.getBuildAnalysis().rethrowFailure();
                return type.cast(new DefaultEclipseBuild(builder.rootProject));
            }

            throw new UnsupportedOperationException();
        }
    }
}
