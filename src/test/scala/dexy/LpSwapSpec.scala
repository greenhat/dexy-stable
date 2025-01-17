package dexy

import dexy.DexySpec._
import kiosk.ergo.{DhtData, KioskBox, KioskLong}
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

// Test Lp contracts for following path
// Swap Erg and Dexy tokens using constant product formula after taking fee into account
class LpSwapSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {
  // ToDo: Add tests (and modify contracts) such that:
  //   1. Cannot add junk registers in the end
  //   2. Cannot add junk tokens in the end
  //   3. Changing NFT/tokensIds/addresses should fail for "sell Dexy" and "No Change" flows as well

  val dummyTokenId = "59e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0fad80a"

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L

  property("Swap (sell Ergs) should work") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeDenomLp - feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp >= BigInt(deltaReservesX) * (feeNumLp - feeDenomLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      // all ok, swap should work
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Swap (sell Ergs) should fail if Lp address changed") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeDenomLp - feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp >= BigInt(deltaReservesX) * (feeNumLp - feeDenomLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        changeAddress, // <--------------- this value is changed
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should fail if LpSwap address changed") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeDenomLp - feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp >= BigInt(deltaReservesX) * (feeNumLp - feeDenomLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        changeAddress, // <--------------- this value is changed
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should fail if Lp NFT changed") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeDenomLp - feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp >= BigInt(deltaReservesX) * (feeNumLp - feeDenomLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array(
          (dummyTokenId, 1), // <--------------- this value is changed
          (lpToken, lpBalance),
          (dexyUSD, reservesYOut)
        )
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should fail if LpSwap NFT changed") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeDenomLp - feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp >= BigInt(deltaReservesX) * (feeNumLp - feeDenomLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array(
          (lpNFT, 1),
          (lpToken, lpBalance),
          (dexyUSD, reservesYOut)
        )
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array(
          (dummyTokenId, 1) // <--------------- this value is changed
        )
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should fail if Lp token changed") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeDenomLp - feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp >= BigInt(deltaReservesX) * (feeNumLp - feeDenomLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, lpBalance))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array(
          (lpNFT, 1),
          (dummyTokenId, lpBalance), // <--------------- this value is changed
          (dexyUSD, reservesYOut)
        )
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should fail if Dexy token changed") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeDenomLp - feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp >= BigInt(deltaReservesX) * (feeNumLp - feeDenomLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, reservesYOut))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array(
          (lpNFT, 1),
          (lpToken, lpBalance),
          (dummyTokenId, reservesYOut) // <--------------- this value is changed
        )
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should not work if more Dexy taken") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeDenomLp - feeNumLp) / feeDenomLp).toLong + 1 // taken 1 more dexy token than allowed
    assert(buyY == 998)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp < BigInt(deltaReservesX) * (feeNumLp - feeDenomLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Swap (sell Dexy) should work") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesXIn.toDouble / reservesYIn
    val sellY = 1000L
    val buyX = (sellY * rate * (feeDenomLp - feeNumLp) / feeDenomLp).toLong
    assert(buyX == 9970000)

    val reservesXOut = reservesXIn - buyX
    val reservesYOut = reservesYIn + sellY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesX) * reservesYIn * feeDenomLp >= BigInt(deltaReservesY) * (feeNumLp - feeDenomLp) * reservesXIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, sellY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs - deltaReservesX,
        registers = Array(),
        tokens = Array()
      )

      // all ok, swap should work
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Swap (sell Dexy) should not work if more Ergs taken") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesXIn.toDouble / reservesYIn
    val sellY = 1000L
    val buyX = (sellY * rate * (feeDenomLp - feeNumLp) / feeDenomLp).toLong + 1 // take one NanoErg extra
    assert(buyX == 9970001)

    val reservesXOut = reservesXIn - buyX
    val reservesYOut = reservesYIn + sellY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesX) * reservesYIn * feeDenomLp < BigInt(deltaReservesY) * (feeNumLp - feeDenomLp) * reservesXIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, sellY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs - deltaReservesX,
        registers = Array(),
        tokens = Array()
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("No change should work") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val reservesXOut = reservesXIn
    val reservesYOut = reservesYIn

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      // all ok, swap should work
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

}
