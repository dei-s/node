package one.mir.it.sync.smartcontract

import one.mir.crypto
import one.mir.it.api.SyncHttpApi._
import one.mir.it.sync.{minFee, setScriptFee, transferAmount}
import one.mir.it.transactions.BaseTransactionSuite
import one.mir.it.util._
import one.mir.state._
import one.mir.transaction.Proofs
import one.mir.transaction.smart.SetScriptTransaction
import one.mir.transaction.smart.script.ScriptCompiler
import one.mir.transaction.transfer._
import org.scalatest.CancelAfterFailure
import play.api.libs.json.Json

class SetScriptTransactionSuite extends BaseTransactionSuite with CancelAfterFailure {
  private val fourthAddress: String = sender.createAddress()

  private val acc0 = pkByAddress(firstAddress)
  private val acc1 = pkByAddress(secondAddress)
  private val acc2 = pkByAddress(thirdAddress)
  private val acc3 = pkByAddress(fourthAddress)

  test("setup acc0 with 1 mir") {
    val tx =
      TransferTransactionV2
        .selfSigned(
          version = 2,
          assetId = None,
          sender = sender.privateKey,
          recipient = acc0,
          amount = 3 * transferAmount + 3 * (0.00001.mir + 0.00002.mir), // Script fee
          timestamp = System.currentTimeMillis(),
          feeAssetId = None,
          feeAmount = minFee,
          attachment = Array.emptyByteArray
        )
        .explicitGet()

    val transferId = sender
      .signedBroadcast(tx.json())
      .id
    nodes.waitForHeightAriseAndTxPresent(transferId)
  }

  test("set acc0 as 2of2 multisig") {
    val scriptText = s"""
        match tx {
          case t: Transaction => {
            let A = base58'${ByteStr(acc1.publicKey)}'
            let B = base58'${ByteStr(acc2.publicKey)}'
            let AC = sigVerify(tx.bodyBytes,tx.proofs[0],A)
            let BC = sigVerify(tx.bodyBytes,tx.proofs[1],B)
            AC && BC
          }
          case _ => false
        }
      """.stripMargin

    val script = ScriptCompiler(scriptText, isAssetScript = false).explicitGet()._1
    val setScriptTransaction = SetScriptTransaction
      .selfSigned(SetScriptTransaction.supportedVersions.head, acc0, Some(script), setScriptFee, System.currentTimeMillis())
      .explicitGet()

    val setScriptId = sender
      .signedBroadcast(setScriptTransaction.json())
      .id

    nodes.waitForHeightAriseAndTxPresent(setScriptId)

    val acc0ScriptInfo = sender.addressScriptInfo(acc0.address)

    acc0ScriptInfo.script.isEmpty shouldBe false
    acc0ScriptInfo.scriptText.isEmpty shouldBe false
    acc0ScriptInfo.script.get.startsWith("base64:") shouldBe true

    val json = Json.parse(sender.get(s"/transactions/info/$setScriptId").getResponseBody)
    (json \ "script").as[String].startsWith("base64:") shouldBe true
  }

  test("can't send from acc0 using old pk") {
    val tx =
      TransferTransactionV2
        .selfSigned(
          version = 2,
          assetId = None,
          sender = acc0,
          recipient = acc3,
          amount = transferAmount,
          timestamp = System.currentTimeMillis(),
          feeAssetId = None,
          feeAmount = minFee + 0.00001.mir + 0.00002.mir,
          attachment = Array.emptyByteArray
        )
        .explicitGet()
    assertBadRequest(sender.signedBroadcast(tx.json()))
  }

  test("can send from acc0 using multisig of acc1 and acc2") {
    val unsigned =
      TransferTransactionV2
        .create(
          version = 2,
          assetId = None,
          sender = acc0,
          recipient = acc3,
          amount = transferAmount,
          timestamp = System.currentTimeMillis(),
          feeAssetId = None,
          feeAmount = minFee + 0.004.mir,
          attachment = Array.emptyByteArray,
          proofs = Proofs.empty
        )
        .explicitGet()
    val sig1 = ByteStr(crypto.sign(acc1, unsigned.bodyBytes()))
    val sig2 = ByteStr(crypto.sign(acc2, unsigned.bodyBytes()))

    val signed = unsigned.copy(proofs = Proofs(Seq(sig1, sig2)))

    val versionedTransferId =
      sender.signedBroadcast(signed.json()).id

    nodes.waitForHeightAriseAndTxPresent(versionedTransferId)
  }

  test("can clear script at acc0") {
    val unsigned = SetScriptTransaction
      .create(
        version = SetScriptTransaction.supportedVersions.head,
        sender = acc0,
        script = None,
        fee = setScriptFee + 0.004.mir,
        timestamp = System.currentTimeMillis(),
        proofs = Proofs.empty
      )
      .explicitGet()
    val sig1 = ByteStr(crypto.sign(acc1, unsigned.bodyBytes()))
    val sig2 = ByteStr(crypto.sign(acc2, unsigned.bodyBytes()))

    val signed = unsigned.copy(proofs = Proofs(Seq(sig1, sig2)))
    val clearScriptId = sender
      .signedBroadcast(signed.json())
      .id

    nodes.waitForHeightAriseAndTxPresent(clearScriptId)
  }

  test("can send using old pk of acc0") {
    val tx =
      TransferTransactionV2
        .selfSigned(
          version = 2,
          assetId = None,
          sender = acc0,
          recipient = acc3,
          amount = transferAmount,
          timestamp = System.currentTimeMillis(),
          feeAssetId = None,
          feeAmount = minFee + 0.004.mir,
          attachment = Array.emptyByteArray
        )
        .explicitGet()
    val txId = sender.signedBroadcast(tx.json()).id
    nodes.waitForHeightAriseAndTxPresent(txId)
  }
}
