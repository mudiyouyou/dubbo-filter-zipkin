package com.mudiyouyou.dubbo.filter;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.*;
import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.twitter.zipkin.gen.Endpoint;
import com.mudiyouyou.dubbo.zipkin.BraveHolder;

import java.util.Collection;
import java.util.Collections;

@Activate(group = Constants.CONSUMER, value = "DubboBraveConsumerFilter")
public class DubboBraveConsumerFilter implements Filter {

    
    private final Gson gson = new GsonBuilder().create();
    private final ClientResponseInterceptor clientResponseInterceptor;
    private final ClientRequestInterceptor clientRequestInterceptor;
    private final Brave brave;

    public DubboBraveConsumerFilter() {
        brave = BraveHolder.get();
        clientRequestInterceptor = brave.clientRequestInterceptor();
        clientResponseInterceptor = brave.clientResponseInterceptor();
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        clientRequestInterceptor.handle(new DubboClientRequest(invoker, invocation));
        Result result = invoker.invoke(invocation);
        clientResponseInterceptor.handle(new DubboClientResponse(result));
        return result;
    }

    private class DubboClientRequest implements ClientRequestAdapter {

        private final Invocation invocation;
        private final Invoker<?> invoker;

        public DubboClientRequest(Invoker<?> invoker, Invocation invocation) {
            this.invocation = invocation;
            this.invoker = invoker;
        }

        @Override
        public String getSpanName() {
            return "client." + invocation.getMethodName();
        }

        @Override
        public void addSpanIdToRequest(SpanId spanId) {
            RpcContext context = RpcContext.getContext();
            context.setAttachment(BraveHttpHeaders.TraceId.getName(),String.valueOf(spanId.traceId));
            context.setAttachment(BraveHttpHeaders.SpanId.getName(),String.valueOf(spanId.spanId));
            context.setAttachment(BraveHttpHeaders.ParentSpanId.getName(),String.valueOf(spanId.parentId));
        }

        @Override
        public Collection<KeyValueAnnotation> requestAnnotations() {
            return Collections.emptyList();
        }

        @Override
        public Endpoint serverAddress() {
            return null;
        }
    }

    private class DubboClientResponse implements ClientResponseAdapter {
        private final Result result;

        public DubboClientResponse(Result result) {
            this.result = result;
        }

        @Override
        public Collection<KeyValueAnnotation> responseAnnotations() {
            return Collections.emptyList();
        }
    }
}
