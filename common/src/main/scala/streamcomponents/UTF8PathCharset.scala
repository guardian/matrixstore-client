package streamcomponents

import java.io.File
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Path

import akka.stream.scaladsl.{Flow, GraphDSL}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object UTF8PathCharset {
  def apply() = GraphDSL.create() {implicit builder=>
    val f = builder.add(new UTF8PathCharset)
    FlowShape(f.in,f.out)
  }
}

class UTF8PathCharset extends GraphStage[FlowShape[Path,Path]]{
  private final val in:Inlet[Path] = Inlet.create("UTF8PathCharset.in")
  private final val out:Outlet[Path] = Outlet.create("UTF8PathCharset.out")

  override def shape: FlowShape[Path, Path] = FlowShape.of(in, out)

  /*taken from https://www.turro.org/publications/?item=114&page=0 with thanks! */
  def convert(value: String, fromEncoding: String, toEncoding: String) = new String(value.getBytes(fromEncoding), toEncoding)

  def charset(value: String, charsets: Array[String]): String = {
    val probe = StandardCharsets.UTF_8.name
    for (c <- charsets) {
      val charset = Charset.forName(c)
      if (charset != null) if (value == convert(convert(value, charset.name, probe), probe, charset.name)) return c
    }
    StandardCharsets.UTF_8.name
  }

  def tryConvert(value:String) = Try {charset(value, Array("ISO-8859-1","UTF-8")) }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val incoming = grab(in)

        val pathString = incoming.toString
        tryConvert(pathString) match {
          case Success(converted)=>
            if(pathString==converted){
              logger.debug(s"No conversion needed for ${pathString}")
              push(out, incoming)
            } else {
              logger.debug(s"Fixed path $pathString to $converted")
              push(out, new File(pathString).toPath)
            }
          case Failure(err)=>
            logger.error(s"Could not perform UTF8 conversion on $pathString: ", err)
            failStage(err)
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
