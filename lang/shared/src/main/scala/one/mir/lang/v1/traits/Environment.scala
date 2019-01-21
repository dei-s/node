package one.mir.lang.v1.traits

import one.mir.lang.v1.traits.domain._
import shapeless._

trait Environment {
  def height: Long
  def networkByte: Byte
  def inputEntity: Tx :+: Ord :+: CNil
  def transactionById(id: Array[Byte]): Option[Tx]
  def transactionHeightById(id: Array[Byte]): Option[Long]
  def data(addressOrAlias: Recipient, key: String, dataType: DataType): Option[Any]
  def resolveAlias(name: String): Either[String, Recipient.Address]
  def accountBalanceOf(addressOrAlias: Recipient, assetId: Option[Array[Byte]]): Either[String, Long]
}
