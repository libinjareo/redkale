/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.nio.*;
import java.nio.channels.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;
import org.redkale.util.*;

/**
 * 根Servlet的处理逻辑类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public class PrepareRunner implements Runnable {

    private final AsyncConnection channel;

    private final Context context;

    private ByteBuffer data;

    private Response response;

    public PrepareRunner(Context context, AsyncConnection channel, ByteBuffer data, Response response) {
        this.context = context;
        this.channel = channel;
        this.data = data;
        this.response = response;
    }

    @Override
    public void run() {
        final boolean keepalive = response != null;
        final PrepareServlet prepare = context.prepare;
        final ObjectPool<? extends Response> responsePool = context.responsePool;
        if (data != null) { //BIO模式的UDP连接创建AsyncConnection时已经获取到ByteBuffer数据了
            if (response == null) response = responsePool.get();
            try {
                response.init(channel);
                prepare.prepare(data, response.request, response);
            } catch (Throwable t) {
                context.logger.log(Level.WARNING, "prepare servlet abort, forece to close channel ", t);
                response.finish(true);
            }
            return;
        }
        if (response == null) response = responsePool.get();
        final ByteBuffer buffer = response.request.pollReadBuffer();
        try {
            channel.read(buffer, keepalive ? context.getAliveTimeoutSeconds() : 0, TimeUnit.SECONDS, null,
                new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer count, Void attachment1) {
                    if (count < 1) {
                        response.request.offerReadBuffer(buffer);
                        channel.dispose();// response.init(channel); 在调用之前异常
                        response.removeChannel();
                        response.finish(true);
                        return;
                    }
//                    {  //测试
//                        buffer.flip();
//                        byte[] bs = new byte[buffer.remaining()];
//                        buffer.get(bs);
//                        System.println(new String(bs));
//                    }
                    buffer.flip();
                    response.init(channel);
                    try {
                        prepare.prepare(buffer, response.request, response);
                    } catch (Throwable t) {  //此处不可  context.offerBuffer(buffer); 以免prepare.prepare内部异常导致重复 offerBuffer
                        context.logger.log(Level.WARNING, "prepare servlet abort, forece to close channel ", t);
                        response.finish(true);
                    }
                }

                @Override
                public void failed(Throwable exc, Void attachment2) {
                    response.request.offerReadBuffer(buffer);
                    channel.dispose();// response.init(channel); 在调用之前异常
                    response.removeChannel();
                    response.finish(true);
                    if (exc != null && context.logger.isLoggable(Level.FINEST)) {
                        context.logger.log(Level.FINEST, "Servlet Handler read channel erroneous, forece to close channel ", exc);
                    }
                }
            });
        } catch (Exception te) {
            response.request.offerReadBuffer(buffer);
            channel.dispose();// response.init(channel); 在调用之前异常
            response.removeChannel();
            response.finish(true);
            if (te != null && context.logger.isLoggable(Level.FINEST)) {
                context.logger.log(Level.FINEST, "Servlet read channel erroneous, forece to close channel ", te);
            }
        }
    }

    protected void prepare(ByteBuffer buffer, Request request, Response response) throws IOException {
        context.prepare.prepare(buffer, request, response);
    }

    protected void initResponse(Response response, AsyncConnection channel) {
        response.init(channel);
    }

    protected Response pollResponse() {
        return context.responsePool.get();
    }

    protected Request pollRequest(Response response) {
        return response.request;
    }

    protected AsyncConnection removeChannel(Response response) {
        return response.removeChannel();
    }

    protected ByteBuffer pollReadBuffer(Request request) {
        return request.pollReadBuffer();
    }

    protected ByteBuffer pollReadBuffer(Response response) {
        return response.request.pollReadBuffer();
    }

    protected void offerReadBuffer(Request request, ByteBuffer buffer) {
        request.offerReadBuffer(buffer);
    }

    protected void offerReadBuffer(Response response, ByteBuffer buffer) {
        response.request.offerReadBuffer(buffer);
    }
}
