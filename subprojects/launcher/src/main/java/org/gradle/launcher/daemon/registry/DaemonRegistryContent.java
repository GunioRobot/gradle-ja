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

package org.gradle.launcher.daemon.registry;

import org.gradle.messaging.remote.Address;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author: Szczepan Faber, created at: 8/29/11
 */
public class DaemonRegistryContent implements Serializable {

    private Map<Address, DaemonStatus> statusesMap = new HashMap<Address, DaemonStatus>();

    /**
     * returns all statuses. May be empty.
     */
    public List<DaemonStatus> getStatuses() {
        return new LinkedList(statusesMap.values());
    }

    /**
     * Gets the status for given address. May return null.
     */
    public DaemonStatus getStatus(Address address) {
        return statusesMap.get(address);
    }

    /**
     * Removes the status
     */
    public void removeStatus(Address address) {
        statusesMap.remove(address);
    }

    /**
     * sets the status for given address
     */
    public void setStatus(Address address, DaemonStatus status) {
        statusesMap.put(address, status);
    }
}