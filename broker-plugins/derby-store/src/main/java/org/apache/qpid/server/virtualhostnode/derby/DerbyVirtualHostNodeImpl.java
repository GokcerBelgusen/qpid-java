/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.qpid.server.virtualhostnode.derby;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.derby.DerbyConfigurationStore;
import org.apache.qpid.server.store.derby.DerbyUtils;
import org.apache.qpid.server.store.preferences.PreferenceStore;
import org.apache.qpid.server.util.FileHelper;
import org.apache.qpid.server.virtualhostnode.AbstractStandardVirtualHostNode;

@ManagedObject( category = false,
                type = DerbyVirtualHostNodeImpl.VIRTUAL_HOST_NODE_TYPE,
                validChildTypes = "org.apache.qpid.server.virtualhostnode.derby.DerbyVirtualHostNodeImpl#getSupportedChildTypes()" )
public class DerbyVirtualHostNodeImpl extends AbstractStandardVirtualHostNode<DerbyVirtualHostNodeImpl> implements DerbyVirtualHostNode<DerbyVirtualHostNodeImpl>
{
    public static final String VIRTUAL_HOST_NODE_TYPE = "DERBY";

    static
    {
        DerbyUtils.configureDerbyLogging();
    }

    @ManagedAttributeField
    private String _storePath;

    @ManagedObjectFactoryConstructor(conditionallyAvailable = true, condition = "org.apache.qpid.server.store.derby.DerbyUtils#isAvailable()")
    public DerbyVirtualHostNodeImpl(Map<String, Object> attributes, Broker<?> parent)
    {
        super(attributes, parent);
    }

    @Override
    protected void writeLocationEventLog()
    {
        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.STORE_LOCATION(getStorePath()));
    }

    @Override
    protected DurableConfigurationStore createConfigurationStore()
    {
        return new DerbyConfigurationStore(VirtualHost.class);
    }

    @Override
    public String getStorePath()
    {
        return _storePath;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " [id=" + getId() + ", name=" + getName() + ", storePath=" + getStorePath() + "]";
    }


    public static Map<String, Collection<String>> getSupportedChildTypes()
    {
        return Collections.singletonMap(VirtualHost.class.getSimpleName(), getSupportedVirtualHostTypes(true));
    }

    @Override
    public void validateOnCreate()
    {
        if (!new FileHelper().isWritableDirectory(getStorePath()))
        {
            throw new IllegalConfigurationException("The store path is not writable directory");
        }
    }

    @Override
    public PreferenceStore getPreferenceStore()
    {
        return ((DerbyConfigurationStore)getConfigurationStore()).getPreferenceStore();
    }
}
