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
package org.gradle.launcher.daemon.server;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.Connection;

import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A long-lived build server that accepts commands via a communication channel.
 * <p>
 * Daemon instances are single use and have a start/stop debug. They are also threadsafe.
 * <p>
 * See {@link org.gradle.launcher.daemon.client.DaemonClient} for a description of the daemon communication protocol.
 */
public class Daemon implements Runnable, Stoppable {

    private static final Logger LOGGER = Logging.getLogger(Daemon.class);

    private final DaemonServerConnector connector;
    private final DaemonRegistry daemonRegistry;
    private final DaemonCommandExecuter commandExecuter;

    private DaemonStateCoordinator stateCoordinator;

    private final StoppableExecutor handlersExecutor = new DefaultExecutorFactory().create("Daemon Connection Handler");

    private final Lock debugLock = new ReentrantLock();

    private Address connectorAddress;
    private DomainRegistryUpdater registryUpdater;

    /**
     * Creates a new daemon instance.
     * 
     * @param loggingServices The service registry for logging services used by this daemon
     * @param connector The provider of server connections for this daemon
     * @param daemonRegistry The registry that this daemon should advertise itself in
     */
    public Daemon(DaemonServerConnector connector, DaemonRegistry daemonRegistry, DaemonCommandExecuter commandExecuter) {
        this.connector = connector;
        this.daemonRegistry = daemonRegistry;
        this.commandExecuter = commandExecuter;
    }

    /**
     * Starts the daemon, receiving connections asynchronously (i.e. returns immediately).
     * 
     * @throws IllegalStateException if this daemon is already running, or has already been stopped.
     */
    public void start() {
        LOGGER.debug("start() called on daemon");
        debugLock.lock();
        try {
            if (stateCoordinator != null) {
                throw new IllegalStateException("cannot start daemon as it is already running");
            }
            

            // Get ready to accept connections, but we are assuming that no connections will be established
            // because we have not yet advertised that we are open for business by entering our address into
            // the registry, which happens a little further down in this method.
            connectorAddress = connector.start(new IncomingConnectionHandler() {
                public void handle(final Connection<Object> connection) {

                    //we're spinning a thread to do work to avoid blocking the connection
                    //This means that the Daemon potentially can do multiple things but we only allows a single build at a time
                    handlersExecutor.execute(new Runnable() {
                        public void run() {
                            try {
                                Command command = (Command) connection.receive();
                                commandExecuter.executeCommand(connection, command, stateCoordinator);
                            } catch (RuntimeException e) {
                                LOGGER.error("Error processing the incoming command.", e);
                                //TODO SF figure out if we can use our executor's exception handler.
                                throw e; //in case the default exception handler needs it.
                            }
                        }
                    });
                }
            });

            registryUpdater = new DomainRegistryUpdater(daemonRegistry, connectorAddress);
            
            Runnable onStart = new Runnable() {
                public void run() {
                    LOGGER.debug("Daemon starting at: " + new Date() + ", with address: " + connectorAddress);
                    registryUpdater.onStart();
                }
            };
            
            Runnable onStartCommand = new Runnable() {
                public void run() {
                    registryUpdater.onStartActivity();
                }
            };

            Runnable onFinishCommand = new Runnable() {
                public void run() {
                    registryUpdater.onCompleteActivity();
                }
            };
            
            Runnable onStop = new Runnable() {
                public void run() {
                    LOGGER.info("Stop requested. Daemon is stopping accepting new connections...");
                    registryUpdater.onStop();
                    connector.stop(); // will block until any running commands are finished
                }
            };

            stateCoordinator = new DaemonStateCoordinator(onStart, onStartCommand, onFinishCommand, onStop);

            // ready, set, go
            stateCoordinator.start();
        } finally {
            debugLock.unlock();
        }
    }

    /**
     * Stops the daemon, blocking until any current requests/connections have been satisfied.
     * <p>
     * This is the semantically the same as sending the daemon the Stop command.
     */
    public void stop() {
        LOGGER.debug("stop() called on daemon");
        debugLock.lock();
        try {
            stateCoordinator.stop();
        } finally {
            debugLock.unlock();
        }
    }

    /**
     * Blocks until this daemon is stopped by something else (i.e. does not ask it to stop)
     */
    public void awaitStop() {
        LOGGER.debug("awaitStop() called on daemon");
        stateCoordinator.awaitStop();
    }

    /**
     * Waits until the daemon is either stopped, or has been idle for the given number of milliseconds.
     *
     * @return true if it was stopped, false if it hit the given idle timeout.
     */
    public boolean awaitStopOrIdleTimeout(int idleTimeout) {
        LOGGER.debug("awaitStopOrIdleTimeout({}) called on daemon", idleTimeout);
        return stateCoordinator.awaitStopOrIdleTimeout(idleTimeout);
    }

    /**
     * Starts the daemon, blocking until it is stopped (either by Stop command or by another thread calling stop())
     */
    public void run() {
        start();
        awaitStop();
    }

}
