package com.kactech.atomictc;

public class TradeMessage {
	byte[] bytes;
	byte[] signature;

	public static class Init extends TradeMessage {
		byte[] bailInHash;
		byte[] pubKey;
		byte[] hisPubKey;
		byte[] hx;
		String send;
		String receive;
		long lockTime;
		long amount;
		long refundAmount;
	}

	public static class SigRefund extends TradeMessage {
		byte[] id;
		byte[] sigHisRefund;
	}

	public static class Accept extends TradeMessage.SigRefund {
		byte[] bailInHash;
		long lockTime;
		long amount;
		long refundAmount;
	}

	public static class FirstBail extends TradeMessage.SigRefund {
		byte[] bailIn;
	}

	public static class Bail extends TradeMessage {
		byte[] id;
		byte[] bailIn;
	}
}