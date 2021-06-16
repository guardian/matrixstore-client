import java.util.Properties
import scala.util.{Failure, Try}

case class VersionInfo(buildSha: String, buildNumber: String)

object VersionInfo {
  def loadFromResource = {
    val maybeProperties = Try {
      val props = new Properties()
      Option(getClass.getResourceAsStream("version.properties")) match {
        case Some(stream)=>
          val result = Try { props.load(stream) }.map(_=>props)
          stream.close()
          result
        case None=>
          Failure(new RuntimeException("version.properties not found in classpath"))
      }
    }.flatten

    maybeProperties.map(props=>
      VersionInfo(
        props.getProperty("build-sha","none"),
        props.getProperty("build-number","DEV")
      )
    )
  }
}
