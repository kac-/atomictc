package com.kactech.atomictc;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.spongycastle.util.encoders.Hex;

import com.google.bitcoin.core.Utils;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class TradeState {
	public static class Bytes {
		public final byte[] b;
		public final String s;

		public Bytes(byte[] b) {
			this.b = Arrays.copyOf(b, b.length);
			this.s = Utils.bytesToHexString(b);
		}

		public Bytes(String s) {
			this.s = s;
			this.b = Hex.decode(s);
		}

		@Override
		public String toString() {
			return s;
		}

		public static class GsonAdapter extends TypeAdapter<TradeState.Bytes> {
			@Override
			public TradeState.Bytes read(JsonReader in) throws IOException {
				String s = in.nextString();
				return new Bytes(s);
			}

			@Override
			public void write(JsonWriter out, TradeState.Bytes value) throws IOException {
				if (value == null)
					out.nullValue();
				else
					out.value(value.s);
			}
		}
	}

	public static class Unspent {
		public Bytes txid;
		public long vout;
		public String address;
		public Bytes scriptPubKey;
		public double amount;

		public Unspent() {

		}

		public Unspent(Bytes txid, long vout, String address, Bytes scriptPubKey, double amount) {
			super();
			this.txid = txid;
			this.vout = vout;
			this.address = address;
			this.scriptPubKey = scriptPubKey;
			this.amount = amount;
		}

		@Override
		public String toString() {
			return "Unspent [txid=" + txid + ", vout=" + vout + ", address=" + address + ", scriptPubKey="
					+ scriptPubKey + ", amount=" + amount + "]";
		}

	}

	public static class His {
		public Bytes pubKey;
		public Bytes bailInHash;
		public Bytes sigRefund;
		public Bytes bailIn;
		public Long amount;
		public Long refundAmount;
		public Long lockTime;
	}

	String send;
	String receive;
	List<Unspent> unspent;
	BigInteger fee;
	BigInteger change;

	Bytes pubKey;
	Bytes x;
	Bytes hx;
	Bytes bailIn;
	Bytes refund;
	Bytes sigRefund;
	Bytes sigHisRefund;
	Bytes id;

	Long currentBlock, lockTime;
	BigInteger amount;
	Long refundAmount;

	His his = new His();

	@Override
	public String toString() {
		return "AtomicState [send=" + send + ", receive=" + receive + ", unspent=" + unspent
				+ ", fee=" + fee + ", change=" + change + ", myPubKey=" + pubKey + ", hisPubKey=" + his.pubKey + "]";
	}

	public TradeMessage.Init toInit() {
		TradeMessage.Init m = new TradeMessage.Init();
		m.bailInHash = id.b;
		m.pubKey = pubKey.b;
		m.hisPubKey = his.pubKey.b;
		m.hx = hx.b;
		m.lockTime = lockTime;
		m.amount = amount.longValue();
		m.refundAmount = refundAmount;
		m.receive = receive;
		m.send = send;
		return m;
	}

	public TradeMessage.Accept toAccept() {
		TradeMessage.Accept m = new TradeMessage.Accept();
		m.id = id.b;
		m.sigHisRefund = sigHisRefund.b;
		m.bailInHash = Utils.reverseBytes(Utils.doubleDigest(bailIn.b));
		m.lockTime = lockTime;
		m.amount = amount.longValue();
		m.refundAmount = refundAmount;
		return m;
	}

	public TradeMessage.FirstBail toFirstBail() {
		TradeMessage.FirstBail m = new TradeMessage.FirstBail();
		m.id = id.b;
		m.sigHisRefund = sigHisRefund.b;
		m.bailIn = bailIn.b;
		return m;
	}

	public TradeMessage.Bail toBail() {
		TradeMessage.Bail m = new TradeMessage.Bail();
		m.id = id.b;
		m.bailIn = bailIn.b;
		return m;
	}

}