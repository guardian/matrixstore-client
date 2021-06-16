import akka.actor.ActorSystem
import akka.stream.Materializer
import interpreter.Interpreter
import org.jline.reader.{Candidate, Completer, LineReader, LineReaderBuilder, MaskingCallback, ParsedLine}
import org.jline.terminal.{Terminal, TerminalBuilder}
import org.jline.reader.impl.DefaultParser

import java.util.function.Consumer
import scala.concurrent.Await
import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main {
  lazy implicit val actorSystem:ActorSystem = ActorSystem("matrixstore-client")
  lazy implicit val mat:Materializer = Materializer(actorSystem)

  val defaultPrompt = "> "

  private val interpreter = new Interpreter

  val maybeVersionInfo = VersionInfo.loadFromResource

  def terminalInputLoop(callbacks:Seq[Consumer[LineReader]]=Seq())(implicit terminal: Terminal):Unit = {
    val parser = new DefaultParser()

    implicit val lineReader = LineReaderBuilder.builder()
      .terminal(terminal)
      //.completer(completer)
      .parser(parser)
      .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%M%P > ")
      .variable(LineReader.INDENTATION, 2)
      .option(LineReader.Option.INSERT_BRACKET, true)
      .build()


    callbacks.foreach(_.accept(lineReader))
    maybeVersionInfo match {
      case Success(versionInfo)=>
        terminal.writer().println(s"Welcome to matrixstore-client version ${versionInfo.buildNumber} from sha ${versionInfo.buildSha}")
        terminal.writer().println("Use `help` to see a list of available commands")
        terminal.writer().flush()
      case Failure(err)=>
        terminal.writer().println(s"Warning: Could not load version information - ${err.getMessage}")
        terminal.writer().println("Welcome to matrixstore-client")
        terminal.writer().println("Use `help` to see a list of available commands")
        terminal.writer().flush()
    }

    while(true) {
      val prompt = interpreter.session.activeVaultId match {
        case None=>
          defaultPrompt
        case Some(vaultId)=>
          s"$vaultId$defaultPrompt"
      }

      //we must read in the line here, this stores it in the LineReader's state so we can ignore the return value
      //and rely on the LineReader to send it directly to the parser
      lineReader.readLine(prompt, "", null.asInstanceOf[MaskingCallback], null)
      val request = lineReader.getParsedLine
      val tokens = request.words().asScala
      if(tokens.headOption.contains("exit")) {
        terminal.writer().println("Goodbye!")
        terminal.writer().flush()
        interpreter.session.cleanup()
        return
      }

      interpreter.handle(lineReader.getParsedLine)
    }
  }

  def main(args: Array[String]):Unit = {
    //get system default terminal
    implicit val terminal:Terminal = TerminalBuilder.terminal()

    terminalInputLoop()
    Await.result(actorSystem.terminate(), 1.minute)
  }
}
