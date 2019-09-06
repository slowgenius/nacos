/*
 * Copyright (C) 2019 the original author or authors.
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
package com.alibaba.nacos.naming.push.grpc.listener;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.naming.push.PushPacket;
import com.alibaba.nacos.core.remoting.grpc.GrpcNetExceptionUtils;
import com.alibaba.nacos.core.remoting.grpc.interactive.GrpcRequestStreamInteractive;
import com.alibaba.nacos.core.remoting.proto.InteractivePayload;
import com.alibaba.nacos.naming.push.AbstractPushClient;
import com.alibaba.nacos.naming.push.grpc.GrpcEmitterService;
import com.alibaba.nacos.naming.push.grpc.GrpcPushClient;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @author pbting
 * @date 2019-09-06 2:04 PM
 */
@Component
public class GrpcEmitterEventListener extends AbstractGrpcEmitterEventListener {

    @Override
    public boolean onEvent(GrpcEmitterService.EmitterRecyclableEvent event, int listenerIndex) {
        GrpcEmitterService grpcEmitterService = (GrpcEmitterService) event.getSource();
        Map<String, AbstractPushClient> clients = event.getValue();
        Map<String, AbstractPushClient> pushFailure = new HashMap<>(8);
        for (Map.Entry<String, AbstractPushClient> entry : clients.entrySet()) {
            AbstractPushClient pushClient = entry.getValue();
            String key = entry.getKey();
            if (!(pushClient instanceof GrpcPushClient)) {
                continue;
            }
            PushPacket pushPacket = grpcEmitterService.prepareHostsData(pushClient);
            pushPacket.setLastRefTime(System.currentTimeMillis());
            GrpcPushClient grpcPushClient = (GrpcPushClient) pushClient;
            GrpcRequestStreamInteractive pusher = grpcPushClient.getPusher();
            try {
                pusher.sendResponsePayload(InteractivePayload.newBuilder()
                    .setPayload(ByteString.copyFrom(JSON.toJSONString(pushPacket).getBytes(Charset.forName("utf-8")))).build());
            } catch (Exception e) {
                if (GrpcNetExceptionUtils.isNetUnavailable(e, null)) {
                    pushFailure.put(key, pushClient);
                }
            }
        }
        event.setParameter(PUSH_FAILURE, pushFailure);
        return true;
    }

    @Override
    public int pipelineOrder() {
        return 1;
    }
}
