package commands

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{Attributes, ClosedShape, Inlet, KillSwitch, KillSwitches, Materializer, SharedKillSwitch, SinkShape}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph}
import akka.stream.stage.{AbstractInHandler, GraphStageLogic, GraphStageWithMaterializedValue}
import com.om.mxs.client.japi.{Constants, MatrixStore, SearchTerm}
import models.ObjectMatrixEntry
import org.jline.reader.LineReader
import org.jline.terminal.Terminal
import streamcomponents.OMFastSearchSource

import scala.concurrent.{Future, Promise}

object SearchFunctions {
  class PrintResultSink(includeFields:Array[String],
                        fieldsToPrint:Array[String],
                        itemsPerPage:Int,
                        showHeaders:Boolean,
                        killSwitch:KillSwitch)(implicit terminal:Terminal, lineReader: LineReader)
    extends GraphStageWithMaterializedValue[SinkShape[ObjectMatrixEntry], Future[Done]] {
    private val in:Inlet[ObjectMatrixEntry] = Inlet.create("PrintResultSink.in")

    override def shape: SinkShape[ObjectMatrixEntry] = SinkShape.of(in)

    def paddedString(source:String, padTo:Int):String = {
      if(source.length>=padTo) {
        source
      } else {
        source + (" " * (padTo-source.length))
      }
    }

    def printHeaderLine(): Unit = {
      val elems = Seq(
        paddedString("OID", 42),
        paddedString("File size", 8),
        paddedString("Filename", 20),
      ) ++ fieldsToPrint.map(_.padTo(10," "))
      terminal.writer().println(elems.mkString("\t"))
      terminal.writer().flush()
    }

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes)= {
      val completionPromise = Promise[Done]()

      val logic = new GraphStageLogic(shape) {
        private var ctr:Int = 0

        setHandler(in, new AbstractInHandler {
          override def onPush(): Unit = {
            val result = grab(in)
            val fields = Seq(
              "Path or filename: " + result.maybeGetPath().getOrElse("[no path]"),
              "OID: " + result.oid,
              "Size in bytes: " + result.getFileSize.map(_.toString).getOrElse("[no size]"),
              "MXFS_MODIFICATION_TIME: " + result.timeAttribute("MXFS_MODIFICATION_TIME"),
              "MXFS_ACCESS_TIME: " + result.timeAttribute("MXFS_ACCESS_TIME"),
              "MXFS_CREATION_TIME: " + result.timeAttribute("MXFS_CREATION_TIME"),
              "MXFS_ARCHIVE_TIME: " + result.timeAttribute("MXFS_ARCHIVE_TIME"),
            ) ++ fieldsToPrint.map(f => s"$f: " + result.stringAttribute(f).getOrElse("-"))

            terminal.writer().println(fields.mkString("\n"))
            terminal.writer().println("------------")
            terminal.writer().flush()
            ctr += 1
            if(ctr==itemsPerPage) {
              terminal.writer().println("Press Q [ENTER] to quit or [ENTER] for the next page")
              terminal.writer().flush()
              try {
                val charsRead = lineReader.readLine(' ')

                if (charsRead.charAt(0) == 'q' || charsRead.charAt(0) == 'Q') {
                  killSwitch.shutdown()
                  return
                }
                ctr = 0
              } catch {
                case _:IndexOutOfBoundsException=>
                  ctr=0
              }
            }
            pull(in)
          }

          override def onUpstreamFinish(): Unit = {
            completionPromise.success(Done.done())
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            completionPromise.failure(ex)
          }
        })

        override def preStart(): Unit = {
          pull(in)
        }
      }

      (logic, completionPromise.future)
    }
  }

  def doSearch[T](params:Seq[String],
               mxs:MatrixStore,
               vaultId:String,
               includeFields:Array[String],
               sinkFactory: GraphStageWithMaterializedValue[SinkShape[ObjectMatrixEntry], Future[T]],
               killSwitch: SharedKillSwitch)
              (implicit terminal:Terminal, lineReader:LineReader, actorSystem: ActorSystem, mat:Materializer) = {
    val baseQueryString = s"${params(1)}"
    val queryStringWithIncludes = if(includeFields.isEmpty) {
      baseQueryString
    } else {
      baseQueryString + s"\nkeywords:__mxs_id,${includeFields.mkString(",")}"
    }

    val terms = Array(SearchTerm.createSimpleTerm(Constants.CONTENT, queryStringWithIncludes))

    val graph = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val ksFlow = builder.add(killSwitch.flow[ObjectMatrixEntry])
      val src = builder.add(new OMFastSearchSource(mxs, vaultId, terms, includeFields, contentSearchBareTerm = true))

      src ~> ksFlow ~> sink
      ClosedShape
    }

    RunnableGraph.fromGraph(graph).run()
  }

  def maybeUserFriendlySize(maybeSize:Option[Long]) = maybeSize.map(userFriendlySize(_)).getOrElse("(no size)")

  def userFriendlySize(rawSizeInBytes:Long, iteration:Int=0):String = {
    val suffixes = Seq("bytes","KiB", "MiB", "GiB", "TiB", "PiB")

    if(iteration>=suffixes.size-1) {
      s"$rawSizeInBytes ${suffixes(iteration)}"
    } else if(rawSizeInBytes>1024) {
      userFriendlySize(rawSizeInBytes/1024, iteration+1)
    } else {
      s"$rawSizeInBytes ${suffixes(iteration)}"
    }
  }
}
