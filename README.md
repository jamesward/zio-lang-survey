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

Local Docker:
```
docker build .
```

GCloud Docker & Cloud Run:
```
export PROJECT_ID=$(gcloud config get-value project)
gcloud builds submit -t gcr.io/$PROJECT_ID/zio-lang-survey
gcloud beta run deploy --image gcr.io/$PROJECT_ID/zio-lang-survey --region us-central1 --allow-unauthenticated zio-lang-survey
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
