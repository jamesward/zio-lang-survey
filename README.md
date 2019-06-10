ZIO Language Survey
-------------------

```
./sbt "~reStart -web"
```

```
curl -H "Content-Type: application/json" -X POST -d @example-start.json localhost:8080
curl -H "Content-Type: application/json" -X POST -d @example-wrong.json localhost:8080
curl -H "Content-Type: application/json" -X POST -d @example-scala.json localhost:8080
```

TODO:

 - Fix the web server
 - Add JSON handling
 - Add Graal
 - Fix Action Package
 - Stackdriver

LATER:

 - Use Ref
 - Use ZIO Console
 - Replace RawHttp with httpcomponents
