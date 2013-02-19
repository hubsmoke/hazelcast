/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.concurrent.countdownlatch;

import com.hazelcast.client.ClientCommandHandler;
import com.hazelcast.concurrent.countdownlatch.client.CDLAwaitHandler;
import com.hazelcast.concurrent.countdownlatch.client.CDLCountdownHandler;
import com.hazelcast.concurrent.countdownlatch.client.CDLGetCountHandler;
import com.hazelcast.concurrent.countdownlatch.client.CDLSetCountHandler;
import com.hazelcast.nio.protocol.Command;
import com.hazelcast.partition.MigrationEndpoint;
import com.hazelcast.partition.MigrationType;
import com.hazelcast.spi.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @mdogan 1/10/13
 */
public class CountDownLatchService implements ManagedService, RemoteService, MigrationAwareService, ClientProtocolService {

    public final static String SERVICE_NAME = "hz:impl:countDownLatchService";

    private final ConcurrentMap<String, CountDownLatchInfo> latches = new ConcurrentHashMap<String, CountDownLatchInfo>();
    private NodeEngine nodeEngine;

    public int getCount(String name) {
        final CountDownLatchInfo latch = latches.get(name);
        return latch != null ? latch.getCount() : 0;
    }

    public boolean setCount(String name, int count, String owner) {
        if (count <= 0) {
            latches.remove(name);
            return false;
        } else {
            CountDownLatchInfo latch = latches.get(name);
            if (latch == null) {
                latch = new CountDownLatchInfo(name);
                latches.put(name, latch);
            }
            return latch.setCount(count, owner);
        }
    }

    public void setCountDirect(String name, int count, String owner) {
        if (count <= 0) {
            latches.remove(name);
        } else {
            CountDownLatchInfo latch = latches.get(name);
            if (latch == null) {
                latch = new CountDownLatchInfo(name);
                latches.put(name, latch);
            }
            latch.setCountDirect(count, owner);
        }
    }

    public void countDown(String name) {
        final CountDownLatchInfo latch = latches.get(name);
        if (latch != null) {
            if (latch.countDown() == 0) {
                latches.remove(name);
            }
        }
    }

    public boolean shouldWait(String name) {
        final CountDownLatchInfo latch = latches.get(name);
        return latch != null && latch.getCount() > 0;
    }

    public void init(NodeEngine nodeEngine, Properties properties) {
        this.nodeEngine = nodeEngine;
    }

    public void reset() {
        latches.clear();
    }

    public void shutdown() {
        latches.clear();
    }

    public String getServiceName() {
        return SERVICE_NAME;
    }

    public CountDownLatchProxy createDistributedObject(Object objectId) {
        return new CountDownLatchProxy(String.valueOf(objectId), nodeEngine);
    }

    public CountDownLatchProxy createDistributedObjectForClient(Object objectId) {
        return new CountDownLatchProxy(String.valueOf(objectId), nodeEngine);
    }

    public void destroyDistributedObject(Object objectId) {
        latches.remove(String.valueOf(objectId));
    }

    public void beforeMigration(MigrationServiceEvent migrationServiceEvent) {
    }

    public Operation prepareMigrationOperation(MigrationServiceEvent event) {
        if (event.getReplicaIndex() > 1) {
            return null;
        }
        final Collection<CountDownLatchInfo> data = new LinkedList<CountDownLatchInfo>();
        for (Map.Entry<String, CountDownLatchInfo> latchEntry : latches.entrySet()) {
            if (nodeEngine.getPartitionService().getPartitionId(latchEntry.getKey()) == event.getPartitionId()) {
                data.add(latchEntry.getValue());
            }
        }
        return data.isEmpty() ? null : new CountDownLatchMigrationOperation(data);
    }

    public void commitMigration(MigrationServiceEvent event) {
        if (event.getReplicaIndex() > 1) {
            return;
        }
        if (event.getMigrationEndpoint() == MigrationEndpoint.SOURCE
                && event.getMigrationType() == MigrationType.MOVE) {
            clearPartition(event.getPartitionId());
        }
    }

    public void rollbackMigration(MigrationServiceEvent event) {
        if (event.getReplicaIndex() > 1) {
            return;
        }
        if (event.getMigrationEndpoint() == MigrationEndpoint.DESTINATION) {
            clearPartition(event.getPartitionId());
        }
    }

    private void clearPartition(int partitionId) {
        final Iterator<String> iter = latches.keySet().iterator();
        while (iter.hasNext()) {
            if (nodeEngine.getPartitionService().getPartitionId(iter.next()) == partitionId) {
                iter.remove();
            }
        }
    }

    public int getMaxBackupCount() {
        return 1;
    }

    public CountDownLatchInfo getLatch(String name) {
        return latches.get(name);
    }

    public void add(CountDownLatchInfo latch) {
        latches.put(latch.getName(), latch);
    }

    public Map<Command, ClientCommandHandler> getCommandsAsMap() {
        Map<Command, ClientCommandHandler> map = new HashMap<Command, ClientCommandHandler>();
        map.put(Command.CDLAWAIT, new CDLAwaitHandler(this));
        map.put(Command.CDLCOUNTDOWN, new CDLCountdownHandler(this));
        map.put(Command.CDLGETCOUNT, new CDLGetCountHandler(this));
        map.put(Command.CDLSETCOUNT, new CDLSetCountHandler(this));
        return map;
    }
}
