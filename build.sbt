enablePlugins(GraalVMNativeImagePlugin)

val ZioVersion = "1.0-RC5"
val circeVersion = "0.10.0"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)
libraryDependencies ++= Seq(
  "org.scalaz"                      %% "scalaz-zio"              % ZioVersion,
  "com.athaydes.rawhttp"            %  "rawhttp-core"            % "2.2",
  "org.apache.httpcomponents.core5" %  "httpcore5"               % "5.0-beta7",
  "com.google.cloud"                %  "google-cloud-monitoring" % "1.74.0",
  "org.scalatest"                   %% "scalatest"               % "3.0.5" % "test",
)

graalVMNativeImageOptions += "--static"
