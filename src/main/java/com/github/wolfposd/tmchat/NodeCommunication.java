/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2016 - 2018 @wolfposd
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.wolfposd.tmchat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.websocket.CloseReason;

import com.github.jtendermint.crypto.ByteUtil;
import com.github.jtendermint.jabci.api.CodeType;
import com.github.jtendermint.jabci.api.ICheckTx;
import com.github.jtendermint.jabci.api.ICommit;
import com.github.jtendermint.jabci.api.IDeliverTx;
import com.github.jtendermint.jabci.socket.TSocket;
import com.github.jtendermint.jabci.types.RequestCheckTx;
import com.github.jtendermint.jabci.types.RequestCommit;
import com.github.jtendermint.jabci.types.RequestDeliverTx;
import com.github.jtendermint.jabci.types.ResponseCheckTx;
import com.github.jtendermint.jabci.types.ResponseCommit;
import com.github.jtendermint.jabci.types.ResponseDeliverTx;
import com.github.jtendermint.websocket.Websocket;
import com.github.jtendermint.websocket.WebsocketException;
import com.github.jtendermint.websocket.WebsocketStatus;
import com.github.jtendermint.websocket.jsonrpc.JSONRPC;
import com.github.jtendermint.websocket.jsonrpc.Method;
import com.github.jtendermint.websocket.jsonrpc.calls.StringParam;
import com.github.wolfposd.tmchat.frontend.FrontendListener;
import com.github.wolfposd.tmchat.frontend.ISendMessage;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;

public class NodeCommunication implements ICheckTx, IDeliverTx, ICommit, ISendMessage, WebsocketStatus {

    private Websocket wsClient;
    private TSocket socket;

    private Gson gson = new Gson();
    private int hashCount = 0;

    private Map<String, FrontendListener> frontends = new HashMap<>();

    public NodeCommunication() {

        wsClient = new Websocket(this);
        socket = new TSocket();
        socket.registerListener(this);
        new Thread(socket::start).start();
        System.out.println("Started ABCI Socket Interface");

        System.out.println("Now waiting on ABCI-Sockets before connecting to Websocket...");
        
        // need atleast 3 socket connections: info,mempool,consensus
        while(socket.sizeOfConnectedABCISockets() < 3) {
            sleep(2000);
            System.out.println("sleeping 2 seconds, to wait for ABCI-connections");
        }
        System.out.println("ABCI connections: " + socket.sizeOfConnectedABCISockets());
        System.out.println("connecting websocket in 5 seconds");
        
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        executorService.schedule(() -> reconnectWS(), 5, TimeUnit.SECONDS);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    private void reconnectWS() {
        System.out.println("Trying to connect to Websocket...");
        try {
            wsClient.connect();
            System.out.println("connected");
        } catch (WebsocketException e) {
            e.printStackTrace();
        }
    }

    public void registerFrontend(String username, FrontendListener f) {
        frontends.put(username, f);
    }

    @Override
    public ResponseDeliverTx receivedDeliverTx(RequestDeliverTx req) {
        
        byte[] byteArray = req.getTx().toByteArray();
        Message msg = gson.fromJson(new String(byteArray), Message.class);

        FrontendListener l = frontends.get(msg.receiver);
        if (l != null) {
            l.messageIncoming(msg);
        }

        return ResponseDeliverTx.newBuilder().setCode(CodeType.OK).build();
    }

    @Override
    public ResponseCheckTx requestCheckTx(RequestCheckTx req) {
        return ResponseCheckTx.newBuilder().setCode(CodeType.OK).build();
    }

    @Override
    public ResponseCommit requestCommit(RequestCommit requestCommit) {
        hashCount += 1;
        return ResponseCommit.newBuilder().setData(ByteString.copyFrom(ByteUtil.toBytes(hashCount))).build();
    }

    @Override
    public void sendMessage(Message m) {
        JSONRPC rpc = new StringParam(Method.BROADCAST_TX_ASYNC, gson.toJson(m).getBytes());
        wsClient.sendMessage(rpc, e -> {
            // no interest
        });
    }

    @Override
    public void wasClosed(CloseReason cr) {
        if (!"Manual Close".equals(cr.getReasonPhrase())) {
            System.out.println("Websocket closed... reconnecting");
            reconnectWS();
        }
    }

}
