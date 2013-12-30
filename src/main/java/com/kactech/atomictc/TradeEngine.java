package com.kactech.atomictc;

import java.math.BigInteger;
import java.util.Arrays;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Base58;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Transaction.SigHash;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutPoint;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.crypto.TransactionSignature;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.bitcoin.script.ScriptOpCodes;
import com.kactech.atomictc.TradeState.Bytes;
import com.kactech.atomictc.TradeState.Unspent;

public class TradeEngine {

	public TradeEngine(TradeStateStore store, KeyStore ks, MessageSerializer ser) {
		super();
		this.store = store;
		this.ks = ks;
		this.ser = ser;

	}

	TradeStateStore store;
	KeyStore ks;
	MessageSerializer ser;

	public void createBailIn(TradeState ts) throws Exception {
		if (ts.sigRefund != null)
			throw new IllegalStateException("already done");

		NetworkDetails net = NetworkDetails.get(ts.send);
		if (ts.amount.compareTo(net.minTxOut) < 0)
			throw new IllegalStateException("amount below min");

		byte[][] requiredKeys = new byte[ts.unspent.size() + 1][];
		BigInteger sum = BigInteger.ZERO;
		for (int i = 0; i < ts.unspent.size(); i++) {
			Unspent ru = ts.unspent.get(i);
			sum = sum.add(BigInteger.valueOf(Math.round(Math.floor(ru.amount * net.base))));
			requiredKeys[i] = Base58.decode(ru.address);
			requiredKeys[i] = Arrays.copyOfRange(requiredKeys[i], 1, requiredKeys[i].length - 4);
		}
		requiredKeys[requiredKeys.length - 1] = Utils.sha256hash160(ts.pubKey.b);
		ECKey[] keys = ks.getKeys(requiredKeys);

		ts.fee = net.minTxFee
				.multiply(BigInteger.valueOf(1 + (145l + 35 + ts.unspent.size() * 150) / 1000));
		Transaction tx1;
		while (true) {
			ts.change = sum.subtract(ts.fee).subtract(ts.amount);
			if (ts.change.compareTo(BigInteger.ZERO) < 0)
				throw new IllegalStateException("insufficiend funds");
			if (ts.change.compareTo(net.minTxOut) < 0) {
				ts.fee = ts.fee.add(ts.change);
				ts.change = BigInteger.ZERO;
			}

			tx1 = new Transaction(net.params);
			for (Unspent ru : ts.unspent)
				tx1.addInput(new TransactionInput(net.params, tx1, new byte[] {}, new TransactionOutPoint(
						net.params,
						ru.vout,
						new Sha256Hash(ru.txid.b))));

			tx1.addOutput(new TransactionOutput(net.params, tx1, sum.subtract(ts.fee).subtract(ts.change),
					makeCrossScript(ts.pubKey.b, ts.his.pubKey.b, ts.hx.b)
							.getProgram()));

			if (ts.change.compareTo(BigInteger.ZERO) > 0)
				tx1.addOutput(new TransactionOutput(net.params, tx1, ts.change, new Address(net.params, Utils
						.sha256hash160(ts.pubKey.b))));

			for (int i = 0; i < ts.unspent.size(); i++) {
				Unspent ru = ts.unspent.get(i);
				byte[] pubkeyHash = Base58.decode(ru.address);
				pubkeyHash = Arrays.copyOfRange(pubkeyHash, 1, pubkeyHash.length - 4);
				ECKey key;
				key = keys[i];

				Script pubKeyScript = new Script(ru.scriptPubKey.b);
				TransactionSignature sigA2 = tx1.calculateSignature(i, key, pubKeyScript, SigHash.ALL, false);
				tx1.getInput(i)
						.setScriptSig(
								new ScriptBuilder().data(sigA2.encodeToBitcoin()).data(key.getPubKey()).build());
			}
			for (int i = 0; i < ts.unspent.size(); i++) {
				Unspent ru = ts.unspent.get(i);
				Script pubKeyScript = new Script(ru.scriptPubKey.b);
				tx1.getInput(i).getScriptSig().correctlySpends(tx1, i, pubKeyScript, false);

			}
			BigInteger calculatedFee = net.minTxFee
					.multiply(BigInteger.valueOf(1 + tx1.getMessageSize() / 1000));
			if (ts.fee.compareTo(calculatedFee) < 0)
				ts.fee = calculatedFee;
			else
				break;
		}

		ts.bailIn = new Bytes(tx1.bitcoinSerialize());

		Transaction ref = createRefundTx(net.params,
				tx1.getOutput(0).getValue().subtract(net.minTxFee).longValue(), ts.pubKey.b, ts.lockTime, tx1
						.getHash().getBytes());
		ts.refund = new Bytes(ref.unsafeBitcoinSerialize());
		TransactionSignature sigA2 = ref.calculateSignature(0, keys[keys.length - 1], tx1
				.getOutput(0).getScriptPubKey(), SigHash.ALL, false);
		ts.refundAmount = ref.getOutput(0).getValue().longValue();
		ts.sigRefund = new Bytes(sigA2.encodeToBitcoin());
		if (ts.x != null)
			ts.id = new Bytes(Utils.reverseBytes(Utils.doubleDigest(ts.bailIn.b)));
	}

