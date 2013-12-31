package com.kactech.atomictc;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.DumpedPrivateKey;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.script.ScriptBuilder;
import com.kactech.atomictc.TradeState.Bytes;
import com.kactech.atomictc.TradeState.Unspent;

public class AtomicTests {
	@Test
	public void fiveStepsTradeTest() throws Exception {

		/***** INIT STAGE ****/

		TradeState tsA = new TradeState(), tsB;
		tsA.send = "XPM";
		tsA.receive = "BTC";

		NetworkDetails netA = NetworkDetails.get(tsA.send), netB = NetworkDetails.get(tsA.receive);
		RpcClient rpcA = new RpcClient(netA);

		long availableFundsA = 0;
		{
			// get and convert Rpc.Unspent to Unspent
			// sum available funds
			tsA.unspent = new ArrayList<TradeState.Unspent>();
			for (Rpc.Unspent ru : rpcA.listUnspent()) {
				tsA.unspent.add(new Unspent(new Bytes(ru.txid), ru.vout, ru.address, new Bytes(ru.scriptPubKey),
						ru.amount));
				availableFundsA += Math.round(Math.floor(ru.amount * netA.base));
			}

			// get our pubKey dumping priv key from first unspent address
			tsA.pubKey = new Bytes(new DumpedPrivateKey(netA.params,
					rpcA.dumpPrivKey(tsA.unspent.get(0).address)).getKey().getPubKey());
		}

		// create Bob's wallet
		Wallet walletB = new Wallet(netB.params);
		walletB.addKey(new ECKey());
		// Bob's pubKey 
		byte[] pubKeyB = walletB.getKeys().get(0).getPubKey();

		// setup trading engines for both sides
		TradeEngine eA = new TradeEngine(new MapGsonStateStore(), new RpcKeyStore(rpcA), new ScriptMessageSerializer());
		TradeEngine eB = new TradeEngine(new MapGsonStateStore(), new WalletKeyStore(walletB),
				new ScriptMessageSerializer());

		// common tradeId which is Alice bail in tx hash
		byte[] tradeId;
		// we'll pass messages between both sides in this variable
		TradeMessage msg;

		/***** TRADE STAGE *****/

		// #1 Alice initializes trade
		{
			// we're setting up trade
			tsA.his.pubKey = new Bytes(pubKeyB);
			// and generates x
			{
				byte[] x = new byte[32];
				new Random().nextBytes(x);
				tsA.x = new Bytes(x);
				tsA.hx = new Bytes(Utils.sha256hash160(x));
			}
			// amount of XPM to exchange
			tsA.amount = BigInteger.valueOf(availableFundsA * 2 / 3);
			// 48h lock time
			tsA.lockTime = System.currentTimeMillis() / 1000l + 60 * 60 * 48;

			// create bail in and refund transactions
			eA.createBailIn(tsA);
			// both are saved in tsA
			// save updated state in the store (only message handler routine makes automatically)
			eA.store.saveTradeState(tsA);
			// set tradeId, we'll use it between sites
			tradeId = tsA.id.b;

			// create message for Bob which contains our keys, bailInHash, amounts
			msg = eA.makeInit(tsA);
		}

		// #2 Bob handles Init message
		{
			eB.handle(msg);
			// now Bob knows that Alice want trade, what is exchanges, how much she offer
			// Bob's engine created new TradeState at his site, we can fetch it from the store
			tsB = eB.store.getTradeState(tradeId);

			// Bob agrees to trade and signs Alice's refund
			eB.signHisRefund(tsB);
			// to make things simpler Bob will create his bail in too
			{
				// we'll fabricate unspent transactions (wallet is fake)
				ECKey k = walletB.getKeys().get(0);
				Address addr = k.toAddress(NetworkDetails.get(tsB.send).params);
				byte[] scriptPubKey = ScriptBuilder.createOutputScript(addr).getProgram();
				Random r = new Random();
				List<Unspent> l = new ArrayList<TradeState.Unspent>();
				double sum = 0;
				while (sum < 5) {
					byte[] txid = new byte[32];
					r.nextBytes(txid);
					double amount = r
							.nextDouble() * 5;
					l.add(new Unspent(new Bytes(txid), r.nextInt(5), addr.toString(), new Bytes(scriptPubKey),
							amount));
					sum += amount;
				}
				tsB.unspent = l;

				// amount we want to send to Alice in this trade (2/3 of input amount)
				tsB.amount = BigInteger.valueOf(Math.round(Math.floor(sum * netB.base * 2 / 3)));
			}
			// 24h lock time
			tsB.lockTime = System.currentTimeMillis() / 1000l + 60 * 60 * 24;

			// now the engine can create bail in refund for Bob
			eB.createBailIn(tsB);
			// save updated state
			eB.store.saveTradeState(tsB);

			// create Accept message (signature of Alice's refund and our refund)
			msg = eB.makeAccept(tsB);
		}

		// #3 Alice receives Accept
		{
			eA.handle(msg);
			// engine verified message and updated related TradeState

			// fetch state from our store
			tsA = eA.store.getTradeState(tradeId);
			// amounts looks fine so Alice signs Bob's refund
			eA.signHisRefund(tsA);
			eA.store.saveTradeState(tsA);

			// create a message with Bob's refund signature and the copy of our bail in tx  
			msg = eA.makeFirstBail(tsA);
		}

		// #4 Bob receives signature and Alice's bail in tx
		{
			eB.handle(msg);
			// we can verify bail in, broadcast it, set up listener, etc.

			// get updated state
			tsB = eB.store.getTradeState(tradeId);
			// reply with our bail in
			msg = eB.makeBail(tsB);
		}

		// #5 Alice receives Bob's bail in
		{
			eA.handle(msg);
		}
		/**
		 * Now everything is set up for revealing x and finalizing the trade. If
		 * Alice is good trader and want to make more trades with Bob in the
		 * future, she should send him x to let him get his coins w/o listening
		 * to the blockchain.
		 */

	}
}
