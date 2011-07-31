/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal;

import groovy.lang.Closure;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.collections.FilteredSet;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.CollectionEventRegister;

import java.util.Set;
import java.util.Collection;
import java.util.LinkedHashSet;

public class DefaultDomainObjectSet<T> extends DefaultDomainObjectCollection<T> implements DomainObjectSet<T> {

    public DefaultDomainObjectSet(Class<T> type) {
        this(type, new LinkedHashSet<T>());
    }

    public DefaultDomainObjectSet(Class<T> type, Collection<T> store) {
        super(type, store);
    }

    protected DefaultDomainObjectSet(DefaultDomainObjectSet<? super T> store, CollectionFilter<T> filter) {
        this(filter.getType(), store.filteredStore(filter), store.filteredEvents(filter));
    }

    protected DefaultDomainObjectSet(Class<T> type, Collection<T> store, CollectionEventRegister<T> eventRegister) {
        super(type, store, eventRegister);
    }

    protected <S extends T> DefaultDomainObjectSet<S> filtered(CollectionFilter<S> filter) {
        return new DefaultDomainObjectSet<S>(this, filter);
    }

    protected <S extends T> Set<S> filteredStore(CollectionFilter<S> filter) {
        return new FilteredSet<T, S>(this, filter);
    }

    public <S extends T> DomainObjectSet<S> withType(Class<S> type) {
        return filtered(createFilter(type));
    }

    public DomainObjectSet<T> matching(Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    public DomainObjectSet<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

}
