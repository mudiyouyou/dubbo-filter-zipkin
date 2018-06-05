package com.mudiyouyou.dubbo.filter;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.*;
import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mudiyouyou.dubbo.zipkin.BraveHolder;

import java.util.*;

@Activate(group = Constants.PROVIDER, value = "DubboBraveProviderFilter")
public class DubboBraveProviderFilter implements Filter {

    private final ServerRequestInterceptor serverRequestInterceptor;
    private final ServerResponseInterceptor serverResponseInterceptor;
    private final Gson gson = new GsonBuilder().create();

    public DubboBraveProviderFilter() {
        Brave brave = BraveHolder.get();
        serverRequestInterceptor = brave.serverRequestInterceptor();
        serverResponseInterceptor = brave.serverResponseInterceptor();
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        serverRequestInterceptor.handle(new DubboServerRequest(invoker, invocation));
        Result result = invoker.invoke(invocation);
        serverResponseInterceptor.handle(new DubboServerResponse(result));
        return result;
    }

    private class DubboServerRequest implements ServerRequestAdapter {

        private final Invocation invocation;
        private final Invoker<?> invoker;

        public DubboServerRequest(Invoker<?> invoker, Invocation invocation) {
            this.invocation = invocation;
            this.invoker = invoker;
        }

        @Override
        public TraceData getTraceData() {
            Map<String, String> attachment = invocation.getAttachments();
            String traceId = attachment.get(BraveHttpHeaders.TraceId.getName());
            if (traceId != null) {
                String spanId = attachment.get(BraveHttpHeaders.SpanId.getName());
                String parentSpanId = attachment.get(BraveHttpHeaders.ParentSpanId.getName());
                if (spanId != null && traceId != null) {
                    SpanId span = getSpanId(traceId, spanId, parentSpanId);
                    return TraceData.builder().sample(true).spanId(span).build();
                }
            }
            return TraceData.builder().build();
        }

        private SpanId getSpanId(String traceId, String spanId, String parentSpanId) {
            return SpanId.builder()
                    .spanId(Long.parseLong(spanId))
                    .parentId(parentSpanId == null ? null : Long.parseLong(parentSpanId))
                    .traceId(Long.parseLong(traceId)).build();
        }

        @Override
        public String getSpanName() {
            return "server." + invocation.getMethodName();
        }

        @Override
        public Collection<KeyValueAnnotation> requestAnnotations() {
            String remoteAddr = RpcContext.getContext().getRemoteAddressString();
            if (remoteAddr != null) {
                List<KeyValueAnnotation> list = new ArrayList<>();
                list.add(KeyValueAnnotation.create("CONSUMER_ADDR", remoteAddr));
                list.add(KeyValueAnnotation.create("METHOD", invocation.getMethodName()));
                list.add(KeyValueAnnotation.create("ARGUMENTS", gson.toJson(invocation.getArguments() != null ? invocation.getArguments() : new Object())));
                return list;
            }
            return Collections.emptyList();
        }
    }

    private class DubboServerResponse implements ServerResponseAdapter {
        private final Result result;

        public DubboServerResponse(Result result) {
            this.result = result;
        }

        @Override
        public Collection<KeyValueAnnotation> responseAnnotations() {
            if (result != null) {
                if (result.hasException()) {
                    return Collections.singletonList(KeyValueAnnotation.create("ERROR", gson.toJson(result.getException().getMessage())));
                } else {
                    return Collections.singletonList(KeyValueAnnotation.create("RESULT", gson.toJson(result.getValue())));
                }
            }
            return Collections.emptyList();
        }
    }
}
