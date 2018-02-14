package com.walmart.stores.sfp;

import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.StatsFactory;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.senders.UdpSender;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Path("/service")
public class TracingService {

	@GET
	@Path("/status")
	public Response getMsg() {
		String output = "Jersey say Status is good!";
		return Response.status(200).entity(output).build();
	}
 
	@GET
	@Path("/item")
	public Response getMsg(@Context HttpHeaders httpHeaders) {
		MultivaluedMap<String, String> headers = httpHeaders.getRequestHeaders();
		HashMap<String,String> headerMap = new HashMap<String,String>();
		for (HashMap.Entry<String,List<String>> e : headers.entrySet()) {
			headerMap.put(e.getKey(), e.getValue().get(0));
		}

		Tracer tracer = buildTracer("testTrace");
		Span span = getCurrentSpan(tracer, headerMap);

		try {
			TimeUnit.SECONDS.sleep(1);
		} catch (Exception ex){
			String exception = ex.toString();
		}

		span.finish();

		return Response.status(200).entity("success").build();
	}

	private Tracer buildTracer(String tracerName){
		String jaegerAgentIpAddress = "172.28.128.5";
		UdpSender sender = new UdpSender(jaegerAgentIpAddress, 6831, 1000);
		StatsFactory statsFactory = new StatsFactoryImpl(new InMemoryStatsReporter());
		Metrics metrics = new Metrics(statsFactory);
		Reporter reporter = new RemoteReporter(sender, 1000, 100, metrics);
		Tracer tracer =
				new com.uber.jaeger.Tracer.Builder(tracerName, reporter, new ConstSampler(true))
						.withStatsReporter(new InMemoryStatsReporter())
						.build();
		return tracer;
	}

	private Span getCurrentSpan(Tracer tracer, HashMap<String,String> headerMap){
		SpanContext spanContext = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapExtractAdapter(headerMap));
		headerMap.put("uber-funcOneBaggagekey", "funcOneBaggageValue");

		String operationName = "service1";
		Span span = null;
		if (spanContext != null) {
			span = tracer.buildSpan(operationName).asChildOf(spanContext).startManual();
		}
		else{
			span = tracer.buildSpan(operationName).startManual();
			tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS,
					new TextMapInjectAdapter(headerMap));
		}
		span.setTag("span.kind", "client");

		return span;
	}
 
}