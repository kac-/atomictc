package com.kactech.atomictc;


public interface MessageSerializer {
	public byte[] serialize(TradeMessage msg);

	public TradeMessage deserialize(byte[] msg);

	public byte[] wrapWithSignature(TradeMessage msg);
}