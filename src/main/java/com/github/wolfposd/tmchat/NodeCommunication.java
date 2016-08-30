/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2016
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

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.github.jtmsp.api.IAppendTx;
import com.github.jtmsp.api.ICheckTx;
import com.github.jtmsp.api.ICommit;
import com.github.jtmsp.socket.TSocket;
import com.github.jtmsp.types.Types.CodeType;
import com.github.jtmsp.types.Types.RequestAppendTx;
import com.github.jtmsp.types.Types.RequestCheckTx;
import com.github.jtmsp.types.Types.RequestCommit;
import com.github.jtmsp.types.Types.ResponseAppendTx;
import com.github.jtmsp.types.Types.ResponseCheckTx;
import com.github.jtmsp.types.Types.ResponseCommit;
import com.github.jtmsp.websocket.ByteUtil;
import com.github.jtmsp.websocket.TMWSClient;
import com.github.jtmsp.websocket.TMWSClient.WSListener;
import com.github.jtmsp.websocket.jsonrpc.JSONRPC;
import com.github.wolfposd.tmchat.frontend.FrontendListener;
import com.github.wolfposd.tmchat.frontend.ISendMessage;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;

public class NodeCommunication implements ICheckTx, IAppendTx, ICommit, WSListener, ISendMessage {

    private TMWSClient wsClient;
    private TSocket socket;

    private Gson gson = new Gson();
    private int hashCount = 0;

    private Map<String, FrontendListener> frontends = new HashMap<>();

    public NodeCommunication() {

        socket = new TSocket();
        socket.registerListener(this);
        new Thread(socket::start).start();
        System.out.println("Started TMSP Socket");

        // wait 5 seconds before connecting the websocket
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        executorService.schedule(() -> reconnectWS(), 5, TimeUnit.SECONDS);
    }

    private void reconnectWS() {
        try {
            wsClient = new TMWSClient("http://localhost:46657/websocket");
            wsClient.addListener(this);
            wsClient.connectBlocking();
            System.out.println("Websocket connected");
        } catch (URISyntaxException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void registerFrontend(String username, FrontendListener f) {
        frontends.put(username, f);
    }

    @Override
    public ResponseAppendTx receivedAppendTx(RequestAppendTx req) {

        Message msg = gson.fromJson(new String(req.getTx().toByteArray()), Message.class);

        FrontendListener l = frontends.get(msg.receiver);
        if (l != null) {
            l.messageIncoming(msg);
        }

        return ResponseAppendTx.newBuilder().setCode(CodeType.OK).build();
    }

    @Override
    public ResponseCheckTx requestCheckTx(RequestCheckTx req) {
        return ResponseCheckTx.newBuilder().setCode(CodeType.OK).build();
    }

    @Override
    public ResponseCommit requestCommit(RequestCommit requestCommit) {
        hashCount += 1;
        return ResponseCommit.newBuilder().setCode(CodeType.OK).setData(ByteString.copyFrom(ByteUtil.toBytes(hashCount))).build();
    }

    @Override
    public void sendMessage(Message m) {
        JSONRPC rpc = JSONRPC.broadcastTXSync(gson.toJson(m));
        wsClient.send(rpc, e -> {
            // no interest
            });
    }

    @Override
    public void onPingSent() {
        JSONRPC rpc = JSONRPC.blockHeight(1);
        wsClient.send(rpc, cb -> {
            // no interest
            });
    }

    @Override
    public void onClose(int code, String codeName, String reason, boolean remote) {
        System.out.println("Websocket closed... reconnecting");
        reconnectWS();
    }

}
