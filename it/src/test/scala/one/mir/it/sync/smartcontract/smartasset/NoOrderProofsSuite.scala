package one.mir.it.sync.smartcontract.smartasset

import one.mir.account.AddressScheme
import one.mir.it.api.SyncHttpApi._
import one.mir.it.sync.{someAssetAmount, _}
import one.mir.it.transactions.BaseTransactionSuite
import one.mir.state.ByteStr
import one.mir.transaction.Proofs
import one.mir.transaction.assets.BurnTransactionV2
import one.mir.transaction.smart.script.ScriptCompiler
import one.mir.transaction.transfer.TransferTransactionV2
import scala.concurrent.duration._

class NoOrderProofsSuite extends BaseTransactionSuite {
  test("try to use Order in asset scripts") {
    try {
      sender.issue(
        firstAddress,
        "assetWProofs",
        "Test coin for assetWProofs test",
        someAssetAmount,
        0,
        reissuable = true,
        issueFee,
        2,
        script = Some(
          ScriptCompiler(
            s"""
              |match tx {
              |  case o: Order => true
              |  case _ => false
              |}""".stripMargin,
            isAssetScript = true
          ).explicitGet()._1.bytes.value.base64)
      )

      fail("ScriptCompiler didn't throw expected error")
    } catch {
      case ex: java.lang.Exception => ex.getMessage should include("Compilation failed: Matching not exhaustive")
      case _: Throwable            => fail("ScriptCompiler works incorrect for orders with smart assets")
    }
  }

  test("try to use proofs in assets script") {
    val errProofMsg = "Reason: Script doesn't exist and proof doesn't validate as signature"
    val assetWProofs = sender
      .issue(
        firstAddress,
        "assetWProofs",
        "Test coin for assetWProofs test",
        someAssetAmount,
        0,
        reissuable = true,
        issueFee,
        2,
        script = Some(
          ScriptCompiler(
            s"""
                let proof = base58'assetWProofs'
                match tx {
                  case tx: SetAssetScriptTransaction | TransferTransaction | ReissueTransaction | BurnTransaction => tx.proofs[0] == proof
                  case _ => false
                }""".stripMargin,
            false
          ).explicitGet()._1.bytes.value.base64),
        waitForTx = true
      )
      .id

    val incorrectTrTx = TransferTransactionV2
      .create(
        2,
        Some(ByteStr.decodeBase58(assetWProofs).get),
        pkByAddress(firstAddress),
        pkByAddress(thirdAddress),
        1,
        System.currentTimeMillis + 10.minutes.toMillis,
        None,
        smartMinFee,
        Array.emptyByteArray,
        Proofs(Seq(ByteStr("assetWProofs".getBytes())))
      )
      .right
      .get

    assertBadRequestAndMessage(
      sender.signedBroadcast(incorrectTrTx.json()),
      errProofMsg
    )

    val incorrectBrTx = BurnTransactionV2
      .create(
        2,
        AddressScheme.current.chainId,
        pkByAddress(firstAddress),
        ByteStr.decodeBase58(assetWProofs).get,
        1,
        smartMinFee,
        System.currentTimeMillis + 10.minutes.toMillis,
        Proofs(Seq(ByteStr("assetWProofs".getBytes())))
      )
      .right
      .get

    assertBadRequestAndMessage(
      sender.signedBroadcast(incorrectBrTx.json()),
      errProofMsg
    )
  }

}
