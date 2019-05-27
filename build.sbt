enablePlugins(JavaAppPackaging, DockerPlugin)

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-zio" % "1.0-RC4",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "com.google.cloud" % "google-cloud-monitoring" % "1.74.0"
)

dockerPermissionStrategy := com.typesafe.sbt.packager.docker.DockerPermissionStrategy.Run

dockerRepository := sys.props.get("docker.repo")

dockerUsername := sys.props.get("docker.username")

packageName := sys.props.get("docker.packagename").getOrElse(name.value)
