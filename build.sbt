enablePlugins(JavaAppPackaging, DockerPlugin)

val Http4sVersion = "0.20.1"
val ZioVersion = "1.0-RC4"

libraryDependencies ++= Seq(
  "org.scalaz"      %% "scalaz-zio"              % ZioVersion,
  "org.scalaz"      %% "scalaz-zio-interop-cats" % ZioVersion,
  "org.scalatest"   %% "scalatest"               % "3.0.5" % "test",
  "com.google.cloud" % "google-cloud-monitoring" % "1.74.0",
  "org.http4s"      %% "http4s-blaze-server"     % Http4sVersion,
  "org.http4s"      %% "http4s-blaze-client"     % Http4sVersion,
  "org.http4s"      %% "http4s-circe"            % Http4sVersion,
  "org.http4s"      %% "http4s-dsl"              % Http4sVersion
)

dockerPermissionStrategy := com.typesafe.sbt.packager.docker.DockerPermissionStrategy.Run

dockerRepository := sys.props.get("docker.repo")

dockerUsername := sys.props.get("docker.username")

packageName := sys.props.get("docker.packagename").getOrElse(name.value)
