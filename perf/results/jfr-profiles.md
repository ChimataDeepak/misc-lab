JVM: OpenJDK 64-Bit Server VM 21.0.11, args: [], concurrency 32, warm-up 10 s, measured 20 s

| Scenario | fetch | indexes | pool | cache | req | err | req/s | p50 ms | p95 ms | p99 ms | max ms | SQL/req | max waiting for conn | cache hit |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| S1-indexes | naive | yes | 10 | 0s | 5286 | 0 | 264.3 | 110.2 | 230.5 | 315.6 | 593.1 | 21.6 | 22 | 0% |
| S2-fetchjoin | join | yes | 10 | 0s | 19623 | 0 | 981.0 | 30.8 | 53.6 | 73.2 | 256.0 | 2.0 | 22 | 0% |
| S4-cache | join | yes | 10 | 5s | 239566 | 0 | 11978.1 | 2.0 | 6.5 | 13.9 | 221.3 | 0.1 | 22 | 97% |

#### JFR S1-indexes
CPU samples: 172 | GC: 11 collections, total pause 112 ms

CPU by library (first frame in that library):
- org.apache.catalina.core.ApplicationFilterChain.internalDoFilter: 20.3%
- com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializerBase.formatValue: 3.5%
- org.apache.catalina.mapper.Mapper.internalMapWrapper: 1.7%
- org.hibernate.resource.jdbc.internal.ResourceRegistryStandardImpl.register: 1.2%
- org.apache.catalina.core.StandardPipeline.isAsyncSupported: 1.2%
- org.apache.catalina.connector.CoyoteAdapter.postParseRequest: 1.2%

Hottest top-of-stack methods:
- java.util.HashMap.getNode: 3.5%
- java.time.format.DateTimeFormatterBuilder$InstantPrinterParser.format: 2.3%
- org.apache.catalina.mapper.Mapper.internalMapWrapper: 1.7%
- jdk.internal.net.http.Exchange$$Lambda.0x00000088024e1f48.apply: 1.2%
- org.apache.catalina.core.StandardPipeline.isAsyncSupported: 1.2%
- ch.qos.logback.classic.Logger.isTraceEnabled: 1.2%

Thread-park time (>=1 ms parks) by where it parked:
- com.zaxxer.hikari.util.ConcurrentBag.borrow: 423210 ms
- (other: jdk.internal.misc.Unsafe.park): 286327 ms
- org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run: 37967 ms
- org.apache.tomcat.util.threads.TaskQueue.poll: 2429 ms
- jdk.internal.net.http.ConnectionPool.returnToPool: 3 ms

#### JFR S2-fetchjoin
CPU samples: 271 | GC: 20 collections, total pause 38 ms

CPU by library (first frame in that library):
- org.apache.catalina.core.ApplicationFilterChain.internalDoFilter: 35.8%
- com.fasterxml.jackson.core.json.JsonWriteContext.createChildObjectContext: 1.8%
- org.apache.coyote.http11.Http11OutputBuffer.write: 1.5%
- org.apache.catalina.mapper.Mapper.internalMapWrapper: 1.5%
- com.fasterxml.jackson.databind.ser.BeanPropertyWriter.serializeAsField: 1.5%
- org.apache.catalina.connector.Request.removeAttribute: 1.1%

Hottest top-of-stack methods:
- java.util.HashMap.getNode: 3.7%
- java.util.HashMap.putVal: 3.0%
- com.fasterxml.jackson.core.json.JsonWriteContext.createChildObjectContext: 1.8%
- org.springframework.web.util.pattern.LiteralPathElement.matches: 1.8%
- java.lang.String.charAt: 1.8%
- java.util.regex.Matcher.match: 1.5%

Thread-park time (>=1 ms parks) by where it parked:
- (other: jdk.internal.misc.Unsafe.park): 469593 ms
- com.zaxxer.hikari.util.ConcurrentBag.borrow: 409193 ms
- org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run: 37977 ms
- org.apache.tomcat.util.threads.TaskQueue.poll: 2911 ms
- com.zaxxer.hikari.pool.HikariPool.addBagItem: 2 ms

#### JFR S4-cache
CPU samples: 1181 | GC: 40 collections, total pause 259 ms

CPU by library (first frame in that library):
- org.apache.catalina.core.ApplicationFilterChain.internalDoFilter: 43.1%
- com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializerBase.formatValue: 5.6%
- com.fasterxml.jackson.databind.ser.BeanPropertyWriter.serializeAsField: 3.1%
- org.apache.coyote.Request.getAttribute: 1.9%
- com.fasterxml.jackson.core.json.JsonWriteContext.createChildObjectContext: 1.4%
- org.apache.coyote.http11.Http11OutputBuffer.write: 1.2%

Hottest top-of-stack methods:
- java.util.HashMap.getNode: 6.4%
- java.time.format.DateTimeFormatterBuilder$InstantPrinterParser.format: 3.0%
- jakarta.servlet.http.HttpServletRequestWrapper.getMethod: 1.8%
- java.util.concurrent.ConcurrentHashMap.get: 1.6%
- com.fasterxml.jackson.databind.ser.BeanPropertyWriter.serializeAsField: 1.5%
- com.fasterxml.jackson.core.json.JsonWriteContext.createChildObjectContext: 1.4%

Thread-park time (>=1 ms parks) by where it parked:
- (other: jdk.internal.misc.Unsafe.park): 1481407 ms
- org.apache.tomcat.util.threads.TaskQueue.poll: 426753 ms
- org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run: 37976 ms
- org.postgresql.util.LazyCleaner$1.run: 30001 ms
- com.zaxxer.hikari.util.ConcurrentBag.borrow: 9448 ms
