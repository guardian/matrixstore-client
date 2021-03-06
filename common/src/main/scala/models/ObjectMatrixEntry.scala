package models

import java.time.{Instant, ZoneId, ZonedDateTime}
import akka.stream.Materializer
import com.om.mxs.client.japi.{MXFSFileAttributes, Vault}
import helpers.MetadataHelper

import scala.concurrent.ExecutionContext
import scala.util.Try

case class ObjectMatrixEntry(oid:String, attributes:Option[MxsMetadata], fileAttribues:Option[FileAttributes]) {
  def getMxsObject(implicit vault:Vault) = vault.getObject(oid)

  def getMetadata(implicit vault:Vault, mat:Materializer, ec:ExecutionContext) = MetadataHelper
    .getAttributeMetadata(getMxsObject)
    .map(mxsMeta=>
      this.copy(oid, Some(mxsMeta), Some(FileAttributes(MetadataHelper.getMxfsMetadata(getMxsObject))))
    )

  def getMetadataSync(implicit vault:Vault) = this.copy(
    attributes=Some(MetadataHelper.getAttributeMetadataSync(getMxsObject)),
    fileAttribues=Some(FileAttributes(MetadataHelper.getMxfsMetadata(getMxsObject)))
  )

  def hasMetadata:Boolean = attributes.isDefined && fileAttribues.isDefined

  def stringAttribute(key:String) = attributes.flatMap(_.stringValues.get(key))
  def intAttribute(key:String) = attributes.flatMap(_.intValues.get(key)) match {
    case ok@Some(_)=>ok
    case None=>
      for {
        attrs <- attributes
        stringValue <- attrs.stringValues.get(key)
        intValue <- Try { stringValue.toInt }.toOption
      } yield intValue
  }

  def boolAttribute(key:String) = attributes.flatMap(_.boolValues.get(key)) match {
    case ok@Some(_)=>ok
    case None=>
      for {
        attrs <- attributes
        stringValue <- attrs.stringValues.get(key)
        boolValue <- Try { stringValue.toBoolean }.toOption
      } yield boolValue
  }

  def longAttribute(key:String) = attributes.flatMap(_.longValues.get(key)) match {
    case ok@Some(_)=>ok
    case None=>
      for {
        attrs <- attributes
        stringValue <- attrs.stringValues.get(key)
        longValue <- Try { stringValue.toLong }.toOption
      } yield longValue
  }

  def timeAttribute(key:String, zoneId:ZoneId=ZoneId.systemDefault()) = longAttribute(key)
    .map(v=>ZonedDateTime.ofInstant(Instant.ofEpochMilli(v),zoneId))

  def maybeGetPath() = stringAttribute("MXFS_PATH")
  def maybeGetFilename() = stringAttribute("MXFS_FILENAME")

  def pathOrFilename = maybeGetPath() match {
    case Some(p)=>Some(p)
    case None=>maybeGetFilename()
  }

  def getFileSize:Option[Long] = longAttribute("__mxs__length") match {
    case longResult@Some(_)=>longResult
    case None=>
      Try {
        stringAttribute("__mxs__length").map(_.toLong)
      }.toOption.flatten
  }
}