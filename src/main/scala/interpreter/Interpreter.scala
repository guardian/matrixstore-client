package interpreter

import akka.actor.ActorSystem
import akka.stream.Materializer
import commands._
import org.jline.reader.{LineReader, ParsedLine}
import org.jline.terminal.Terminal

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class Interpreter(implicit val actorSystem: ActorSystem, mat:Materializer) {
  val knownCommandsTree:Seq[Token] = Seq(
    LeafToken("connect", new commands.Connect()),
    LeafToken("disconnect", new Disconnect),
    LeafToken("dis", new Disconnect),
    LeafToken("stacktrace", new Stacktrace),
    LeafToken("help", new Help),
    LeafToken("search", new Search),
    LeafToken("lookup", new LookupFilename),
    LeafToken("delete", new Delete),
    LeafToken("md5", new MD5),
    BranchToken("set", Seq(
      LeafToken("timeout", new SetAsyncTimeout),
      LeafToken("pagesize", new SetPageSize),
    )),
    BranchToken("show", Seq(
      LeafToken("headers", new ShowHeaders)
    ))
  )

  //initialise the session to empty. this var gets updated on every command that updates the session
  var session = Session.empty

  /**
   * called when the user presses ENTER on a line and we need to interpret it.
   * this takes care of routing the command to an appropriate handler and displaying error messages as appropriate
   * @param parsedLine
   * @param terminal
   * @param lineReader
   */
  def handle(parsedLine: ParsedLine)(implicit terminal:Terminal, lineReader: LineReader) = {
    val tokens = parsedLine.words().asScala.toSeq

    def findAndRun(current:String, remainder:Seq[String], toSearch:Seq[Token]):Try[Session] = toSearch.find(_.name == current) match {
      case Some(BranchToken(_, children))=>
        findAndRun(remainder.head, remainder.tail, children)
      case Some(LeafToken(_, cmd))=>
        cmd.run(tokens, session)
      case _=>
        terminal.writer().println("Syntax error. Try 'help' for accessing basic online help")
        Success(session)
    }

    findAndRun(tokens.head, tokens.tail, knownCommandsTree) match {
      case Success(updatedSession)=>
        session = updatedSession
      case Failure(err)=>
        session = session.copy(lastException = Some(err))
        terminal.writer().println(s"That command failed: ${err.getMessage}. Run 'stacktrace' to see a stacktrace of where")
    }
  }
}