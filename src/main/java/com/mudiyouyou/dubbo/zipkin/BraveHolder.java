package com.mudiyouyou.dubbo.zipkin;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.EmptySpanCollectorMetricsHandler;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.http.HttpSpanCollector;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class BraveHolder {

    /**
     * tracing.properties format
     * tracing.enabled=true
     * tracing.url=http://192.168.10.188:9411
     * tracing.sampler=1
     */
    private static class BraveSingelton {
        private static Properties properties = new Properties();
        private static Brave brave;

        static {
            try {
                InputStream resourcde = BraveHolder.class.getClassLoader().getResourceAsStream("tracing.properties");
                properties.load(resourcde);
                String url = properties.getProperty("tracing.url");
                String enabled = properties.getProperty("tracing.enabled");
                String sampler = properties.getProperty("tracing.sampler");
                String serviceName = properties.getProperty("tracing.serviceName");
                int connectTimeout = Integer.parseInt(properties.getProperty("tracing.connectTimeout"));
                int flushInterval = Integer.parseInt(properties.getProperty("tracing.flushInterval"));
                int readTimeout = Integer.parseInt(properties.getProperty("tracing.readTimeout"));
                boolean compressionEnabled = Boolean.parseBoolean(properties.getProperty("tracing.compressionEnabled"));
                if (enabled != null && enabled.equalsIgnoreCase("true")) {
                    HttpSpanCollector.Config config = HttpSpanCollector.Config.builder()
                            .compressionEnabled(compressionEnabled)
                            .connectTimeout(connectTimeout)
                            .flushInterval(flushInterval)
                            .readTimeout(readTimeout)
                            .build();
                    HttpSpanCollector spanCollector = HttpSpanCollector.create(url, config, new EmptySpanCollectorMetricsHandler());
                    Brave.Builder builder = new Brave.Builder(serviceName);// 指定serviceName
                    builder.spanCollector(spanCollector);
                    builder.traceSampler(Sampler.create(Float.parseFloat(sampler)));// 采集率
                    brave = builder.build();
                }
            } catch (IOException e) {
                throw new RuntimeException("Tracing startup error:missing tracing.properties in classpath", e);
            } catch (Exception e) {
                throw new RuntimeException("Tracing startup error:" + e.getMessage(), e);
            }
        }
    }

    public static Brave get() {
        if (BraveSingelton.brave != null) {
            return BraveSingelton.brave;
        }
        throw new RuntimeException("Please set up tracing.enabled to true in tracing.properties");
    }
}
