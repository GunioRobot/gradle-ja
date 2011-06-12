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
package org.gradle.messaging.remote.internal;

import org.gradle.messaging.concurrent.AsyncStoppable;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.dispatch.DiscardingFailureHandler;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.DispatchFailureHandler;
import org.gradle.util.IdGenerator;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MessageHub implements AsyncStoppable {
    private final Lock lock = new ReentrantLock();
    private final CompositeStoppable executors = new CompositeStoppable();
    private final CompositeStoppable connections = new CompositeStoppable();
    private final Set<ProtocolStack<Message>> handlers = new HashSet<ProtocolStack<Message>>();
    private final Map<String, ProtocolStack<Message>> outgoingUnicasts = new HashMap<String, ProtocolStack<Message>>();
    private final Map<String, ProtocolStack<Message>> outgoingBroadcasts = new HashMap<String, ProtocolStack<Message>>();
    private final DispatchFailureHandler<Object> failureHandler;
    private final Router router;
    private final String displayName;
    private final String nodeName;
    private final ExecutorFactory executorFactory;
    private final IdGenerator<?> idGenerator;
    private final ClassLoader messagingClassLoader;
    private final StoppableExecutor incomingExecutor;

    public MessageHub(String displayName, String nodeName, ExecutorFactory executorFactory, IdGenerator<?> idGenerator, ClassLoader messagingClassLoader) {
        this.displayName = displayName;
        this.nodeName = nodeName;
        this.executorFactory = executorFactory;
        this.idGenerator = idGenerator;
        this.messagingClassLoader = messagingClassLoader;
        failureHandler = new DiscardingFailureHandler<Object>(LoggerFactory.getLogger(MessageHub.class));
        StoppableExecutor executor = executorFactory.create(displayName + " message router");
        executors.add(executor);
        router = new Router(executor, failureHandler);

        incomingExecutor = executorFactory.create(displayName + " worker");
        executors.add(incomingExecutor);
    }

    /**
     * Adds an incoming connection. Stops the connection when finished with it.
     */
    public void addConnection(Connection<Message> connection) {
        lock.lock();
        try {
            AsyncConnectionAdapter<Message> asyncConnection = new AsyncConnectionAdapter<Message>(connection, failureHandler, executorFactory, new ConnectionDisconnectProtocol());
            connections.add(asyncConnection);

            AsyncConnection<Message> incomingEndpoint = router.createRemoteConnection();
            incomingEndpoint.dispatchTo(new MethodInvocationMarshallingDispatch(asyncConnection));
            asyncConnection.dispatchTo(new MethodInvocationUnmarshallingDispatch(incomingEndpoint, messagingClassLoader));
        } finally {
            lock.unlock();
        }
    }

    public Dispatch<Object> addUnicastOutgoing(String channel) {
        lock.lock();
        try {
            ProtocolStack<Message> outgoing = outgoingUnicasts.get(channel);
            if (outgoing == null) {
                Protocol<Message> unicastSendProtocol = new UnicastSendProtocol();
                Protocol<Message> sendProtocol = new SendProtocol(idGenerator.generateId(), nodeName, channel);
                StoppableExecutor executor = executorFactory.create(displayName + " outgoing " + channel);
                executors.add(executor);
                outgoing = new ProtocolStack<Message>(executor, failureHandler, failureHandler, unicastSendProtocol, sendProtocol, new ConnectionDisconnectProtocol());
                outgoingUnicasts.put(channel, outgoing);

                AsyncConnection<Message> outgoingEndpoint = router.createLocalConnection();
                outgoing.getBottom().dispatchTo(outgoingEndpoint);
                outgoingEndpoint.dispatchTo(outgoing.getBottom());
            }
            return new OutgoingMultiplex(channel, outgoing.getTop());
        } finally {
            lock.unlock();
        }
    }

    public Dispatch<Object> addMulticastOutgoing(String channel) {
        lock.lock();
        try {
            ProtocolStack<Message> outgoing = outgoingBroadcasts.get(channel);
            if (outgoing == null) {
                Protocol<Message> broadcastProtocol = new BroadcastSendProtocol();
                Protocol<Message> sendProtocol = new SendProtocol(idGenerator.generateId(), nodeName, channel);
                StoppableExecutor executor = executorFactory.create(displayName + " outgoing broadcast " + channel);
                executors.add(executor);
                outgoing = new ProtocolStack<Message>(executor, failureHandler, failureHandler, broadcastProtocol, sendProtocol, new ConnectionDisconnectProtocol());
                outgoingBroadcasts.put(channel, outgoing);

                AsyncConnection<Message> outgoingEndpoint = router.createLocalConnection();
                outgoing.getBottom().dispatchTo(outgoingEndpoint);
                outgoingEndpoint.dispatchTo(outgoing.getBottom());
            }
            return new OutgoingMultiplex(channel, outgoing.getTop());
        } finally {
            lock.unlock();
        }
    }

    public void addIncoming(final String channel, final Dispatch<Object> dispatch) {
        lock.lock();
        try {
            final Object id = idGenerator.generateId();
            Protocol<Message> workerProtocol = new WorkerProtocol(dispatch);
            Protocol<Message> receiveProtocol = new ReceiveProtocol(id, nodeName, channel);

            ProtocolStack<Message> stack = new ProtocolStack<Message>(incomingExecutor, failureHandler, failureHandler, workerProtocol, receiveProtocol, new ConnectionDisconnectProtocol());
            handlers.add(stack);

            AsyncConnection<Message> incomingEndpoint = router.createLocalConnection();
            stack.getBottom().dispatchTo(incomingEndpoint);
            incomingEndpoint.dispatchTo(stack.getBottom());
        } finally {
            lock.unlock();
        }
    }

    public void requestStop() {
        lock.lock();
        try {
            for (ProtocolStack<Message> stack : outgoingUnicasts.values()) {
                stack.requestStop();
            }
            for (ProtocolStack<Message> stack : outgoingBroadcasts.values()) {
                stack.requestStop();
            }
            for (ProtocolStack<?> handler : handlers) {
                handler.requestStop();
            }
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        requestStop();

        CompositeStoppable stoppable = new CompositeStoppable();
        lock.lock();
        try {
            stoppable.add(outgoingUnicasts.values());
            stoppable.add(outgoingBroadcasts.values());
            stoppable.add(handlers);
            stoppable.add(connections);
            stoppable.add(router);
            stoppable.add(executors);
        } finally {
            outgoingUnicasts.clear();
            outgoingBroadcasts.clear();
            handlers.clear();
            lock.unlock();
        }

        stoppable.stop();
    }
}
