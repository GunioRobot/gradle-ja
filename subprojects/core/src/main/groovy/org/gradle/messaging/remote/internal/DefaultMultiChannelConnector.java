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

package org.gradle.messaging.remote.internal;

import org.gradle.api.Action;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;

public class DefaultMultiChannelConnector implements MultiChannelConnector, Stoppable {
    private final OutgoingConnector outgoingConnector;
    private final ExecutorFactory executorFactory;
    private final StoppableExecutor executorService;
    private final HandshakeIncomingConnector incomingConnector;

    public DefaultMultiChannelConnector(OutgoingConnector<Message> outgoingConnector, IncomingConnector<Object> incomingConnector,
                                        ExecutorFactory executorFactory) {
        this.outgoingConnector = new HandshakeOutgoingConnector(outgoingConnector);
        this.executorFactory = executorFactory;
        executorService = executorFactory.create("Incoming Connection Handler");
        this.incomingConnector = new HandshakeIncomingConnector(incomingConnector, executorService);
    }

    public void stop() {
        executorService.stop();
    }

    public Address accept(final Action<ConnectEvent<MultiChannelConnection<Object>>> action) {
        return incomingConnector.accept(new Action<ConnectEvent<Connection<Object>>>() {
            public void execute(ConnectEvent<Connection<Object>> event) {
                finishConnect(event, action);
            }
        });
    }

    private void finishConnect(ConnectEvent<Connection<Object>> event,
                               Action<ConnectEvent<MultiChannelConnection<Object>>> action) {
        Address localAddress = event.getLocalAddress();
        Address remoteAddress = event.getRemoteAddress();
        DefaultMultiChannelConnection channelConnection = new DefaultMultiChannelConnection(executorFactory,
                String.format("Incoming Connection %s", localAddress), event.getConnection(), localAddress, remoteAddress);
        action.execute(new ConnectEvent<MultiChannelConnection<Object>>(channelConnection, localAddress, remoteAddress));
    }

    public MultiChannelConnection<Object> connect(Address destinationAddress) {
        Connection<Object> connection = outgoingConnector.connect(destinationAddress);
        return new DefaultMultiChannelConnection(executorFactory,
                String.format("Outgoing Connection %s", destinationAddress), connection, null, destinationAddress);
    }
}
