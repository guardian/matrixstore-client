package commands
import akka.Done
import akka.actor.ActorSystem
import akka.stream.{Attributes, ClosedShape, Inlet, KillSwitch, KillSwitches, Materializer, SinkShape}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic, GraphStageWithMaterializedValue}
import com.om.mxs.client.japi.{Constants, MatrixStore, SearchTerm}
import interpreter.Session
import models.ObjectMatrixEntry
import org.jline.reader.LineReader
import org.jline.terminal.Terminal
import streamcomponents.OMFastSearchSource

import java.nio.CharBuffer
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Success, Try}

class Search extends BaseCommand {
  class PrintResultSink(includeFields:Array[String], itemsPerPage:Int, showHeaders:Boolean, killSwitch:KillSwitch)(implicit terminal:Terminal, lineReader: LineReader)
    extends GraphStageWithMaterializedValue[SinkShape[ObjectMatrixEntry], Future[Done]] {
    private val in:Inlet[ObjectMatrixEntry] = Inlet.create("PrintResultSink.in")

    private val fieldsToPrint = includeFields.filter(n=>{ //we print these anyway
      n!="MXFS_FILENAME" && n!="MXFS_PATH" && n!= "DPSP_SIZE" && n!="__mxs__length"
    })
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
              result.oid,
              result.getFileSize.map(_.toString).getOrElse("[no size]"),
              result.maybeGetPath().getOrElse("[no path]"),
            ) ++ fieldsToPrint.map(f => result.stringAttribute(f).getOrElse("-"))

            terminal.writer().println(fields.mkString("\t"))
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

  def doSearch(params:Seq[String], mxs:MatrixStore, vaultId:String, itemsPerPage:Int, showHeaders:Boolean, includeFields:Array[String])
              (implicit terminal:Terminal, lineReader:LineReader, actorSystem: ActorSystem, mat:Materializer) = {
    val baseQueryString = s"${params(1)}"
    val queryStringWithIncludes = if(includeFields.isEmpty) {
      baseQueryString
    } else {
      baseQueryString + s"\nkeywords:__mxs_id,${includeFields.mkString(",")}"
    }

    val terms = Array(SearchTerm.createSimpleTerm(Constants.CONTENT, queryStringWithIncludes))

    val ks = KillSwitches.shared("user-quit")

    val graph = GraphDSL.create(new PrintResultSink(includeFields, itemsPerPage, showHeaders, ks)) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val ksFlow = builder.add(ks.flow[ObjectMatrixEntry])
      val src = builder.add(new OMFastSearchSource(mxs, vaultId, terms, includeFields, contentSearchBareTerm = true))

      src ~> ksFlow ~> sink
      ClosedShape
    }

    RunnableGraph.fromGraph(graph).run()
  }

  override def run(params: Seq[String], session: Session)
                  (implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat: Materializer): Try[Session] = {
    (session.activeConnection, session.activeVaultId) match {
      case (Some(mxs), Some(vaultId))=>
        val baseIncludeFields = Array(
          "MXFS_PATH",
          "MXFS_FILENAME",
          "DPSP_SIZE",
          "__mxs__length",
          "__mxs__location"
        )
        Try {
          Await.result(
            doSearch(params, mxs, vaultId, session.itemsPerPage, session.showHeaders, baseIncludeFields),
            session.asyncTimeout
          )
        }.map(_=>session)
      case _=>
        terminal.writer().println("You must connect to a vault before you can search")
        terminal.writer().flush()
        Success(session)
    }
  }
}
