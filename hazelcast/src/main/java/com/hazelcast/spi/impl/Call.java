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

package com.hazelcast.spi.impl;

import com.hazelcast.core.MemberLeftException;
import com.hazelcast.instance.MemberImpl;
import com.hazelcast.nio.Address;

final class Call {

    private final Address target;
    private final InvocationImpl callback;

    public Call(Address target, InvocationImpl callback) {
        this.target = target;
        this.callback = callback;
    }

    public void offerResponse(Object response) {
        callback.notify(response);
    }

    // @mm - I guess we dont need to take any action on disconnect.
//    public void onDisconnect(Address disconnectedAddress) {
//        if (disconnectedAddress.equals(target)) {
//            callback.notify(new TargetDisconnectedException(disconnectedAddress));
//        }
//    }

    public void onMemberLeft(MemberImpl leftMember) {
        if (leftMember.getAddress().equals(target)) {
            callback.notify(new MemberLeftException(leftMember));
        }
    }

    @Override
    public String toString() {
        return "Call{" +
               "target=" + target +
               ", callback=" + callback +
               '}';
    }
}
