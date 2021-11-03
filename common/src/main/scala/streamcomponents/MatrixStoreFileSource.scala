package streamcomponents

import java.io.InputStream
import java.nio.ByteBuffer

import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.util.ByteString
import com.om.mxs.client.japi.{AccessOption, MatrixStore, MxsObject, SeekableByteChannel, UserInfo, Vault}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._


class MatrixStoreFileSource(vault:Vault, sourceId:String, bufferSize:Int=2*1024*1024) extends GraphStage[SourceShape[ByteString]]{
  private final val out:Outlet[ByteString] = Outlet.create("MatrixStoreFileSource.out")

  override def shape: SourceShape[ByteString] = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    private var stream:InputStream = _
    private var mxsFile:MxsObject = _

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        val bytes = new Array[Byte](bufferSize)
        val bytesRead = stream.read(bytes,0,bufferSize)

        if(bytesRead == -1){
          logger.info(s"MXS file read on ${mxsFile.getId} completed")
          complete(out)
        } else {
          logger.debug(s"Pushing $bytesRead bytes into the stream...")

          //ensure that final chunk is written with correct size
          val finalBytes = if(bytesRead==bufferSize){
            bytes
          } else {
            val nb = new Array[Byte](bytesRead)
            for(i<- 0 until bytesRead) nb.update(i, bytes(i))
            nb
          }
          push(out,ByteString(finalBytes))
        }
      }
    })

    override def preStart(): Unit = {
      mxsFile = vault.getObject(sourceId)
      stream = mxsFile.newInputStream()

      logger.debug(s"Stream is $stream")
    }

    override def postStop(): Unit = {
      if(stream!=null) stream.close()
    }
  }
}
