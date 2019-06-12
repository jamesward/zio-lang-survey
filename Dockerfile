FROM oracle/graalvm-ce:19.0.0 as builder

WORKDIR /app
COPY . /app

RUN gu install native-image

RUN ./sbt graalvm-native-image:packageBin

FROM alpine:3.9.4

COPY --from=builder /app/target/graalvm-native-image/app /app

CMD ["/app", "-web"]