	public void handle(TradeMessage tm) throws Exception {
		if (tm instanceof TradeMessage.Init)
			on((TradeMessage.Init) tm);
		else if (tm instanceof TradeMessage.Accept)
			on((TradeMessage.Accept) tm);
		else if (tm instanceof TradeMessage.FirstBail)
			on((TradeMessage.FirstBail) tm);
		else if (tm instanceof TradeMessage.Bail)
			on((TradeMessage.Bail) tm);
		else
			throw new Exception("unknown message " + tm.getClass());
	}

	public void on(TradeMessage.Init msg) throws Exception {
		byte[] id = msg.bailInHash;
		if (store.getTradeState(id) != null)
			throw new Exception("trade exists");
		ECKey key = ks.getKeys(Utils.sha256hash160(msg.hisPubKey))[0];
		if (key == null)
			throw new Exception("not my key");
		if (!ECKey.verify(Sha256Hash.create(msg.bytes).getBytes(), msg.signature, msg.pubKey))
			throw new Exception("invalid message signature");
		TradeState ts = new TradeState();
		ts.id = new Bytes(id);
		ts.pubKey = new Bytes(msg.hisPubKey);
		ts.his.pubKey = new Bytes(msg.pubKey);
		ts.his.lockTime = msg.lockTime;
		ts.his.amount = msg.amount;
		ts.his.refundAmount = msg.refundAmount;
		ts.hx = new Bytes(msg.hx);
		ts.send = msg.receive;
		ts.receive = msg.send;
		ts.his.bailInHash = new Bytes(id);
		store.saveTradeState(ts);
	}

	public void on(TradeMessage.Accept msg) throws Exception {
		TradeState ts = store.getTradeState(msg.id);
		if (ts == null)
			throw new Exception("trade not found");
		if (ts.his.sigRefund != null)
			throw new Exception("my refund already singed");
		if (!ECKey.verify(Sha256Hash.create(msg.bytes).getBytes(), msg.signature, ts.his.pubKey.b))
			throw new Exception("invalid message signature");

		verifyHisSigOnRefund(ts, msg.sigHisRefund);
		ts.his.sigRefund = new Bytes(msg.sigHisRefund);
		ts.his.lockTime = msg.lockTime;
		ts.his.amount = msg.amount;
		ts.his.refundAmount = msg.refundAmount;
		ts.his.bailInHash = new Bytes(msg.bailInHash);
		store.saveTradeState(ts);
	}

	public void on(TradeMessage.FirstBail msg) throws Exception {
		TradeState ts = store.getTradeState(msg.id);
		if (ts == null)
			throw new Exception("trade not found");
		if (ts.his.sigRefund != null)
			throw new Exception("my refund already singed");
		if (!ECKey.verify(Sha256Hash.create(msg.bytes).getBytes(), msg.signature, ts.his.pubKey.b))
			throw new Exception("invalid message signature");
		verifyHisSigOnRefund(ts, msg.sigHisRefund);

		//TODO check bail-in tx validity
		Transaction bailIn = new Transaction(NetworkDetails.get(ts.receive).params, msg.bailIn);
		if (!Arrays.equals(ts.id.b, bailIn.getHash().getBytes()))
			throw new Exception("bailIn hash not match");
		ts.his.bailIn = new Bytes(bailIn.unsafeBitcoinSerialize());
		ts.his.sigRefund = new Bytes(msg.sigHisRefund);
		store.saveTradeState(ts);
	}

