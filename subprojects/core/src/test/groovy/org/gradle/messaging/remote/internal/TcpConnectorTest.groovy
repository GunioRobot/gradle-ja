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
package org.gradle.messaging.remote.internal

import org.gradle.api.Action
import org.gradle.util.ConcurrentSpecification

class TcpConnectorTest extends ConcurrentSpecification {
    final def serializer = new DefaultMessageSerializer<String>(getClass().classLoader)
    final def outgoingConnector = new TcpOutgoingConnector<String>(serializer)
    final def incomingConnector = new TcpIncomingConnector<String>(executorFactory, serializer)

    def "client can connect to server"() {
        Action action = Mock()

        when:
        def address = incomingConnector.accept(action)
        def connection = outgoingConnector.connect(address)

        then:
        connection != null

        cleanup:
        incomingConnector.requestStop()
    }

    def "server executes action when incoming connection received"() {
        def connectionReceived = startsAsyncAction()
        Action action = Mock()

        when:
        connectionReceived.started {
            def address = incomingConnector.accept(action)
            outgoingConnector.connect(address)
        }

        then:
        1 * action.execute(!null) >> { connectionReceived.done() }

        cleanup:
        incomingConnector.requestStop()
    }

    def "client throws exception when cannot connect to server"() {
        def address = new SocketInetAddress(InetAddress.getByName("localhost"), 12345)

        when:
        outgoingConnector.connect(address)

        then:
        ConnectException e = thrown()
        e.message.startsWith "Could not connect to server ${address}."
    }
}
