JVM: OpenJDK 64-Bit Server VM 21.0.11, args: [], concurrency 32, warm-up 10 s, measured 20 s

| Scenario | fetch | indexes | pool | cache | req | err | req/s | p50 ms | p95 ms | p99 ms | max ms | SQL/req | max waiting for conn | cache hit |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| S4-cache | join | yes | 10 | 5s | 230158 | 0 | 11507.4 | 2.4 | 5.7 | 9.3 | 161.6 | 0.1 | 3 | 96% |
| S4-cache | join | yes | 10 | 5s | 312094 | 0 | 15599.2 | 1.7 | 4.3 | 7.4 | 206.1 | 0.0 | 22 | 97% |
JVM: OpenJDK 64-Bit Server VM 21.0.11, args: [-XX:+UseParallelGC], concurrency 32, warm-up 10 s, measured 20 s

| Scenario | fetch | indexes | pool | cache | req | err | req/s | p50 ms | p95 ms | p99 ms | max ms | SQL/req | max waiting for conn | cache hit |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| S4-cache | join | yes | 10 | 5s | 234009 | 0 | 11699.6 | 2.3 | 5.5 | 9.7 | 170.9 | 0.1 | 10 | 97% |
| S4-cache | join | yes | 10 | 5s | 94845 | 0 | 4742.0 | 3.1 | 16.8 | 56.9 | 1015.9 | 0.1 | 22 | 96% |
JVM: OpenJDK 64-Bit Server VM 21.0.11, args: [-XX:+UseZGC, -XX:+ZGenerational], concurrency 32, warm-up 10 s, measured 20 s

| Scenario | fetch | indexes | pool | cache | req | err | req/s | p50 ms | p95 ms | p99 ms | max ms | SQL/req | max waiting for conn | cache hit |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| S4-cache | join | yes | 10 | 5s | 31058 | 0 | 1552.5 | 12.3 | 58.6 | 144.9 | 504.8 | 0.2 | 22 | 88% |
| S4-cache | join | yes | 10 | 5s | 49117 | 0 | 2455.6 | 8.6 | 38.2 | 66.5 | 397.8 | 0.2 | 22 | 90% |
JVM: OpenJDK 64-Bit Server VM 21.0.11, args: [-Xmx128m], concurrency 32, warm-up 10 s, measured 20 s

| Scenario | fetch | indexes | pool | cache | req | err | req/s | p50 ms | p95 ms | p99 ms | max ms | SQL/req | max waiting for conn | cache hit |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| S4-cache | join | yes | 10 | 5s | 29430 | 0 | 1471.0 | 12.5 | 68.4 | 93.2 | 213.4 | 0.2 | 20 | 88% |
| S4-cache | join | yes | 10 | 5s | 34301 | 0 | 1712.3 | 6.4 | 54.7 | 88.1 | 167.5 | 0.2 | 20 | 88% |
