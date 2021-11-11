package commands
import akka.Done
import akka.actor.ActorSystem
import akka.stream.stage.{AbstractInHandler, GraphStageLogic, GraphStageWithMaterializedValue}
import akka.stream.{Attributes, Inlet, KillSwitch, KillSwitches, Materializer, SinkShape}
import com.om.mxs.client.japi.Vault
import interpreter.Session
import models.ObjectMatrixEntry
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.collection.mutable
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success, Try}

class SearchDel extends BaseCommand {
  import SearchFunctions._

  val baseIncludeFields = Array(
    "MXFS_PATH",
    "MXFS_FILENAME",
    "__mxs__length",
    "MXFS_INTRASH"
  )

  class ResultListSink(killSwitch:KillSwitch)(implicit terminal:Terminal, lineReader: LineReader)
    extends GraphStageWithMaterializedValue[SinkShape[ObjectMatrixEntry], Future[(Seq[String], Long)]] {
    private val in:Inlet[ObjectMatrixEntry] = Inlet.create("PrintResultSink.in")

    override def shape: SinkShape[ObjectMatrixEntry] = SinkShape.of(in)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[(Seq[String], Long)]) = {
      val completionPromise = Promise[(Seq[String], Long)]()

      val logic = new GraphStageLogic(shape) {
        private var oidList = mutable.Seq[String]()
        private var totalSize:Long = 0
        private var ctr = 0

        setHandler(in, new AbstractInHandler {
          override def onPush(): Unit = {
            val entry = grab(in)
            terminal.writer().println(s"${entry.oid}\t${maybeUserFriendlySize(entry.getFileSize)}\t${entry.maybeGetPath().getOrElse("[no path]")}")
            oidList = oidList.appended(entry.oid)
            totalSize += entry.getFileSize.getOrElse(0L)
            ctr +=1
            if(ctr>10) {
              terminal.writer().flush()
              ctr=0
            }
            pull(in)
          }

          override def onUpstreamFinish(): Unit = {
            completionPromise.success((oidList.toSeq, totalSize))  //convert to an immutable seq when returning
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

  def tryToDelete(vault:Vault, oid:String)(implicit terminal: Terminal):Try[Unit] = Try {
    val obj = vault.getObject(oid)
    obj.delete()
  } match {
    case s@Success(_)=>
      terminal.writer().println(s"Deleted $oid")
      s
    case e@Failure(err)=>
      terminal.writer().println(s"$oid failed deletion: ${err.getMessage}")
      e
  }

  override def run(params: Seq[String], session: Session)
                  (implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat: Materializer): Try[Session] = {
    var ctr:Int=0
    (session.activeConnection, session.activeVaultId) match {
      case (Some(mxs), Some(vaultId))=>
        Try {
          val ks = KillSwitches.shared("user-quit")
          val sinkFactory = new ResultListSink(ks)
          terminal.writer().println(s"The following files will be deleted:\n")
          terminal.writer().flush()
          val result = Await.result(
            doSearch(params, mxs, vaultId, baseIncludeFields ++ session.fields, sinkFactory, ks),
            session.asyncTimeout
          )
          val oidsToDelete = result._1
          val totalSizeToDelete = result._2
          terminal.writer().println(s"\n\n\n${oidsToDelete.length} files totalling ${userFriendlySize(totalSizeToDelete)} will be deleted. Are you sure you want to continue? (y/n)")
          terminal.writer().flush()
          val promptResult = terminal.reader().read(1800L)
          promptResult match {
            case -1=>
              terminal.writer().println("Got EOF while waiting for user response")
              terminal.writer().flush()
            case -2=>
              terminal.writer().println("Error while waiting for user response")
              terminal.writer().flush()
            case _=>
              val char = promptResult.toChar
              if(char.toLower=='y') {
                terminal.writer().println(s"Deleting ${oidsToDelete.length} items...")
                withVault(mxs, vaultId) { vault =>
                  val results = oidsToDelete.map(oid=>{
                    val result = tryToDelete(vault, oid)
                    ctr+=1
                    if(ctr>50) terminal.writer().flush()
                    result
                  })
                  terminal.writer().flush()
                  val successes = results.count(_.isSuccess)
                  val failures = results.count(_.isFailure)
                  terminal.writer().println(s"Deletion runcompleted. $successes files deleted successfully and $failures failed.")
                  Success( () )
                }
              } else {
                terminal.writer().println("Not deleting anything then")
              }
              terminal.writer().flush()
          }
        }.map(_=>session)
      case _=>
        terminal.writer().println("You must connect to a vault before you can search")
        terminal.writer().flush()
        Success(session)
    }
  }
}