	public void on(TradeMessage.Bail msg) throws Exception {
		TradeState ts = store.getTradeState(msg.id);
		if (ts == null)
			throw new Exception("trade not found");
		if (ts.his.bailIn != null)
			throw new Exception("already have his bail in");
		if (!ECKey.verify(Sha256Hash.create(msg.bytes).getBytes(), msg.signature, ts.his.pubKey.b))
			throw new Exception("invalid message signature");

		//TODO check bail-in tx validity
		Transaction bailIn = new Transaction(NetworkDetails.get(ts.receive).params, msg.bailIn);
		if (!Arrays.equals(ts.his.bailInHash.b, bailIn.getHash().getBytes()))
			throw new Exception("bailIn hash not match");
		ts.his.bailIn = new Bytes(bailIn.unsafeBitcoinSerialize());
		store.saveTradeState(ts);
	}

	public void signHisRefund(TradeState ts) throws Exception {
		if (ts.sigHisRefund != null)
			throw new IllegalStateException("already signed");
		NetworkDetails net = NetworkDetails.get(ts.receive);
		Transaction ref = createRefundTx(net.params, ts.his.refundAmount, ts.his.pubKey.b, ts.his.lockTime,
				ts.his.bailInHash.b);

		Script scrPubKey = makeCrossScript(ts.his.pubKey.b, ts.pubKey.b, ts.hx.b);
		TransactionSignature sig = ref.calculateSignature(0, ks.getKeys(Utils.sha256hash160(ts.pubKey.b))[0],
				scrPubKey, SigHash.ALL, false);
		ts.sigHisRefund = new Bytes(sig.encodeToBitcoin());
	}

	public TradeMessage.Init makeInit(TradeState ts) {
		return sig(ts.toInit(), ts.pubKey.b);
	}

	public TradeMessage.Accept makeAccept(TradeState ts) {
		return sig(ts.toAccept(), ts.pubKey.b);
	}

	public TradeMessage.FirstBail makeFirstBail(TradeState ts) {
		return sig(ts.toFirstBail(), ts.pubKey.b);
	}

	public TradeMessage.Bail makeBail(TradeState ts) {
		return sig(ts.toBail(), ts.pubKey.b);
	}

	private <T extends TradeMessage> T sig(T m, byte[] pubKey) {
		m.signature = ks.getKeys(Utils.sha256hash160(pubKey))[0].sign(Sha256Hash.create(ser.serialize(m)))
				.encodeToDER();
		if (true)
			System.out.println(Common.armor(ser.wrapWithSignature(m), "ATOMIC CROSS-CHAIN TRADE ("
					+ m.getClass().getSimpleName() + ")", true)
					+ "\n");
		return m;
	}

	private static Transaction createRefundTx(NetworkParameters params, long refundAmount, byte[] pubKey,
			long lockTime,
			byte[] bailInHash) {
		Transaction ref = new Transaction(params);
		ref.addInput(new TransactionInput(params, ref, new byte[] {}, new TransactionOutPoint(params, 0,
				new Sha256Hash(bailInHash))));
		ref.addOutput(new TransactionOutput(params, ref, BigInteger.valueOf(refundAmount),
				new Address(params, Utils.sha256hash160(pubKey))));
		ref.setLockTime(lockTime);
		return ref;
	}

	public static void verifyHisSigOnRefund(TradeState s, byte[] sig) throws ScriptException, ProtocolException {
		NetworkParameters params = NetworkDetails.get(s.send).params;
		Transaction bailIn = new Transaction(params, s.bailIn.b);
		Transaction refund = new Transaction(params, s.refund.b);
		refund.getInput(0).setScriptSig(
				new ScriptBuilder()
						.smallNum(0)
						.data(s.sigRefund.b)
						.data(sig)
						.smallNum(1).build());
		refund.getInput(0).getScriptSig().correctlySpends(refund, 0, bailIn.getOutput(0).getScriptPubKey(), false);
	}

	public static Script makeCrossScript(byte[] keyA, byte[] keyB, byte[] hx) {
		return new ScriptBuilder()
				.op(ScriptOpCodes.OP_IF)//if 
				.smallNum(2).data(keyA).data(keyB)
				.smallNum(2).op(ScriptOpCodes.OP_CHECKMULTISIG)
				.op(ScriptOpCodes.OP_ELSE)//else
				.op(ScriptOpCodes.OP_HASH160).data(hx).op(ScriptOpCodes.OP_EQUAL)
				.data(keyB).op(ScriptOpCodes.OP_CHECKSIG)
				.op(ScriptOpCodes.OP_ENDIF)
				.build();
	}

}