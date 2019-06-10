enablePlugins(JavaAppPackaging, DockerPlugin)

val ZioVersion = "1.0-RC5"

libraryDependencies ++= Seq(
  "org.scalaz"                      %% "scalaz-zio"              % ZioVersion,
  "com.athaydes.rawhttp"            %  "rawhttp-core"            % "2.2",
  "org.apache.httpcomponents.core5" %  "httpcore5"               % "5.0-beta7",
  "com.google.cloud"                %  "google-cloud-monitoring" % "1.74.0",
  "org.scalatest"                   %% "scalatest"               % "3.0.5" % "test",

)

dockerPermissionStrategy := com.typesafe.sbt.packager.docker.DockerPermissionStrategy.Run

dockerRepository := sys.props.get("docker.repo")

dockerUsername := sys.props.get("docker.username")

packageName := sys.props.get("docker.packagename").getOrElse(name.value)
