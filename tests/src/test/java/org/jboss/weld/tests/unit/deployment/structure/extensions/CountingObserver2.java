/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.tests.unit.deployment.structure.extensions;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessManagedBean;

public class CountingObserver2 implements Extension {

    private int beforeBeanDiscovery;
    private int processFooManagedBean;
    private int processBarManagedBean;

    public void observeBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event, BeanManager beanManager) {
        beforeBeanDiscovery++;
        event.addAnnotatedType(beanManager.createAnnotatedType(Bar.class));
    }

    public void observerProcessFooManagedBean(@Observes ProcessManagedBean<Foo> event) {
        processFooManagedBean++;
    }

    public void observerProcessBarManagedBean(@Observes ProcessManagedBean<Bar> event) {
        processBarManagedBean++;
    }

    public int getBeforeBeanDiscovery() {
        return beforeBeanDiscovery;
    }

    public int getProcessFooManagedBean() {
        return processFooManagedBean;
    }

    public int getProcessBarManagedBean() {
        return processBarManagedBean;
    }

}